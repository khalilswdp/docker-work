package com.example.demo.service.zeebe;

import com.example.demo.util.api.ApiError;
import com.example.demo.util.api.ForbiddenException;

public class ZeebePermissionDeniedException
        extends ForbiddenException {
    public ZeebePermissionDeniedException(String message) {
        super(ApiError.INVALID_OPERATION, message);
    }
}