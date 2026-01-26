package com.example.demo.util.api;

public class ClientException
extends RuntimeException {
    private final ApiError apiError;

    protected ClientException() {
        this((ApiError)null);
    }

    protected ClientException(ApiError apiError) {
        this(apiError, null);
    }

    protected ClientException(ApiError apiError, String message) {
        super(message);
        this.apiError = apiError;
    }

    public ApiError getApiError() {
        return this.apiError;
    }
}