package com.shr.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MarketDataResponse {
    @JsonProperty("Global Quote")
    private GlobalQuote globalQuote;
}