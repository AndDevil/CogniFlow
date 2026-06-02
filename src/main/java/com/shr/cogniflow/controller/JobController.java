package com.shr.cogniflow.controller;

import com.shr.cogniflow.MarketDataService;
import com.shr.cogniflow.config.CogniflowConfig;
import com.shr.cogniflow.service.VectorStoreService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
@Hidden // Hides this from Swagger UI so public users don't see it
public class JobController {

    private final MarketDataService marketDataService;
    private final VectorStoreService vectorStoreService;
    private final CogniflowConfig config;

    @PostMapping("/run-scan")
    public ResponseEntity<String> triggerMarketScan(
            @RequestHeader(value = "X-CloudScheduler-JobSecret", required = false) String secret) {

        String configuredSecret = config.getJobSecret();

        if (configuredSecret == null || configuredSecret.isEmpty()) {
            log.warn("Job secret is not configured. Rejecting trigger request for safety.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Job execution disabled: Secret not configured.");
        }

        if (!configuredSecret.equals(secret)) {
            log.warn("Unauthorized attempt to trigger market scan.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        log.info("Authorized trigger received. Delegating to MarketDataService...");
        
        try {
            marketDataService.executeMarketScanBatch();
            return ResponseEntity.ok("Batch scan completed successfully.");
        } catch (Exception e) {
            log.error("Batch scan failed during execution.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Batch scan failed.");
        }
    }

    @GetMapping("/weaviate-check")
    public ResponseEntity<String> checkWeaviateConnection() {
        var result = vectorStoreService.checkConnection();
        if (result.hasErrors()) {
            return ResponseEntity.status(500).body("Connection failed: " + result.getError().getMessages());
        }
        return ResponseEntity.ok("Weaviate connection is alive and kicking! Status: " + result.getResult());
    }
}
