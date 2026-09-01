package com.jclinical.inventory.infra.adapters.in.web.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        UUID clinicId,
        String name,
        String contactName,
        String phone,
        String email,
        String taxId,
        String notes,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
