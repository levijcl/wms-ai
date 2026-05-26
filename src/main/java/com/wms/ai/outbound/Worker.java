package com.wms.ai.outbound;

/**
 * Immutable public view of a worker — part of the Outbound module's sealed port.
 * Callers see {@code Worker}, never the JPA {@code WorkerEntity}.
 *
 * @param id          stable identifier (seeded reference data)
 * @param name        worker's display name
 * @param currentZone warehouse zone the worker is currently in (for zone affinity)
 * @param status      current availability state
 */
public record Worker(String id, String name, String currentZone, WorkerStatus status) {}
