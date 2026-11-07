package com.jcode.jcomercy.companymanagement.infra.mail;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Corre algo cuando la transacción en curso se confirma; si no hay ninguna, lo corre de inmediato. Es lo que hace falta
 * para mandar un correo con un enlace: antes de confirmar, el enlace todavía no existe en la base de datos.
 */
final class AfterCommit {

    private AfterCommit() {
    }

    static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
