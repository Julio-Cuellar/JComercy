package com.jclinical.inventory.infra.adapters.in.messaging;

import com.jclinical.core.events.MaterialReservationReleasedEvent;
import com.jclinical.core.events.MaterialReservationRequestedEvent;
import com.jclinical.inventory.domain.ports.in.ManageMaterialReservationUseCase;
import com.jclinical.inventory.domain.ports.in.ManageMaterialReservationUseCase.ReservationLineCommand;
import com.jclinical.inventory.infra.config.InventoryRabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MaterialReservationEventListener {

    private final ManageMaterialReservationUseCase reservationUseCase;

    @RabbitListener(queues = InventoryRabbitConfig.RESERVATION_REQUESTED_QUEUE, containerFactory = "domainEventListenerContainerFactory")
    public void onReservationRequested(MaterialReservationRequestedEvent event) {
        log.info(">>>> [INVENTARIO] Reservando material para cita {} ({} líneas)", event.appointmentId(), event.materials().size());
        var lines = event.materials().stream()
                .map(line -> new ReservationLineCommand(line.materialId(), line.materialName(), line.quantity()))
                .toList();
        reservationUseCase.reserveForAppointment(event.clinicId(), event.appointmentId(), lines);
    }

    @RabbitListener(queues = InventoryRabbitConfig.RESERVATION_RELEASED_QUEUE, containerFactory = "domainEventListenerContainerFactory")
    public void onReservationReleased(MaterialReservationReleasedEvent event) {
        log.info(">>>> [INVENTARIO] Liberando reservas de material para cita {}", event.appointmentId());
        reservationUseCase.releaseForAppointment(event.clinicId(), event.appointmentId());
    }
}
