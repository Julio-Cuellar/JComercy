package com.jcode.jcomercy.companymanagement.infra.web.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** ERR-005: el 500 no debe mostrar el mensaje interno y la excepción debe quedar registrada. */
class GlobalExceptionHandlerTest {

    private static final String INTERNAL_MESSAGE =
        "could not execute statement [insert into account_credential (secret_hash) values (?)]";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/accounts/registration");
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(logs);
    }

    @Test
    @DisplayName("Un error inesperado responde 500 sin mostrar el mensaje interno de la excepción")
    void unexpectedErrorDoesNotExposeTheInternalMessage() {
        var response = handler.handleGeneral(new IllegalStateException(INTERNAL_MESSAGE), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        var problem = response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(problem.instance()).isEqualTo("/api/v1/accounts/registration");
        assertThat(problem.detail())
            .isEqualTo("No pudimos completar la operación por un problema nuestro. Intenta de nuevo en unos minutos.")
            .doesNotContain("account_credential", "secret_hash");
    }

    @Test
    @DisplayName("Un error inesperado queda registrado con su traza para poder diagnosticarlo")
    void unexpectedErrorIsLoggedWithItsStackTrace() {
        handler.handleGeneral(new IllegalStateException(INTERNAL_MESSAGE), request);

        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("/api/v1/accounts/registration");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo(INTERNAL_MESSAGE);
        });
    }
}
