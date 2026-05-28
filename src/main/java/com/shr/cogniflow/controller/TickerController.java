package com.shr.cogniflow.controller;

import com.shr.cogniflow.service.TickerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickers")
@RequiredArgsConstructor
@Slf4j
public class TickerController {

    private final TickerService tickerService;

    @GetMapping
    public ResponseEntity<List<String>> getTickers() {
        return ResponseEntity.ok(tickerService.getTickers());
    }

    @PostMapping("/{ticker}")
    public ResponseEntity<Void> addTicker(@PathVariable String ticker) {
        tickerService.addTicker(ticker);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{ticker}")
    public ResponseEntity<Void> removeTicker(@PathVariable String ticker) {
        tickerService.removeTicker(ticker);
        return ResponseEntity.noContent().build();
    }
}
