package com.example.demo.util.api;

import com.example.demo.core.GilBaseException;

public class ServerException
extends GilBaseException {
    public ServerException(Throwable cause) {
        super(cause);
    }

    public ServerException(String message) {
        super(message);
    }

    public ServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServerException() {
    }
}