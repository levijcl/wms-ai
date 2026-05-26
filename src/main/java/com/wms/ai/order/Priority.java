package com.wms.ai.order;

/**
 * Dispatch priority of an order. Ordered from least to most urgent; the AI
 * coordinator (and any baseline) ranks {@code URGENT} ahead of {@code HIGH},
 * etc. (README §5).
 */
public enum Priority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
