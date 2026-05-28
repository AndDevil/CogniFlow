package com.shr.cogniflow.service;

import com.shr.cogniflow.config.CogniflowConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.weaviate.WeaviateContainer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class VectorStoreIntegrationTest {

    @Container
    static WeaviateContainer weaviate = new WeaviateContainer("semitechnologies/weaviate:1.24.1");

    private VectorStoreService vectorStoreService;
    private CogniflowConfig config;
    private static final int VECTOR_DIM = 1536;

    @BeforeEach
    void setUp() {
        config = new CogniflowConfig();
        CogniflowConfig.Weaviate wv = config.getWeaviate();
        wv.setHost(weaviate.getHost());
        wv.setPort(weaviate.getFirstMappedPort());
        wv.setScheme("http");

        vectorStoreService = new VectorStoreService(config);
        vectorStoreService.init();
    }

    @Test
    void testStoreAndRetrieveInsight() {
        String symbol = "TSLA";
        String price = "200.00";
        String insight = "Tesla is showing strong vertical integration.";
        float[] vector = new float[VECTOR_DIM];
        vector[0] = 1.0f; // Points perfectly along X axis

        // Store
        vectorStoreService.storeInsight(symbol, price, insight, vector);

        // Retrieve by symbol
        List<Map<String, Object>> results = vectorStoreService.getLatestInsightBySymbol(symbol);
        
        assertFalse(results.isEmpty(), "Should find stored insight");
        assertEquals(symbol, results.get(0).get("symbol"));
        assertEquals(price, results.get(0).get("price"));
        assertEquals(insight, results.get(0).get("insight"));
    }

    @Test
    void testSemanticSearch() {
        // Use orthogonal vectors
        float[] v1 = new float[VECTOR_DIM]; v1[1] = 1.0f; // Points perfectly along Y axis
        float[] v2 = new float[VECTOR_DIM]; v2[2] = 1.0f; // Points perfectly along Z axis
        
        float[] queryV = new float[VECTOR_DIM]; 
        queryV[2] = 0.9f; // Strongly aligned with Z axis (v2/MSFT)
        queryV[1] = 0.1f; // Slightly aligned with Y axis (v1/AAPL)

        // Store two distinct insights
        vectorStoreService.storeInsight("AAPL", "150.00", "Apple products are popular.", v1);
        vectorStoreService.storeInsight("MSFT", "300.00", "Microsoft dominates cloud.", v2);

        // Search with a vector close to the second one
        List<Map<String, Object>> searchResults = vectorStoreService.semanticSearch(queryV, 1);

        assertFalse(searchResults.isEmpty(), "Should return search results");
        assertEquals("MSFT", searchResults.get(0).get("symbol"), "Semantic search should return MSFT as the closest match");
    }
}
