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

@Service
@Slf4j
public class MarketDataService {

    private final RestClient restClient;
    private final AiAnalysisService aiService;
    private final VectorStoreService vectorStore;
    private final EmbeddingService embeddingService;

    @Value("${alphavantage.api.key}")
    private String apiKey;

    public MarketDataService(RestClient.Builder builder,
                             AiAnalysisService aiService,
                             VectorStoreService vectorStore,
                             EmbeddingService embeddingService) {
        this.restClient = builder.baseUrl("https://www.alphavantage.co").build();
        this.aiService = aiService;
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
    }

    /**
     * Heartbeat: Runs every hour (3600000ms).
     * You can add more symbols to the list to expand your data lake.
     */
    @Scheduled(fixedRate = 3600000)
    public void fetchAndAnalyzeScheduled() {
        log.info("--- Scheduled Pulse: Starting Automated Market Scan ---");
        fetchAndAnalyze("IBM");
        // fetchAndAnalyze("MSFT"); // Feel free to add more!
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

            if (response != null && response.getGlobalQuote() != null) {
                String price = response.getGlobalQuote().getPrice();
                log.info("Success! Current Price for {}: {}", symbol, price);

                // 2. INTELLIGENCE: Get AI Sentiment Analysis
                String rawAiInsight = aiService.analyzeMarketTrend(response.getGlobalQuote());
                String cleanInsight = extractTextFromResponse(rawAiInsight);

                // 3. SMART ENCODING: Convert text to mathematical Vector
                float[] vector = embeddingService.getEmbedding(cleanInsight);

                // 4. PERSISTENCE: Save to Long-Term Memory (Weaviate)
                if (vector != null) {
                    vectorStore.storeInsight(symbol, price, cleanInsight, vector);
                    log.info("--- COGNIFLOW PIPELINE COMPLETE ---");
                    log.info("Insight: {}", cleanInsight);
                    log.info("------------------------------------");
                } else {
                    log.error("Failed to generate vector. Memory storage aborted.");
                }

            } else {
                log.warn("API limit or symbol error. Alpha Vantage is blocking the request.");
            }
        } catch (Exception e) {
            log.error("Pipeline failure: Ensure all Docker containers and API keys are active.", e);
        }
    }

    /**
     * Extracts the core text from the Gemini JSON response.
     */
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