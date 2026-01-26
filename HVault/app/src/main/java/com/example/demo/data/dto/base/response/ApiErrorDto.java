package com.example.demo.data.dto.base.response;

import com.example.demo.util.api.ApiError;

public record ApiErrorDto(ApiError reason, String detail) {
}