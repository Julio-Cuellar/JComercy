package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.branch.Branch;

import java.time.Instant;

public record BranchDto(
    String publicBranchId,
    String publicCompanyId,
    String code,
    String name,
    String type,
    DomicilioFiscalDto domicilio,
    String phoneNumber,
    String email,
    String rfc,
    String fiscalName,
    String fiscalPostalCode,
    String centroCostoId,
    String almacenId,
    String listaPreciosId,
    String serieFacturacion,
    Long folioActual,
    String status,
    String timeZone,
    String moneda,
    String responsableId,
    java.time.LocalDate fechaApertura,
    java.time.LocalDate fechaCierre,
    Instant createdAt,
    Instant updatedAt,
    long version
) {
    public static BranchDto fromDomain(Branch branch) {
        return new BranchDto(
            branch.getPublicBranchId(),
            branch.getPublicCompanyId(),
            branch.getCode(),
            branch.getName(),
            branch.getType().name(),
            DomicilioFiscalDto.fromDomain(branch.getDomicilio()),
            branch.getPhoneNumber(),
            branch.getEmail(),
            branch.getRfc(),
            branch.getFiscalName(),
            branch.getFiscalPostalCode(),
            branch.getCentroCostoId(),
            branch.getAlmacenId(),
            branch.getListaPreciosId(),
            branch.getSerieFacturacion(),
            branch.getFolioActual(),
            branch.getStatus().name(),
            branch.getTimeZone(),
            branch.getMoneda(),
            branch.getResponsableId(),
            branch.getFechaApertura(),
            branch.getFechaCierre(),
            branch.getCreatedAt(),
            branch.getUpdatedAt(),
            branch.getVersion()
        );
    }
}
