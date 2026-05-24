package com.quizbot.app;

import com.quizbot.core.domain.MetricsSnapshot;
import com.quizbot.core.repository.MetricsSnapshotRepository;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MetricsPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(MetricsPersistenceService.class);

    private final MeterRegistry meterRegistry;
    private final MetricsSnapshotRepository repository;

    public MetricsPersistenceService(MeterRegistry meterRegistry, MetricsSnapshotRepository repository) {
        this.meterRegistry = meterRegistry;
        this.repository = repository;
    }

    @PostConstruct
    public void loadMetrics() {
        MetricsSnapshot snapshot = repository.findTopByOrderBySavedAtDesc().block();
        if (snapshot == null) {
            log.info("Снимок метрик не найден в БД, начинаем с нуля");
            return;
        }
        log.info("Восстанавливаем метрики из снимка от {}", snapshot.savedAt());

        for (MetricsSnapshot.CounterEntry entry : snapshot.counters()) {
            meterRegistry.counter(entry.name(), toTagArray(entry.tags())).increment(entry.count());
        }

        for (MetricsSnapshot.TimerEntry entry : snapshot.timers()) {
            if (entry.count() > 0) {
                Timer timer = meterRegistry.timer(entry.name(), toTagArray(entry.tags()));
                long meanNanos = (long) (entry.totalMs() / entry.count() * 1_000_000);
                for (long i = 0; i < entry.count(); i++) {
                    timer.record(Duration.ofNanos(meanNanos));
                }
            }
        }

        for (MetricsSnapshot.SummaryEntry entry : snapshot.summaries()) {
            if (entry.count() > 0) {
                DistributionSummary summary = meterRegistry.summary(entry.name(), toTagArray(entry.tags()));
                double mean = entry.total() / entry.count();
                for (long i = 0; i < entry.count(); i++) {
                    summary.record(mean);
                }
            }
        }

        log.info("Метрики восстановлены: счётчиков={}, таймеров={}, распределений={}",
                snapshot.counters().size(), snapshot.timers().size(), snapshot.summaries().size());
    }

    @PreDestroy
    public void saveMetrics() {
        log.info("Сохраняем снимок метрик в БД...");

        List<MetricsSnapshot.CounterEntry> counters = meterRegistry.find("llm.api.calls").counters()
                .stream()
                .map(c -> new MetricsSnapshot.CounterEntry(c.getId().getName(), tagsToMap(c), c.count()))
                .collect(Collectors.toList());

        List<MetricsSnapshot.TimerEntry> timers = meterRegistry.find("llm.api.duration").timers()
                .stream()
                .map(t -> new MetricsSnapshot.TimerEntry(
                        t.getId().getName(), tagsToMap(t),
                        t.count(),
                        t.totalTime(TimeUnit.MILLISECONDS),
                        t.max(TimeUnit.MILLISECONDS)))
                .collect(Collectors.toList());

        List<MetricsSnapshot.SummaryEntry> summaries = meterRegistry.find("llm.api.tokens").summaries()
                .stream()
                .map(s -> new MetricsSnapshot.SummaryEntry(
                        s.getId().getName(), tagsToMap(s),
                        (long) s.count(),
                        s.totalAmount(),
                        s.max()))
                .collect(Collectors.toList());

        if (counters.isEmpty() && timers.isEmpty() && summaries.isEmpty()) {
            log.info("Метрик нет — снимок не сохраняется");
            return;
        }

        repository.deleteAll()
                .then(repository.save(MetricsSnapshot.create(counters, timers, summaries)))
                .block();

        log.info("Снимок метрик сохранён: счётчиков={}, таймеров={}, распределений={}",
                counters.size(), timers.size(), summaries.size());
    }

    private String[] toTagArray(Map<String, String> tags) {
        List<String> list = new ArrayList<>();
        tags.forEach((k, v) -> { list.add(k); list.add(v); });
        return list.toArray(new String[0]);
    }

    private Map<String, String> tagsToMap(io.micrometer.core.instrument.Meter meter) {
        Map<String, String> map = new LinkedHashMap<>();
        meter.getId().getTags().forEach(t -> map.put(t.getKey(), t.getValue()));
        return map;
    }
}
