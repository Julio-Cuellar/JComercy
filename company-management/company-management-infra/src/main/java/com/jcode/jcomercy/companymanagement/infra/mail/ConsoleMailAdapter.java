package com.jcode.jcomercy.companymanagement.infra.mail;

import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Imprime el correo completo en la consola en vez de mandarlo ({@code jcomercy.mail.mode=console}): mientras no hay
 * servidor de correo, así se ven los códigos y enlaces de recuperación de contraseña y de invitación en pruebas y en
 * trabajo local.
 *
 * <p>El enlace trae el código que da acceso, y la consola es el registro: por eso este modo no arranca si el perfil activo
 * es {@code prod} o {@code production}. Hay que cambiar a {@code smtp} antes de salir a producción.
 */
@Component
@ConditionalOnProperty(name = "jcomercy.mail.mode", havingValue = "console")
public class ConsoleMailAdapter implements MailPort {
    private static final Logger log = LoggerFactory.getLogger(ConsoleMailAdapter.class);

    public ConsoleMailAdapter(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException("El correo por consola es solo para trabajo local: ahí los códigos de acceso quedarían en el registro. Usa jcomercy.mail.mode=smtp.");
        }
        log.warn("Correo en modo consola: los correos se imprimen aquí y no se mandan a nadie. No lo uses en producción.");
    }

    @Override
    public void send(Message message) {
        log.info("""

            ================ CORREO (modo consola: no se mandó a nadie) ================
            Para: {}
            Asunto: {}

            {}
            ============================================================================
            """, message.to(), message.subject(), message.body());
    }
}
