package com.procureai.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.procureai.domain.entity.Product;
import com.procureai.domain.entity.PurchaseOrder;
import com.procureai.domain.entity.PurchaseOrderItem;
import com.procureai.domain.entity.Supplier;
import com.procureai.domain.entity.SupplierProduct;
import com.procureai.domain.entity.Warehouse;
import com.procureai.domain.enums.PurchaseOrderStatus;
import com.procureai.domain.enums.SupplierOfferStatus;
import com.procureai.domain.enums.SupplierStatus;
import com.procureai.repository.PurchaseOrderItemRepository;
import com.procureai.repository.SupplierProductRepository;
import com.procureai.service.dto.PlanningHorizon;
import com.procureai.service.dto.PurchaseOrderEvidence;
import com.procureai.service.dto.SupplierEvidence;
import com.procureai.service.dto.SupplierOfferEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderAndSupplierInvestigationTest {

    private final Product product = new Product("WM-001", "Wireless Mouse", null, "EACH", null, true);
    private final Warehouse warehouse = new Warehouse("DEL-01", "Delhi FC", "Delhi", "IN", true);
    private final Supplier supplier = new Supplier("SUP-01", "Northstar", SupplierStatus.ACTIVE);
    private final SupplierProduct offer = offer(supplier, product, warehouse, 100L, 50L,
            new BigDecimal("249.5000"), SupplierOfferStatus.AVAILABLE);

    @Mock
    private PurchaseOrderItemRepository itemRepository;

    @Mock
    private SupplierProductRepository supplierProductRepository;

    @Test
    void incomingSupplySeparatesConfirmedAndUnconfirmedAndExcludesIneligibleOrLateLines() {
        LocalDate first = LocalDate.of(2026, 11, 13);
        PlanningHorizon horizon = new PlanningHorizon(first, first.plusDays(2));
        List<PurchaseOrderItem> items = List.of(
                line("ACK", PurchaseOrderStatus.ACKNOWLEDGED, 20L, 15L, 5L, first),
                line("SUB", PurchaseOrderStatus.SUBMITTED, 10L, null, 0L, first.plusDays(1)),
                line("PART", PurchaseOrderStatus.PARTIALLY_RECEIVED, 5L, 4L, 2L, first.plusDays(2)),
                line("DRAFT", PurchaseOrderStatus.DRAFT, 9L, null, 0L, first),
                line("PENDING", PurchaseOrderStatus.PENDING_APPROVAL, 8L, null, 0L, first),
                line("CANCEL", PurchaseOrderStatus.CANCELLED, 8L, 8L, 2L, first),
                line("FAILED", PurchaseOrderStatus.FAILED, 8L, 8L, 0L, first),
                line("RECEIVED", PurchaseOrderStatus.RECEIVED, 8L, 8L, 8L, first),
                line("LATE", PurchaseOrderStatus.ACKNOWLEDGED, 6L, 6L, 0L, first.plusDays(4))
        );
        when(itemRepository.findBySupplierProductIn(List.of(offer))).thenReturn(items);
        OpenPurchaseOrderInvestigationService service = new OpenPurchaseOrderInvestigationService(
                supplierProductRepository, itemRepository);

        PurchaseOrderEvidence evidence = service.investigate(product, warehouse, horizon, List.of(offer));

        assertEquals(12L, evidence.confirmedIncomingUnits());
        assertEquals(16L, evidence.uncertainPotentialUnits());
        assertTrue(evidence.complete());
        assertEquals(9, evidence.lines().size());
        assertEquals(0L, evidence.lines().stream().filter(line -> line.poNumber().equals("DRAFT"))
                .findFirst().orElseThrow().confirmedOutstandingUnits());
        assertEquals(0L, evidence.lines().stream().filter(line -> line.poNumber().equals("CANCEL"))
                .findFirst().orElseThrow().unconfirmedPotentialUnits());
        assertFalse(evidence.lines().stream().filter(line -> line.poNumber().equals("LATE"))
                .findFirst().orElseThrow().dueWithinHorizon());
    }

    @Test
    void relationshipMismatchMakesIncomingEvidenceIncompleteAndContributesNothing() {
        Supplier otherSupplier = new Supplier("SUP-02", "Other", SupplierStatus.ACTIVE);
        ReflectionTestUtils.setField(supplier, "id", 1L);
        ReflectionTestUtils.setField(otherSupplier, "id", 2L);
        PurchaseOrder order = order("MISMATCH", PurchaseOrderStatus.ACKNOWLEDGED, otherSupplier);
        PurchaseOrderItem item = new PurchaseOrderItem(order, offer, 10L, 10L, 0L,
                BigDecimal.ONE, LocalDate.of(2026, 11, 13));
        when(itemRepository.findBySupplierProductIn(List.of(offer))).thenReturn(List.of(item));
        OpenPurchaseOrderInvestigationService service = new OpenPurchaseOrderInvestigationService(
                supplierProductRepository, itemRepository);

        PurchaseOrderEvidence evidence = service.investigate(product, warehouse,
                new PlanningHorizon(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 13)), List.of(offer));

        assertFalse(evidence.complete());
        assertEquals(0L, evidence.confirmedIncomingUnits());
        assertTrue(evidence.warnings().get(0).contains("supplier does not match"));
    }

    @Test
    void supplierEvidenceReportsEachOffersRoundingWithoutSelectingOne() {
        Supplier alternate = new Supplier("SUP-02", "MetroTech", SupplierStatus.ACTIVE);
        SupplierProduct alternateOffer = offer(alternate, product, warehouse, 50L, 25L,
                new BigDecimal("269.0000"), SupplierOfferStatus.TEMPORARILY_UNAVAILABLE);
        when(supplierProductRepository.findByProductAndWarehouse(product, warehouse))
                .thenReturn(List.of(offer, alternateOffer));
        SupplierInvestigationService service = new SupplierInvestigationService(supplierProductRepository);

        SupplierEvidence evidence = service.investigate(product, warehouse, 813L);

        assertEquals(2, evidence.offers().size());
        SupplierOfferEvidence primary = evidence.offers().get(0);
        SupplierOfferEvidence secondary = evidence.offers().get(1);
        assertEquals(850L, primary.roundedOrderUnits());
        assertEquals(37L, primary.additionalUnitsAfterRounding());
        assertEquals(new BigDecimal("249.5000"), primary.unitPrice());
        assertEquals("INR", primary.currencyCode());
        assertEquals(825L, secondary.roundedOrderUnits());
        assertEquals(12L, secondary.additionalUnitsAfterRounding());
        assertEquals(SupplierOfferStatus.TEMPORARILY_UNAVAILABLE, secondary.offerStatus());
        assertEquals(813L, evidence.requestedUnits());
    }

    @Test
    void noSupplierOffersAreAnExplicitEvidenceWarning() {
        when(supplierProductRepository.findByProductAndWarehouse(product, warehouse)).thenReturn(List.of());
        SupplierEvidence evidence = new SupplierInvestigationService(supplierProductRepository)
                .investigate(product, warehouse, 800L);

        assertTrue(evidence.offers().isEmpty());
        assertTrue(evidence.warnings().stream().anyMatch(warning -> warning.contains("No supplier offers")));
    }

    private PurchaseOrderItem line(String number, PurchaseOrderStatus status, Long ordered,
                                   Long confirmed, Long received, LocalDate deliveryDate) {
        return new PurchaseOrderItem(order(number, status, supplier), offer, ordered, confirmed, received,
                new BigDecimal("249.5000"), deliveryDate);
    }

    private PurchaseOrder order(String number, PurchaseOrderStatus status, Supplier orderSupplier) {
        return new PurchaseOrder(number, orderSupplier, warehouse, LocalDate.of(2026, 11, 1), status, "INR", null);
    }

    private SupplierProduct offer(Supplier offerSupplier, Product offerProduct, Warehouse offerWarehouse,
                                  long moq, long pack, BigDecimal price, SupplierOfferStatus status) {
        return new SupplierProduct(offerSupplier, offerProduct, offerWarehouse, "SUP-WM", moq, pack, 5,
                price, "INR", status);
    }
}
