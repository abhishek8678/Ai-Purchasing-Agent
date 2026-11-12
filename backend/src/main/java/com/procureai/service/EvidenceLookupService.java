package com.procureai.service;

import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.Warehouse;
import com.procureai.repository.ProductRepository;
import com.procureai.repository.WarehouseRepository;
import com.procureai.service.exception.InvestigationException;
import org.springframework.stereotype.Service;

@Service
public class EvidenceLookupService {

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    public EvidenceLookupService(ProductRepository productRepository, WarehouseRepository warehouseRepository) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
    }

    public Product requireProduct(String sku) {
        return productRepository.findBySku(sku).orElseThrow(() -> new InvestigationException(
                "UNKNOWN_PRODUCT", "No product exists with SKU " + sku));
    }

    public Warehouse requireWarehouse(String code) {
        return warehouseRepository.findByCode(code).orElseThrow(() -> new InvestigationException(
                "UNKNOWN_WAREHOUSE", "No warehouse exists with code " + code));
    }
}
