# CogniFlow

CogniFlow is an intelligent market analysis and ingestion engine that leverages AI to provide sentiment analysis and semantic search capabilities for stock market data.

## 🚀 Overview

CogniFlow automatically fetches market data for curated tickers, analyzes the trends using the Google Gemini AI, and stores both the raw data and the AI-generated insights in a Weaviate vector database. This allows for powerful semantic search and trend discovery across historical market snapshots.

## ✨ Features

- **Automated Ingestion:** Scheduled polling of Alpha Vantage for real-time market data.
- **AI-Powered Analysis:** Generates concise, professional market "vibe checks" using the Gemini 2.5 Flash model.
- **Semantic Vector Storage:** Stores insights as high-dimensional vectors in Weaviate for advanced search capabilities.
- **Resilient Pipeline:** Includes robust error handling for API rate limits and network congestion.
- **Company-Specific Discovery:** Capability to query latest insights by ticker symbol.

## 🛠 Tech Stack

- **Java 17+** with **Spring Boot 3.x**
- **Google Gemini AI** (Generative AI)
- **Weaviate** (Vector Database)
- **Alpha Vantage API** (Market Data)
- **Docker & Docker Compose**
- **Lombok & Jackson**

## 🚦 Getting Started

### Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- API Keys for:
    - [Alpha Vantage](https://www.alphavantage.co/support/#api-key)
    - [Google AI (Gemini)](https://aistudio.google.com/app/apikey)

### Configuration

Create or update your `src/main/resources/application.properties` (or set environment variables):

```properties
cogniflow.alphavantage-api-key=YOUR_ALPHA_VANTAGE_KEY
cogniflow.google-ai-api-key=YOUR_GEMINI_API_KEY
cogniflow.job-secret=YOUR_CRON_JOB_SECRET_TOKEN

# Weaviate Configuration (Defaults for local Docker)
cogniflow.weaviate.host=localhost
cogniflow.weaviate.port=8081
cogniflow.weaviate.scheme=http
# cogniflow.weaviate.api-key=YOUR_WCS_API_KEY (Optional for Weaviate Cloud)
```

### Running the Application

1. **Start the Infrastructure:**
   ```bash
   docker-compose up -d
   ```
   This starts the Weaviate vector database on port `8081`.

2. **Build and Run:**
   ```bash
   ./mvnw spring-boot:run
   ```

## 🔍 API Usage

- **Semantic Search:** Query the system using natural language to find similar market trends and insights.
- **Market Scans:** The system automatically performs a scan every few hours (configurable in `MarketDataService`).
- **Internal Trigger:** Manually trigger a market scan via POST to `/api/internal/run-scan`. This requires the `X-CloudScheduler-JobSecret` header to match the configured `cogniflow.job-secret`.

## 🛡 License

This project is for internal/educational use.
