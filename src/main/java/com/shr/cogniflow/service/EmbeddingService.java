package com.shr.cogniflow.service;

import com.shr.cogniflow.config.CogniflowConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmbeddingService {

    private final RestClient restClient;
    private final CogniflowConfig config;

    public EmbeddingService(RestClient.Builder builder, CogniflowConfig config) {
        this.restClient = builder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.config = config;
    }

    public float[] getEmbedding(String text) {
        log.info("Generating stable text embedding using gemini-embedding-001...");

        String apiKey = config.getGoogleAiApiKey();
        if (apiKey == null || apiKey.isEmpty() || "YOUR_GEMINI_API_KEY".equals(apiKey)) {
            throw new RuntimeException("Google AI API Key is missing or using default placeholder. Please check 'COGNIFLOW_GOOGLE_AI_API_KEY' environment variable.");
        }

        var requestBody = Map.of(
                "model", "models/gemini-embedding-001",
                "task_type", "RETRIEVAL_QUERY",
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        try {
            Map response = restClient.post()
                    .uri("/v1/models/gemini-embedding-001:embedContent?key=" + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (request, responseBody) -> {
                        String errorBody = new String(responseBody.getBody().readAllBytes());
                        log.error("Gemini API Error: Status {}, Body: {}", responseBody.getStatusCode(), errorBody);
                        throw new RuntimeException("Gemini API Error: " + responseBody.getStatusCode() + " - " + errorBody);
                    })
                    .body(Map.class);

            if (response != null && response.containsKey("embedding")) {
                Map embeddingMap = (Map) response.get("embedding");
                List<Number> values = (List<Number>) embeddingMap.get("values");

                if (values == null) {
                    throw new RuntimeException("Embedding response received but 'values' is null.");
                }

                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    vector[i] = values.get(i).floatValue();
                }
                return vector;
            } else {
                throw new RuntimeException("Embedding API returned unexpected response format.");
            }
        } catch (RuntimeException e) {
            throw e; // Pass through our custom error messages
        } catch (Exception e) {
            log.error("Stable embedding generation failed due to an exception.", e);
            throw new RuntimeException("Unexpected error during embedding: " + e.getMessage());
        }
    }
}
