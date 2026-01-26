package com.example.demo.service.zeebe;

import com.example.demo.util.api.ServerException;

public class ZeebeUnexpectedException
extends ServerException {
    public ZeebeUnexpectedException(String message) {
        super(message);
    }
}