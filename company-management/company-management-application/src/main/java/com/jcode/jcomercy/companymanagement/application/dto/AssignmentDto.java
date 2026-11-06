package com.jcode.jcomercy.companymanagement.application.dto;

import java.util.List;

/**
 * Una sucursal de una persona. Los roles son los de la persona, no los de cada sucursal: el contrato prevé roles por
 * sucursal, pero el modelo todavía no los distingue.
 */
public record AssignmentDto(
    String assignmentId,
    String membershipId,
    String branchId,
    List<String> roleIds,
    String status,
    long version
) { }
