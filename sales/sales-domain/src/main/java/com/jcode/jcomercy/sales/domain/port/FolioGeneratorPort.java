package com.jcode.jcomercy.sales.domain.port;

/** El folio de cada ticket: consecutivo por sucursal, sin repetidos aunque se pidan a la vez. */
public interface FolioGeneratorPort {

    /** El siguiente folio de la sucursal, por ejemplo {@code TCK-000123}. */
    String next(String companyId, String branchId);
}
