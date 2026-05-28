package com.shr.cogniflow.service;

import com.shr.cogniflow.config.CogniflowConfig;
import com.shr.cogniflow.dto.GlobalQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AiAnalysisServiceTest {

    @Mock
    private RestClient.Builder builder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private CogniflowConfig config;

    private AiAnalysisService aiAnalysisService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);
        
        aiAnalysisService = new AiAnalysisService(builder, config);
    }

    @Test
    void testAnalyzeMarketTrend_Success() {
        GlobalQuote quote = new GlobalQuote();
        quote.setSymbol("IBM");
        quote.setPrice("150.00");
        quote.setChangePercent("1.5%");

        when(config.getGoogleAiApiKey()).thenReturn("fake-key");

        // Mocking the RestClient chain
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        
        Map<String, Object> mockResponse = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", "IBM is showing a bullish trend."
                                ))
                        )
                ))
        );
        when(responseSpec.body(Map.class)).thenReturn(mockResponse);

        String result = aiAnalysisService.analyzeMarketTrend(quote);

        assertEquals("IBM is showing a bullish trend.", result);
    }

    @Test
    void testAnalyzeMarketTrend_Fallback_ManualTrigger() {
        // Circuit Breaker logic usually requires AOP which isn't active in simple unit tests 
        // without @SpringBootTest. Here we test the fallback logic directly.
        GlobalQuote quote = new GlobalQuote();
        quote.setSymbol("IBM");
        quote.setPrice("150.00");
        
        String result = aiAnalysisService.analyzeMarketTrendFallback(quote, new RuntimeException("Simulated Failure"));
        
        assertTrue(result.contains("Fallback Insight"));
        assertTrue(result.contains("IBM"));
        assertTrue(result.contains("150.00"));
    }
}
