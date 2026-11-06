package com.jcode.jcomercy.companymanagement.infra.mail;

import com.jcode.jcomercy.companymanagement.domain.model.employee.EmailStatus;
import com.jcode.jcomercy.companymanagement.domain.port.EmployeeInvitationRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.InvitationDeliveryPort;
import com.jcode.jcomercy.companymanagement.domain.port.MailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manda el enlace de una invitación cuando la transacción que la guardó se confirma, y anota si el correo salió. A
 * diferencia de la recuperación de contraseña, aquí quien lo pidió es una persona de la empresa con permiso: necesita
 * saber si el correo no salió, para reenviarlo. Por eso el envío es en el mismo hilo y su resultado queda guardado.
 *
 * <p>El código del enlace vive solo en la llamada: no se escribe en el registro ni se guarda. Un fallo se anota sin él
 * y sin el destinatario.
 */
@Component
public class InvitationMailer implements InvitationDeliveryPort {
    private static final Logger log = LoggerFactory.getLogger(InvitationMailer.class);

    private final MailPort mail;
    private final EmployeeInvitationRepositoryPort invitations;
    private final TransactionTemplate ownTransaction;
    private final String publicUrl;

    public InvitationMailer(MailPort mail, EmployeeInvitationRepositoryPort invitations, PlatformTransactionManager transactions,
                            @Value("${jcomercy.public-url:http://localhost:3000}") String publicUrl) {
        this.mail = mail;
        this.invitations = invitations;
        // Después de confirmar ya no hay transacción útil: anotar el resultado necesita la suya propia.
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Override
    public void deliverAfterCommit(Delivery delivery) {
        AfterCommit.run(() -> send(delivery));
    }

    private void send(Delivery delivery) {
        EmailStatus result;
        try {
            mail.send(message(delivery));
            result = EmailStatus.SENT;
        } catch (RuntimeException e) {
            log.error("No se pudo enviar la invitación invitationId={} causa={}", delivery.publicInvitationId(), e.getClass().getSimpleName());
            result = EmailStatus.FAILED;
        }
        EmailStatus finalResult = result;
        try {
            ownTransaction.executeWithoutResult(status -> invitations.findByPublicInvitationId(delivery.publicInvitationId())
                .ifPresent(invitation -> {
                    invitation.markEmail(finalResult);
                    invitations.save(invitation);
                }));
        } catch (RuntimeException e) {
            log.error("No se pudo anotar el resultado del correo invitationId={} causa={}", delivery.publicInvitationId(), e.getClass().getSimpleName());
        }
    }

    private MailPort.Message message(Delivery delivery) {
        String roles = String.join(", ", delivery.roleNames());
        return new MailPort.Message(delivery.email(), "Te dieron acceso a " + delivery.companyName() + " en JComercy", """
            Hola %s:

            %s te dio acceso a JComercy con el rol de %s. Abre este enlace para elegir tu contraseña y entrar:

            %s/activar-acceso?codigo=%s

            El enlace vale 7 días y se puede usar una sola vez. Si no esperabas este correo, no hagas nada.

            JComercy
            """.formatted(delivery.firstName(), delivery.companyName(), roles, publicUrl, delivery.code()));
    }
}
