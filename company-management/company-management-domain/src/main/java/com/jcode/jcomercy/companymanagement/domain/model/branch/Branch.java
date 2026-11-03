package com.jcode.jcomercy.companymanagement.domain.model.branch;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.model.company.DomicilioFiscal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Branch {
    private final UUID id;
    private final String publicBranchId;
    private final String publicCompanyId;
    private String code;
    private String name;
    private BranchType type;
    private DomicilioFiscal domicilio;
    private String phoneNumber;
    private String email;
    private String rfc;
    private String fiscalName;
    private String fiscalPostalCode;
    private String centroCostoId;
    private String almacenId;
    private String listaPreciosId;
    private String serieFacturacion;
    private Long folioActual;
    private BranchStatus status;
    private String timeZone;
    private String moneda;
    private String responsableId;
    private LocalDate fechaApertura;
    private LocalDate fechaCierre;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Branch(UUID id, String publicBranchId, String publicCompanyId, String code, String name,
                  BranchType type, DomicilioFiscal domicilio, String phoneNumber, String email,
                  String rfc, String fiscalName, String fiscalPostalCode,
                  String centroCostoId, String almacenId, String listaPreciosId, String serieFacturacion,
                  Long folioActual, BranchStatus status, String timeZone, String moneda, String responsableId,
                  LocalDate fechaApertura, LocalDate fechaCierre, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id no puede ser nulo");
        this.publicBranchId = Objects.requireNonNull(publicBranchId, "publicBranchId no puede ser nulo");
        this.publicCompanyId = Objects.requireNonNull(publicCompanyId, "publicCompanyId no puede ser nulo");
        this.code = validateCode(code);
        this.name = validateName(name);
        this.type = Objects.requireNonNull(type, "type no puede ser nulo");
        this.domicilio = Objects.requireNonNull(domicilio, "El domicilio no puede ser nulo");
        this.phoneNumber = required(phoneNumber, "El número de contacto");
        this.email = optional(email);
        this.rfc = optional(rfc);
        this.fiscalName = optional(fiscalName);
        this.fiscalPostalCode = optional(fiscalPostalCode);
        this.centroCostoId = optional(centroCostoId);
        this.almacenId = optional(almacenId);
        this.listaPreciosId = optional(listaPreciosId);
        this.serieFacturacion = optional(serieFacturacion);
        this.folioActual = folioActual != null ? folioActual : 0L;
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.timeZone = timeZone != null ? timeZone : "America/Mexico_City";
        this.moneda = moneda != null && !moneda.isBlank() ? moneda.trim().toUpperCase() : "MXN";
        this.responsableId = optional(responsableId);
        this.fechaApertura = fechaApertura;
        this.fechaCierre = fechaCierre;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt no puede ser nulo");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt no puede ser nulo");
        this.version = version;
    }

    public static Branch create(String publicCompanyId, String code, String name, BranchType type,
                                DomicilioFiscal domicilio, String phoneNumber, String email,
                                String rfc, String fiscalName, String fiscalPostalCode,
                                String centroCostoId, String almacenId, String listaPreciosId, String serieFacturacion,
                                Long folioActual, String moneda, String responsableId,
                                LocalDate fechaApertura, LocalDate fechaCierre) {
        Instant now = Instant.now();
        String publicId = "brn_" + UUID.randomUUID().toString().replace("-", "");
        return new Branch(UUID.randomUUID(), publicId, publicCompanyId, code, name, type, domicilio, phoneNumber, email,
            rfc, fiscalName, fiscalPostalCode, centroCostoId, almacenId, listaPreciosId, serieFacturacion, folioActual,
            BranchStatus.CONFIGURING, "America/Mexico_City", moneda, responsableId, fechaApertura, fechaCierre, now, now, 0L);
    }

    public static Branch create(String publicCompanyId, String code, String name, boolean isHeadOffice,
                                DomicilioFiscal domicilio, String phoneNumber) {
        return create(publicCompanyId, code, name, isHeadOffice ? BranchType.MATRIZ : BranchType.SUCURSAL,
            domicilio, phoneNumber, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private String validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new DomainRuleViolationException("El código de la sucursal no puede estar vacío.");
        }
        return code.trim().toUpperCase();
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new DomainRuleViolationException("El nombre de la sucursal no puede estar vacío.");
        }
        return name.trim();
    }

    private String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainRuleViolationException(label + " no puede estar vacío.");
        }
        return value.trim();
    }

    private String optional(String value) { return value == null ? "" : value.trim(); }

    public void updateProfile(String name, String timeZone) {
        if (this.status == BranchStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede actualizar una sucursal eliminada.");
        }
        this.name = validateName(name);
        if (timeZone != null && !timeZone.trim().isEmpty()) {
            this.timeZone = timeZone.trim();
        }
        this.updatedAt = Instant.now();
    }

    public void completeConfiguration() {
        if (this.status != BranchStatus.CONFIGURING) {
            throw new DomainRuleViolationException("La sucursal no está en estado CONFIGURING.");
        }
        this.status = BranchStatus.READY_FOR_ACTIVATION;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (this.status == BranchStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede activar una sucursal eliminada.");
        }
        this.status = BranchStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void requireCoverage() {
        this.status = BranchStatus.PENDING_PLAN;
        this.updatedAt = Instant.now();
    }

    public void suspend(String reason) {
        if (this.status == BranchStatus.DELETED) {
            throw new DomainRuleViolationException("No se puede suspender una sucursal eliminada.");
        }
        this.status = BranchStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        if (this.status != BranchStatus.SUSPENDED && this.status != BranchStatus.PENDING_PLAN) {
            throw new DomainRuleViolationException("Solo se pueden reactivar sucursales suspendidas o pendientes de plan.");
        }
        this.status = BranchStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void setHeadOffice(boolean headOffice) {
        this.type = headOffice ? BranchType.MATRIZ : BranchType.SUCURSAL;
        this.updatedAt = Instant.now();
    }

    public void delete() {
        this.status = BranchStatus.DELETED;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getPublicBranchId() { return publicBranchId; }
    public String getPublicCompanyId() { return publicCompanyId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BranchType getType() { return type; }
    public boolean isHeadOffice() { return type == BranchType.MATRIZ; }
    public DomicilioFiscal getDomicilio() { return domicilio; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public String getRfc() { return rfc; }
    public String getFiscalName() { return fiscalName; }
    public String getFiscalPostalCode() { return fiscalPostalCode; }
    public String getCentroCostoId() { return centroCostoId; }
    public String getAlmacenId() { return almacenId; }
    public String getListaPreciosId() { return listaPreciosId; }
    public String getSerieFacturacion() { return serieFacturacion; }
    public Long getFolioActual() { return folioActual; }
    public BranchStatus getStatus() { return status; }
    public String getTimeZone() { return timeZone; }
    public String getMoneda() { return moneda; }
    public String getResponsableId() { return responsableId; }
    public LocalDate getFechaApertura() { return fechaApertura; }
    public LocalDate getFechaCierre() { return fechaCierre; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
