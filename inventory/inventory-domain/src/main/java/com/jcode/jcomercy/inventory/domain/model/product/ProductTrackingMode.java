package com.jcode.jcomercy.inventory.domain.model.product;

public enum ProductTrackingMode {
    NONE(false, false),
    LOT(true, false),
    SERIAL(false, true),
    LOT_AND_EXPIRATION(true, false),
    SERIAL_AND_EXPIRATION(false, true);

    private final boolean lot;
    private final boolean serial;

    ProductTrackingMode(boolean lot, boolean serial) {
        this.lot = lot;
        this.serial = serial;
    }

    public boolean requiresLot() {
        return lot;
    }

    public boolean requiresSerial() {
        return serial;
    }

    public boolean supportsExpiration() {
        return this == LOT_AND_EXPIRATION || this == SERIAL_AND_EXPIRATION;
    }
}
