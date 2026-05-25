package com.wms.ai.inventory.internal;

import com.wms.ai.inventory.InventoryService;
import com.wms.ai.inventory.Stock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Package-private implementation of the Inventory port. Maps the package-private
 * {@link StockEntity} to the public {@link Stock} record so JPA types never
 * cross the module boundary.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final StockRepository repository;

    InventoryServiceImpl(StockRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Stock> getStock(String sku) {
        return repository.findById(sku).map(InventoryServiceImpl::toStock);
    }

    @Override
    public List<Stock> listAll() {
        return repository.findAll().stream().map(InventoryServiceImpl::toStock).toList();
    }

    @Override
    public boolean reserve(String sku, int quantity) {
        throw new UnsupportedOperationException("reserve not implemented yet"); // Task 4
    }

    @Override
    public void release(String sku, int quantity) {
        throw new UnsupportedOperationException("release not implemented yet"); // Task 5
    }

    private static Stock toStock(StockEntity entity) {
        return new Stock(entity.getSku(), entity.getQuantity(), entity.getLocation());
    }
}
