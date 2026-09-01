package com.jcode.jcomercy.inventory.infra.persistence.adapter;

import com.jcode.jcomercy.inventory.domain.port.SerialNumberGeneratorPort;
import com.jcode.jcomercy.inventory.infra.persistence.entity.InventorySerialSequenceJpaEntity;
import com.jcode.jcomercy.inventory.infra.persistence.repository.InventorySerialSequenceJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class SerialNumberGeneratorAdapter implements SerialNumberGeneratorPort {
    private final InventorySerialSequenceJpaRepository repository;
    public SerialNumberGeneratorAdapter(InventorySerialSequenceJpaRepository repository) { this.repository = repository; }
    @Override
    public String next(String companyId) {
        var sequence = repository.findForUpdate(companyId).orElseGet(() -> create(companyId));
        long value = sequence.getNextValue();
        sequence.advance();
        repository.save(sequence);
        return "SER-%06d".formatted(value);
    }
    private InventorySerialSequenceJpaEntity create(String companyId) {
        try { return repository.saveAndFlush(new InventorySerialSequenceJpaEntity(companyId, 1)); }
        catch (DataIntegrityViolationException ignored) {
            return repository.findForUpdate(companyId).orElseThrow(() -> ignored);
        }
    }
}
