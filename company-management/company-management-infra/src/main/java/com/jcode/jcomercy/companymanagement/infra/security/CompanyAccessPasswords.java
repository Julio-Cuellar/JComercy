package com.jcode.jcomercy.companymanagement.infra.security;

import com.jcode.jcomercy.companymanagement.domain.exception.DomainRuleViolationException;
import com.jcode.jcomercy.companymanagement.domain.port.AccountRepositoryPort;
import com.jcode.jcomercy.companymanagement.domain.port.PasswordHasher;
import com.jcode.jcomercy.companymanagement.infra.security.CompanyAccessPasswordException.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La contraseña opcional que cada persona le puede poner a su acceso a una empresa (ERR-011). Con ella hay que
 * escribirla para entrar; cinco intentos fallidos bloquean ese acceso quince minutos. El contador y el bloqueo se
 * guardan en la base de datos, así que sobreviven a un reinicio. Solo se guarda la huella cifrada, y ninguna contraseña
 * llega nunca al registro.
 *
 * <p>Escribir mal la contraseña de la empresa, al entrar o al cambiarla, y escribir mal la de la cuenta al quitarla,
 * gastan el mismo contador: ninguna de las tres vías sirve para adivinar sin que el bloqueo lo note. Cada operación
 * es una sentencia propia, sin transacción compartida: así un intento fallido queda registrado aunque la petición
 * termine en error.
 */
@Component
public class CompanyAccessPasswords {
    private static final Logger log = LoggerFactory.getLogger(CompanyAccessPasswords.class);

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int MIN_LENGTH = 8;

    private static final String REQUIRED_TO_ENTER = "Esta empresa pide su contraseña. Escríbela para entrar.";
    private static final String CURRENT_REQUIRED = "Para cambiar la contraseña de esta empresa, escribe la actual.";
    private static final String ACCOUNT_REQUIRED =
        "Para quitar la contraseña de esta empresa, escribe la contraseña de tu cuenta.";
    private static final String TOO_SHORT = "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.";
    private static final String SAME_AS_ACCOUNT = "La contraseña de la empresa debe ser distinta a la de tu cuenta.";

    /** Lo que hay guardado de un acceso con contraseña. */
    private record Stored(String hash, int failedAttempts, OffsetDateTime lockedUntil) { }

    /** Un intento reservado. {@code lockUntil} es el bloqueo que habría puesto si fue el último de los cinco. */
    private record Attempt(OffsetDateTime lockUntil) { }

    private final JdbcTemplate jdbc;
    private final PasswordHasher hasher;
    private final AccountRepositoryPort accounts;

    public CompanyAccessPasswords(JdbcTemplate jdbc, PasswordHasher hasher, AccountRepositoryPort accounts) {
        this.jdbc = jdbc;
        this.hasher = hasher;
        this.accounts = accounts;
    }

    /** Las empresas donde la cuenta le puso contraseña a su acceso. */
    public Set<String> protectedCompanyIds(String accountId) {
        return new HashSet<>(jdbc.queryForList(
            "SELECT public_company_id FROM company_management.company_access_password WHERE user_id=?",
            String.class, accountId));
    }

    /** Si el acceso tiene contraseña, hay que escribirla bien para entrar; si no la tiene, entra sin más. */
    public void checkToEnter(String accountId, String companyId, String password) {
        find(accountId, companyId).ifPresent(stored ->
            proveCompanyPassword(accountId, companyId, stored, password, REQUIRED_TO_ENTER));
    }

    /**
     * Pone la contraseña del acceso o la cambia. Cambiarla exige escribir la actual; ponerla por primera vez, no. La
     * nueva debe tener al menos 8 caracteres y ser distinta a la de la cuenta, o la protección no serviría de nada.
     */
    public void set(String accountId, String companyId, String currentPassword, String newPassword) {
        Optional<Stored> stored = find(accountId, companyId);
        stored.ifPresent(existing -> proveCompanyPassword(accountId, companyId, existing, currentPassword, CURRENT_REQUIRED));
        if (newPassword == null || newPassword.length() < MIN_LENGTH) {
            throw new DomainRuleViolationException(TOO_SHORT);
        }
        if (matchesAccountPassword(accountId, newPassword)) {
            throw new DomainRuleViolationException(SAME_AS_ACCOUNT);
        }
        save(accountId, companyId, hasher.hash(newPassword));
        log.info("Contraseña de empresa {} accountId={} companyId={}",
            stored.isPresent() ? "cambiada" : "puesta", accountId, companyId);
    }

