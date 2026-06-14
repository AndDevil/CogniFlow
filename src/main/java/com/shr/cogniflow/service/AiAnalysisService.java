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
        this.restClient = builder.baseUrl("https://api.groq.com/openai/v1").build();
        this.config = config;
    }

    @CircuitBreaker(name = "groqAi", fallbackMethod = "analyzeMarketTrendFallback")
    public String analyzeMarketTrend(GlobalQuote quote) {
        log.info("Asking AI for a vibe check on {}...", quote.getSymbol());

        String apiKey = config.getGroqApiKey();
        if (apiKey == null || apiKey.isEmpty() || "YOUR_GROQ_API_KEY".equals(apiKey)) {
            log.error("PRODUCTION ERROR: Groq API Key is missing for analysis. Circuit breaker will trip.");
            throw new IllegalStateException("AI Analysis failed: Missing Groq API Key.");
        }
        apiKey = apiKey.trim();

        String prompt = String.format(
                "Analyze this asset: %s. Price: %s. Change: %s. Give a 2 sentence summary.",
                quote.getSymbol(), quote.getPrice(), quote.getChangePercent()
        );

        var requestBody = Map.of(
                "model", "llama-3.1-8b-instant",
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt
                )),
                "temperature", 0.2
        );

        try {
            Map response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return extractTextFromResponse(response);

        } catch (HttpStatusCodeException e) {
            log.warn("Groq API call failed with status: {}. Re-throwing for circuit breaker.", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            log.error("Critical failure during AI text generation. Re-throwing for circuit breaker.", e);
            throw e;
        }
    }

    /**
     * Fallback method for analyzeMarketTrend when Groq API fails or the circuit is open.
     */
    public String analyzeMarketTrendFallback(GlobalQuote quote, Throwable t) {
        log.warn("Circuit Breaker [groqAi] activated for {}. Reason: {}", quote.getSymbol(), t.getMessage());
        return String.format("Fallback Insight: The asset %s is currently trading at $%s. Advanced AI analysis is temporarily unavailable (Circuit Breaker active).",
                quote.getSymbol(), quote.getPrice());
    }

    @CircuitBreaker(name = "groqAi", fallbackMethod = "resolveCompanyToTickerFallback")
    public String resolveCompanyToTicker(String query) {
        log.info("Asking AI to resolve query '{}' to a stock ticker...", query);

        String apiKey = config.getGroqApiKey();
        if (apiKey == null || apiKey.isEmpty() || "YOUR_GROQ_API_KEY".equals(apiKey)) {
            log.error("PRODUCTION ERROR: Groq API Key is missing for ticker resolution.");
            throw new IllegalStateException("Ticker resolution failed: Missing Groq API Key.");
        }
        apiKey = apiKey.trim();

        String prompt = String.format(
                "Identify the primary stock ticker symbol for: '%s'. Respond with EXACTLY and ONLY the uppercase ticker symbol (e.g., AAPL, V, MSFT). If it is already a ticker, return it uppercase. If it's not a publicly traded company, return UNKNOWN.",
                query
        );

        var requestBody = Map.of(
                "model", "llama-3.1-8b-instant",
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt
                )),
                "temperature", 0.1
        );

        try {
            Map response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            String resolved = extractTextFromResponse(response).trim().toUpperCase();
            // Clean up any potential markdown or extra chars returned by the LLM
            resolved = resolved.replaceAll("[^A-Z]", "");
            
            if (resolved.isEmpty()) return "UNKNOWN";
            return resolved;

        } catch (HttpStatusCodeException e) {
            log.warn("Groq API call failed during ticker resolution: {}. Re-throwing.", e.getStatusCode());
            throw e;
        } catch (Exception e) {
            log.error("Critical failure during ticker resolution.", e);
            throw e;
        }
    }

    public String resolveCompanyToTickerFallback(String query, Throwable t) {
        log.warn("Circuit Breaker [groqAi] active during resolution for '{}'. Defaulting to raw input.", query);
        String fallback = query.toUpperCase().trim().replaceAll("[^A-Z]", "");
        return fallback.isEmpty() ? "UNKNOWN" : fallback;
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map response) {
        try {
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                    if (message != null && message.containsKey("content")) {
                        return (String) message.get("content");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Groq response map", e);
        }
        return "Unable to extract insight at this time.";
    }
}
