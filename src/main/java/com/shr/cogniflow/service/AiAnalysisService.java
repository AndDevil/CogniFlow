package com.shr.cogniflow.service;

import com.shr.cogniflow.config.CogniflowConfig;
import com.shr.cogniflow.dto.GlobalQuote;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AiAnalysisService {

    private final RestClient restClient;
    private final CogniflowConfig config;

    public AiAnalysisService(RestClient.Builder builder, CogniflowConfig config) {
        this.restClient = builder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.config = config;
    }

    @CircuitBreaker(name = "geminiAi", fallbackMethod = "analyzeMarketTrendFallback")
    public String analyzeMarketTrend(GlobalQuote quote) {
        log.info("Asking AI for a vibe check on {}...", quote.getSymbol());

        String prompt = String.format(
                "Analyze this asset: %s. Price: %s. Change: %s. Give a 2 sentence summary.",
                quote.getSymbol(), quote.getPrice(), quote.getChangePercent()
        );

        var requestBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );

        String apiKey = config.getGoogleAiApiKey();

        try {
            Map response = restClient.post()
                    .uri("/v1/models/gemini-2.5-flash:generateContent?key=" + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return extractTextFromResponse(response);

        } catch (HttpStatusCodeException e) {
            log.warn("Gemini API call failed with status: {}. Re-throwing for circuit breaker.", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            log.error("Critical failure during AI text generation. Re-throwing for circuit breaker.", e);
            throw e;
        }
    }

    /**
     * Fallback method for analyzeMarketTrend when Gemini API fails or the circuit is open.
     */
    public String analyzeMarketTrendFallback(GlobalQuote quote, Throwable t) {
        log.warn("Circuit Breaker [geminiAi] activated for {}. Reason: {}", quote.getSymbol(), t.getMessage());
        return String.format("Fallback Insight: The asset %s is currently trading at $%s. Advanced AI analysis is temporarily unavailable (Circuit Breaker active).",
                quote.getSymbol(), quote.getPrice());
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map response) {
        try {
            if (response != null && response.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> firstCandidate = candidates.get(0);
                    Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (!parts.isEmpty()) {
                        return (String) parts.get(0).get("text");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Gemini response map", e);
        }
        return "Unable to extract insight at this time.";
    }
}
