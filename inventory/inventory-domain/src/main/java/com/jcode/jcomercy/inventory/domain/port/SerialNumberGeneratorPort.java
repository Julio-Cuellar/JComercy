package com.jcode.jcomercy.inventory.domain.port;

public interface SerialNumberGeneratorPort {
    String next(String companyId);
}
