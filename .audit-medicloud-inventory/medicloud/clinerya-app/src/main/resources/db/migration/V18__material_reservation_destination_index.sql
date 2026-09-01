CREATE INDEX IF NOT EXISTS idx_material_reservations_clinic_status_appointment
    ON core.material_reservations (clinic_id, status, appointment_id);
