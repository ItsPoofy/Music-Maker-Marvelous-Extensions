package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;
import xerca.xercamusic.client.ModClient;
import xerca.xercamusic.client.NoteSound;
import xerca.xercamusic.common.item.IItemInstrument;
import m3e.instrument.InstrumentVariations;
import m3e.sound.InstrumentSoundProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Local-only sheet-style audio bridge used by the score import preview. */
final class ScoreImportPreviewOutput implements InstrumentMidiScreenAccess {
    private final Supplier<IItemInstrument> instrument;
    private final Supplier<String> variation;
    private final Supplier<Float> tuning;
    private final Map<Long, Voice> active = new HashMap<>();
    private final Map<Integer, NoteSound> auditions = new HashMap<>();

    ScoreImportPreviewOutput(IItemInstrument instrument) {
        this(() -> instrument);
    }

    ScoreImportPreviewOutput(Supplier<IItemInstrument> instrument) {
        this(instrument, () -> InstrumentOptions.playerDefaultVariation(instrument.get().getInstrumentId()));
    }

    ScoreImportPreviewOutput(Supplier<IItemInstrument> instrument, Supplier<String> variation) {
        this(instrument, variation, () -> InstrumentSoundProfile.DEFAULT_TUNING_HZ);
    }

    ScoreImportPreviewOutput(Supplier<IItemInstrument> instrument, Supplier<String> variation,
                             Supplier<Float> tuning) {
        this.instrument = Objects.requireNonNull(instrument, "instrument");
        this.variation = Objects.requireNonNull(variation, "variation");
        this.tuning = Objects.requireNonNull(tuning, "tuning");
    }

    @Override public boolean playMidiNote(int midiNote, int velocity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return false;
        IItemInstrument selected = instrument();
        IItemInstrument.InsSound sample = InstrumentVariations.resolve(
                selected, midiNote, variation.get());
        if (sample == null) return false;
        long eventId = InstrumentPlaybackContext.songbookEventId();
        if (eventId == 0L) return false;
        RemoteEnvelopeContext.beginProfileOnly(previewProfile());
        NoteSound sound;
        try {
            sound = ModClient.playNote(sample.sound(), minecraft.player.getX(),
                    minecraft.player.getY(), minecraft.player.getZ(), SoundSource.PLAYERS,
                    MidiNoteVolume.scale(1.0F, velocity), sample.pitch(), (byte) -1);
        } finally {
            RemoteEnvelopeContext.end();
        }
        Voice prior = active.put(eventId, new Voice(midiNote, sound));
        stopImmediately(prior);
        return true;
    }

    boolean audition(int midiNote, int velocity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return false;
        IItemInstrument selected = instrument();
        IItemInstrument.InsSound sample = InstrumentVariations.resolve(
                selected, midiNote, variation.get());
        if (sample == null) return false;
        stopAudition(midiNote);
        RemoteEnvelopeContext.beginProfileOnly(previewProfile());
        try {
            NoteSound sound = ModClient.playNote(sample.sound(), minecraft.player.getX(),
                    minecraft.player.getY(), minecraft.player.getZ(), SoundSource.PLAYERS,
                    MidiNoteVolume.scale(1.0F, velocity), sample.pitch(), (byte) -1);
            auditions.put(midiNote, sound);
            return true;
        } finally {
            RemoteEnvelopeContext.end();
        }
    }

    void stopAudition(int midiNote) {
        NoteSound sound = auditions.remove(midiNote);
        if (sound instanceof InstrumentEnvelopeAccess access) access.m3e$stopImmediately();
        else if (sound != null) sound.stopSound();
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
    }

    void stopAuditions() {
        for (int pitch : List.copyOf(auditions.keySet())) stopAudition(pitch);
    }

    @Override public void stopMidiNote(int midiNote) {
        long eventId = InstrumentPlaybackContext.songbookEventId();
        Voice voice = active.remove(eventId);
        stopImmediately(voice);
    }

    @Override public void stopMidiNoteImmediately(int midiNote) {
        long eventId = InstrumentPlaybackContext.songbookEventId();
        Voice voice = active.remove(eventId);
        stopImmediately(voice);
    }

