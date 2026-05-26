package com.wms.ai.web.dto;

/**
 * JSON error body returned by {@code ApiExceptionHandler}. {@code error} is the
 * exception's simple type name (e.g. {@code IllegalStateException}) and {@code message}
 * is its message verbatim, so the console event log can show the guardrail reason
 * (README §6, §7).
 */
public record ApiError(String error, String message) {}
