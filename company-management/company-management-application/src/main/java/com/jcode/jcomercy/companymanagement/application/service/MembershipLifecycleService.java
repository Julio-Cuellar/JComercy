package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.LifecycleDto;
import com.jcode.jcomercy.companymanagement.domain.exception.ConflictException;
import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.model.membership.CompanyMembership;
import com.jcode.jcomercy.companymanagement.domain.model.membership.MembershipStatus;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;
import com.jcode.jcomercy.companymanagement.domain.port.MembershipRepositoryPort;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * El acceso de una persona a la empresa: suspenderlo (se puede deshacer), reactivarlo y revocarlo (definitivo). El efecto
 * es inmediato en company-management: cada petición revisa la membresía en la base de datos, así que un token ya entregado
 * deja de servir en la siguiente. Nadie se suspende ni se revoca a sí mismo.
 */
public class MembershipLifecycleService {

    private final MembershipRepositoryPort memberships;
    private final CompanyRepositoryPort companies;
    private final EventOutboxPort outbox;
    private final IdempotencyRegistry idempotency;

    public MembershipLifecycleService(MembershipRepositoryPort memberships, CompanyRepositoryPort companies,
                                      EventOutboxPort outbox, IdempotencyRegistry idempotency) {
        this.memberships = Objects.requireNonNull(memberships);
        this.companies = Objects.requireNonNull(companies);
        this.outbox = Objects.requireNonNull(outbox);
        this.idempotency = Objects.requireNonNull(idempotency);
    }

    @Transactional
    public LifecycleDto suspend(String companyId, String membershipId, String actorUserId, String correlationId, String idempotencyKey) {
        return idempotency.execute("membership.suspend:" + membershipId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            CompanyMembership membership = membership(companyId, membershipId);
            requireNotYourself(membership, actorUserId, "suspender");
            if (membership.getStatus() != MembershipStatus.ACTIVE) {
                throw new ConflictException(membership.getStatus() == MembershipStatus.REVOKED
                    ? "Este acceso ya está revocado y no se puede suspender."
                    : "Este acceso ya está suspendido.");
            }
            membership.suspend();
            return changed("membership.suspended", company, membership, actorUserId, correlationId);
        });
    }

    @Transactional
    public LifecycleDto reactivate(String companyId, String membershipId, String actorUserId, String correlationId, String idempotencyKey) {
        return idempotency.execute("membership.reactivate:" + membershipId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            CompanyMembership membership = membership(companyId, membershipId);
            if (membership.getStatus() != MembershipStatus.SUSPENDED) {
                throw new ConflictException(membership.getStatus() == MembershipStatus.REVOKED
                    ? "Este acceso está revocado y ya no se puede reactivar. Hay que registrar a la persona otra vez."
                    : "Este acceso no está suspendido.");
            }
            membership.reactivate();
            return changed("membership.reactivated", company, membership, actorUserId, correlationId);
        });
    }

    @Transactional
    public LifecycleDto revoke(String companyId, String membershipId, String actorUserId, String correlationId, String idempotencyKey) {
        return idempotency.execute("membership.revoke:" + membershipId, idempotencyKey, () -> {
            Company company = requireCompany(companyId);
            CompanyMembership membership = membership(companyId, membershipId);
            requireNotYourself(membership, actorUserId, "revocar");
            if (membership.getStatus() == MembershipStatus.REVOKED) {
                throw new ConflictException("Este acceso ya está revocado.");
            }
            membership.revoke();
            return changed("membership.revoked", company, membership, actorUserId, correlationId);
        });
    }

    private LifecycleDto changed(String eventType, Company company, CompanyMembership membership, String actorUserId, String correlationId) {
        CompanyMembership saved = memberships.saveMembership(membership);
        String correlation = correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", "");
        outbox.recordEvent(eventType, "1.0", saved.getVersion(), "company_management", correlation, null,
            company.getPublicAccountId(), company.getPublicCompanyId(), null, actorUserId,
            Map.of("membershipId", saved.getPublicMembershipId(), "companyId", company.getPublicCompanyId(),
                "userId", saved.getUserId(), "roles", saved.getRoles().stream().sorted().toList(), "status", saved.getStatus().name()));
        return new LifecycleDto(saved.getPublicMembershipId(), saved.getStatus().name(), saved.getUpdatedAt(), correlation);
    }

    /** Quien suspende o revoca su propio acceso se queda fuera de la empresa sin poder deshacerlo. */
    private static void requireNotYourself(CompanyMembership membership, String actorUserId, String verb) {
        if (membership.getUserId().equals(actorUserId)) {
            throw new DomainRuleViolationException("No puedes " + verb + " tu propio acceso: te quedarías fuera de la empresa. Pídeselo a otra persona de la administración.");
        }
    }

    private CompanyMembership membership(String companyId, String membershipId) {
        return memberships.findMembershipByPublicId(membershipId)
            .filter(found -> found.getPublicCompanyId().equals(companyId))
            .orElseThrow(() -> new EntityNotFoundException("Membresía", membershipId));
    }

    private Company requireCompany(String companyId) {
        return companies.findByPublicCompanyId(companyId).orElseThrow(() -> new EntityNotFoundException("Empresa", companyId));
    }
}