    private static void stopImmediately(@Nullable Voice voice) {
        if (voice == null) return;
        if (voice.sound() instanceof InstrumentEnvelopeAccess access) {
            access.m3e$stopImmediately();
        } else {
            voice.sound().stopSound();
        }
        Minecraft.getInstance().getSoundManager().stop(voice.sound());
    }

    @Override public void applyMidiGlissando(int midiNote, byte[] semitoneWaypoints,
                                              @Nullable byte[] waypointPositions,
                                              int lengthTicks) { }

    @Override public void applyMidiGlissando(long eventId, int midiNote,
                                              byte[] semitoneWaypoints,
                                              @Nullable byte[] waypointPositions,
                                              long durationMicros) {
        Voice voice = active.get(eventId | Long.MIN_VALUE);
        if (voice == null) voice = active.get(eventId);
        if (voice == null) return;
        IItemInstrument.InsSound source = InstrumentVariations.resolve(
                instrument(), midiNote, variation.get());
        if (source == null) return;
        float[] pitches = new float[semitoneWaypoints.length];
        for (int index = 0; index < pitches.length; index++) {
            pitches[index] = source.pitch() * (float) Math.pow(2.0D,
                    semitoneWaypoints[index] / 12.0D);
        }
        int ticks = Math.max(1, (int) Math.min(Integer.MAX_VALUE,
                Math.round(durationMicros / 50_000.0D)));
        if (waypointPositions != null && waypointPositions.length == pitches.length) {
            float[] positions = new float[pitches.length];
            for (int index = 0; index < positions.length; index++) {
                positions[index] = (waypointPositions[index] & 0xFF) / 100.0F;
            }
            voice.sound().setGlissando(pitches, positions, ticks);
        } else {
            voice.sound().setGlissando(pitches, ticks);
        }
    }

    @Override public void setMidiDynamicVolume(int midiNote, float volume) {
        for (Voice voice : active.values()) {
            if (voice.pitch() == midiNote) voice.sound().setDynamicVolume(volume);
        }
    }

    @Override public void setMidiDynamicVolume(long eventId, int midiNote, float volume) {
        Voice voice = active.get(eventId | Long.MIN_VALUE);
        if (voice == null) voice = active.get(eventId);
        if (voice != null) voice.sound().setDynamicVolume(volume);
    }

    @Override public void setMidiNoteExpression(int midiNote, float bendSemitones,
                                                float vibratoDepth) {
        for (Voice voice : active.values()) {
            if (voice.pitch() == midiNote && voice.sound() instanceof InstrumentEnvelopeAccess access) {
                access.m3e$setMidiExpression(bendSemitones, vibratoDepth);
            }
        }
    }

    @Override public void setMidiNoteExpression(long eventId, int midiNote,
                                                 float bendSemitones,
                                                 float vibratoDepth) {
        Voice voice = active.get(eventId | Long.MIN_VALUE);
        if (voice == null) voice = active.get(eventId);
        if (voice != null && voice.sound() instanceof InstrumentEnvelopeAccess access) {
            access.m3e$setMidiExpression(bendSemitones, vibratoDepth);
        }
    }

    @Override public @Nullable NoteSound takeMidiNote(int midiNote) { return null; }
    @Override public void adoptMidiNote(int midiNote, @Nullable NoteSound sound) { }
    @Override public int instrumentId() { return instrument().getInstrumentId(); }
    @Override public boolean playbackInstrumentPresent() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.level != null;
    }
    @Override public void reopenInstrumentScreen() { }
    private IItemInstrument instrument() {
        return Objects.requireNonNull(instrument.get(), "preview instrument");
    }
    private InstrumentSoundProfile previewProfile() {
        Float requested = tuning.get();
        float hz = requested == null ? InstrumentSoundProfile.DEFAULT_TUNING_HZ : requested;
        return InstrumentPlaybackContext.apply(InstrumentSoundProfile.DEFAULT.withTuning(hz));
    }
    private record Voice(int pitch, NoteSound sound) { }
}
