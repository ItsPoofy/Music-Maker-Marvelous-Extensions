package m3e.sheet;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import xerca.xercamusic.common.item.Items;

/** Adapts canonical timing to base UI clocks and reads legacy sheet timing during import. */
public record SheetTiming(int projectionBps, int musicalTempoBpm, int lengthBeats) {
    public static final int DEFAULT_TEMPO_BPM = 120;
    private static final int COMPATIBILITY_PROJECTION_BPS = 50;

    public SheetTiming {
        if (projectionBps < 1 || projectionBps > 50) {
            throw new IllegalArgumentException("projection BPS must be between 1 and 50");
        }
        if (musicalTempoBpm < 1 || musicalTempoBpm > 3_000) {
            throw new IllegalArgumentException("musical tempo must be between 1 and 3000 BPM");
        }
        if (lengthBeats < 0) throw new IllegalArgumentException("sheet length cannot be negative");
    }

    public static SheetTiming from(ItemStack sheet) {
        MusicScore score = sheet.get(MusicSheetComponents.SCORE);
        if (score != null) {
            long durationMicros = score.tempoMap().microsAt(score.lengthTick());
            return new SheetTiming(20, score.initialBpm(),
                    (int) Math.min(Integer.MAX_VALUE,
                            Math.max(1L, (durationMicros + 49_999L) / 50_000L)));
        }
        int projectionBps = Math.clamp(
                sheet.getOrDefault(Items.SHEET_BPS, (byte) 2) & 0xFF, 1, 50);
        int lengthBeats = Math.max(0, sheet.getOrDefault(Items.SHEET_LENGTH, 0));
        return fromStored(projectionBps,
                sheet.get(MusicSheetComponents.SOURCE_BPM), lengthBeats);
    }

    public static SheetTiming from(MusicScore song,
                                   MusicScore.Projection projection) {
        return new SheetTiming(projection.bps(), song.initialBpm(), projection.length());
    }

    public static SheetTiming fromStored(int projectionBps,
                                         @Nullable Integer sourceTempoBpm,
                                         int lengthBeats) {
        int boundedProjection = Math.clamp(projectionBps, 1, 50);
        return new SheetTiming(boundedProjection,
                resolveMusicalTempo(sourceTempoBpm, boundedProjection),
                Math.max(0, lengthBeats));
    }

    public SheetTiming withMusicalTempo(int sourceTempoBpm) {
        return new SheetTiming(projectionBps,
                Math.clamp(sourceTempoBpm, 1, 3_000), lengthBeats);
    }

    public int playbackTempoBpm(int configuredTempoBpm) {
        if (configuredTempoBpm <= 0) return musicalTempoBpm;
        int configured = Math.clamp(configuredTempoBpm, 1, 3_000);
        return projectionBps == COMPATIBILITY_PROJECTION_BPS
                && configured == 3_000 && musicalTempoBpm != 3_000
                ? musicalTempoBpm : configured;
    }

    public long durationMicros() {
        return Math.round(lengthBeats * 1_000_000.0D / projectionBps);
    }

    public double effectiveRate(double speed, int configuredTempoBpm) {
        return Math.clamp(speed, 0.25D, 2.0D)
                * playbackTempoBpm(configuredTempoBpm) / musicalTempoBpm;
    }

    public double nativeClockScale() {
        return musicalTempoBpm / (projectionBps * 60.0D);
    }

    public static void clearLegacyComponents(ItemStack sheet) {
        sheet.remove(Items.SHEET_BPS);
        sheet.remove(Items.SHEET_LENGTH);
    }

    public static boolean hasCanonicalClock(ItemStack sheet) {
        if (sheet.get(MusicSheetComponents.SCORE) != null) return true;
        if (sheet.get(Items.SHEET_ID) == null) return false;
        int bps = sheet.getOrDefault(Items.SHEET_BPS, (byte) 0) & 0xFF;
        int length = sheet.getOrDefault(Items.SHEET_LENGTH, 0);
        return bps < 1 || bps > 50 || length < 1;
    }

    public static int resolveMusicalTempo(@Nullable Integer sourceTempoBpm,
                                          int projectionBps) {
        if (sourceTempoBpm != null) return Math.clamp(sourceTempoBpm, 1, 3_000);
        int boundedProjection = Math.clamp(projectionBps, 1, 50);
        if (boundedProjection == COMPATIBILITY_PROJECTION_BPS) {
            return DEFAULT_TEMPO_BPM;
        }
        return Math.clamp(boundedProjection * 60, 1, 3_000);
    }
}
