package com.jcode.jcomercy.sales.application.service;

import com.jcode.jcomercy.sales.application.exception.SalesApplicationException;
import com.jcode.jcomercy.sales.domain.model.sale.Sale;
import com.jcode.jcomercy.sales.domain.port.SaleRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Consultar las ventas de una sucursal. Una venta de otra sucursal o empresa no se encuentra, igual que una que no existe. */
public class SaleQueryService {

    private final SaleRepositoryPort sales;

    public SaleQueryService(SaleRepositoryPort sales) {
        this.sales = sales;
    }

    @Transactional(readOnly = true)
    public Sale get(String companyId, String branchId, UUID saleId) {
        return sales.findById(companyId, branchId, saleId)
                .orElseThrow(() -> SalesApplicationException.notFound("No encontramos esa venta en esta sucursal."));
    }

    @Transactional(readOnly = true)
    public List<Sale> list(String companyId, String branchId) {
        return sales.findByBranch(companyId, branchId);
    }
}
