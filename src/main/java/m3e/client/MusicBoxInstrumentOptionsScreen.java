package m3e.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;
import xerca.xercamusic.common.item.IItemInstrument;
import xerca.xercamusic.common.tile_entity.TileEntityMusicBox;
import m3e.instrument.M3EInstruments;
import m3e.musicbox.MusicBoxLoopMode;
import m3e.network.MusicBoxLibraryStatePacket;
import m3e.songbook.SongbookHeader;
import m3e.sound.InstrumentSoundProfile;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedPerformance;
import m3e.instrument.InstrumentVariations;
import m3e.musicbox.MusicBoxInstrumentProfileAccess;

import java.util.List;
import java.util.UUID;

public final class MusicBoxInstrumentOptionsScreen extends Screen {
    private static final int HEIGHT = InstrumentPanelGeometry.BUTTON_HEIGHT;
    private static final int GAP = InstrumentPanelGeometry.GAP;

    private final @Nullable Screen parent;
    private final BlockPos pos;
    private final MusicBoxModel model = new MusicBoxModel();
    private final SongbookInstrumentPanel songbookPanel;
    private final MusicBoxTarget instrumentTarget = new MusicBoxTarget();
    private final InstrumentOptionsPanel instrumentPanel;
    private InstrumentSoundProfile currentProfile = InstrumentSoundProfile.DEFAULT;
    private String currentVariation = InstrumentVariations.DEFAULT_ID;
    private DexedPatch currentPatch = DexedPatch.initVoice();
    private DexedPerformance currentPerformance = DexedPerformance.DEFAULT;
    private MusicBoxPlaybackControls.State playbackState = new MusicBoxPlaybackControls.State(
            false, MusicBoxLoopMode.NO_LOOP.ordinal(), false, false,
            0L, 1.0D, 120, 0, 0, 0L);
    private MusicBoxLibraryControls.State libraryState =
            MusicBoxLibraryControls.State.empty();
    private int instrumentId = -1;
    private Tab tab = Tab.PLAYBACK;
    private int stateRequestTicks;

    public MusicBoxInstrumentOptionsScreen(@Nullable Screen parent, BlockPos pos) {
        super(Component.literal("Music Box Options"));
        this.parent = parent;
        this.pos = pos.immutable();
        this.songbookPanel = new SongbookInstrumentPanel(Minecraft.getInstance(), this,
                this::addRenderableWidget, this::rebuildWidgets, model);
        this.instrumentPanel = new InstrumentOptionsPanel(Minecraft.getInstance(), this,
                this::addRenderableWidget, this::rebuildWidgets,
                this::rebuildWidgets, instrumentTarget);
    }

    @Override protected void init() {
        clearWidgets();
        songbookPanel.detached();
        TileEntityMusicBox musicBox = musicBox();
        if (musicBox == null || musicBox.getInstrument() == null) {
            closeToParent();
            return;
        }
        instrumentId = instrumentId(musicBox.getInstrument());
        if (instrumentId < 0) {
            closeToParent();
            return;
        }
        currentProfile = MusicBoxInstrumentProfiles.profile(musicBox);
        currentVariation = MusicBoxInstrumentProfiles.variation(musicBox);
        currentPatch = MusicBoxInstrumentProfiles.dexedPatch(musicBox);
        currentPerformance = MusicBoxInstrumentProfiles.dexedPerformance(musicBox);
        playbackState = MusicBoxPlaybackControls.state(musicBox);
        libraryState = MusicBoxLibraryControls.state(musicBox);
        MusicBoxPlaybackControls.request(pos);

        PianoExpandedMenu.Geometry menu = PianoExpandedMenu.standaloneGeometry(
                width, height);
        addRenderableWidget(new PianoExpandedMenu.Background(menu));
        addNavigation(menu.navigation());
        addContent(menu.content(), musicBox);
    }

