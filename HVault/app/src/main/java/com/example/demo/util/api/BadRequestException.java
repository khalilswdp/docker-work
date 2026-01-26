package com.example.demo.util.api;

public class BadRequestException
        extends ClientException {
    public BadRequestException(ApiError apiError, String message) {
        super(apiError, message);
    }
}
