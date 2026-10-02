package m3e.songbook;

import m3e.sheet.MusicScore;

public record SongbookPlayback(MusicScore song) {
    public SongbookPlayback {
        if (song == null) throw new IllegalArgumentException("missing Songbook playback song");
    }
}
