package com.example.demo.controller;

import com.example.demo.controller.base.InternalApiController;
import com.example.demo.data.dto.base.response.ApiErrorDto;
import com.example.demo.data.dto.base.response.ApiResponseWrapperDto;
import com.example.demo.data.validation.constraint.InviteLimit;
import com.example.demo.service.zeebe.ZeebeInvalidArgumentException;
import com.example.demo.service.zeebe.ZeebePermissionDeniedException;
import com.example.demo.service.zeebe.ZeebeUnexpectedException;
import com.example.demo.util.api.*;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.filter.ServerHttpObservationFilter;

@ControllerAdvice(assignableTypes={InternalApiController.class}, basePackages={"com.example.demo.test.controller"})
@Hidden
public class ExceptionHandlingAdvice {
    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlingAdvice.class);

    @ResponseStatus(value= HttpStatus.BAD_REQUEST)
    @ExceptionHandler(value={ConstraintViolationException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class, MissingServletRequestParameterException.class})
    @ResponseBody
    public ApiResponseWrapperDto returnBadRequest(HttpServletRequest request, Exception e) {
        ExceptionHandlingAdvice.setErrorInObservationContext(request, e);
        return this.createInvalidInputErrorResponse();
    }

    @ResponseStatus(value=HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    @ResponseBody
    public ApiResponseWrapperDto returnBadRequest(HttpServletRequest request, MethodArgumentNotValidException e) {
        ExceptionHandlingAdvice.setErrorInObservationContext(request, e);
        if (this.containsInviteLimitExceededViolation(e)) {
            return this.createErrorResponse(ApiError.INVITE_LIMIT_EXCEEDED);
        }
        return this.createInvalidInputErrorResponse();
    }

    @ResponseStatus(value=HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    @ResponseBody
    public ApiResponseWrapperDto returnBadRequest(HttpServletRequest request, ZeebeInvalidArgumentException e) {
        ExceptionHandlingAdvice.setErrorInObservationContext(request, e);
        return this.createErrorResponse(e.getApiError(), e.getMessage());
    }

    @ResponseStatus(value=HttpStatus.BAD_REQUEST)
    @ExceptionHandler
    @ResponseBody
    public ApiResponseWrapperDto returnBadRequest(HttpServletRequest request, BadRequestException e) {
        return this.createErrorResponse(request, e);
    }

    @ResponseStatus(value=HttpStatus.UNAUTHORIZED)
    @ExceptionHandler
    @ResponseBody
    public ApiResponseWrapperDto returnUnauthorized(HttpServletRequest request, UnauthorizedException e) {
        return this.createErrorResponse(request, e);
    }

    @ResponseStatus(value=HttpStatus.FORBIDDEN)
    @ExceptionHandler
    @ResponseBody
    public ApiResponseWrapperDto returnForbidden(HttpServletRequest request, ForbiddenException e) {
        return this.createErrorResponse(request, e);
    }

    @ResponseStatus(value=HttpStatus.FORBIDDEN)
    @ExceptionHandler
    @ResponseBody
    public ApiResponseWrapperDto returnForbidden(HttpServletRequest request, ZeebePermissionDeniedException e) {
        ExceptionHandlingAdvice.setErrorInObservationContext(request, e);
        return this.createErrorResponse(e.getApiError(), e.getMessage());
    }

    @ResponseStatus(value=HttpStatus.NOT_FOUND)
    @ExceptionHandler(value={AccessDeniedException.class, ConversionFailedException.class, MissingPathVariableException.class})
    public void returnNotFound(HttpServletRequest request, Exception e) {
        ExceptionHandlingAdvice.setErrorInObservationContext(request, e);
    }

    @ResponseStatus(value=HttpStatus.NOT_FOUND)
    @ExceptionHandler
    @ResponseBody
    public ApiResponseWrapperDto returnNotFound(HttpServletRequest request, NotFoundException e) {
        return this.createErrorResponse(request, e);
    }

    @ResponseStatus(value=HttpStatus.CONFLICT)
    @ExceptionHandler
    @ResponseBody
    public ApiResponseWrapperDto returnConflict(HttpServletRequest request, ConflictException e) {
        return this.createErrorResponse(request, e);
    }

    @ResponseStatus(value=HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler
    public void returnInternalServerError(HttpServletRequest request, ServerException e) {
        ExceptionHandlingAdvice.setErrorInObservationContext(request, e);
        log.error("Request failed unexpectedly", e);
    }

    @ResponseStatus(value=HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler
    @ResponseBody
    public ApiResponseWrapperDto returnInternalServerError(HttpServletRequest request, ZeebeUnexpectedException e) {
        ExceptionHandlingAdvice.setErrorInObservationContext(request, e);
        return this.createErrorResponse(null, e.getMessage());
    }

    private static void setErrorInObservationContext(HttpServletRequest request, Throwable error) {
        ServerHttpObservationFilter.findObservationContext(request).ifPresent(context -> context.setError(error));
    }

    private ApiResponseWrapperDto createErrorResponse(HttpServletRequest request, ClientException e) {
        ExceptionHandlingAdvice.setErrorInObservationContext(request, e);
        return this.createErrorResponse(e.getApiError());
    }

    private ApiResponseWrapperDto createInvalidInputErrorResponse() {
        return this.createErrorResponse(ApiError.INVALID_INPUT);
    }

    private ApiResponseWrapperDto createErrorResponse(ApiError reason) {
        ApiErrorDto errorDto = new ApiErrorDto(reason, null);
        return this.createApiResponseWrapperDto(errorDto);
    }

    private ApiResponseWrapperDto createErrorResponse(ApiError reason, String detail) {
        ApiErrorDto errorDto = new ApiErrorDto(reason, detail);
        return this.createApiResponseWrapperDto(errorDto);
    }

    private ApiResponseWrapperDto createApiResponseWrapperDto(ApiErrorDto errorDto) {
        return new ApiResponseWrapperDto(null, Set.of(errorDto));
    }

    private boolean containsInviteLimitExceededViolation(BindException e) {
        return e.getFieldErrors().stream().filter(fieldError -> fieldError.getCodes() != null).flatMap(fieldError -> Arrays.stream(fieldError.getCodes())).anyMatch(code -> InviteLimit.class.getSimpleName().equals(code));
    }
}