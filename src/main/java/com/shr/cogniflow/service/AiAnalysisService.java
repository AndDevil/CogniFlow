package com.shr.cogniflow.service;

import com.shr.cogniflow.dto.GlobalQuote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
public class AiAnalysisService {

    private final RestClient restClient;

    @Value("${google.ai.api.key}")
    private String apiKey;

    public AiAnalysisService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://generativelanguage.googleapis.com").build();
    }

    public String analyzeMarketTrend(GlobalQuote quote) {
        log.info("Asking AI for a vibe check on {}...", quote.getSymbol());

        // We create a prompt that tells the AI exactly what we want
        String prompt = String.format(
                "Analyze this stock data: %s is trading at %s with a change of %s. " +
                        "In 2 sentences, what is the market sentiment? Be professional and concise.",
                quote.getSymbol(), quote.getPrice(), quote.getChangePercent()
        );

        // This is the standard Gemini API request structure
        var requestBody = Map.of("contents", new Object[]{
                Map.of("parts", new Object[]{
                        Map.of("text", prompt)
                })
        });

        try {
            // CHANGE THIS LINE in your AiAnalysisService.java
            return restClient.post()
                    .uri("/v1/models/gemini-2.5-flash:generateContent?key=" + apiKey) // Changed v1beta to v1
                    .body(requestBody)
                    .retrieve()
                    .body(String.class); // We'll parse this properly in the next step
        } catch (Exception e) {
            log.error("AI Analysis failed", e);
            return "Could not generate analysis.";
        }
    }
}