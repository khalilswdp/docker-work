package com.example.demo.util.api;

import com.example.demo.core.GilBaseException;

public class ClientException
extends GilBaseException {
    private final ApiError apiError;

    protected ClientException() {
        this(null);
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