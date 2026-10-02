package m3e.sync;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sliding NTP estimator. It rejects high-queueing samples and fits clock
 * offset over client time so long sessions do not accumulate clock drift.
 */
public final class ClockOffsetEstimator {
    private static final int MAX_SAMPLES = 24;
    private static final double MAX_SLOPE = 500.0D / 1_000_000.0D;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();

    public void add(long clientSent, long serverReceived, long serverSent,
                    long clientReceived) {
        long rtt = (clientReceived - clientSent) - (serverSent - serverReceived);
        if (rtt <= 0L || rtt > 5_000_000_000L) return;
        long clientMid = clientSent + (clientReceived - clientSent) / 2L;
        long serverMid = serverReceived + (serverSent - serverReceived) / 2L;
        samples.addLast(new Sample(clientMid, serverMid - clientMid, rtt));
        while (samples.size() > MAX_SAMPLES) samples.removeFirst();
    }

    public boolean ready() { return samples.size() >= 2; }

    public long serverMinusClient(long clientNow) {
        if (samples.isEmpty()) return 0L;
        List<Sample> accepted = acceptedSamples();
        if (accepted.size() == 1) return accepted.getFirst().offset;
        double origin = accepted.getFirst().clientMid;
        double meanX = 0.0D;
        double meanY = 0.0D;
        for (Sample sample : accepted) {
            meanX += sample.clientMid - origin;
            meanY += sample.offset;
        }
        meanX /= accepted.size();
        meanY /= accepted.size();
        double numerator = 0.0D;
        double denominator = 0.0D;
        for (Sample sample : accepted) {
            double x = sample.clientMid - origin - meanX;
            numerator += x * (sample.offset - meanY);
            denominator += x * x;
        }
        double slope = denominator == 0.0D ? 0.0D
                : Math.clamp(numerator / denominator, -MAX_SLOPE, MAX_SLOPE);
        return Math.round(meanY + slope * (clientNow - origin - meanX));
    }

    public long bestRttNanos() {
        return samples.stream().mapToLong(Sample::rtt).min().orElse(0L);
    }

    public void clear() { samples.clear(); }

    private List<Sample> acceptedSamples() {
        long minimum = samples.stream().mapToLong(Sample::rtt)
                .min().orElse(Long.MAX_VALUE);
        long cutoff = minimum + Math.max(10_000_000L, minimum / 3L);
        List<Sample> accepted = new ArrayList<>();
        for (Sample sample : samples) {
            if (sample.rtt <= cutoff) accepted.add(sample);
        }
        accepted.sort(Comparator.comparingLong(Sample::clientMid));
        return accepted;
    }

    private record Sample(long clientMid, long offset, long rtt) { }
}
