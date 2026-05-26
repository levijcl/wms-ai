package com.wms.ai.web.dto;

/**
 * Request body for the optional {@code POST /api/inventory/release} endpoint: restock
 * {@code quantity} units of {@code sku} (used on cancellation/failure).
 */
public record ReleaseRequest(String sku, int quantity) {}
