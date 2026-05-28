package com.shr.cogniflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shr.cogniflow.config.CogniflowConfig;
import com.shr.cogniflow.dto.GlobalQuote;
import com.shr.cogniflow.dto.MarketDataResponse;
import com.shr.cogniflow.service.AiAnalysisService;
import com.shr.cogniflow.service.EmbeddingService;
import com.shr.cogniflow.service.TickerService;
import com.shr.cogniflow.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MarketDataServiceTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private AiAnalysisService aiService;

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CogniflowConfig config;

    @Mock
    private TickerService tickerService;

    private MarketDataService marketDataService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        marketDataService = new MarketDataService(
                restClientBuilder,
                aiService,
                vectorStoreService,
                embeddingService,
                objectMapper,
                config,
                tickerService
        );
    }

    @Test
    void testFetchAndAnalyze_Success() {
        String symbol = "IBM";
        when(config.getAlphavantageApiKey()).thenReturn("fake-key");
        when(aiService.resolveCompanyToTicker(symbol)).thenReturn(symbol);

        MarketDataResponse mockResponse = new MarketDataResponse();
        GlobalQuote quote = new GlobalQuote();
        quote.setPrice("150.00");
        mockResponse.setGlobalQuote(quote);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MarketDataResponse.class)).thenReturn(mockResponse);

        when(aiService.analyzeMarketTrend(any())).thenReturn("Bullish sentiment");
        when(embeddingService.getEmbedding(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        marketDataService.fetchAndAnalyze(symbol);

        verify(vectorStoreService, times(1)).storeInsight(eq(symbol), eq("150.00"), eq("Bullish sentiment"), any());
    }

    @Test
    void testFetchAndAnalyze_ApiLimit() {
        String symbol = "IBM";
        when(config.getAlphavantageApiKey()).thenReturn("fake-key");
        when(aiService.resolveCompanyToTicker(symbol)).thenReturn(symbol);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MarketDataResponse.class)).thenReturn(null);

        marketDataService.fetchAndAnalyze(symbol);

        verify(vectorStoreService, never()).storeInsight(anyString(), anyString(), anyString(), any());
    }

    @Test
    void testFetchAndAnalyzeScheduled() {
        when(tickerService.getTickers()).thenReturn(List.of("IBM", "AAPL"));
        when(config.getAlphavantageApiKey()).thenReturn("fake-key");
        
        // Mock API chain for success to avoid errors in logs/execution
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MarketDataResponse.class)).thenReturn(null);

        marketDataService.fetchAndAnalyzeScheduled();

        verify(tickerService, times(1)).getTickers();
    }
}
