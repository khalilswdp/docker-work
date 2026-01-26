package com.example.demo.util.api;

public enum ApiError {
    INVALID_INPUT,
    INVITE_LIMIT_EXCEEDED,
    INVALID_PASSWORD,
    INVALID_OPERATION,
    REVISION_CONFLICT,
    MAINTENANCE_MODE;

    public static ApiError valueOfIgnoreCase(String name) {
        for (ApiError error : values()) {
            if (error.name().equalsIgnoreCase(name)) {
                return error;
            }
        }
        throw new IllegalArgumentException("No enum constant " + ApiError.class.getName() + "." + name);
    }
}