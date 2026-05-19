package com.shr.cogniflow;

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

    // A curated, safe list of tickers to track within daily free-tier limits
    private static final List<String> TRACKED_TICKERS = List.of("IBM", "AAPL", "MSFT");

    @Value("${alphavantage.api.key}")
    private String apiKey;

    public MarketDataService(RestClient.Builder builder,
                             AiAnalysisService aiService,
                             VectorStoreService vectorStoreService,
                             EmbeddingService embeddingService) {
        this.restClient = builder.baseUrl("https://www.alphavantage.co").build();
        this.aiService = aiService;
        this.vectorStoreService = vectorStoreService;
        this.embeddingService = embeddingService;
    }

    /**
     * Runs once every 3 hours (10,800,000 ms).
     * With 3 tickers, this consumes 24 API calls per day, staying safely
     * under Alpha Vantage's strict 25 requests/day limit.
     */
    @Scheduled(fixedRate = 10800000)
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

    private String extractTextFromResponse(String rawJson) {
        try {
            if (rawJson.contains("\"text\": \"")) {
                String text = rawJson.split("\"text\": \"")[1].split("\"")[0];
                return text.replace("\\n", " ").trim();
            }
        } catch (Exception e) {
            log.warn("AI parsing glitch, falling back to raw output.");
        }
        return rawJson;
    }
}