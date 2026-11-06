package com.jcode.jcomercy.companymanagement.application.service;

import com.jcode.jcomercy.companymanagement.application.dto.BranchDto;
import com.jcode.jcomercy.companymanagement.domain.exception.DuplicateResourceException;
import com.jcode.jcomercy.companymanagement.domain.exception.EntityNotFoundException;
import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.model.branch.Branch;
import com.jcode.jcomercy.companymanagement.domain.model.branch.BranchType;
import com.jcode.jcomercy.companymanagement.domain.model.company.DomicilioFiscal;
import com.jcode.jcomercy.companymanagement.domain.port.BranchRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.CompanyRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.EventOutboxPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

public class BranchApplicationService {
    private final BranchRepositoryPort branchRepository;
    private final CompanyRepositoryPort companyRepository;
    private final EventOutboxPort outboxPort;
    private final IdempotencyRegistry idempotencyRegistry;

    public BranchApplicationService(BranchRepositoryPort branchRepository, CompanyRepositoryPort companyRepository, EventOutboxPort outboxPort, IdempotencyRegistry idempotencyRegistry) {
        this.branchRepository = Objects.requireNonNull(branchRepository);
        this.companyRepository = Objects.requireNonNull(companyRepository);
        this.outboxPort = Objects.requireNonNull(outboxPort);
        this.idempotencyRegistry = Objects.requireNonNull(idempotencyRegistry);
    }

    @Transactional
    public BranchDto createBranch(String publicCompanyId, String code, String name, com.jcode.jcomercy.companymanagement.domain.model.branch.BranchType type,
                                  String calle, String numeroExterior, String numeroInterior, String colonia,
                                  String codigoPostal, String municipioAlcaldia, String estado, String pais, String phoneNumber, String email,
                                  String rfc, String fiscalName, String fiscalPostalCode,
                                  String centroCostoId, String almacenId, String listaPreciosId, String serieFacturacion,
                                  Long folioActual, String moneda, String responsableId, LocalDate fechaApertura, LocalDate fechaCierre,
                                  String actorUserId, String correlationId, String idempotencyKey) {
        return idempotencyRegistry.execute("branch.create:" + publicCompanyId, idempotencyKey,
            () -> createBranchInternal(publicCompanyId, code, name, type, calle, numeroExterior, numeroInterior, colonia,
                codigoPostal, municipioAlcaldia, estado, pais, phoneNumber, email, rfc, fiscalName, fiscalPostalCode,
                centroCostoId, almacenId, listaPreciosId, serieFacturacion, folioActual, moneda, responsableId,
                fechaApertura, fechaCierre, actorUserId, correlationId));
    }

    public BranchDto createBranch(String publicCompanyId, String code, String name, boolean isHeadOffice,
                                  String calle, String numeroExterior, String numeroInterior, String colonia,
                                  String codigoPostal, String municipioAlcaldia, String estado, String pais, String phoneNumber,
                                  String actorUserId, String correlationId, String idempotencyKey) {
        return createBranch(publicCompanyId, code, name,
            isHeadOffice ? com.jcode.jcomercy.companymanagement.domain.model.branch.BranchType.MATRIZ : com.jcode.jcomercy.companymanagement.domain.model.branch.BranchType.SUCURSAL,
            calle, numeroExterior, numeroInterior, colonia, codigoPostal, municipioAlcaldia, estado, pais, phoneNumber,
            null, null, null, null, null, null, null, null, null, null, null, null, null, actorUserId, correlationId, idempotencyKey);
    }

