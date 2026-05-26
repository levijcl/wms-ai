package com.wms.ai.web;

import com.wms.ai.web.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The one place the module error contract becomes HTTP, identical for human clicks now
 * and AI-triggered calls later (README §3.5, §6):
 *
 * <ul>
 *   <li>{@code IllegalArgumentException} (bad/unknown input, unknown enum string) → <b>400</b>
 *   <li>{@code IllegalStateException} (illegal transition, insufficient stock, raced worker) → <b>409</b>
 * </ul>
 *
 * <p>Each carries an {@link ApiError} with the exception's own message, so a guardrail
 * rejection is something the console can display rather than an opaque 500. Anything
 * else falls through to the default 500.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiError onIllegalArgument(IllegalArgumentException ex) {
        return new ApiError(ex.getClass().getSimpleName(), ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError onIllegalState(IllegalStateException ex) {
        return new ApiError(ex.getClass().getSimpleName(), ex.getMessage());
    }
}
