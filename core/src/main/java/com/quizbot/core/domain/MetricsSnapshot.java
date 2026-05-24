package com.quizbot.core.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document("metrics_snapshots")
public record MetricsSnapshot(
        @Id String id,
        Instant savedAt,
        List<CounterEntry> counters,
        List<TimerEntry> timers,
        List<SummaryEntry> summaries
) {
    public record CounterEntry(String name, Map<String, String> tags, double count) {}
    public record TimerEntry(String name, Map<String, String> tags, long count, double totalMs, double maxMs) {}
    public record SummaryEntry(String name, Map<String, String> tags, long count, double total, double max) {}

    public static MetricsSnapshot create(List<CounterEntry> counters,
                                         List<TimerEntry> timers,
                                         List<SummaryEntry> summaries) {
        return new MetricsSnapshot(null, Instant.now(), counters, timers, summaries);
    }
}
