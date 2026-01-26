package com.example.demo.util.api;

public class ForbiddenException
extends ClientException {
    public ForbiddenException(ApiError apiError) {
        super(apiError);
    }

    public ForbiddenException(ApiError apiError, String message) {
        super(apiError, message);
    }
}