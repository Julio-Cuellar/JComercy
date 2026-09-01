package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import com.jcode.jcomercy.companymanagement.domain.model.branch.Branch;
import com.jcode.jcomercy.companymanagement.domain.model.branch.BranchStatus;
import com.jcode.jcomercy.companymanagement.domain.model.branch.BranchType;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "branch", schema = "company_management")
public class BranchJpaEntity {
    @Id
    private UUID id;

    @Column(name = "public_branch_id", nullable = false, unique = true, length = 64)
    private String publicBranchId;

    @Column(name = "public_company_id", nullable = false, length = 64)
    private String publicCompanyId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "is_head_office", nullable = false)
    private boolean isHeadOffice;

    @Column(name = "type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private BranchType type;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "calle", column = @Column(name = "domicilio_calle", nullable = false, length = 200)),
        @AttributeOverride(name = "numeroExterior", column = @Column(name = "domicilio_numero_exterior", nullable = false, length = 30)),
        @AttributeOverride(name = "numeroInterior", column = @Column(name = "domicilio_numero_interior", nullable = false, length = 30)),
        @AttributeOverride(name = "colonia", column = @Column(name = "domicilio_colonia", nullable = false, length = 120)),
        @AttributeOverride(name = "codigoPostal", column = @Column(name = "domicilio_codigo_postal", nullable = false, length = 20)),
        @AttributeOverride(name = "municipioAlcaldia", column = @Column(name = "domicilio_municipio_alcaldia", nullable = false, length = 120)),
        @AttributeOverride(name = "estado", column = @Column(name = "domicilio_estado", nullable = false, length = 100)),
        @AttributeOverride(name = "pais", column = @Column(name = "domicilio_pais", nullable = false, length = 100))
    })
    private DomicilioFiscalJpaEmbeddable domicilio;
    @Column(name = "phone_number", nullable = false, length = 30)
    private String phoneNumber;

    @Column(name = "email", length = 255) private String email;
    @Column(name = "rfc", length = 13) private String rfc;
    @Column(name = "fiscal_name", length = 255) private String fiscalName;
    @Column(name = "fiscal_postal_code", length = 20) private String fiscalPostalCode;
    @Column(name = "centro_costo_id", length = 64) private String centroCostoId;
    @Column(name = "almacen_id", length = 64) private String almacenId;
    @Column(name = "lista_precios_id", length = 64) private String listaPreciosId;
    @Column(name = "serie_facturacion", length = 32) private String serieFacturacion;
    @Column(name = "folio_actual") private Long folioActual;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private BranchStatus status;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "moneda", nullable = false, length = 3) private String moneda;
    @Column(name = "responsable_id", length = 64) private String responsableId;
    @Column(name = "fecha_apertura") private LocalDate fechaApertura;
    @Column(name = "fecha_cierre") private LocalDate fechaCierre;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public BranchJpaEntity() {}

    public static BranchJpaEntity fromDomain(Branch domain) {
        BranchJpaEntity entity = new BranchJpaEntity();
        entity.id = domain.getId();
        entity.publicBranchId = domain.getPublicBranchId();
        entity.publicCompanyId = domain.getPublicCompanyId();
        entity.code = domain.getCode();
        entity.name = domain.getName();
        entity.isHeadOffice = domain.isHeadOffice();
        entity.type = domain.getType();
        entity.domicilio = DomicilioFiscalJpaEmbeddable.fromDomain(domain.getDomicilio());
        entity.phoneNumber = domain.getPhoneNumber();
        entity.email = domain.getEmail();
        entity.rfc = domain.getRfc();
        entity.fiscalName = domain.getFiscalName();
        entity.fiscalPostalCode = domain.getFiscalPostalCode();
        entity.centroCostoId = domain.getCentroCostoId();
        entity.almacenId = domain.getAlmacenId();
        entity.listaPreciosId = domain.getListaPreciosId();
        entity.serieFacturacion = domain.getSerieFacturacion();
        entity.folioActual = domain.getFolioActual();
        entity.status = domain.getStatus();
        entity.timeZone = domain.getTimeZone();
        entity.moneda = domain.getMoneda();
        entity.responsableId = domain.getResponsableId();
        entity.fechaApertura = domain.getFechaApertura();
        entity.fechaCierre = domain.getFechaCierre();
        entity.createdAt = domain.getCreatedAt();
        entity.updatedAt = domain.getUpdatedAt();
        entity.version = domain.getVersion();
        return entity;
    }

    public Branch toDomain() {
        return new Branch(id, publicBranchId, publicCompanyId, code, name,
            type != null ? type : (isHeadOffice ? BranchType.MATRIZ : BranchType.SUCURSAL),
            domicilio.toDomain(), phoneNumber, email, rfc, fiscalName, fiscalPostalCode, centroCostoId, almacenId, listaPreciosId,
            serieFacturacion, folioActual, status, timeZone, moneda, responsableId, fechaApertura, fechaCierre, createdAt, updatedAt, version);
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public String getPublicBranchId() { return publicBranchId; }
    public String getPublicCompanyId() { return publicCompanyId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isHeadOffice() { return isHeadOffice; }
    public DomicilioFiscalJpaEmbeddable getDomicilio() { return domicilio; }
    public String getPhoneNumber() { return phoneNumber; }
    public BranchStatus getStatus() { return status; }
    public String getTimeZone() { return timeZone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
