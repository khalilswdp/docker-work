package com.example.demo.util.api;

public class ConflictException
extends ClientException {
    public ConflictException(ApiError apiError) {
        super(apiError);
    }
}