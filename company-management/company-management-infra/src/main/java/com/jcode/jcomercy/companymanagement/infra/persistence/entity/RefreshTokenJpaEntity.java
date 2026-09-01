package com.jcode.jcomercy.companymanagement.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="refresh_token", schema="company_management")
public class RefreshTokenJpaEntity {
    @Id private UUID id;
    @Column(name="token_hash", nullable=false, unique=true, length=64) private String tokenHash;
    @Column(name="account_id", nullable=false, length=64) private String accountId;
    @Column(name="expires_at", nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    public RefreshTokenJpaEntity() {}
    public static RefreshTokenJpaEntity create(String hash,String accountId,Instant expiresAt){
        var e=new RefreshTokenJpaEntity(); e.id=UUID.randomUUID(); e.tokenHash=hash; e.accountId=accountId; e.expiresAt=expiresAt; return e;
    }
    public String getAccountId(){return accountId;} public Instant getExpiresAt(){return expiresAt;} public Instant getRevokedAt(){return revokedAt;}
    public void revoke(){revokedAt=Instant.now();}
}
