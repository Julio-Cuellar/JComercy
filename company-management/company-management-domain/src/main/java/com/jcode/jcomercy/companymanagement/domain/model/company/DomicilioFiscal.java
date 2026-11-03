package com.jcode.jcomercy.companymanagement.domain.model.company;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;

import java.util.Objects;

public class DomicilioFiscal {
    private final String calle;
    private final String numeroExterior;
    private final String numeroInterior;
    private final String colonia;
    private final String codigoPostal;
    private final String municipioAlcaldia;
    private final String estado;
    private final String pais;

    public DomicilioFiscal(String calle, String numeroExterior, String numeroInterior, String colonia,
                           String codigoPostal, String municipioAlcaldia, String estado, String pais) {
        this.calle = required(calle, "La calle");
        this.numeroExterior = required(numeroExterior, "El número exterior");
        this.numeroInterior = optional(numeroInterior);
        this.colonia = required(colonia, "La colonia");
        this.codigoPostal = required(codigoPostal, "El código postal");
        this.municipioAlcaldia = required(municipioAlcaldia, "El municipio o alcaldía");
        this.estado = required(estado, "El estado");
        this.pais = required(pais, "El país");
    }

    private static String required(String value, String label) {
        if (value == null || value.trim().isEmpty()) throw new DomainRuleViolationException(label + " no puede estar vacío.");
        return value.trim();
    }

    private static String optional(String value) { return value == null ? "" : value.trim(); }

    public String getCalle() { return calle; }
    public String getNumeroExterior() { return numeroExterior; }
    public String getNumeroInterior() { return numeroInterior; }
    public String getColonia() { return colonia; }
    public String getCodigoPostal() { return codigoPostal; }
    public String getMunicipioAlcaldia() { return municipioAlcaldia; }
    public String getEstado() { return estado; }
    public String getPais() { return pais; }
}
