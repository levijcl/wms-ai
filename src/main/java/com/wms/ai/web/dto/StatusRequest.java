package com.wms.ai.web.dto;

/**
 * Request body for the {@code .../status} transition endpoints. The status arrives as
 * a raw {@code String} and is converted to the target enum in the controller, so an
 * unknown value yields a clean 400 via the error advice rather than a framework
 * deserialization error.
 */
public record StatusRequest(String status) {}
