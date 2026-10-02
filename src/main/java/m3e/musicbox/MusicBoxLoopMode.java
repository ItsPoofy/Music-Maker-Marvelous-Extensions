package m3e.musicbox;

public enum MusicBoxLoopMode {
    NO_LOOP("No Loop"),
    FINISH_SONGBOOK("Finish Songbook"),
    LOOP_SONG("Loop Song"),
    LOOP_SONGBOOK("Loop Songbook");

    private final String label;

    MusicBoxLoopMode(String label) { this.label = label; }

    public String label() { return label; }

    public MusicBoxLoopMode next(boolean songbook) {
        if (!songbook) return this == LOOP_SONG ? NO_LOOP : LOOP_SONG;
        return switch (this) {
            case NO_LOOP -> FINISH_SONGBOOK;
            case FINISH_SONGBOOK -> LOOP_SONG;
            case LOOP_SONG -> LOOP_SONGBOOK;
            case LOOP_SONGBOOK -> NO_LOOP;
        };
    }

    public static MusicBoxLoopMode byId(int id) {
        MusicBoxLoopMode[] values = values();
        return id < 0 || id >= values.length ? NO_LOOP : values[id];
    }
}
