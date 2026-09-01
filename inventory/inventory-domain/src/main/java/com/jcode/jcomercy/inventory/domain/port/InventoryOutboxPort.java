package com.jcode.jcomercy.inventory.domain.port;

public interface InventoryOutboxPort {
    void publish(String eventType, String companyId, String branchId, String actorUserId, String correlationId, Object data);
}
