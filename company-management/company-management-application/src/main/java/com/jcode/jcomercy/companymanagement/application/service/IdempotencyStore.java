package com.jcode.jcomercy.companymanagement.application.service;

public interface IdempotencyStore {
    boolean claim(String operation, String key);
    void release(String operation, String key);
}
