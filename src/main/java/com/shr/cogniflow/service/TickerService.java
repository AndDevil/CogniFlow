package com.shr.cogniflow.service;

import com.shr.cogniflow.config.CogniflowConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@Slf4j
public class TickerService {

    private final Set<String> tickers;

    public TickerService(CogniflowConfig config) {
        // Initialize with tickers from config, but make it mutable
        this.tickers = new CopyOnWriteArraySet<>(config.getTrackedTickers());
        log.info("Initialized TickerService with: {}", tickers);
    }

    public List<String> getTickers() {
        return new ArrayList<>(tickers);
    }

    public void addTicker(String ticker) {
        String upperTicker = ticker.toUpperCase().trim();
        if (tickers.add(upperTicker)) {
            log.info("Added ticker: {}", upperTicker);
        }
    }

    public void removeTicker(String ticker) {
        String upperTicker = ticker.toUpperCase().trim();
        if (tickers.remove(upperTicker)) {
            log.info("Removed ticker: {}", upperTicker);
        }
    }

    public void resetToDefault(List<String> defaultTickers) {
        tickers.clear();
        defaultTickers.forEach(t -> tickers.add(t.toUpperCase().trim()));
        log.info("Reset tickers to: {}", tickers);
    }
}
