package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.Material;

public interface MaterialMapper {

    MaterialEntity toEntity(Material domain);

    Material toDomain(MaterialEntity entity);
}
