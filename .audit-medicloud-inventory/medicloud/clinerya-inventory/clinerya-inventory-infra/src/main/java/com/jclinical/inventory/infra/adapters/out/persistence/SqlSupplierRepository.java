package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.Supplier;
import com.jclinical.inventory.domain.ports.out.SupplierRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SqlSupplierRepository implements SupplierRepositoryPort {
    private final SpringDataSupplierRepository repository;

    @Override
    public Supplier save(Supplier supplier) {
        return toDomain(repository.save(toEntity(supplier)));
    }

    @Override
    public Optional<Supplier> findByIdAndClinicId(UUID supplierId, UUID clinicId) {
        return repository.findByIdAndClinicId(supplierId, clinicId).map(this::toDomain);
    }

    @Override
    public List<Supplier> findByClinicId(UUID clinicId) {
        return repository.findByClinicIdOrderByNameAsc(clinicId).stream().map(this::toDomain).toList();
    }

    private SupplierEntity toEntity(Supplier supplier) {
        return SupplierEntity.builder()
                .id(supplier.getId())
                .clinicId(supplier.getClinicId())
                .name(supplier.getName())
                .contactName(supplier.getContactName())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .taxId(supplier.getTaxId())
                .notes(supplier.getNotes())
                .active(supplier.isActive())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }

    private Supplier toDomain(SupplierEntity entity) {
        return Supplier.builder()
                .id(entity.getId())
                .clinicId(entity.getClinicId())
                .name(entity.getName())
                .contactName(entity.getContactName())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .taxId(entity.getTaxId())
                .notes(entity.getNotes())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
