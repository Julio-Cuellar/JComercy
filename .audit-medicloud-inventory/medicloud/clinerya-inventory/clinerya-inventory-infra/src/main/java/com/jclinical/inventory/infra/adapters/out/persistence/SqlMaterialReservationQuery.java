package com.jclinical.inventory.infra.adapters.out.persistence;

import com.jclinical.inventory.domain.model.MaterialReservationDetail;
import com.jclinical.inventory.domain.model.MaterialReservationStatus;
import com.jclinical.inventory.domain.ports.out.MaterialReservationQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SqlMaterialReservationQuery implements MaterialReservationQueryPort {

    private static final String ACTIVE_RESERVATIONS_SQL = """
            SELECT mr.id AS reservation_id,
                   mr.appointment_id,
                   a.patient_id,
                   COALESCE(
                       NULLIF(BTRIM(CONCAT_WS(' ', p.first_name, p.last_name_paterno, p.last_name_materno)), ''),
                       'Paciente no asignado'
                   ) AS patient_name,
                   COALESCE(
                       NULLIF(BTRIM(selected_items.treatment_name), ''),
                       NULLIF(BTRIM(qi.description), ''),
                       NULLIF(BTRIM(a.reason), ''),
                       'Tratamiento no especificado'
                   ) AS treatment_name,
                   a.scheduled_start,
                   mr.material_id,
                   COALESCE(NULLIF(BTRIM(mr.material_name), ''), m.name) AS material_name,
                   m.unit_of_measure,
                   mr.quantity,
                   m.current_stock,
                   COALESCE(m.reserved_quantity, 0) AS total_reserved_quantity,
                   m.current_stock - COALESCE(m.reserved_quantity, 0) AS available_quantity,
                   mr.status,
                   mr.created_at
              FROM inventory.material_reservations mr
              JOIN inventory.materials m
                ON m.id = mr.material_id
               AND m.clinic_id = mr.clinic_id
              JOIN agenda.appointments a
                ON a.id = mr.appointment_id
               AND a.clinic_id = mr.clinic_id
              LEFT JOIN patients.patients p
                ON p.id = a.patient_id
               AND p.clinic_id = mr.clinic_id
              LEFT JOIN treatments.quotation_items qi
                ON qi.id = a.quotation_item_id
              LEFT JOIN LATERAL (
                   SELECT STRING_AGG(qi_selected.description, ', ' ORDER BY aqi.quotation_item_id) AS treatment_name
                     FROM agenda.appointment_quotation_items aqi
                     JOIN treatments.quotation_items qi_selected
                       ON qi_selected.id = aqi.quotation_item_id
                    WHERE aqi.appointment_id = a.id
              ) selected_items ON TRUE
             WHERE mr.clinic_id = ?
               AND mr.status = 'RESERVED'
             ORDER BY mr.created_at DESC, a.scheduled_start DESC, patient_name, material_name, mr.id
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<MaterialReservationDetail> findActiveByClinicId(UUID clinicId) {
        return jdbcTemplate.query(ACTIVE_RESERVATIONS_SQL, (resultSet, rowNumber) -> new MaterialReservationDetail(
                resultSet.getObject("reservation_id", UUID.class),
                resultSet.getObject("appointment_id", UUID.class),
                resultSet.getObject("patient_id", UUID.class),
                resultSet.getString("patient_name"),
                resultSet.getString("treatment_name"),
                resultSet.getTimestamp("scheduled_start").toLocalDateTime(),
                resultSet.getObject("material_id", UUID.class),
                resultSet.getString("material_name"),
                resultSet.getString("unit_of_measure"),
                resultSet.getBigDecimal("quantity"),
                resultSet.getBigDecimal("current_stock"),
                resultSet.getBigDecimal("total_reserved_quantity"),
                resultSet.getBigDecimal("available_quantity"),
                MaterialReservationStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        ), clinicId);
    }
}
