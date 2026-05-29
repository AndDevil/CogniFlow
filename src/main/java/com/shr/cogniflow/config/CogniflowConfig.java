package com.shr.cogniflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "cogniflow")
@Data
public class CogniflowConfig {

    /**
     * Alpha Vantage API Key for fetching market data.
     */
    private String alphavantageApiKey;

    /**
     * Google AI (Gemini) API Key for analysis and embeddings.
     */
    private String googleAiApiKey;

    /**
     * List of stock tickers to track.
     */
    private List<String> trackedTickers = List.of("IBM", "AAPL", "MSFT");

    /**
     * Secret token used to authorize incoming requests from Google Cloud Scheduler.
     */
    private String jobSecret;

    /**
     * Weaviate configuration.
     */
    private Weaviate weaviate = new Weaviate();

    @Data
    public static class Weaviate {
        private String host = "localhost";
        private int port = 8081;
        private String scheme = "http";
        private String apiKey = ""; // API Key for Weaviate Cloud (WCS)
    }
}
