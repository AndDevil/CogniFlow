package com.shr.cogniflow.controller;

import com.shr.cogniflow.service.TickerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TickerController.class)
class TickerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TickerService tickerService;

    @Test
    void testGetTickers() throws Exception {
        when(tickerService.getTickers()).thenReturn(List.of("IBM", "AAPL"));

        mockMvc.perform(get("/api/tickers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("IBM"));
    }

    @Test
    void testAddTicker() throws Exception {
        mockMvc.perform(post("/api/tickers/TSLA"))
                .andExpect(status().isOk());

        verify(tickerService, times(1)).addTicker("TSLA");
    }

    @Test
    void testRemoveTicker() throws Exception {
        mockMvc.perform(delete("/api/tickers/IBM"))
                .andExpect(status().isNoContent());

        verify(tickerService, times(1)).removeTicker("IBM");
    }
}
