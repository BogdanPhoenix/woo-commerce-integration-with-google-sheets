package com.woo_commerce.google_sheets.exception.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.woo_commerce.google_sheets.dto.HandlerResponse;
import com.woo_commerce.google_sheets.exception.GoogleSheetException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class Handler extends ResponseEntityExceptionHandler {
    
    @ExceptionHandler(GoogleSheetException.class)
    public ResponseEntity<HandlerResponse> handleResponseEntity(GoogleSheetException ex) {
        log.error("Google Sheets API error: {}", ex.getMessage(), ex);
        
        HttpStatus status = determineHttpStatus(ex.getErrorCode());
        
        HandlerResponse response = HandlerResponse.builder()
            .status(ex.getErrorCode().toString())
            .errorMessage(ex.getMessage())
            .build();

        return ResponseEntity.status(status).body(response);
    }
    
    private HttpStatus determineHttpStatus(GoogleSheetException.ErrorCode errorCode) {
        return switch (errorCode) {
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case SHEET_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
