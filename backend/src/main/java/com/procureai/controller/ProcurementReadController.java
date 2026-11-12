package com.procureai.controller;

import java.util.List;

import com.procureai.agent.dto.AgentRunResponse;
import com.procureai.readmodel.dto.ApprovalQueueResponse;
import com.procureai.readmodel.dto.CatalogProductResponse;
import com.procureai.readmodel.dto.InventoryRowResponse;
import com.procureai.readmodel.dto.PurchaseOrderResponse;
import com.procureai.readmodel.dto.SupplierOfferResponse;
import com.procureai.readmodel.dto.WarehouseResponse;
import com.procureai.readmodel.service.ProcurementReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProcurementReadController {
    private final ProcurementReadService readService;

    public ProcurementReadController(ProcurementReadService readService) { this.readService = readService; }

    @GetMapping("/products")
    public List<CatalogProductResponse> products() { return readService.products(); }

    @GetMapping("/warehouses")
    public List<WarehouseResponse> warehouses() { return readService.warehouses(); }

    @GetMapping("/inventory")
    public List<InventoryRowResponse> inventory() { return readService.inventory(); }

    @GetMapping("/supplier-offers")
    public List<SupplierOfferResponse> offers(@RequestParam String productSku, @RequestParam String warehouseCode) {
        return readService.offers(productSku, warehouseCode);
    }

    @GetMapping("/agent-runs")
    public List<AgentRunResponse> runs() { return readService.recentRuns(); }

    @GetMapping("/approvals")
    public List<ApprovalQueueResponse> approvals() { return readService.approvals(); }

    @GetMapping("/purchase-orders")
    public List<PurchaseOrderResponse> purchaseOrders() { return readService.purchaseOrders(); }

    @GetMapping("/purchase-orders/{id}")
    public PurchaseOrderResponse purchaseOrder(@PathVariable Long id) { return readService.purchaseOrder(id); }
}
