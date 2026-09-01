package com.jclinical.inventory.domain.model;

public enum MovementType {
    PURCHASE_ENTRY,
    USAGE_EXIT,
    SALE_EXIT,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT;

    public boolean increasesStock() {
        return this == PURCHASE_ENTRY || this == ADJUSTMENT_IN;
    }

    public boolean decreasesStock() {
        return this == USAGE_EXIT || this == SALE_EXIT || this == ADJUSTMENT_OUT;
    }

    public boolean isAdjustment() {
        return this == ADJUSTMENT_IN || this == ADJUSTMENT_OUT;
    }
}
