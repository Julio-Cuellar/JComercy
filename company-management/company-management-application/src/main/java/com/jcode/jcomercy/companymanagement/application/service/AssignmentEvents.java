package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.domain.model.assignment.BranchAssignment;
import com.jcode.jcomercy.companymanagement.domain.model.company.Company;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Los eventos de una sucursal de una persona ({@code branch-assignment.created}, {@code .updated} y {@code .revoked}). Los
 * usan quien las agrega al activar un acceso y quien las administra después. Llevan identificadores y estado, nunca datos
 * personales. El contrato solo admite ACTIVE y SUSPENDED en el estado: al retirarla no se publica estado.
 */
final class AssignmentEvents {

    private AssignmentEvents() {
    }

    static void record(EventOutboxPort outbox, String type, BranchAssignment assignment, Company company, String actorUserId,
                       String correlationId, boolean withStatus) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assignmentId", assignment.getPublicAssignmentId());
        data.put("membershipId", assignment.getPublicMembershipId());
        data.put("branchId", assignment.getPublicBranchId());
        if (withStatus) {
            data.put("status", assignment.getStatus().name());
        }
        outbox.recordEvent(type, "1.0", assignment.getVersion(), "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null, company.getPublicAccountId(), company.getPublicCompanyId(), assignment.getPublicBranchId(), actorUserId, data);
    }
}
