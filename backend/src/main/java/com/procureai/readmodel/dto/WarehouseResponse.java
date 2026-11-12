package com.procureai.readmodel.dto;

public record WarehouseResponse(Long id, String code, String name, String city, String countryCode,
                                Boolean active) { }
