package com.jcode.jcomercy.companymanagement.application.dto;

import java.util.List;

/** Una membresía en la lista: quién es, en qué estado está y con qué roles. Nunca lleva datos de acceso. */
public record MembershipSummaryDto(
    String membershipId,
    String userId,
    String companyId,
    String status,
    List<String> roleIds,
    long version
) { }
