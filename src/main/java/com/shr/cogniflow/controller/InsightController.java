package com.shr.cogniflow.controller;

import com.shr.cogniflow.MarketDataService;
import com.shr.cogniflow.service.EmbeddingService;
import com.shr.cogniflow.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@Slf4j
public class InsightController {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final MarketDataService marketDataService;

    // ISO formatter for human readability (e.g., 2026-05-18 06:58:58)
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
                    .withZone(ZoneId.systemDefault());

    /**
     * Live on-demand analysis for ANY stock symbol.
     * This triggers the full pipeline: Fetch -> AI Analysis -> Vector Store.
     */
    @GetMapping("/live/{symbol}")
    public ResponseEntity<Map<String, Object>> getLiveInsight(@PathVariable String symbol) {
        log.info("REST request for live on-demand analysis of symbol: {}", symbol);
        Map<String, Object> result = marketDataService.fetchAndAnalyze(symbol);

        if (result.containsKey("error")) {
            return ResponseEntity.status(503).body(result);
        }

        // Format timestamp for consistency
        Map<String, Object> formattedResult = new LinkedHashMap<>(result);
        if (formattedResult.containsKey("timestamp")) {
            formattedResult.put("timestamp", ISO_FORMATTER.format(Instant.ofEpochMilli((Long) result.get("timestamp"))));
        }

        return ResponseEntity.ok(formattedResult);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchInsights(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int limit) {

        log.info("REST request to search insights for conceptual query: '{}'", query);

        // 1. Vectorize the search query phrase
        float[] queryVector = embeddingService.getEmbedding(query);
        if (queryVector == null) {
            return ResponseEntity.internalServerError().build();
        }

        // 2. Query the vector database
        List<Map<String, Object>> rawResults = vectorStoreService.semanticSearch(queryVector, limit);

        // 3. Presentation Layer Transformation: Format the timestamps
        List<Map<String, Object>> formattedResults = new ArrayList<>();

        for (Map<String, Object> rawMap : rawResults) {
            // Create a mutable copy to avoid immutable map mutations
            Map<String, Object> formattedMap = new LinkedHashMap<>(rawMap);

            if (formattedMap.containsKey("timestamp") && formattedMap.get("timestamp") instanceof Number) {
                long epochMilli = ((Number) formattedMap.get("timestamp")).longValue();

                // Convert epoch milliseconds directly into an ISO formatted string
                String cleanIsoDate = ISO_FORMATTER.format(Instant.ofEpochMilli(epochMilli));

                formattedMap.put("timestamp", cleanIsoDate);
            }

            formattedResults.add(formattedMap);
        }

        return ResponseEntity.ok(formattedResults);
    }
}
