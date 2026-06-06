package com.shr.cogniflow.controller;

import com.shr.cogniflow.service.EmbeddingService;
import com.shr.cogniflow.service.TickerService;
import com.shr.cogniflow.service.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InsightController.class)
class InsightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmbeddingService embeddingService;

    @MockitoBean
    private VectorStoreService vectorStoreService;

    @MockitoBean
    private com.shr.cogniflow.MarketDataService marketDataService;

    @MockitoBean
    private TickerService tickerService;

    @Test
    void testGetLiveInsight_Success() throws Exception {
        String symbol = "TSLA";
        Map<String, Object> mockResult = Map.of(
                "symbol", symbol,
                "price", "200.00",
                "insight", "Stable growth",
                "timestamp", 1684435200000L
        );

        when(marketDataService.fetchAndAnalyze(symbol)).thenReturn(mockResult);

        mockMvc.perform(get("/api/insights/live/" + symbol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(symbol))
                .andExpect(jsonPath("$.price").value("200.00"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testGetLiveInsight_Failure() throws Exception {
        String symbol = "INVALID";
        Map<String, Object> mockResult = Map.of("error", "Invalid symbol");

        when(marketDataService.fetchAndAnalyze(symbol)).thenReturn(mockResult);

        mockMvc.perform(get("/api/insights/live/" + symbol))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Invalid symbol"));
    }

    @Test
    void testSearchInsights_Success() throws Exception {
        String query = "market trend";
        float[] mockVector = new float[]{0.1f, 0.2f};
        
        when(embeddingService.getEmbedding(anyString())).thenReturn(mockVector);
        
        List<Map<String, Object>> mockResults = List.of(
                Map.of(
                        "symbol", "IBM",
                        "price", "150.00",
                        "insight", "Bullish",
                        "timestamp", 1684435200000L // Example epoch
                )
        );
        
        when(vectorStoreService.semanticSearch(any(), anyInt())).thenReturn(mockResults);
        when(vectorStoreService.hybridSearch(anyString(), any(), anyInt())).thenReturn(mockResults);

        mockMvc.perform(get("/api/insights/search")
                .param("query", query)
                .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("IBM"))
                .andExpect(jsonPath("$[0].price").value("150.00"))
                .andExpect(jsonPath("$[0].timestamp").exists());
    }

    @Test
    void testSearchInsights_InternalServerError() throws Exception {
        when(embeddingService.getEmbedding(anyString()))
                .thenThrow(new RuntimeException("Failure connecting to generativelanguage.googleapis.com"));

        mockMvc.perform(get("/api/insights/search")
                .param("query", "fail"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("GEMINI_API_ERROR"))
                .andExpect(jsonPath("$.message").value("Failure connecting to generativelanguage.googleapis.com"));
    }

    @Test
    void testGetPipelineStatus_Success() throws Exception {
        when(tickerService.getTickers()).thenReturn(List.of("AAPL", "MSFT"));
        
        when(vectorStoreService.getLatestInsightBySymbol("AAPL")).thenReturn(List.of(
            Map.of("symbol", "AAPL", "price", "150.0", "insight", "Good", "timestamp", System.currentTimeMillis() - 1000L)
        ));
        when(vectorStoreService.getLatestInsightBySymbol("MSFT")).thenReturn(List.of(
            Map.of("symbol", "MSFT", "price", "250.0", "insight", "Old info", "timestamp", System.currentTimeMillis() - 8 * 60 * 60 * 1000L)
        ));
        
        when(vectorStoreService.getTotalInsightsCount()).thenReturn(42);
        when(vectorStoreService.getLastGlobalIngestionTime()).thenReturn(System.currentTimeMillis() - 1000L);

        mockMvc.perform(get("/api/insights/pipeline/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInsights").value(42))
                .andExpect(jsonPath("$.lastGlobalRun").exists())
                .andExpect(jsonPath("$.tickers[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.tickers[0].status").value("healthy"))
                .andExpect(jsonPath("$.tickers[1].symbol").value("MSFT"))
                .andExpect(jsonPath("$.tickers[1].status").value("stale"));
    }

    @Test
    void testGetPipelineStatus_NoData() throws Exception {
        when(tickerService.getTickers()).thenReturn(List.of("GOOG"));
        when(vectorStoreService.getLatestInsightBySymbol("GOOG")).thenReturn(List.of());
        when(vectorStoreService.getTotalInsightsCount()).thenReturn(0);
        when(vectorStoreService.getLastGlobalIngestionTime()).thenReturn(null);

        mockMvc.perform(get("/api/insights/pipeline/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInsights").value(0))
                .andExpect(jsonPath("$.lastGlobalRun").value("Never"))
                .andExpect(jsonPath("$.tickers[0].symbol").value("GOOG"))
                .andExpect(jsonPath("$.tickers[0].status").value("failed"))
                .andExpect(jsonPath("$.tickers[0].lastIngested").isEmpty());
    }
}
