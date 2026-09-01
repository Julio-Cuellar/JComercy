package com.jclinical.core.events;

public final class DomainEventRoutingKeys {

    private DomainEventRoutingKeys() {
    }

    public static final String DOMAIN_EVENTS_EXCHANGE = "jclinical.domain.events";
    public static final String DEAD_LETTER_EXCHANGE = "jclinical.dlx";

    public static final String CONSUMPTION_RECONCILED = "consumption.reconciled";
    public static final String MATERIAL_RESERVATION_REQUESTED = "material.reservation.requested";
    public static final String MATERIAL_RESERVATION_RELEASED = "material.reservation.released";
    public static final String PAYMENT_REGISTERED = "payment.registered";
    public static final String SUPPLY_WASTED = "supply.wasted";
    public static final String CASH_EXPENSE_REGISTERED = "cash.expense.registered";
    public static final String CASH_EXPENSE_VOIDED = "cash.expense.voided";
    public static final String PURCHASE_ORDER_CREATED = "purchase.order.created";
    public static final String APPOINTMENT_SCHEDULED = "appointment.scheduled";
    public static final String APPOINTMENT_RESCHEDULED = "appointment.rescheduled";
    public static final String APPOINTMENT_CANCELLED = "appointment.cancelled";
    public static final String APPOINTMENT_DELETED = "appointment.deleted";
}
