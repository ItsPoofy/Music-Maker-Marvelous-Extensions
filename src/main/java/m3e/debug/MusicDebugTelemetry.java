package m3e.debug;

import m3e.M3E;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opt-in, local structured diagnostics for music timing and state changes.
 * Events are written only to the normal game log; nothing is transmitted.
 */
public final class MusicDebugTelemetry {
    private static final int MAX_SAMPLE_KEYS = 2_048;
    private static final long STALE_SAMPLE_NANOS = 60_000_000_000L;
    private static final boolean SYSTEM_ENABLED = Boolean.getBoolean("m3e.music.debug");
    private static final Map<String, Long> LAST_SAMPLE_NANOS = new ConcurrentHashMap<>();
    private static volatile boolean clientEnabled;

    private MusicDebugTelemetry() { }

    public static boolean enabled() {
        return SYSTEM_ENABLED || clientEnabled;
    }

    public static void setClientEnabled(boolean enabled) {
        clientEnabled = enabled;
        if (enabled) event("diagnostics", "enabled", "source", "client_config",
                "system_property", SYSTEM_ENABLED);
        else LAST_SAMPLE_NANOS.clear();
    }

    public static void event(String subsystem, String event, Object... fields) {
        if (!enabled()) return;
        M3E.LOGGER.info("[music-debug] {}.{} {}", clean(subsystem), clean(event),
                format(fields));
    }

    /** Logs at most once per key in the requested interval. */
    public static void sampled(String key, long minimumIntervalNanos,
                               String subsystem, String event, Object... fields) {
        if (!enabled()) return;
        long now = System.nanoTime();
        if (LAST_SAMPLE_NANOS.size() > MAX_SAMPLE_KEYS) {
            LAST_SAMPLE_NANOS.entrySet().removeIf(entry ->
                    now - entry.getValue() > STALE_SAMPLE_NANOS);
            if (LAST_SAMPLE_NANOS.size() > MAX_SAMPLE_KEYS) LAST_SAMPLE_NANOS.clear();
        }
        String safeKey = clean(key);
        Long previous = LAST_SAMPLE_NANOS.putIfAbsent(safeKey, now);
        if (previous != null) {
            if (now - previous < Math.max(0L, minimumIntervalNanos)) return;
            if (!LAST_SAMPLE_NANOS.replace(safeKey, previous, now)) return;
        }
        event(subsystem, event, fields);
    }

    public static void warning(String subsystem, String event, Throwable error,
                               Object... fields) {
        if (!enabled()) return;
        M3E.LOGGER.warn("[music-debug] {}.{} {} error={}", clean(subsystem),
                clean(event), format(fields), error == null ? "unknown" : clean(error.toString()));
    }

    private static String format(Object[] fields) {
        if (fields == null || fields.length == 0) return "";
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < fields.length; index += 2) {
            if (output.length() > 0) output.append(' ');
            String name = clean(fields[index]);
            Object value = index + 1 < fields.length ? fields[index + 1] : "missing";
            output.append(name).append('=').append(clean(value));
        }
        return output.toString();
    }

    private static String clean(Object value) {
        String text = String.valueOf(value).replace('\n', ' ').replace('\r', ' ')
                .replace('=', ':').strip();
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }
}
