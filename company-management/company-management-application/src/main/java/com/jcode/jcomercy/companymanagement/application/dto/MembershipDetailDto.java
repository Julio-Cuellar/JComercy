package com.jcode.jcomercy.companymanagement.application.dto;

import java.time.Instant;
import java.util.List;

/** El detalle de una membresía: lo de la lista, sus fechas y un resumen de las sucursales que tiene asignadas. */
public record MembershipDetailDto(
    String membershipId,
    String userId,
    String companyId,
    String status,
    List<String> roleIds,
    List<AssignmentSummary> assignments,
    Instant createdAt,
    Instant updatedAt,
    long version
) {

    public record AssignmentSummary(String assignmentId, String branchId, String status) { }
}
