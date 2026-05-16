package com.shr.cogniflow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmbeddingService {

    private final RestClient restClient;

    @Value("${google.ai.api.key}")
    private String apiKey;

    public EmbeddingService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://generativelanguage.googleapis.com").build();
    }

    public float[] getEmbedding(String text) {
        log.info("Generating stable text embedding using gemini-embedding-001...");

        var requestBody = Map.of(
                "model", "models/gemini-embedding-001",
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

                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    vector[i] = values.get(i).floatValue();
                }
                return vector;
            }
            return null;
        } catch (Exception e) {
            log.error("Stable embedding generation failed.", e);
            return null;
        }
    }
}