    /**
     * Quita la contraseña del acceso. Pide la contraseña de la cuenta y no la de la empresa: es la salida para quien
     * la olvidó. Si el acceso no tenía contraseña, no hay nada que quitar y no cambia nada.
     */
    public void remove(String accountId, String companyId, String accountPassword) {
        Optional<Stored> stored = find(accountId, companyId);
        if (stored.isEmpty()) {
            return;
        }
        failIfLocked(stored.get(), accountId, companyId);
        if (accountPassword == null || accountPassword.isBlank()) {
            throw new CompanyAccessPasswordException(Kind.ACCOUNT_PASSWORD_REQUIRED, ACCOUNT_REQUIRED);
        }
        Optional<Attempt> attempt = claimAttempt(accountId, companyId);
        if (attempt.isEmpty()) {
            return; // Mientras tanto ya se la quitaron: no hay nada que quitar.
        }
        if (!matchesAccountPassword(accountId, accountPassword)) {
            throw failure(accountId, companyId, attempt.get(), Kind.ACCOUNT_PASSWORD_INVALID,
                "Tu contraseña de cuenta no es correcta.", "Contraseña de cuenta incorrecta al quitar la de la empresa");
        }
        jdbc.update("DELETE FROM company_management.company_access_password WHERE user_id=? AND public_company_id=?",
            accountId, companyId);
        log.info("Contraseña de empresa quitada accountId={} companyId={}", accountId, companyId);
    }

    private void proveCompanyPassword(String accountId, String companyId, Stored stored, String password, String whenMissing) {
        failIfLocked(stored, accountId, companyId);
        if (password == null || password.isBlank()) {
            throw new CompanyAccessPasswordException(Kind.COMPANY_PASSWORD_REQUIRED, whenMissing);
        }
        Optional<Attempt> attempt = claimAttempt(accountId, companyId);
        if (attempt.isEmpty()) {
            return; // Mientras tanto le quitaron la contraseña al acceso: ya no hay nada que probar.
        }
        if (!hasher.matches(password, stored.hash())) {
            throw failure(accountId, companyId, attempt.get(), Kind.COMPANY_PASSWORD_INVALID,
                "La contraseña de esta empresa no es correcta.", "Contraseña de empresa incorrecta");
        }
        jdbc.update("""
            UPDATE company_management.company_access_password
            SET failed_attempts=0, locked_until=NULL, updated_at=?
            WHERE user_id=? AND public_company_id=?
            """, now(), accountId, companyId);
    }

    /**
     * Reserva un intento <b>antes</b> de verificar la contraseña. Contarlo después dejaba que una ráfaga de peticiones
     * pasara todas la comprobación del bloqueo antes de que ninguna anotara su fallo, y se probaban muchas más de cinco.
     * Es una sola sentencia: sube el contador y, si este era el último intento, deja el bloqueo puesto en el mismo
     * instante, así ninguna petición posterior verifica nada. Solo actúa si el acceso no está bloqueado.
     *
     * @return el intento reservado, o vacío si la contraseña del acceso ya no existe
     * @throws CompanyAccessPasswordException si el acceso está bloqueado
     */
    private Optional<Attempt> claimAttempt(String accountId, String companyId) {
        for (int tries = 0; tries < 3; tries++) {
            OffsetDateTime now = now();
            OffsetDateTime until = now.plus(LOCK_DURATION);
            int claimed = jdbc.update("""
                UPDATE company_management.company_access_password
                SET failed_attempts = CASE WHEN failed_attempts + 1 >= ? THEN 0 ELSE failed_attempts + 1 END,
                    locked_until = CASE WHEN failed_attempts + 1 >= ? THEN CAST(? AS TIMESTAMP WITH TIME ZONE) ELSE locked_until END,
                    updated_at = ?
                WHERE user_id=? AND public_company_id=? AND (locked_until IS NULL OR locked_until <= ?)
                """, MAX_FAILED_ATTEMPTS, MAX_FAILED_ATTEMPTS, until, now, accountId, companyId, now);
            if (claimed == 1) {
                return Optional.of(new Attempt(until));
            }
            Optional<Stored> current = find(accountId, companyId);
            if (current.isEmpty()) {
                return Optional.empty();
            }
            failIfLocked(current.get(), accountId, companyId);
            // Ni bloqueado ni reservado: el bloqueo venció entre las dos sentencias. Se vuelve a intentar.
        }
        throw locked(1);
    }

