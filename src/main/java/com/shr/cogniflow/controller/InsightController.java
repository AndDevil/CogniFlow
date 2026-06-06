package com.shr.cogniflow.controller;

import com.shr.cogniflow.MarketDataService;
import com.shr.cogniflow.service.EmbeddingService;
import com.shr.cogniflow.service.TickerService;
import com.shr.cogniflow.service.VectorStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Market Insights", description = "Endpoints for searching and generating AI-powered market analysis")
public class InsightController {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final MarketDataService marketDataService;
    private final TickerService tickerService;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
                    .withZone(ZoneId.systemDefault());

    @Operation(summary = "Perform live on-demand analysis", description = "Fetches real-time data for a ticker and generates a fresh AI insight")
    @GetMapping("/live/{symbol}")
    public ResponseEntity<Map<String, Object>> getLiveInsight(
            @Parameter(description = "The stock ticker symbol (e.g., TSLA, AAPL)") @PathVariable String symbol) {
        log.info("REST request for live on-demand analysis of symbol: {}", symbol);
        Map<String, Object> result = marketDataService.fetchAndAnalyze(symbol);

        if (result.containsKey("error")) {
            if ("UNKNOWN_TICKER".equals(result.get("errorCode"))) {
                return ResponseEntity.status(404).body(result);
            }
            return ResponseEntity.status(503).body(result);
        }

        Map<String, Object> formattedResult = new LinkedHashMap<>(result);
        if (formattedResult.containsKey("timestamp")) {
            formattedResult.put("timestamp", ISO_FORMATTER.format(Instant.ofEpochMilli((Long) result.get("timestamp"))));
        }

        return ResponseEntity.ok(formattedResult);
    }

    @Operation(summary = "Search historical insights", description = "Performs semantic vector search or hybrid search across all stored AI insights")
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchInsights(
            @Parameter(description = "Natural language query (e.g., 'bullish tech stocks')") @RequestParam String query,
            @Parameter(description = "Maximum number of results to return") @RequestParam(defaultValue = "3") int limit,
            @Parameter(description = "Enable hybrid search (combining semantic and keyword)") @RequestParam(defaultValue = "true") boolean hybrid) {

        log.info("REST request to search insights for conceptual query: '{}', hybrid: {}", query, hybrid);

        float[] queryVector = embeddingService.getEmbedding(query);

        List<Map<String, Object>> rawResults;
        if (hybrid) {
            rawResults = vectorStoreService.hybridSearch(query, queryVector, limit);
        } else {
            rawResults = vectorStoreService.semanticSearch(queryVector, limit);
        }

        List<Map<String, Object>> formattedResults = new ArrayList<>();
        for (Map<String, Object> rawMap : rawResults) {
            Map<String, Object> formattedMap = new LinkedHashMap<>(rawMap);
            if (formattedMap.containsKey("timestamp") && formattedMap.get("timestamp") instanceof Number) {
                long epochMilli = ((Number) formattedMap.get("timestamp")).longValue();
                formattedMap.put("timestamp", ISO_FORMATTER.format(Instant.ofEpochMilli(epochMilli)));
            }
            formattedResults.add(formattedMap);
        }

        return ResponseEntity.ok(formattedResults);
    }

    @Operation(summary = "Get data pipeline status and metrics", description = "Fetches the latest ingestion times, statuses, and global metrics from Weaviate")
    @GetMapping("/pipeline/status")
    public ResponseEntity<Map<String, Object>> getPipelineStatus() {
        log.info("REST request to get data pipeline status and metrics");

        List<String> tickers = tickerService.getTickers();
        List<Map<String, Object>> tickerStatuses = new ArrayList<>();
        long now = System.currentTimeMillis();
        long sixHoursMs = 6 * 60 * 60 * 1000L;

        for (String ticker : tickers) {
            List<Map<String, Object>> latestList = vectorStoreService.getLatestInsightBySymbol(ticker);
            Map<String, Object> statusMap = new LinkedHashMap<>();
            statusMap.put("symbol", ticker);

            if (latestList != null && !latestList.isEmpty()) {
                Map<String, Object> latest = latestList.get(0);
                if (latest.containsKey("timestamp") && latest.get("timestamp") instanceof Number) {
                    long timestamp = ((Number) latest.get("timestamp")).longValue();
                    statusMap.put("lastIngested", ISO_FORMATTER.format(Instant.ofEpochMilli(timestamp)));
                    
                    if (now - timestamp > sixHoursMs) {
                        statusMap.put("status", "stale");
                    } else {
                        statusMap.put("status", "healthy");
                    }
                } else {
                    statusMap.put("lastIngested", null);
                    statusMap.put("status", "failed");
                }
            } else {
                statusMap.put("lastIngested", null);
                statusMap.put("status", "failed");
            }
            tickerStatuses.add(statusMap);
        }

        Long lastGlobalRunEpoch = vectorStoreService.getLastGlobalIngestionTime();
        String lastGlobalRun = lastGlobalRunEpoch != null ? ISO_FORMATTER.format(Instant.ofEpochMilli(lastGlobalRunEpoch)) : "Never";
        int totalInsights = vectorStoreService.getTotalInsightsCount();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("lastGlobalRun", lastGlobalRun);
        response.put("totalInsights", totalInsights);
        response.put("tickers", tickerStatuses);

        return ResponseEntity.ok(response);
    }
}
