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
        
        // Defensive check: API Key existence
        if (apiKey == null || apiKey.isEmpty() || "YOUR_GEMINI_API_KEY".equals(apiKey)) {
            String weaviateHost = config.getWeaviate().getHost();
            boolean isLocal = weaviateHost.contains("localhost") || weaviateHost.contains("127.0.0.1");
            if (isLocal) {
                log.warn("DEVELOPMENT WARNING: Google AI API Key is missing. Search and embeddings will use simulated vectors.");
                float[] dummyVector = new float[768];
                int hash = text.hashCode();
                for (int i = 0; i < dummyVector.length; i++) {
                    dummyVector[i] = (float) Math.sin(hash + i);
                }
                return dummyVector;
            } else {
                log.error("PRODUCTION ERROR: Google AI API Key is missing. Search and embeddings will fail. " +
                        "Ensure 'COGNIFLOW_GOOGLE_AI_API_KEY' is set in Cloud Run environment variables.");
                throw new IllegalStateException("AI Embedding service failed: Missing API Key configuration.");
            }
        }

        // Diagnostic: Log masked key to verify source (Env vs Property)
        String maskedKey = apiKey.length() > 8 
                ? apiKey.substring(0, 4) + "...." + apiKey.substring(apiKey.length() - 4)
                : "****";
        log.info("Using Gemini API Key: {}", maskedKey);

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
                    .onStatus(status -> status.value() == 401 || status.value() == 403, (request, responseBody) -> {
                        log.error("CRITICAL: API Key invalid or quota exceeded (401/403). check Google AI Console.");
                        throw new RuntimeException("AI Embedding service failed: Invalid API Key or Quota exceeded.");
                    })
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (request, responseBody) -> {
                        String errorBody = new String(responseBody.getBody().readAllBytes());
                        log.error("Gemini API Error: Status {}, Body: {}", responseBody.getStatusCode(), errorBody);
                        throw new RuntimeException("Gemini API Error: " + responseBody.getStatusCode());
                    })
                    .body(Map.class);

            if (response != null && response.containsKey("embedding")) {
                Map embeddingMap = (Map) response.get("embedding");
                List<Number> values = (List<Number>) embeddingMap.get("values");

                if (values == null) {
                    log.error("Gemini API returned success but values were null.");
                    throw new RuntimeException("Embedding response received but 'values' is null.");
                }

                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    vector[i] = values.get(i).floatValue();
                }
                return vector;
            } else {
                log.error("Gemini API returned unexpected response format: {}", response);
                throw new RuntimeException("Embedding API returned unexpected response format.");
            }
        } catch (RuntimeException e) {
            // Log specifically if it's our own thrown exceptions
            log.warn("Managed Embedding Failure: {}", e.getMessage());
            throw e; 
        } catch (Exception e) {
            log.error("UNEXPECTED ERROR: Stable embedding generation failed due to a low-level exception.", e);
            throw new RuntimeException("Unexpected error during embedding: " + e.getMessage());
        }
    }
}
