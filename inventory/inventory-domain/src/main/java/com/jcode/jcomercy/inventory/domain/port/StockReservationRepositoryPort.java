package com.jcode.jcomercy.inventory.domain.port;

import com.jcode.jcomercy.inventory.domain.model.stock.StockReservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reference arguments must already be normalized with {@link StockReservation#normalizeReference(String)}. */
public interface StockReservationRepositoryPort {
    StockReservation save(StockReservation reservation);
    List<StockReservation> findActiveByReference(String companyId, String referenceType, String referenceId);
    /** Ids only, so callers can lock the balances before loading (and trusting) the reservations. */
    List<UUID> findActiveBalanceIdsByReference(String companyId, String referenceType, String referenceId);
    boolean existsActiveByReference(String companyId, String referenceType, String referenceId);
    List<StockReservation> findDue(Instant now, int limit);
    List<UUID> findDueBalanceIds(Instant now, int limit);
}