    private BranchDto createBranchInternal(String publicCompanyId, String code, String name, com.jcode.jcomercy.companymanagement.domain.model.branch.BranchType type,
                                           String calle, String numeroExterior, String numeroInterior, String colonia,
                                           String codigoPostal, String municipioAlcaldia, String estado, String pais, String phoneNumber, String email,
                                           String rfc, String fiscalName, String fiscalPostalCode,
                                           String centroCostoId, String almacenId, String listaPreciosId, String serieFacturacion,
                                           Long folioActual, String moneda, String responsableId, LocalDate fechaApertura, LocalDate fechaCierre,
                                           String actorUserId, String correlationId) {
        if (!companyRepository.existsByPublicCompanyId(publicCompanyId)) {
            throw new EntityNotFoundException("Company", publicCompanyId);
        }
        companyRepository.findByPublicCompanyIdForUpdate(publicCompanyId);
        if (branchRepository.existsByPublicCompanyIdAndCode(publicCompanyId, code)) {
            throw new DuplicateResourceException("Branch", "code", code);
        }
        List<Branch> companyBranches = branchRepository.findByPublicCompanyId(publicCompanyId);
        if (companyBranches.isEmpty() && type != BranchType.MATRIZ) {
            throw new DomainRuleViolationException("La primera sucursal de la empresa debe ser de tipo MATRIZ.");
        }
        if (!companyBranches.isEmpty() && type == BranchType.MATRIZ) {
            throw new DuplicateResourceException("Branch", "type", "MATRIZ");
        }

        Branch branch = Branch.create(publicCompanyId, code, name, type,
            new DomicilioFiscal(calle, numeroExterior, numeroInterior, colonia, codigoPostal, municipioAlcaldia, estado, pais), phoneNumber,
            email, rfc, fiscalName, fiscalPostalCode, centroCostoId, almacenId, listaPreciosId, serieFacturacion,
            folioActual, moneda, responsableId, fechaApertura, fechaCierre);
        Branch saved = branchRepository.save(branch);

        String publicAccountId = companyRepository.findByPublicCompanyId(publicCompanyId)
            .orElseThrow(() -> new EntityNotFoundException("Company", publicCompanyId)).getPublicAccountId();
        outboxPort.recordEvent(
            "branch.created",
            "1.0",
            saved.getVersion(),
            "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null,
            publicAccountId,
            publicCompanyId,
            saved.getPublicBranchId(),
            actorUserId,
            Map.ofEntries(
                Map.entry("branchId", saved.getPublicBranchId()),
                Map.entry("companyId", publicCompanyId),
                Map.entry("code", saved.getCode()),
                Map.entry("name", saved.getName()),
                Map.entry("type", saved.getType().name()),
                Map.entry("isHeadOffice", saved.isHeadOffice()),
                Map.entry("status", saved.getStatus().name())
            )
        );

        return BranchDto.fromDomain(saved);
    }

    public BranchDto getBranchByPublicId(String publicCompanyId, String publicBranchId) {
        Branch branch = branchRepository.findByPublicCompanyIdAndPublicBranchId(publicCompanyId, publicBranchId)
            .orElseThrow(() -> new EntityNotFoundException("Branch", publicBranchId));
        return BranchDto.fromDomain(branch);
    }

    public List<BranchDto> listBranchesByCompany(String publicCompanyId) {
        return branchRepository.findByPublicCompanyId(publicCompanyId)
            .stream()
            .map(BranchDto::fromDomain)
            .toList();
    }

    @Transactional
    public BranchDto activateBranch(String publicCompanyId, String publicBranchId, String actorUserId, String correlationId, String idempotencyKey) {
        return idempotencyRegistry.execute("branch.activate:" + publicCompanyId + ":" + publicBranchId, idempotencyKey,
            () -> activateBranchInternal(publicCompanyId, publicBranchId, actorUserId, correlationId));
    }

    private BranchDto activateBranchInternal(String publicCompanyId, String publicBranchId, String actorUserId, String correlationId) {
        Branch branch = branchRepository.findByPublicCompanyIdAndPublicBranchId(publicCompanyId, publicBranchId)
            .orElseThrow(() -> new EntityNotFoundException("Branch", publicBranchId));

        String publicAccountId = companyRepository.findByPublicCompanyId(publicCompanyId)
            .orElseThrow(() -> new EntityNotFoundException("Company", publicCompanyId)).getPublicAccountId();
        branch.activate();
        Branch saved = branchRepository.save(branch);

        outboxPort.recordEvent(
            "branch.activated",
            "1.0",
            saved.getVersion(),
            "company_management",
            correlationId != null ? correlationId : "cor_" + UUID.randomUUID().toString().replace("-", ""),
            null,
            publicAccountId,
            saved.getPublicCompanyId(),
            saved.getPublicBranchId(),
            actorUserId,
            Map.of(
                "branchId", saved.getPublicBranchId(),
                "companyId", saved.getPublicCompanyId(),
                "status", saved.getStatus().name()
            )
        );

        return BranchDto.fromDomain(saved);
    }
}
