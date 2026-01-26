package com.example.demo.data.dto.base.response;

import java.util.Set;

public record ApiResponseWrapperDto(Object data, Set<ApiErrorDto> errors) {
}