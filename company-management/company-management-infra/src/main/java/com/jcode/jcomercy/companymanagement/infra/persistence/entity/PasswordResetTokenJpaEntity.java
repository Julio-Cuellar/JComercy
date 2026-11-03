package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="password_reset_token", schema="company_management")
public class PasswordResetTokenJpaEntity {
    @Id private UUID id;
    @Column(name="token_hash", nullable=false, unique=true, length=64) private String tokenHash;
    @Column(name="account_id", nullable=false, length=64) private String accountId;
    @Column(name="expires_at", nullable=false) private Instant expiresAt;
    @Column(name="used_at") private Instant usedAt;
    public PasswordResetTokenJpaEntity() {}
    public static PasswordResetTokenJpaEntity create(String hash,String accountId,Instant expiresAt){
        var e=new PasswordResetTokenJpaEntity(); e.id=UUID.randomUUID(); e.tokenHash=hash; e.accountId=accountId; e.expiresAt=expiresAt; return e;
    }
    public String getAccountId(){return accountId;} public Instant getExpiresAt(){return expiresAt;} public Instant getUsedAt(){return usedAt;}
    public void use(){usedAt=Instant.now();}
}
