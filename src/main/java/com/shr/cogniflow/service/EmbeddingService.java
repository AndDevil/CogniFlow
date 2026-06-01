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
            log.error("Google AI API Key is missing or using default placeholder. Please check 'cogniflow.google-ai-api-key'.");
            return null;
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
                    .body(Map.class);

            if (response != null && response.containsKey("embedding")) {
                Map embeddingMap = (Map) response.get("embedding");
                List<Number> values = (List<Number>) embeddingMap.get("values");

                if (values == null) {
                    log.error("Embedding response received but 'values' is null.");
                    return null;
                }

                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    vector[i] = values.get(i).floatValue();
                }
                return vector;
            } else {
                log.error("Embedding API returned unexpected response format: {}", response);
                return null;
            }
        } catch (Exception e) {
            log.error("Stable embedding generation failed due to an exception.", e);
            return null;
        }
    }
}
