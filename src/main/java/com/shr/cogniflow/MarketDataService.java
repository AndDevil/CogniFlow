package com.shr.cogniflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shr.cogniflow.dto.MarketDataResponse;
import com.shr.cogniflow.service.AiAnalysisService;
import com.shr.cogniflow.service.EmbeddingService;
import com.shr.cogniflow.service.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Slf4j
public class MarketDataService {

    private final RestClient restClient;
    private final AiAnalysisService aiService;
    private final VectorStoreService vectorStoreService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper; // Built-in Spring bean for safe JSON parsing
    private final String apiKey;

    // A curated, safe list of tickers to track within daily free-tier limits
    private static final List<String> TRACKED_TICKERS = List.of("IBM", "AAPL", "MSFT");

    // Fixed the lifecycle bug by shifting @Value injection to the constructor parameters
    public MarketDataService(RestClient.Builder builder,
                             AiAnalysisService aiService,
                             VectorStoreService vectorStoreService,
                             EmbeddingService embeddingService,
                             ObjectMapper objectMapper,
                             @Value("${alphavantage.api.key}") String apiKey) {
        this.restClient = builder.baseUrl("https://www.alphavantage.co").build();
        this.aiService = aiService;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;

        // This check will now execute correctly on startup since apiKey is fully initialized
        if (this.apiKey == null || this.apiKey.isEmpty() || "your_default_api_key".equals(this.apiKey)) {
            log.warn("Alpha Vantage API key is not configured. Please set the 'alphavantage.api.key' property.");
        }
    }

    /**
     * Runs once every 3 hours (10,800,000 ms).
     * With 3 tickers, this consumes 24 API calls per day, staying safely
     * under Alpha Vantage's strict 25 requests/day limit.
     */
    @Scheduled(fixedRate = 10800000, initialDelay = 120000)
    public void fetchAndAnalyzeScheduled() {
        log.info("--- Scheduled Pulse: Starting Multi-Ticker Market Scan ---");

        for (String symbol : TRACKED_TICKERS) {
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

    public void fetchAndAnalyze(String symbol) {
        log.info("CogniFlow is pulling data for: {}", symbol);

        try {
            // 1. INGESTION: Fetch from Alpha Vantage
            MarketDataResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", "GLOBAL_QUOTE")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", apiKey)
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

            } else {
                log.warn("API limit hit, invalid symbol, or empty response for ticker: {}", symbol);
            }
        } catch (Exception e) {
            log.error("Pipeline failure on ticker " + symbol, e);
        }
    }

    // Upgraded to robust JSON parsing using ObjectMapper tree traversal while preserving fallback logic
    // Replace your current extractTextFromResponse method with this temporary diagnostic version
    private String extractTextFromResponse(String rawJson) {
        if (rawJson == null) {
            return "";
        }

        String trimmed = rawJson.trim();

        // Defensive check: Only attempt JSON parsing if the string actually looks like JSON
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                JsonNode textNode = root.findValue("text");
                if (textNode != null) {
                    return textNode.asText().trim();
                }
            } catch (Exception e) {
                // Log as debug or fine-grained tracing instead of a scary warning
                log.debug("Attempted to parse as JSON but failed, passing text along.", e);
            }
        }

        // If it's plain text (like your current AI output), cleanly return it as-is
        return trimmed;
    }
}