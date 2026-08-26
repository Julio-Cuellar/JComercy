# Reglas de Entrega, Orden, Deduplicación y Reconciliación

Este documento establece las políticas técnicas y de gobernanza para la entrega de eventos, notificaciones WebSocket en tiempo real, webhooks externos y la integración de reconciliación entre `Company Management` y `Billing`.

---

## 1. Webhooks Externos

### 1.1 Administración y Seguridad
- **Aislamiento por Empresa:** Cada suscripción webhook pertenece a una sola empresa (`publicCompanyId`). Opcionalmente se puede restringir por `branchId` y lista de tipos de evento (`eventTypes`).
- **Autenticación y Firma HMAC:** 
  - Todo payload enviado a un webhook externo incluirá los headers:
    - `X-JComercy-Signature`: Firma HMAC SHA-256 en hexadecimal del payload `X-JComercy-Timestamp + '.' + raw_body` utilizando el secreto de la suscripción.
    - `X-JComercy-Timestamp`: Timestamp en UTC ISO 8601 de la transmisión.
    - `X-JComercy-Event-Id`: Identificador único del evento (`eventId`).
- **Protección Anti-Replay y Secreto Rotatorio:**
  - Los clientes deben rechazar transmisiones cuyo `X-JComercy-Timestamp` tenga una desviación mayor a 300 segundos.
  - La rotación de secreto (`POST .../secret-rotation`) permite un periodo de gracia de 24 horas durante el cual se aceptan firmas generadas con el secreto anterior o el nuevo.

### 1.2 Reintentos y Clasificación de Estados
- **Confirmación Exitosa:** Se considera entregado si el servidor del cliente responde con HTTP status `2xx` (`200 OK`, `202 Accepted`, `204 No Content`) dentro de un tiempo límite de 5000 ms.
- **Ciclo de Reintentos:** Ante respuestas no `2xx` o timeout de conexión, la suscripción ejecutará backoff exponencial (15s, 1m, 5m, 15m, 1h, 6h, 12h, 24h) hasta un máximo de 8 reintentos.
- **Estados del Registro de Entrega (`WebhookDelivery`):**
  - `PENDING`: En cola o reintento programado.
  - `DELIVERED`: Entregado exitosamente con respuesta `2xx`.
  - `FAILED`: Falló tras agotar la política de 8 reintentos o recibir error fatal (ej. `410 Gone`).
  - `EXPIRED`: Transmisión no entregada que superó la ventana de retención (30 días).

---

## 2. WebSockets (`WSS /ws/v1`) y Tiempo Real

### 2.1 Conexión, Autenticación y Autorización
- **Endpoint:** `WSS /ws/v1`
- **Autenticación:** Mediante parámetro de conexión o header `Authorization: Bearer {accessToken}`.
- **Suscripción por Canal:**
  - `account.{accountId}`: Requiere permiso administrativo sobre la cuenta.
  - `company.{companyId}`: Requiere membresía activa en la empresa.
  - `company.{companyId}.branch.{branchId}`: Requiere asignación activa a la sucursal.
  - `company.{companyId}.operations`: Requiere pertenecer a la empresa o ser el iniciador de la operación.
  - `company.{companyId}.notifications`: Notificaciones generales del portal.
- **Revalidación Dinámica:** Cuando cambien los roles, membresías o permisos de un usuario, el servidor invalidará y cerrará automáticamente los canales WebSocket afectados sin desconectar la sesión principal.

### 2.2 Mensajería, Garantía de Entrega y Retención
- **Estructura del Mensaje:**
  - `SUBSCRIBE`: Cliente envía `{"action": "SUBSCRIBE", "channel": "company.com_01J...notifications"}`.
  - `NOTIFICATION`: Servidor envía `{"notificationId": "not_01J...", "type": "CRITICAL", "data": {...}}`.
  - `ACK`: Cliente responde `{"action": "ACK", "notificationId": "not_01J..."}` para notificaciones críticas.
- **Política de Retención por Severidad:**
  - **Notificaciones Críticas (ej. bloqueo de cuenta, suspensión de empresa):** Retención de **30 días**, persistencia temporal en backend, `ACK` obligatorio del cliente y reintentos automáticos al reconectar.
  - **Notificaciones Operativas (ej. sucursal activada, operación asíncrona completada):** Retención de **7 días**, persistencia temporal.
  - **Notificaciones Informativas (ej. avisos menores UI):** Retención de **24 horas**, efímeras sin persistencia obligatoria.

---

## 3. Reglas de Orden, Deduplicación y Secuencia

### 3.1 Deduplicación en Servidor y Cliente
- **Identificador Único (`eventId` / `notificationId`):** Todos los eventos distribuidos mediante bus, WebSocket o webhook transportan un `eventId` único (UUID v7 o ULID con marca temporal ordenada).
- **Procesamiento Idempotente:** Los consumidores en backend y frontend deben mantener una tabla o caché de deduplicación basada en `eventId` (retención de deduplicación mínima de 7 días).

### 3.2 Manejo de Mensajes Fuera de Orden
- **Versión de Entidad y Timestamp (`occurredAt`):**
  - Cada evento incluye la fecha/hora exacta de ocurrencia en UTC (`occurredAt`) y la versión de la entidad modificada (`eventVersion` / `version`).
  - Si un consumidor recibe un evento con una versión o `occurredAt` anterior al estado local confirmado de la entidad, el evento debe descartarse o registrarse únicamente como auditoría histórica, **impidiendo sobreescribir datos más recientes con información antigua**.

---

## 4. Integración y Reconciliación con Billing

### 4.1 Eventos Consumidos de Billing
`Company Management` consume eventos publicados por `Billing` para ajustar las capacidades operativas:
- `subscription.activated` / `subscription.reactivated`: Habilita operación según plan contratado.
- `subscription.suspended` / `subscription.cancelled`: Entra en periodo de gracia o suspende operaciones comerciales.
- `subscription.plan-changed`: Recalcula límites de sucursales y usuarios de plataforma.
- `subscription.payment-rejected` / `subscription.grace-period-started`: Inicia ventana de regularización de **7 días**.
- `branch-addon.created` / `branch-addon.cancelled`: Ajusta la capacidad de sucursales adicionales permitidas.

### 4.2 Política del Periodo de Gracia (7 Días)
- Ante fallos de pago o reducción de plan (`subscription.grace-period-started`), la empresa y sus sucursales operativas actuales dispondrán de **7 días continuos** para regularizar su situación.
- Durante los 7 días:
  - Se permite la operación normal en sucursales activas existentes.
  - Se bloquea la creación o activación de nuevas sucursales o complementos.
  - Se notifican las advertencias mediante canal WebSocket y Portal.
- Transcurridos los 7 días sin regularización, la empresa pasa a estado `SUSPENDED` (bloqueo de escrituras operativas, conservando consultas y descargas).

### 4.3 Reconciliación Síncrona REST en Indisponibilidad
- **Último Estado Válido Confirmado:** Durante indisponibilidades temporales del bus de eventos o de Billing, `Company Management` operará con la última proyección validada localmente.
- **Consulta de Reconciliación Interna:** `Company Management` expondrá el contrato síncrono interno `/internal/v1/companies/{publicCompanyId}/billing-capabilities` para ejecutar reconciliaciones bajo demanda cuando se detecten inconsistencias entre el estado local y Billing.
