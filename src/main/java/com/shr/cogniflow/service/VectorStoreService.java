package com.shr.cogniflow.service;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;
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
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class VectorStoreService {

    private final WeaviateClient client;

    public VectorStoreService() {
        // Connects to your local Weaviate instance in Docker
        Config config = new Config("http", "localhost:8081");
        this.client = new WeaviateClient(config);
    }

    /**
     * Runs automatically when the app starts.
     * Ensures the 'MarketInsight' class exists in Weaviate.
     */
    @PostConstruct
    public void initSchema() {
        log.info("Checking Weaviate for 'MarketInsight' schema...");

        try {
            Result<Boolean> exists = client.schema().exists()
                    .withClassName("MarketInsight")
                    .run();

            if (exists.getResult() != null && !exists.getResult()) {
                WeaviateClass marketClass = WeaviateClass.builder()
                        .className("MarketInsight")
                        .description("Stored AI market analysis and price data")
                        .vectorizer("none") // We'll add custom vectors in the next phase
                        .properties(List.of(
                                Property.builder().name("symbol").dataType(List.of(DataType.TEXT)).build(),
                                Property.builder().name("price").dataType(List.of(DataType.TEXT)).build(),
                                Property.builder().name("insight").dataType(List.of(DataType.TEXT)).build(),
                                Property.builder().name("timestamp").dataType(List.of(DataType.NUMBER)).build()
                        ))
                        .build();

                client.schema().classCreator().withClass(marketClass).run();
                log.info("Successfully created 'MarketInsight' schema in Weaviate.");
            } else {
                log.info("'MarketInsight' schema already exists. System ready.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Weaviate schema. Ensure Docker is running on port 8081.", e);
        }
    }

    /**
     * Saves the AI-generated insight and market data into the vector store.
     */
    // Update your storeInsight method signature and logic:
    public void storeInsight(String symbol, String price, String insight, float[] vector) {
        log.info("Memorizing insight for {} with vector embedding...", symbol);

        // 1. BOXING: Convert float[] to Float[]
        Float[] boxedVector = new Float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            boxedVector[i] = vector[i];
        }

        Map<String, Object> properties = Map.of(
                "symbol", symbol,
                "price", price,
                "insight", insight,
                "timestamp", (double) System.currentTimeMillis()
        );

        // Notice the addition of .withVector(vector)
        Result<WeaviateObject> result = client.data().creator()
                .withClassName("MarketInsight")
                .withProperties(properties)
                .withVector(boxedVector) // This is where the 'Smart' part happens
                .run();

        if (result.hasErrors()) {
            log.error("Weaviate Storage Error: {}", result.getError().getMessages());
        } else {
            log.info("Smart Insight for {} successfully saved.", symbol);
        }
    }

    public List<Map<String, Object>> semanticSearch(float[] queryVector, int limit) {
        log.info("Executing semantic vector search in Weaviate...");

        // 1. Box the primitive float[] to Float[] for the Weaviate Client
        Float[] boxedVector = new Float[queryVector.length];
        for (int i = 0; i < queryVector.length; i++) {
            boxedVector[i] = queryVector[i];
        }

        // 2. Define the data fields we want to pull back out
        Field symbol = Field.builder().name("symbol").build();
        Field price = Field.builder().name("price").build();
        Field insight = Field.builder().name("insight").build();
        Field timestamp = Field.builder().name("timestamp").build();

        // 3. Build the NearVector argument (the mathematical search mechanism)
        NearVectorArgument nearVector = NearVectorArgument.builder()
                .vector(boxedVector)
                .build();

        try {
            // 4. Run the GraphQL Get Query
            Result<GraphQLResponse> result = client.graphQL().get()
                    .withClassName("MarketInsight")
                    .withFields(symbol, price, insight, timestamp)
                    .withNearVector(nearVector)
                    .withLimit(limit)
                    .run();

            if (result.hasErrors()) {
                log.error("Weaviate search error: {}", result.getError().getMessages());
                return Collections.emptyList();
            }

            // 5. Parse the raw response map
            Map<String, Object> data = (Map<String, Object>) result.getResult().getData();
            Map<String, Object> get = (Map<String, Object>) data.get("Get");
            List<Map<String, Object>> insights = (List<Map<String, Object>>) get.get("MarketInsight");

            return insights != null ? insights : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to execute vector search", e);
            return Collections.emptyList();
        }
    }
}