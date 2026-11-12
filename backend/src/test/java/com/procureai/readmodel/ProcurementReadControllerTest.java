package com.procureai.readmodel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.procureai.repository.PurchaseOrderRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProcurementReadControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private PurchaseOrderRepository orderRepository;

    @Test
    void catalogAndInventoryEndpointsReturnSeededDtos() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku == 'WM-001')].unitOfMeasure").value("EACH"));
        mockMvc.perform(get("/api/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'DEL-01')].name").value("Delhi Fulfillment Center"));
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.productSku == 'WM-001' && @.warehouseCode == 'DEL-01')].onHandUnits").value(260))
                .andExpect(jsonPath("$[?(@.productSku == 'WM-001' && @.warehouseCode == 'DEL-01')].availableUnits").value(215));
    }

    @Test
    void approvalOfferAndPurchaseOrderEndpointsReturnPersistedReadModels() throws Exception {
        mockMvc.perform(get("/api/supplier-offers").param("productSku", "WM-001").param("warehouseCode", "DEL-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].supplierCode").exists());
        mockMvc.perform(get("/api/agent-runs"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/approvals"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/purchase-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.poNumber == 'PO-DEL-DEMO-001')].items[0].confirmedUnits").value(250));
        Long orderId = orderRepository.findByPoNumber("PO-DEL-DEMO-001").orElseThrow().getId();
        mockMvc.perform(get("/api/purchase-orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poNumber").value("PO-DEL-DEMO-001"))
                .andExpect(jsonPath("$.items[0].receivedUnits").value(50));
    }

    @Test
    void unknownOrderAndUnknownSupplierOfferScopeReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/purchase-orders/999999"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/supplier-offers").param("productSku", "NO-SUCH-SKU").param("warehouseCode", "DEL-01"))
                .andExpect(status().isNotFound());
    }
}
