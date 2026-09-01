package com.jclinical.inventory.infra.adapters.in.web.dto;

public record SupplierRequest(
        String name,
        String contactName,
        String phone,
        String email,
        String taxId,
        String notes,
        Boolean active
) {}
