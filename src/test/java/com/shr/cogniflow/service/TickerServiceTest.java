package com.shr.cogniflow.service;

import com.shr.cogniflow.config.CogniflowConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class TickerServiceTest {

    @Mock
    private CogniflowConfig config;

    private TickerService tickerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(config.getTrackedTickers()).thenReturn(List.of("IBM", "AAPL"));
        tickerService = new TickerService(config);
    }

    @Test
    void testGetTickers_Initial() {
        List<String> tickers = tickerService.getTickers();
        assertEquals(2, tickers.size());
        assertTrue(tickers.contains("IBM"));
        assertTrue(tickers.contains("AAPL"));
    }

    @Test
    void testAddTicker() {
        tickerService.addTicker("msft");
        List<String> tickers = tickerService.getTickers();
        assertEquals(3, tickers.size());
        assertTrue(tickers.contains("MSFT")); // Normalized to uppercase
    }

    @Test
    void testRemoveTicker() {
        tickerService.removeTicker("IBM");
        List<String> tickers = tickerService.getTickers();
        assertEquals(1, tickers.size());
        assertFalse(tickers.contains("IBM"));
    }

    @Test
    void testAddDuplicateTicker() {
        tickerService.addTicker("IBM");
        assertEquals(2, tickerService.getTickers().size());
    }
}