    private void failIfLocked(Stored stored, String accountId, String companyId) {
        OffsetDateTime now = now();
        if (stored.lockedUntil() != null && stored.lockedUntil().isAfter(now)) {
            Duration remaining = Duration.between(now, stored.lockedUntil());
            long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
            log.warn("Intento durante el bloqueo accountId={} companyId={} segundosRestantes={}", accountId, companyId, seconds);
            throw locked(seconds);
        }
    }

    /**
     * Devuelve el error de un intento fallido: el aviso de cuántos intentos quedan o, si el acceso quedó bloqueado, el
     * bloqueo. El intento ya se contó al reservarlo ({@link #claimAttempt}); aquí solo se lee en qué quedó el contador.
     * Si el bloqueo lo puso este mismo intento se avisa de los quince minutos completos; si ya lo había puesto otro, de
     * lo que falta.
     */
    private CompanyAccessPasswordException failure(String accountId, String companyId, Attempt attempt, Kind kind,
                                                   String what, String logMessage) {
        Optional<Stored> state = find(accountId, companyId);
        OffsetDateTime now = now();
        OffsetDateTime lockedUntil = state.map(Stored::lockedUntil).orElse(null);
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            if (lockedUntil.toInstant().equals(attempt.lockUntil().toInstant())) {
                log.warn("Acceso a empresa bloqueado accountId={} companyId={} hasta={}", accountId, companyId, lockedUntil);
                return locked(LOCK_DURATION.getSeconds());
            }
            Duration remaining = Duration.between(now, lockedUntil);
            return locked(remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0));
        }
        int attempts = state.map(Stored::failedAttempts).orElse(0);
        log.warn("{} accountId={} companyId={} intentos={}", logMessage, accountId, companyId, attempts);
        int left = MAX_FAILED_ATTEMPTS - attempts;
        return new CompanyAccessPasswordException(kind, what + " " + (left == 1
            ? "Te queda 1 intento antes de que se bloquee."
            : "Te quedan " + left + " intentos antes de que se bloquee."));
    }

    private static CompanyAccessPasswordException locked(long seconds) {
        long minutes = Math.max(1, (seconds + 59) / 60);
        return new CompanyAccessPasswordException(Kind.COMPANY_ACCESS_LOCKED,
            "Esta empresa se bloqueó por demasiados intentos fallidos. Vuelve a intentarlo en "
                + (minutes == 1 ? "1 minuto" : minutes + " minutos") + ".", Math.max(1, seconds));
    }

    private boolean matchesAccountPassword(String accountId, String password) {
        return accounts.findByPublicAccountId(accountId)
            .map(account -> account.getOwner().getPasswordHash())
            .map(hash -> hasher.matches(password, hash))
            .orElse(false);
    }

    private Optional<Stored> find(String accountId, String companyId) {
        return jdbc.query("""
            SELECT password_hash, failed_attempts, locked_until FROM company_management.company_access_password
            WHERE user_id=? AND public_company_id=?
            """, (rs, row) -> new Stored(rs.getString("password_hash"), rs.getInt("failed_attempts"),
                rs.getObject("locked_until", OffsetDateTime.class)), accountId, companyId).stream().findFirst();
    }

    /** Guarda la huella: actualiza la fila de la persona en esa empresa o, si no existe, la crea. */
    private void save(String accountId, String companyId, String hash) {
        if (replaceHash(accountId, companyId, hash) > 0) {
            return;
        }
        OffsetDateTime now = now();
        try {
            jdbc.update("""
                INSERT INTO company_management.company_access_password
                  (id, user_id, public_company_id, password_hash, failed_attempts, locked_until, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 0, NULL, ?, ?, 0)
                """, UUID.randomUUID(), accountId, companyId, hash, now, now);
        } catch (DuplicateKeyException concurrent) {
            // Otra petición la creó en este instante: se actualiza esa fila.
            replaceHash(accountId, companyId, hash);
        }
    }

    private int replaceHash(String accountId, String companyId, String hash) {
        return jdbc.update("""
            UPDATE company_management.company_access_password
            SET password_hash=?, failed_attempts=0, locked_until=NULL, updated_at=?, version=version + 1
            WHERE user_id=? AND public_company_id=?
            """, hash, now(), accountId, companyId);
    }

    /** Hasta microsegundos, que es lo que guarda la base de datos: así lo guardado se puede comparar con lo escrito. */
    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
