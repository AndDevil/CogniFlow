package com.shr.cogniflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shr.cogniflow.config.CogniflowConfig;
import com.shr.cogniflow.dto.MarketDataResponse;
import com.shr.cogniflow.service.AiAnalysisService;
import com.shr.cogniflow.service.EmbeddingService;
import com.shr.cogniflow.service.TickerService;
import com.shr.cogniflow.service.VectorStoreService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MarketDataService {

    private final RestClient restClient;
    private final AiAnalysisService aiService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final CogniflowConfig config;
    private final TickerService tickerService;

    public MarketDataService(RestClient.Builder builder,
                             AiAnalysisService aiService,
                             VectorStoreService vectorStoreService,
                             EmbeddingService embeddingService,
                             ObjectMapper objectMapper,
                             CogniflowConfig config,
                             TickerService tickerService) {
        this.restClient = builder.baseUrl("https://www.alphavantage.co").build();
        this.aiService = aiService;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.config = config;
        this.tickerService = tickerService;

        String apiKey = config.getAlphavantageApiKey();
        if (apiKey == null || apiKey.isEmpty() || "your_default_api_key".equals(apiKey)) {
            log.warn("Alpha Vantage API key is not configured. Please set 'cogniflow.alphavantage-api-key'.");
        }
    }

    /**
     * Runs once every 3 hours (10,800,000 ms).
     * Staying safely under Alpha Vantage's strict free-tier limits.
     */
    @Scheduled(fixedRate = 10800000, initialDelay = 120000)
    public void fetchAndAnalyzeScheduled() {
        log.info("--- Scheduled Pulse: Starting Multi-Ticker Market Scan ---");

        List<String> tickers = tickerService.getTickers();
        for (String symbol : tickers) {
            fetchAndAnalyze(symbol);

            // Defensive Guardrail: Sleep for 15 seconds between tickers
            // to safely avoid bursting the 5 requests/minute threshold.
            try {
                log.info("Cooldown delay between ticker fetches...");
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sync cooldown interrupted", e);
            }
        }
        log.info("--- Multi-Ticker Market Scan Batch Complete ---");
    }

    @CircuitBreaker(name = "alphaVantage", fallbackMethod = "fetchAndAnalyzeFallback")
    public Map<String, Object> fetchAndAnalyze(String query) {
        log.info("CogniFlow is pulling live data for query: {}", query);

        // 0. AI Name Resolution (e.g. "Visa" -> "V")
        String symbol = aiService.resolveCompanyToTicker(query);
        if ("UNKNOWN".equals(symbol)) {
            log.warn("Could not resolve '{}' to a valid publicly traded ticker.", query);
            return Map.of("error", "Could not resolve '" + query + "' to a valid publicly traded ticker symbol.");
        }
        
        log.info("Resolved query '{}' to ticker '{}'", query, symbol);

        // 1. INGESTION: Fetch from Alpha Vantage
        MarketDataResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "GLOBAL_QUOTE")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", config.getAlphavantageApiKey())
                        .build())
                .retrieve()
                .body(MarketDataResponse.class);

        if (response != null && response.getGlobalQuote() != null && response.getGlobalQuote().getPrice() != null) {
            String price = response.getGlobalQuote().getPrice();
            log.info("Success! Current Price for {}: {}", symbol, price);

            // 2. INTELLIGENCE: Get AI Sentiment Analysis
            String rawAiInsight = aiService.analyzeMarketTrend(response.getGlobalQuote());
            String cleanInsight = extractTextFromResponse(rawAiInsight);

            // 3. SMART ENCODING: Convert text to mathematical Vector
            float[] vector = embeddingService.getEmbedding(cleanInsight);

            // 4. PERSISTENCE: Save to Long-Term Memory (Weaviate)
            if (vector != null) {
                vectorStoreService.storeInsight(symbol, price, cleanInsight, vector);
                log.info("Pipeline Step Complete: Smart Insight for {} saved.", symbol);
            } else {
                log.error("Failed to generate vector for {}. Storage aborted.", symbol);
            }

            return Map.of(
                    "symbol", symbol,
                    "price", price,
                    "insight", cleanInsight,
                    "timestamp", System.currentTimeMillis()
            );

        } else {
            log.warn("API limit hit, invalid symbol, or empty response for ticker: {}", symbol);
            return Map.of("error", "Data unavailable or limit reached for symbol: " + symbol);
        }
    }

    /**
     * Fallback for Alpha Vantage ingestion failures.
     */
    public Map<String, Object> fetchAndAnalyzeFallback(String symbol, Throwable t) {
        log.warn("Circuit Breaker [alphaVantage] activated for {}. Reason: {}", symbol, t.getMessage());
        return Map.of(
                "symbol", symbol,
                "error", "Live search temporarily unavailable (Circuit Breaker active)",
                "reason", t.getMessage()
        );
    }

    private String extractTextFromResponse(String rawJson) {
        if (rawJson == null) {
            return "";
        }

        String trimmed = rawJson.trim();

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                JsonNode textNode = root.findValue("text");
                if (textNode != null) {
                    return textNode.asText().trim();
                }
            } catch (Exception e) {
                log.debug("Attempted to parse as JSON but failed, passing text along.", e);
            }
        }

        return trimmed;
    }
}
