package com.example.demo.util.api;

public class UnauthorizedException
extends ClientException {
    public UnauthorizedException(ApiError apiError) {
        super(apiError);
    }
}
