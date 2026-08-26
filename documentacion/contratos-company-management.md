# Contratos entre módulos: Company Management

## 1. Alcance

Este documento define los contratos síncronos y asíncronos mediante los cuales otros módulos colaborarán con `company_management`.

Los contratos son la única vía permitida para consultar o notificar información del módulo. Ningún consumidor podrá acceder directamente a sus tablas o esquema.

## 2. Principios

- `company_management` es el propietario de cuentas, empresas, sucursales, membresías, asignaciones, roles y permisos empresariales.
- Cada módulo conserva la propiedad de sus propios datos.
- Las consultas síncronas se utilizarán cuando el consumidor necesite una respuesta inmediata.
- Los eventos asíncronos se utilizarán para comunicar cambios ya confirmados.
- Los identificadores públicos serán los identificadores de integración.
- Los contratos no expondrán información innecesaria o sensible.
- Las operaciones deberán validar contexto de cuenta, empresa, sucursal y usuario.
- Todos los eventos deberán ser idempotentes para permitir reintentos.

## 3. Consumidores principales

| Consumidor | Necesidad |
|---|---|
| Identity | Validar identidad y estado del usuario. |
| Billing | Validar empresa y notificar capacidades contratadas. |
| Personal y Nómina | Relacionar empleados con usuarios y empresas. |
| Inventario | Validar empresa, sucursal y permisos de operación. |
| Ventas | Validar contexto de venta y permisos. |
| Caja | Validar sucursal y autorización para operar caja. |
| Contabilidad | Recibir contexto empresarial y eventos operativos. |
| Fiscal | Consultar configuración empresarial necesaria para cumplimiento. |
| Clinerya | Validar empresa, sucursal y membresías para operaciones clínicas. |
| Portal del empleado | Consultar asignaciones y permisos limitados. |

## 4. Contratos síncronos de consulta

### 4.1 Resolver empresa

```http
GET /api/v1/companies/{publicCompanyId}
```

### 4.2 Resolver sucursal

```http
GET /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}
```

### 4.3 Resolver autorización y permisos efectivos

```http
GET /api/v1/companies/{publicCompanyId}/effective-permissions/{userId}
```

### 4.4 Resolver asignaciones de usuario

```http
GET /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments
```

---

## 5. Eventos Publicados (Notación Canónica Punto)

### 5.1 Eventos Organizacionales

| Evento Canónico | Cuándo se publica |
|---|---|
| `account.created` | Se crea una cuenta administrativa. |
| `account.updated` | Se actualizan datos o preferencias de cuenta. |
| `account.suspended` | Se suspende la cuenta por mora o administración. |
| `account.reactivated` | Se reactiva una cuenta suspendida. |
| `account.deactivated` | Se completa la baja lógica de la cuenta. |
| `company.created` | Se registra una empresa. |
| `company.updated` | Se actualizan perfil o preferencias de empresa. |
| `company.configuration.completed` | Se completan datos obligatorios de empresa. |
| `company.activated` | La empresa queda disponible para operar. |
| `company.suspended` | La empresa se suspende comercial o administrativamente. |
| `company.reactivated` | Se reactiva una empresa suspendida. |
| `company.deletion.requested` | Se solicita la baja lógica de empresa. |
| `company.deleted` | Se confirma la baja lógica de empresa. |
| `branch.created` | Se registra una sucursal. |
| `branch.updated` | Se actualiza perfil o zona horaria de sucursal. |
| `branch.configuration.completed` | Se completa la configuración de sucursal. |
| `branch.activated` | Una sucursal queda operativa. |
| `branch.suspended` | Una sucursal queda suspendida. |
| `branch.reactivated` | Una sucursal se reactiva. |
| `branch.coverage.required` | Una sucursal requiere cobertura de plan (`PENDING_PLAN`). |
| `branch.coverage.confirmed` | Billing confirma cobertura para la sucursal. |
| `branch.head-office.changed` | Se designa una nueva sucursal matriz. |
| `branch.deletion.requested` | Se solicita la baja lógica de sucursal. |
| `branch.deleted` | Se confirma la baja lógica de sucursal. |

### 5.2 Eventos de Acceso y Permisos