    private void addNavigation(PianoKeyboardGeometry.Rect area) {
        int x = area.x() + 6;
        int y = area.y() + 6;
        int width = Math.max(1, area.width() - 12);
        addRenderableWidget(new PianoToolbarButton(font, x, y, width, HEIGHT,
                Component.literal("Playback"), ignored -> switchTab(Tab.PLAYBACK),
                () -> tab == Tab.PLAYBACK));
        y += HEIGHT + 4;
        addRenderableWidget(new PianoToolbarButton(font, x, y, width, HEIGHT,
                Component.literal("Instrument"), ignored -> switchTab(Tab.INSTRUMENT),
                () -> tab == Tab.INSTRUMENT));
    }

    private void addContent(PianoKeyboardGeometry.Rect area,
                            TileEntityMusicBox musicBox) {
        int inset = InstrumentPanelGeometry.SECTION_INSET;
        int available = Math.max(1, area.width() - inset * 2);
        int contentWidth = InstrumentPanelGeometry.boundedWidth(available,
                InstrumentPanelGeometry.MAX_WORKSPACE_WIDTH);
        int x = InstrumentPanelGeometry.centeredX(area.x() + inset,
                available, contentWidth);
        int y = area.y() + inset;
        int bottom = area.bottom() - inset;
        if (tab == Tab.PLAYBACK) addPlaybackTab(x, y, contentWidth, bottom);
        else addInstrumentTab(musicBox, x, y, contentWidth, bottom);
    }

    private void addPlaybackTab(int x, int y, int width, int bottom) {
        List<SongbookClientState.BookOption> books;
        UUID activeBook;
        if (libraryState.songbook()) {
            SongbookHeader header = new SongbookHeader(libraryState.libraryId(),
                    libraryState.revision(), libraryState.capacity(),
                    libraryState.entries().size(), 0);
            books = List.of(new SongbookClientState.BookOption(
                    libraryState.libraryId(), libraryState.label(), header, -1));
            activeBook = libraryState.libraryId();
        } else {
            books = List.of();
            activeBook = null;
        }
        songbookPanel.update(libraryState.entries(), books, activeBook,
                libraryState.selectedIndex(), libraryState.capacity());
        songbookPanel.renderWide(x, y, width, bottom);
        songbookPanel.refreshPlayback();
    }

    private void addInstrumentTab(TileEntityMusicBox musicBox, int x, int y,
                                  int width, int bottom) {
        instrumentPanel.renderInstrument(x, y, width, bottom);
    }

    private void switchTab(Tab next) {
        if (tab == next) return;
        setFocused(null);
        songbookPanel.collapseBooks();
        tab = next;
        rebuildWidgets();
    }

    @Override public void tick() {
        super.tick();
        TileEntityMusicBox musicBox = musicBox();
        if (musicBox == null || musicBox.getInstrument() == null) {
            closeToParent();
            return;
        }
        int nextInstrumentId = instrumentId(musicBox.getInstrument());
        MusicBoxLibraryControls.State nextLibrary = MusicBoxLibraryControls.state(musicBox);
        MusicBoxPlaybackControls.State nextPlayback = MusicBoxPlaybackControls.state(musicBox);
        InstrumentSoundProfile nextProfile = MusicBoxInstrumentProfiles.profile(musicBox);
        String nextVariation = MusicBoxInstrumentProfiles.variation(musicBox);
        DexedPatch nextPatch = MusicBoxInstrumentProfiles.dexedPatch(musicBox);
        DexedPerformance nextPerformance = MusicBoxInstrumentProfiles.dexedPerformance(musicBox);
        boolean rebuild = nextInstrumentId != instrumentId
                || !nextLibrary.equals(libraryState)
                || playbackWidgetsChanged(playbackState, nextPlayback)
                || (tab == Tab.INSTRUMENT && (!nextProfile.equals(currentProfile)
                || !nextVariation.equals(currentVariation)
                || !nextPatch.equals(currentPatch)
                || !nextPerformance.equals(currentPerformance)
                || instrumentPanel.needsRerender()));
        instrumentId = nextInstrumentId;
        libraryState = nextLibrary;
        playbackState = nextPlayback;
        currentProfile = nextProfile;
        currentVariation = nextVariation;
        currentPatch = nextPatch;
        currentPerformance = nextPerformance;
        if (rebuild) {
            rebuildWidgets();
            return;
        }
        songbookPanel.refreshPlayback();
        if (++stateRequestTicks >= 20) {
            stateRequestTicks = 0;
            MusicBoxPlaybackControls.request(pos);
        }
    }

