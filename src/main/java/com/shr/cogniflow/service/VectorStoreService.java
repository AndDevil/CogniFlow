package com.shr.cogniflow.service;

import com.shr.cogniflow.config.CogniflowConfig;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.filters.Operator;
import io.weaviate.client.v1.filters.WhereFilter;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import io.weaviate.client.v1.schema.model.DataType;
import io.weaviate.client.v1.schema.model.Property;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class VectorStoreService {

    private final CogniflowConfig config;
    private WeaviateClient client;
    private static final String CLASS_NAME = "MarketInsight";

    public VectorStoreService(CogniflowConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        CogniflowConfig.Weaviate wv = config.getWeaviate();
        
        // Handle standard ports cleanly for WCS (which usually uses HTTPS and 443)
        String hostPort = wv.getPort() == 80 || wv.getPort() == 443 
                          ? wv.getHost() 
                          : wv.getHost() + ":" + wv.getPort();
                          
        Config weaviateConfig = new Config(wv.getScheme(), hostPort);

        try {
            if (wv.getApiKey() != null && !wv.getApiKey().isEmpty()) {
                log.info("Connecting to Weaviate with API Key Authentication (WCS mode).");
                this.client = WeaviateAuthClient.apiKey(weaviateConfig, wv.getApiKey());
            } else {
                log.info("Connecting to Weaviate without authentication (Local mode).");
                this.client = new WeaviateClient(weaviateConfig);
            }
        } catch (Exception e) {
            log.error("Failed to initialize Weaviate client", e);
            return;
        }

        log.info("Checking Weaviate for '{}' schema at {}://{}...", 
                CLASS_NAME, wv.getScheme(), hostPort);

        Result<Boolean> check = client.schema().exists().withClassName(CLASS_NAME).run();

        if (check.hasErrors()) {
            log.error("Error checking schema: {}", check.getError().getMessages());
            return;
        }

        if (!Boolean.TRUE.equals(check.getResult())) {
            WeaviateClass clazz = WeaviateClass.builder()
                    .className(CLASS_NAME)
                    .properties(List.of(
                            Property.builder().name("symbol").dataType(List.of(DataType.TEXT)).build(),
                            Property.builder().name("price").dataType(List.of(DataType.TEXT)).build(),
                            Property.builder().name("insight").dataType(List.of(DataType.TEXT)).build(),
                            Property.builder().name("timestamp").dataType(List.of(DataType.NUMBER)).build()
                    ))
                    .build();

            Result<Boolean> create = client.schema().classCreator().withClass(clazz).run();
            if (create.hasErrors()) {
                log.error("Failed to create schema: {}", create.getError().getMessages());
            } else {
                log.info("Successfully created '{}' schema in Weaviate.", CLASS_NAME);
            }
        }
    }

    public void storeInsight(String symbol, String price, String insight, float[] vector) {
        log.info("Memorizing insight for {} with vector embedding...", symbol);

        Float[] boxedVector = new Float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            boxedVector[i] = vector[i];
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put("symbol", symbol);
        properties.put("price", price);
        properties.put("insight", insight);
        properties.put("timestamp", (double) System.currentTimeMillis());

        Result<WeaviateObject> result = client.data().creator()
                .withClassName(CLASS_NAME)
                .withProperties(properties)
                .withVector(boxedVector)
                .run();

        if (result.hasErrors()) {
            log.error("Failed to save insight: {}", result.getError().getMessages());
        } else {
            log.info("Smart Insight for {} successfully saved.", symbol);
        }
    }

    public List<Map<String, Object>> semanticSearch(float[] queryVector, int limit) {
        log.info("Executing semantic vector search in Weaviate...");

        Float[] boxedVector = new Float[queryVector.length];
        for (int i = 0; i < queryVector.length; i++) {
            boxedVector[i] = queryVector[i];
        }

        Field symbol = Field.builder().name("symbol").build();
        Field price = Field.builder().name("price").build();
        Field insight = Field.builder().name("insight").build();
        Field timestamp = Field.builder().name("timestamp").build();

        NearVectorArgument nearVector = NearVectorArgument.builder()
                .vector(boxedVector)
                .build();

        try {
            Result<GraphQLResponse> result = client.graphQL().get()
                    .withClassName(CLASS_NAME)
                    .withFields(symbol, price, insight, timestamp)
                    .withNearVector(nearVector)
                    .withLimit(limit)
                    .run();

            if (result.hasErrors()) {
                log.error("Weaviate search error: {}", result.getError().getMessages());
                return Collections.emptyList();
            }

            return extractDataFromResponse(result);
        } catch (Exception e) {
            log.error("Failed to execute vector search", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getLatestInsightBySymbol(String symbol) {
        log.info("Searching Weaviate index explicitly for ticker match: {}", symbol);

        Field symbolField = Field.builder().name("symbol").build();
        Field priceField = Field.builder().name("price").build();
        Field insightField = Field.builder().name("insight").build();
        Field timestampField = Field.builder().name("timestamp").build();

        // Using the v4 native WhereFilter and Operator syntax
        WhereFilter whereFilter = WhereFilter.builder()
                .path(new String[]{ "symbol" })
                .operator(Operator.Equal)
                .valueText(symbol.toUpperCase().trim())
                .build();

        try {
            Result<GraphQLResponse> result = client.graphQL().get()
                    .withClassName(CLASS_NAME)
                    .withFields(symbolField, priceField, insightField, timestampField)
                    .withWhere(whereFilter)
                    .withLimit(1)
                    .run();

            if (result.hasErrors()) {
                log.error("Weaviate error filtering symbol: {}", result.getError().getMessages());
                return Collections.emptyList();
            }

            return extractDataFromResponse(result);
        } catch (Exception e) {
            log.error("Failed to query Weaviate for specific symbol", e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDataFromResponse(Result<GraphQLResponse> result) {
        if (result.getResult() == null || result.getResult().getData() == null) {
            return Collections.emptyList();
        }
        Map<String, Object> data = (Map<String, Object>) result.getResult().getData();
        Map<String, Object> get = (Map<String, Object>) data.get("Get");
        if (get == null || !get.containsKey(CLASS_NAME)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> insights = (List<Map<String, Object>>) get.get(CLASS_NAME);
        return insights != null ? insights : Collections.emptyList();
    }
}