| Evento Canónico | Cuándo se publica |
|---|---|
| `membership.invitation.created` | Se invita un usuario a una empresa. |
| `membership.invitation.accepted` | El usuario acepta la invitación. |
| `membership.invitation.revoked` | Se revoca una invitación pendiente. |
| `membership.created` | Se crea la membresía en la empresa. |
| `membership.updated` | Cambia el estado o alcance de membresía. |
| `membership.suspended` | Se suspende el acceso del usuario. |
| `membership.reactivated` | Se reactiva la membresía. |
| `membership.revoked` | Se revoca el acceso de la membresía. |
| `branch-assignment.created` | Se asigna un usuario a una sucursal. |
| `branch-assignment.updated` | Se actualiza el estado de una asignación. |
| `branch-assignment.revoked` | Se retira la asignación de sucursal. |
| `operational-context.changed` | El usuario selecciona su sucursal activa. |
| `role.created` | Se crea un rol personalizado. |
| `role.updated` | Cambian los permisos de un rol. |
| `role.deactivated` | Se desactiva un rol. |
| `role.deleted` | Se elimina un rol personalizado. |
| `role.assigned` | Se asigna un rol a un usuario. |
| `role.unassigned` | Se desasigna un rol. |
| `effective-permissions.changed` | Cambian los permisos efectivos derivados. |

### 5.3 Eventos de Operaciones Asíncronas

| Evento Canónico | Cuándo se publica |
|---|---|
| `operation.created` | Se registra una operación asíncrona. |
| `operation.progressed` | Progreso incremental en una operación. |
| `operation.completed` | Operación asíncrona finalizada con éxito. |
| `operation.partially-completed` | Operación finalizada con errores parciales. |
| `operation.failed` | Fallo total en la operación. |
| `operation.cancelled` | Operación cancelada por el usuario. |
| `operation.expired` | Operación expirada por tiempo límite. |

### 5.4 Eventos de Capacidad desde Billing

| Evento Canónico | Efecto esperado |
|---|---|
| `subscription.activated` | Habilitar operación según capacidades. |
| `subscription.suspended` | Suspender nuevas operaciones comerciales. |
| `subscription.cancelled` | Cancelar suscripción empresarial. |
| `subscription.plan-changed` | Recalcular límites de sucursales y usuarios. |
| `subscription.payment-approved` | Confirmar pago y restaurar servicios. |
| `subscription.payment-rejected` | Iniciar periodo de gracia de 7 días. |
| `subscription.grace-period-started` | Iniciar ventana de regularización de 7 días. |
| `subscription.grace-period-ended` | Finalizar periodo de gracia y suspender. |
| `branch-addon.created` | Habilitar capacidad de sucursal adicional. |
| `branch-addon.cancelled` | Cancelar capacidad de sucursal adicional. |
| `platform-user-limit.changed` | Actualizar límite de usuarios de plataforma. |
| `billing.administrative-correction-applied` | Aplicar corrección administrativa de Billing. |

---

## 6. Estructura Unificada del Envelope Común

Todos los eventos de la plataforma comparten estrictamente la siguiente estructura de envoltorio (*envelope*):

```json
{
  "eventId": "evt_01J8Z9X2Y3W4V5U6T7S8R9Q0P1",
  "eventType": "company.updated",
  "schemaVersion": "1.0",
  "aggregateVersion": 3,
  "sequence": 105,
  "occurredAt": "2026-08-26T20:00:00Z",
  "producerModule": "company_management",
  "correlationId": "cor_01J8Z9X2Y3W4V5U6T7S8R9Q0P1",
  "causationId": "cause_01J8Z9X2Y3W4V5U6T7S8R9Q0P1",
  "accountId": "acc_01J8Z9X2Y3W4V5U6T7S8R9Q0P1",
  "companyId": "com_01J8Z9X2Y3W4V5U6T7S8R9Q0P1",
  "branchId": "brn_01J8Z9X2Y3W4V5U6T7S8R9Q0P1",
  "actorUserId": "usr_01J8Z9X2Y3W4V5U6T7S8R9Q0P1",
  "data": {}
}
```

### Reglas de Campos del Envelope:
- `eventId`: Identificador único universal del evento (`evt_...`).
- `eventType`: Nombre canónico en notación punto (ej: `company.updated`).
- `schemaVersion`: Versión del contrato JSON del esquema del evento (ej: `"1.0"`).
- `aggregateVersion`: Entero autoincremental que indica la versión del agregado/entidad que sufrió el cambio.
- `sequence`: Entero autoincremental de la secuencia de publicación del canal para ordenamiento de mensajes.
- `occurredAt`: Fecha y hora en formato UTC e ISO 8601 (`YYYY-MM-DDTHH:mm:ssZ`).
- `producerModule`: Módulo emisor del evento (`company_management`, `billing`, `identity`).
- `correlationId`: Identificador de trazabilidad distribuida de la solicitud originaria.
- `causationId`: Identificador de la operación u otro evento que provocó este cambio (opcional).
- `accountId`: Identificador público de la cuenta.
- `companyId`: Identificador público de la empresa.
- `branchId`: Identificador público de la sucursal (opcional cuando el evento es a nivel cuenta/empresa).
- `actorUserId`: Identificador del usuario que ejecutó la acción (nulo en eventos automáticos).
- `data`: Payload estructurado y validable según el esquema específico del tipo de evento.
