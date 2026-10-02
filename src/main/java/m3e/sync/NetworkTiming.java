package m3e.sync;

public record NetworkTiming(long roundTripNanos, long jitterNanos,
                            long serverMinusClientNanos, long updatedNanos) { }
