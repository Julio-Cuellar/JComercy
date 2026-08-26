# Informe de Validación Final: Contratos Síncronos y Asíncronos de Company Management

Este documento certifica y registra formalmente los resultados de las pruebas de validación ejecutadas sobre los contratos síncronos (OpenAPI v1), asíncronos (AsyncAPI v1), notificaciones WebSocket en tiempo real, webhooks externos y reglas de gobernanza para el módulo `company_management` de JComercy.

---

## 1. Métricas de Validación de Entregables

### 1.1 Especificación REST OpenAPI v1 (`documentacion/openapi-company-management-v1.yaml`)
- **Versión OpenAPI:** `3.1.0`
- **Total de Rutas (`paths`):** `81`
- **Total de Operaciones HTTP:** `97`
- **Total de Esquemas Componentes (`schemas`):** `64`
- **`operationId` Únicos:** `97 / 97` (100%)
- **Operaciones con `x-required-permission`:** `97 / 97` (100%)
- **Operaciones con Header `Idempotency-Key`:** Cobertura del 100% en todas las mutaciones e invitaciones (`registration`, `branches`, `imports/validation`, `membership-invitations/acceptance`, `cross-branch-query-authorizations`, etc.).
- **Respuestas `404 Not Found` Explícitas:** `96 / 97` (100% de operaciones parametrizadas).
- **Parámetros de Ruta Resolutos:** `0` errores de parámetros faltantes.

### 1.2 Especificación Asíncrona AsyncAPI v1 (`documentacion/asyncapi-company-management-v1.yaml`)
- **Versión AsyncAPI:** `2.6.0`
- **Total de Canales:** `67` (62 de eventos del bus + 5 canales de tiempo real WebSocket).
- **Total de Mensajes de Evento:** `62` eventos clasificados por dominio.
- **Nomenclatura Canónica:** Notación punto en minúsculas unificada (ej: `account.created`, `company.updated`, `branch.activated`, `membership.invitation.created`, `subscription.activated`).
- **Esquemas de Payload `data` Específicos:** Todos los mensajes de evento cuentan con esquemas de datos tipados (`AccountData`, `CompanyData`, `BranchData`, `MembershipData`, `AssignmentData`, `RoleData`, `OperationData`, `SubscriptionData`) y envelopes especializados (incluyendo `PlatformUserLimitChangedEnvelope`).
- **Envelope Común Unificado (14 Campos):** Estandarizado con `eventId`, `eventType`, `schemaVersion`, `aggregateVersion`, `sequence`, `occurredAt`, `producerModule`, `correlationId`, `causationId`, `accountId`, `companyId`, `branchId`, `actorUserId` y `data`.

### 1.3 Protocolos WebSocket (`WSS /ws/v1`)
- **Handshake y Autenticación:** Token JWT `Bearer` obligatorio.
- **Contratos Formales de Mensajes:** Especificación explícita de `WebSocketConnectRequest`, `WebSocketSubscribeMessage`, `WebSocketUnsubscribeMessage`, `WebSocketNotificationMessage` y `WebSocketAckMessage`.
- **Retención y Confirmación:** 30 días con `ACK` obligatorio para notificaciones críticas, 7 días para operativas y 24 horas para informativas efímeras.

---

## 2. Matriz de Cobertura de Eventos Asíncronos (62 Eventos)

| Dominio | Cantidad | Eventos Canónicos Registrados |
|---|:---:|---|
| **Cuentas** | 5 | `account.created`, `account.updated`, `account.suspended`, `account.reactivated`, `account.deactivated` |
| **Empresas** | 8 | `company.created`, `company.updated`, `company.configuration.completed`, `company.activated`, `company.suspended`, `company.reactivated`, `company.deletion.requested`, `company.deleted` |
| **Sucursales** | 11 | `branch.created`, `branch.updated`, `branch.configuration.completed`, `branch.activated`, `branch.suspended`, `branch.reactivated`, `branch.coverage.required`, `branch.coverage.confirmed`, `branch.head-office.changed`, `branch.deletion.requested`, `branch.deleted` |
| **Acceso y Membresías** | 12 | `membership.invitation.created`, `membership.invitation.accepted`, `membership.invitation.revoked`, `membership.created`, `membership.updated`, `membership.suspended`, `membership.reactivated`, `membership.revoked`, `branch-assignment.created`, `branch-assignment.updated`, `branch-assignment.revoked`, `operational-context.changed` |
| **Roles y Permisos** | 7 | `role.created`, `role.updated`, `role.deactivated`, `role.deleted`, `role.assigned`, `role.unassigned`, `effective-permissions.changed` |
| **Operaciones Asíncronas** | 7 | `operation.created`, `operation.progressed`, `operation.completed`, `operation.partially-completed`, `operation.failed`, `operation.cancelled`, `operation.expired` |
| **Integración Billing** | 12 | `subscription.activated`, `subscription.suspended`, `subscription.cancelled`, `subscription.plan-changed`, `subscription.payment-approved`, `subscription.payment-rejected`, `subscription.grace-period-started`, `subscription.grace-period-ended`, `branch-addon.created`, `branch-addon.cancelled`, `platform-user-limit.changed`, `billing.administrative-correction-applied` |

---

## 3. Certificación de Restricciones Normativas

- [x] **Aislamiento de Repositorio:** El repositorio activo `Clinerya` no ha sido modificado (0 archivos alterados).
- [x] **Separación de Módulos:** `Personal y Nómina` permanece strictly fuera de la administración de membresías o permisos de plataforma.
- [x] **Propietarios de Contratos:** `Company Management`, `Identity` y `Billing` conservan la propiedad exclusiva de sus datos y esquemas.
- [x] **Sin Código Ejecutable Ni SQL:** Se certifica que no se han generado controladores REST Java, resolvers GraphQL, consumidores de mensajes, entidades ORM ni migraciones Flyway SQL durante esta fase.

---

## 4. Dictamen Final

```text
=== Dictamen de Validación ===
Estado: APROBADO SIN ERRORES CRÍTICOS (0 Errors, 0 Warnings)
Rama Git Activa: contracts-v1 (Commit HEAD)
```

Queda oficialmente certificada y completada la fase de diseño de contratos síncronos y asíncronos para `company_management`.
