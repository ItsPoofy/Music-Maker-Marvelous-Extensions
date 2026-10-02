package m3e.client;

import m3e.sheet.MusicScore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Draft retention and acknowledgement rules, independent of screens and networking. */
final class SheetDraftStore {
    record Draft(MusicScore song, int baseVersion, boolean importing, boolean sign,
                 int highlightInterval, int instrument, boolean instrumentLocked) { }
    record Pending(UUID requestId, UUID key, UUID sheetId, ScoreEditSession session,
                   long revision, Draft draft, long startedNanos) { }
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Map<UUID, Pending> requests = new HashMap<>();

    Draft get(UUID key) { return drafts.get(key); }
    void discard(UUID key) { drafts.remove(key); }
    boolean pending(UUID key) { return requests.values().stream().anyMatch(save -> save.key().equals(key)); }
    void retain(UUID key, Draft draft) { drafts.put(key, draft); }

    Pending begin(UUID key, UUID sheetId, ScoreEditSession session, Draft draft, long now) {
        if (pending(key)) return null;
        Pending save = new Pending(UUID.randomUUID(), key, sheetId, session, session.revision(), draft, now);
        drafts.put(key, draft);
        requests.put(save.requestId(), save);
        return save;
    }

    Pending finish(UUID requestId, boolean success, UUID sheetId, int version) {
        Pending save = requests.get(requestId);
        if (save == null) return null;
        if (success && (!save.draft().importing() && (!save.sheetId().equals(sheetId)
                || version != save.draft().baseVersion() + 1) || version < 1)) return null;
        requests.remove(requestId);
        if (success) {
            save.session().acknowledge(save.revision());
            drafts.remove(save.key(), save.draft());
            if (save.session().revision() != save.revision()) {
                drafts.put(sheetId, updated(save, version, false));
            }
        } else {
            drafts.put(save.key(), updated(save, save.draft().baseVersion(), save.draft().importing()));
        }
        return save;
    }

    private static Draft updated(Pending save, int version, boolean importing) {
        Draft old = save.draft();
        return new Draft(save.session().song(), version, importing, old.sign(),
                old.highlightInterval(), old.instrument(), old.instrumentLocked());
    }
    List<Pending> expired(long now, long timeoutNanos) {
        return requests.values().stream().filter(save -> now - save.startedNanos() >= timeoutNanos).toList();
    }
    void clear() { drafts.clear(); requests.clear(); }
}
