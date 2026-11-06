package com.jcode.jcomercy.companymanagement.domain.port;

import java.time.Instant;
import java.util.List;

/**
 * Entrega el enlace de una invitación a la persona. Quien lo implementa espera a que la transacción que guardó la
 * invitación se confirme, para que el enlace funcione al abrirlo, y anota si el correo salió o no.
 */
public interface InvitationDeliveryPort {

    /** Lo que hace falta para armar el correo. {@code code} es el código en claro: vive solo en esta llamada. */
    record Delivery(String publicInvitationId, String email, String firstName, String companyName,
                    List<String> roleNames, String code, Instant expiresAt) { }

    void deliverAfterCommit(Delivery delivery);
}