    private @Nullable TileEntityMusicBox musicBox() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(pos) instanceof TileEntityMusicBox musicBox
                ? musicBox : null;
    }

    private void closeToParent() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override public void onClose() {
        setFocused(null);
        closeToParent();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean mouseScrolled(double mouseX, double mouseY,
                                           double horizontal, double vertical) {
        if (tab == Tab.INSTRUMENT) instrumentPanel.mouseScrolled(mouseX, mouseY, vertical);
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (tab == Tab.INSTRUMENT) instrumentPanel.mouseClicked(event);
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event,
                                          double deltaX, double deltaY) {
        if (tab == Tab.INSTRUMENT) instrumentPanel.mouseDragged(event);
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (tab == Tab.INSTRUMENT) instrumentPanel.mouseReleased(event);
        return super.mouseReleased(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX,
                                            int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0101010);
    }

    private static int instrumentId(IItemInstrument instrument) {
        return instrument instanceof Item item ? M3EInstruments.protocolId(item) : -1;
    }

    private final class MusicBoxTarget implements InstrumentControlTarget, DexedEditorTarget {
        @Override public int instrumentId() {
            return MusicBoxInstrumentOptionsScreen.this.instrumentId;
        }

        @Override public InstrumentSoundProfile profile() { return currentProfile; }

        @Override public void setProfile(InstrumentSoundProfile profile) {
            currentProfile = profile == null ? InstrumentSoundProfile.DEFAULT : profile;
            publish(false);
        }

        @Override public String variation() { return currentVariation; }

        @Override public void setVariation(String variation) {
            currentVariation = InstrumentVariations.normalize(instrumentId, variation);
            publish(false);
        }

        @Override public DexedEditorTarget dexed() {
            TileEntityMusicBox musicBox = musicBox();
            return musicBox != null && M3EInstruments.isDexed(musicBox.getInstrument())
                    ? this : null;
        }

        @Override public long identity() {
            TileEntityMusicBox musicBox = musicBox();
            if (musicBox instanceof MusicBoxInstrumentProfileAccess access) {
                try {
                    UUID id = UUID.fromString(
                            access.m3e$musicBoxInstrumentInstanceId());
                    return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
                } catch (IllegalArgumentException ignored) { }
            }
            return pos.asLong() ^ ((long) instrumentId << 32);
        }

        @Override public DexedPatch patch() { return currentPatch; }

        @Override public DexedPerformance performance() { return currentPerformance; }

        @Override public void apply(DexedPatch patch, DexedPerformance performance,
                                    boolean force) {
            currentPatch = patch == null ? DexedPatch.initVoice() : patch;
            currentPerformance = performance == null
                    ? DexedPerformance.DEFAULT : performance;
            publish(force);
        }

        private void publish(boolean force) {
            MusicBoxInstrumentProfiles.publish(pos, instrumentId, currentProfile,
                    currentVariation, currentPatch, currentPerformance);
        }
    }

    private final class MusicBoxModel implements SongbookPanelModel {
        @Override public long playheadMicros() { return playbackState.playheadMicros(); }
        @Override public long durationMicros() {
            int selected = libraryState.selectedIndex();
            return selected >= 0 && selected < libraryState.entries().size()
                    ? libraryState.entries().get(selected).durationMicros() : 0L;
        }
        @Override public int tempoBpm() { return playbackState.tempoBpm(); }
        @Override public int displayTempoBpm() {
            TileEntityMusicBox musicBox = musicBox();
            return musicBox == null ? tempoBpm()
                    : MusicBoxPlaybackControls.currentTempoBpm(musicBox, playbackState);
        }
        @Override public void setTempoBpm(int bpm) {
            int value = Math.clamp(bpm, 1, 3_000);
            playbackState = playbackState.withTempo(value);
            if (libraryState.songbook() && libraryState.selectedIndex() >= 0) {
                MusicBoxLibraryControls.setTempo(pos, libraryState.selectedIndex(),
                        libraryState.revision(), value);
            } else {
                MusicBoxPlaybackControls.setTempo(pos, value);
            }
        }
        @Override public double speed() { return playbackState.speed(); }
        @Override public void setSpeed(double speed) {
            playbackState = playbackState.withSpeed(speed);
            MusicBoxPlaybackControls.setSpeed(pos, speed);
        }
        @Override public boolean playing() { return playbackState.playing(); }
        @Override public String stateIcon() { return playbackState.playing() ? "II" : "▶"; }
        @Override public void playPause() {
            if (playbackState.playing()) MusicBoxPlaybackControls.pause(pos);
            else MusicBoxPlaybackControls.start(pos);
            MusicBoxPlaybackControls.request(pos);
        }
        @Override public void stop() {
            MusicBoxPlaybackControls.stop(pos);
            MusicBoxPlaybackControls.request(pos);
        }
        @Override public void seekMicros(long micros) {
            MusicBoxPlaybackControls.seek(pos, micros);
        }
        @Override public int transpose() { return playbackState.transpose(); }
        @Override public boolean canTranspose(int delta) {
            int next = playbackState.transpose() + delta;
            return !libraryState.entries().isEmpty() && next >= -48 && next <= 48;
        }
        @Override public void transpose(int delta) {
            int value = Math.clamp(playbackState.transpose() + delta, -48, 48);
            playbackState = playbackState.withTranspose(value);
            MusicBoxPlaybackControls.setTranspose(pos, value);
        }
        @Override public String loopLabel() { return playbackState.loopLabel(); }
        @Override public void cycleLoopMode() {
            MusicBoxPlaybackControls.toggleLoop(pos);
            MusicBoxPlaybackControls.request(pos);
        }
        @Override public boolean shuffle() { return playbackState.shuffle(); }
        @Override public void setShuffle(boolean value) {
            if (value != playbackState.shuffle()) {
                MusicBoxPlaybackControls.toggleShuffle(pos);
                MusicBoxPlaybackControls.request(pos);
            }
        }
        @Override public boolean canShuffle(int songCount) {
            return libraryState.songbook() && songCount > 1;
        }
        @Override public boolean selectBook(UUID bookId) {
            return libraryState.songbook() && libraryState.libraryId().equals(bookId);
        }
        @Override public void selectSong(int index) {
            if (libraryState.songbook()) {
                MusicBoxLibraryControls.select(pos, index, libraryState.revision());
            }
        }
        @Override public int selectedIndex() { return libraryState.selectedIndex(); }
        @Override public int capacity() { return libraryState.capacity(); }
        @Override public boolean extractSelected() {
            if (!libraryState.songbook() || libraryState.selectedIndex() < 0) return false;
            MusicBoxLibraryControls.extract(pos, libraryState.selectedIndex(),
                    libraryState.revision());
            return true;
        }
        @Override public boolean deleteSelected() {
            if (!libraryState.songbook() || libraryState.selectedIndex() < 0) return false;
            MusicBoxLibraryControls.delete(pos, libraryState.selectedIndex(),
                    libraryState.revision());
            return true;
        }
        @Override public boolean canExtract(boolean hasSongs) {
            return libraryState.songbook() && hasSongs && libraryState.capacity() > 1;
        }
        @Override public boolean canDelete(boolean hasSongs) {
            return libraryState.songbook() && hasSongs;
        }
        @Override public String fixedLibraryLabel(int actualSongs, int totalSlots) {
            return libraryState.songbook()
                    ? libraryState.label() + SongbookInstrumentPanel.occupancySuffix(
                    actualSongs, totalSlots)
                    : "None";
        }
    }

    private static boolean playbackWidgetsChanged(
            MusicBoxPlaybackControls.State before,
            MusicBoxPlaybackControls.State after) {
        return before.loopMode() != after.loopMode()
                || before.shuffle() != after.shuffle()
                || Double.compare(before.speed(), after.speed()) != 0
                || before.tempoBpm() != after.tempoBpm()
                || before.transpose() != after.transpose();
    }

    private enum Tab { PLAYBACK, INSTRUMENT }
}
