package com.jcode.jcomercy.companymanagement.application.dto;

import com.jcode.jcomercy.companymanagement.domain.model.company.DomicilioFiscal;

public record DomicilioFiscalDto(String calle, String numeroExterior, String numeroInterior, String colonia,
                                 String codigoPostal, String municipioAlcaldia, String estado, String pais) {
    public static DomicilioFiscalDto fromDomain(DomicilioFiscal address) {
        return new DomicilioFiscalDto(address.getCalle(), address.getNumeroExterior(), address.getNumeroInterior(),
            address.getColonia(), address.getCodigoPostal(), address.getMunicipioAlcaldia(), address.getEstado(), address.getPais());
    }
}
