package com.example.demo.util.api;

public class NotFoundException
extends ClientException {
    protected NotFoundException(String message) {
        super(null, message);
    }

    public NotFoundException() {
    }
}