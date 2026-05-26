package com.wms.ai.web;

import com.wms.ai.inventory.InventoryService;
import com.wms.ai.web.dto.ReleaseRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional convenience endpoint {@code POST /api/inventory/release} — restock units of
 * a SKU on cancellation/failure. Delegates straight to {@link InventoryService}, which
 * owns the quantity guardrail.
 */
@RestController
@RequestMapping("/api")
class InventoryController {

    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @PostMapping("/inventory/release")
    void release(@RequestBody ReleaseRequest request) {
        inventory.release(request.sku(), request.quantity());
    }
}
