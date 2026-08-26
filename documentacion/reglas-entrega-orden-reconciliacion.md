# Reglas de Entrega, Orden, Deduplicación y Reconciliación

Este documento establece las políticas técnicas y de gobernanza para la entrega de eventos, notificaciones WebSocket en tiempo real, webhooks externos y la integración de reconciliación entre `Company Management` y `Billing`.

---

## 1. Webhooks Externos

### 1.1 Administración y Seguridad
- **Aislamiento por Empresa:** Cada suscripción webhook pertenece a una sola empresa (`companyId`). Opcionalmente se puede restringir por `branchId` y lista de tipos de evento (`eventTypes`).
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
- **Handshake y Autenticación:** Se requiere el header `Authorization: Bearer {accessToken}` o parámetro de conexión `?token={accessToken}` durante el handshake WebSocket.
- **Protocolo de Mensajes:**
  - `WebSocketConnectRequest`: Solicitud inicial de handshake.
  - `WebSocketSubscribeMessage`: Cliente solicita suscripción:
    ```json
    { "action": "SUBSCRIBE", "channel": "company.com_01J...notifications", "token": "Bearer ..." }
    ```
  - `WebSocketUnsubscribeMessage`: Cliente solicita desuscripción:
    ```json
    { "action": "UNSUBSCRIBE", "channel": "company.com_01J...notifications" }
    ```
  - `WebSocketNotificationMessage`: Servidor empuja notificación:
    ```json
    {
      "notificationId": "not_01J8Z9X2Y3W4V5U6T7S8R9Q0P1",
      "channel": "company.com_01J...notifications",
      "severity": "CRITICAL",
      "occurredAt": "2026-08-26T20:00:00Z",
      "data": {}
    }
    ```
  - `WebSocketAckMessage`: Cliente confirma recepción de notificación crítica:
    ```json
    { "action": "ACK", "notificationId": "not_01J8Z9X2Y3W4V5U6T7S8R9Q0P1" }
    ```
- **Revalidación Dinámica de Permisos:** Cuando cambien los roles, membresías o permisos de un usuario, el servidor invalidará y cerrará automáticamente los canales WebSocket afectados sin desconectar la sesión principal.

### 2.2 Política de Retención por Severidad
- **Notificaciones Críticas (ej. bloqueo de cuenta, suspensión de empresa):** Retención de **30 días**, persistencia temporal en backend, `ACK` obligatorio del cliente y reintentos automáticos al reconectar.
- **Notificaciones Operativas (ej. sucursal activada, operación asíncrona completada):** Retención de **7 días**, persistencia temporal.
- **Notificaciones Informativas (ej. avisos menores UI):** Retención de **24 horas**, efímeras sin persistencia obligatoria.

---

## 3. Reglas de Orden, Deduplicación y Versionado Distribuido

### 3.1 Separación de Versionado en el Envelope
Para eliminar ambigüedades técnicas, el envelope estandariza tres conceptos distintos de versión y orden:
1. `schemaVersion` (String): Versión del esquema del contrato JSON del evento (ej: `"1.0"`). Permite la evolución compatible de contratos de mensajería.
2. `aggregateVersion` (Integer): Versión secuencial del agregado o entidad del dominio (ej: `3`). Refleja la versión en base de datos.
3. `sequence` (Integer): Entero autoincremental de la secuencia de publicación del canal/stream para garantizar el orden de entrega.

### 3.2 Deduplicación y Manejo de Mensajes Fuera de Orden
- **Deduplicación por `eventId` / `notificationId`:** Los consumidores en backend y frontend deben mantener una tabla o caché de deduplicación basada en `eventId` (retención de deduplicación mínima de 7 días).
- **Descarte de Mensajes Antiguos:** Si un consumidor recibe un evento donde `aggregateVersion` o `sequence` sea menor o igual al estado local confirmado de la entidad, el evento debe descartarse o registrarse únicamente como auditoría pasiva, **impidiendo que mensajes desordenados sobreescriban datos recientes**.

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
- Transcurridos los 7 días sin regularización, la empresa pasa a estado `SUSPENDED` (bloqueo de escrituras operativas, conservando consultas y descargas).

### 4.3 Reconciliación Síncrona REST en Indisponibilidad
- **Consulta de Reconciliación Interna:** `Company Management` expondrá el contrato síncrono interno `/internal/v1/companies/{publicCompanyId}/billing-capabilities` para ejecutar reconciliaciones bajo demanda cuando se detecten inconsistencias entre el estado local y Billing.
