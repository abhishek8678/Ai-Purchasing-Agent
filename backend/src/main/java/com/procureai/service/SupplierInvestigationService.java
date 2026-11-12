package com.procureai.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.entity.Warehouse;
import com.procureai.repository.SupplierProductRepository;
import com.procureai.service.dto.SupplierEvidence;
import com.procureai.service.dto.SupplierOfferEvidence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierInvestigationService {

    private final SupplierProductRepository supplierProductRepository;

    public SupplierInvestigationService(SupplierProductRepository supplierProductRepository) {
        this.supplierProductRepository = supplierProductRepository;
    }

    @Transactional(readOnly = true)
    public SupplierEvidence investigate(Product product, Warehouse warehouse, Long requestedUnits) {
        List<SupplierProduct> offers = supplierProductRepository.findByProductAndWarehouse(product, warehouse);
        return investigate(product, warehouse, requestedUnits, offers);
    }

    @Transactional(readOnly = true)
    public SupplierEvidence investigate(
            Product product,
            Warehouse warehouse,
            Long requestedUnits,
            List<SupplierProduct> offers
    ) {
        List<String> warnings = new ArrayList<>();
        if (offers.isEmpty()) {
            warnings.add("No supplier offers exist for this product and warehouse.");
        }
        if (requestedUnits != null && requestedUnits <= 0) {
            warnings.add("Requested units must be positive; MOQ and pack-size rounding were not calculated.");
        }

        List<SupplierOfferEvidence> evidence = offers.stream()
                .map(offer -> toEvidence(offer, requestedUnits))
                .toList();
        evidence.forEach(offer -> warnings.addAll(offer.warnings()));
        return new SupplierEvidence(product.getSku(), warehouse.getCode(), requestedUnits,
                evidence, List.copyOf(warnings));
    }

    private SupplierOfferEvidence toEvidence(SupplierProduct offer, Long requestedUnits) {
        List<String> warnings = new ArrayList<>();
        Long roundedUnits = null;
        Long additionalUnits = null;
        Long moq = offer.getMinimumOrderQuantity();
        Long packSize = offer.getPackSize();
        if (moq == null || moq <= 0) {
            warnings.add("Supplier offer has a missing or invalid minimum order quantity.");
        } else if (packSize == null || packSize <= 0) {
            warnings.add("Supplier offer has a missing or invalid pack size.");
        } else if (requestedUnits != null && requestedUnits > 0) {
            BigInteger target = BigInteger.valueOf(Math.max(requestedUnits, moq));
            BigInteger pack = BigInteger.valueOf(packSize);
            BigInteger rounded = target.add(pack).subtract(BigInteger.ONE).divide(pack).multiply(pack);
            if (rounded.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                warnings.add("MOQ and pack-size rounding exceeds the supported whole-unit range.");
            } else {
                roundedUnits = rounded.longValueExact();
                additionalUnits = rounded.subtract(BigInteger.valueOf(requestedUnits)).longValueExact();
            }
        }
        if (offer.getSupplier() == null || offer.getSupplier().getStatus() == null) {
            warnings.add("Supplier status is missing from the offer evidence.");
        }
        if (offer.getStatus() == null) {
            warnings.add("Supplier offer status is missing.");
        }
        return new SupplierOfferEvidence(
                offer.getId(),
                offer.getSupplier() == null ? null : offer.getSupplier().getId(),
                offer.getSupplier() == null ? null : offer.getSupplier().getCode(),
                offer.getSupplier() == null ? null : offer.getSupplier().getName(),
                offer.getSupplier() == null ? null : offer.getSupplier().getStatus(),
                offer.getProduct() == null ? null : offer.getProduct().getId(),
                offer.getWarehouse() == null ? null : offer.getWarehouse().getId(),
                offer.getStatus(),
                moq,
                packSize,
                offer.getLeadTimeDays(),
                offer.getUnitPrice(),
                offer.getCurrencyCode(),
                requestedUnits,
                roundedUnits,
                additionalUnits,
                List.copyOf(warnings)
        );
    }
}
