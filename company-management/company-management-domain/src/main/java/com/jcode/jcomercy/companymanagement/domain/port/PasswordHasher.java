package com.jcode.jcomercy.companymanagement.domain.port;

public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String hashedPassword);
}
