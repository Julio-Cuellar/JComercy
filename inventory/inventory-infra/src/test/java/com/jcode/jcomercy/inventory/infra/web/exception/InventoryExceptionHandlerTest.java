package com.jcode.jcomercy.inventory.infra.web.exception;

import com.jcode.jcomercy.inventory.application.exception.InventoryApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryExceptionHandlerTest {

    private final InventoryExceptionHandler handler = new InventoryExceptionHandler();

    @Test
    @DisplayName("Un conflicto responde 409 aunque su mensaje contenga 'not found'")
    void conflictIsNotMistakenForNotFoundBecauseOfItsMessage() {
        var response = handler.handleApplication(
                new InventoryApplicationException("lookup not found in cache, but the sku already exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Un recurso inexistente responde 404 y conserva el mensaje original")
    void notFoundExceptionRespondsWithNotFound() {
        var response = handler.handleApplication(InventoryApplicationException.notFound("product not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("code", "INVENTORY_OPERATION_REJECTED")
                .containsEntry("message", "product not found");
    }
}
