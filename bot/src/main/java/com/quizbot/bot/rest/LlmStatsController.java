package com.quizbot.bot.rest;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class LlmStatsController {

    private final MeterRegistry meterRegistry;
    private final String adminApiKey;

    public LlmStatsController(MeterRegistry meterRegistry,
                               @Value("${admin.api.key:secret-key}") String adminApiKey) {
        this.meterRegistry = meterRegistry;
        this.adminApiKey = adminApiKey;
    }

    @GetMapping("/llm-stats")
    public Mono<ResponseEntity<Object>> getLlmStats(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {
        if (!adminApiKey.equals(apiKey)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied"));
        }

        List<String> operations = new ArrayList<>();
        meterRegistry.find("llm.api.calls").counters().forEach(c -> {
            String op = c.getId().getTag("operation");
            if (op != null && !operations.contains(op)) operations.add(op);
        });
        meterRegistry.find("llm.api.duration").timers().forEach(t -> {
            String op = t.getId().getTag("operation");
            if (op != null && !operations.contains(op)) operations.add(op);
        });
        Collections.sort(operations);

        List<Map<String, Object>> result = operations.stream().map(op -> {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("operation", op);
            stat.put("successCount", (long) meterRegistry.find("llm.api.calls")
                    .tag("operation", op).tag("status", "success")
                    .counters().stream().mapToDouble(c -> c.count()).sum());
            stat.put("errorCount", (long) meterRegistry.find("llm.api.calls")
                    .tag("operation", op).tag("status", "error")
                    .counters().stream().mapToDouble(c -> c.count()).sum());

            Timer timer = meterRegistry.find("llm.api.duration")
                    .tag("operation", op).tag("status", "success").timer();
            if (timer != null && timer.count() > 0) {
                stat.put("meanDurationMs", Math.round(timer.mean(TimeUnit.MILLISECONDS)));
                stat.put("maxDurationMs", Math.round(timer.max(TimeUnit.MILLISECONDS)));
            } else {
                stat.put("meanDurationMs", 0L);
                stat.put("maxDurationMs", 0L);
            }

            DistributionSummary tokens = meterRegistry.find("llm.api.tokens")
                    .tag("operation", op).summary();
            if (tokens != null && tokens.count() > 0) {
                stat.put("totalTokens", Math.round(tokens.totalAmount()));
                stat.put("meanTokens", Math.round(tokens.mean()));
            } else {
                stat.put("totalTokens", 0L);
                stat.put("meanTokens", 0L);
            }

            return stat;
        }).toList();

        return Mono.just(ResponseEntity.ok(result));
    }
}
