package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import com.jcode.jcomercy.companymanagement.domain.model.company.DomicilioFiscal;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class DomicilioFiscalJpaEmbeddable {
    @Column(name = "fiscal_calle", nullable = false, length = 200) private String calle;
    @Column(name = "fiscal_numero_exterior", nullable = false, length = 30) private String numeroExterior;
    @Column(name = "fiscal_numero_interior", nullable = false, length = 30) private String numeroInterior;
    @Column(name = "fiscal_colonia", nullable = false, length = 120) private String colonia;
    @Column(name = "fiscal_codigo_postal", nullable = false, length = 20) private String codigoPostal;
    @Column(name = "fiscal_municipio_alcaldia", nullable = false, length = 120) private String municipioAlcaldia;
    @Column(name = "fiscal_estado", nullable = false, length = 100) private String estado;
    @Column(name = "fiscal_pais", nullable = false, length = 100) private String pais;

    public static DomicilioFiscalJpaEmbeddable fromDomain(DomicilioFiscal d) {
        DomicilioFiscalJpaEmbeddable e = new DomicilioFiscalJpaEmbeddable();
        e.calle=d.getCalle(); e.numeroExterior=d.getNumeroExterior(); e.numeroInterior=d.getNumeroInterior(); e.colonia=d.getColonia();
        e.codigoPostal=d.getCodigoPostal(); e.municipioAlcaldia=d.getMunicipioAlcaldia(); e.estado=d.getEstado(); e.pais=d.getPais();
        return e;
    }
    public DomicilioFiscal toDomain() { return new DomicilioFiscal(calle, numeroExterior, numeroInterior, colonia, codigoPostal, municipioAlcaldia, estado, pais); }
}
