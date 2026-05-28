package com.shr.cogniflow.controller;

import com.shr.cogniflow.service.TickerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ticker Management", description = "Endpoints for managing the list of stock tickers tracked for scheduled analysis")
public class TickerController {

    private final TickerService tickerService;

    @Operation(summary = "Get all tracked tickers", description = "Returns the current list of symbols scheduled for periodic scans")
    @GetMapping
    public ResponseEntity<List<String>> getTickers() {
        return ResponseEntity.ok(tickerService.getTickers());
    }

    @Operation(summary = "Add a ticker to tracking", description = "Adds a new symbol to the scheduled ingestion list")
    @PostMapping("/{ticker}")
    public ResponseEntity<Void> addTicker(
            @Parameter(description = "The ticker symbol to add") @PathVariable String ticker) {
        tickerService.addTicker(ticker);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Remove a ticker from tracking", description = "Removes a symbol from the scheduled ingestion list")
    @DeleteMapping("/{ticker}")
    public ResponseEntity<Void> removeTicker(
            @Parameter(description = "The ticker symbol to remove") @PathVariable String ticker) {
        tickerService.removeTicker(ticker);
        return ResponseEntity.noContent().build();
    }
}
