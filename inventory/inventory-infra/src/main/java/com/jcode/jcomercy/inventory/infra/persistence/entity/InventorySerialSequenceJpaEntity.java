package com.jcode.jcomercy.inventory.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory_serial_sequence", schema = "inventory")
public class InventorySerialSequenceJpaEntity {
    @Id @Column(name = "company_id", length = 100) private String companyId;
    @Column(name = "next_value", nullable = false) private long nextValue;
    @Version @Column(nullable = false) private long version;
    protected InventorySerialSequenceJpaEntity() { }
    public InventorySerialSequenceJpaEntity(String companyId, long nextValue) { this.companyId = companyId; this.nextValue = nextValue; }
    public String getCompanyId() { return companyId; }
    public long getNextValue() { return nextValue; }
    public void advance() { nextValue++; }
}
