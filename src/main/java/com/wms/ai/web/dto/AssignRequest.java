package com.wms.ai.web.dto;

/**
 * Request body for {@code POST /api/dispatch/assign}: the dispatcher's choice of
 * which order to assign to which worker. The coordinator owns all validation — this
 * is a plain transport record.
 */
public record AssignRequest(String orderId, String workerId) {}
