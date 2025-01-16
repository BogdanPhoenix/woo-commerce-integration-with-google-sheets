package com.woo_commerce.google_sheets.exception;

import lombok.Getter;

public class GoogleSheetException extends RuntimeException {
    public enum ErrorCode {
        SHEET_NOT_FOUND,
        PERMISSION_DENIED,
        INVALID_REQUEST,
        SERVICE_UNAVAILABLE,
        AUTHENTICATION_FAILED,
        OPERATION_FAILED
    }

    @Getter
    private final ErrorCode errorCode;

    public GoogleSheetException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GoogleSheetException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message);
    }
} 