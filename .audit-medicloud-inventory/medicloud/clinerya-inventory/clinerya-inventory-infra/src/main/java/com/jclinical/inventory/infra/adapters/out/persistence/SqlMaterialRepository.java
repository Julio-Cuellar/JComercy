package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.Material;
import com.jclinical.inventory.domain.ports.out.MaterialRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SqlMaterialRepository implements MaterialRepositoryPort {

    private final SpringDataMaterialRepository springRepository;
    private final MaterialMapper mapper;

    @Override
    public Material save(Material material) {
        MaterialEntity entity = mapper.toEntity(material);
        MaterialEntity saved = springRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Material> findByIdAndClinicId(UUID materialId, UUID clinicId) {
        return springRepository.findByIdAndClinicId(materialId, clinicId).map(mapper::toDomain);
    }

    @Override
    public Optional<Material> findByIdAndClinicIdForUpdate(UUID materialId, UUID clinicId) {
        return springRepository.findByIdAndClinicIdForUpdate(materialId, clinicId).map(mapper::toDomain);
    }

    @Override
    public List<Material> findByClinicId(UUID clinicId, boolean includeInactive) {
        List<MaterialEntity> entities = includeInactive
                ? springRepository.findByClinicId(clinicId)
                : springRepository.findByClinicIdAndActiveTrue(clinicId);
        return entities.stream().map(mapper::toDomain).toList();
    }
}
