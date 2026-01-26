package com.example.demo.service.zeebe;

import com.example.demo.util.api.ApiError;
import com.example.demo.util.api.BadRequestException;

public class ZeebeInvalidArgumentException
        extends BadRequestException {
    public ZeebeInvalidArgumentException(String message) {
        super(ApiError.INVALID_INPUT, message);
    }
}