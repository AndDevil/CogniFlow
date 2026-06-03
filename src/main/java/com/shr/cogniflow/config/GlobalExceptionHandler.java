package com.shr.cogniflow.config;

import com.shr.cogniflow.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.LocalDateTime;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<ErrorResponse> handleHttpStatusCodeException(HttpStatusCodeException ex, HttpServletRequest request) {
        log.error("External service error [{}]: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatusCode().value())
                .error(HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase())
                .errorCode("EXTERNAL_SERVICE_ERROR")
                .message("An external API (Gemini or Alpha Vantage) returned an error: " + ex.getStatusText())
                .path(request.getRequestURI())
                .build();
        
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception caught by GlobalExceptionHandler", ex);

        String message = ex.getMessage();
        String errorCode = "INTERNAL_SERVER_ERROR";

        // Heuristic to provide better context for common failures
        if (message != null) {
            if (message.contains("Weaviate")) {
                errorCode = "WEAVIATE_CONNECTION_ERROR";
            } else if (message.contains("generativelanguage")) {
                errorCode = "GEMINI_API_ERROR";
            }
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .errorCode(errorCode)
                .message(message != null ? message : "An unexpected error occurred.")
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
