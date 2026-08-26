# Orden de trabajo: contratos síncronos y asíncronos de Company Management

## 1. Objetivo

Completar y aprobar los contratos técnicos del módulo `company_management` antes de iniciar la implementación de código, persistencia o migraciones Flyway.

La fase debe dejar definidos:

- interfaces REST en OpenAPI;
- eventos del bus;
- mensajes WebSocket;
- webhooks externos;
- eventos de integración con `Billing`;
- permisos y alcance por cuenta, empresa y sucursal.

## 2. Restricciones obligatorias

- No modificar el repositorio activo de `Clinerya`.
- No reutilizar tablas ni migraciones clínicas.
- No generar controladores, resolvers, consumidores ni SQL durante esta fase.
- No permitir acceso directo a tablas de otro módulo.
- Mantener `company_management` como propietario de sus contratos y datos.
- Mantener `Identity` como propietario de usuarios y credenciales.
- Mantener `Billing` como propietario de planes, pagos y suscripciones.
- Mantener `Personal/Nómina` separado de membresías y permisos de plataforma.

## 3. Orden de ejecución

### Paso 1: proteger el estado actual

Crear un punto de recuperación del estado documental actual mediante commit o tag en el repositorio nuevo de JComercy.

Verificar que el repositorio activo de Clinerya no haya sido modificado.

### Paso 2: revisar OpenAPI

Revisar y aprobar:

- las 81 rutas actuales;
- los 97 `operationId`;
- los 64 schemas;
- permisos `x-required-permission`;
- respuestas `401`, `403`, `404`, `409` y `422`;
- paginación por cursor;
- `Idempotency-Key`;
- `ETag` e `If-Match`;
- aislamiento de empresas y sucursales;
- estados de cuenta, empresa, sucursal, membresía, asignación y operación.

Archivo principal:

`documentacion/openapi-company-management-v1.yaml`

### Paso 3: crear la especificación AsyncAPI

Crear:

`documentacion/asyncapi-company-management-v1.yaml`

La especificación debe incluir, como mínimo:

#### Eventos de cuenta

- `account.created`;
- `account.updated`;
- `account.suspended`;
- `account.reactivated`;
- `account.deactivated`.

#### Eventos de empresa

- `company.created`;
- `company.updated`;
- `company.configuration.completed`;
- `company.activated`;
- `company.suspended`;
- `company.reactivated`;
- `company.deletion.requested`;
- `company.deleted`.

#### Eventos de sucursal

- `branch.created`;
- `branch.updated`;
- `branch.configuration.completed`;
- `branch.activated`;
- `branch.suspended`;
- `branch.reactivated`;
- `branch.coverage.required`;
- `branch.coverage.confirmed`;
- `branch.head-office.changed`;
- `branch.deletion.requested`;
- `branch.deleted`.

#### Eventos de acceso

- `membership.invitation.created`;
- `membership.invitation.accepted`;
- `membership.invitation.revoked`;
- `membership.created`;
- `membership.updated`;
- `membership.suspended`;
- `membership.reactivated`;
- `membership.revoked`;
- `branch-assignment.created`;
- `branch-assignment.updated`;
- `branch-assignment.revoked`;
- `operational-context.changed`.

#### Eventos de roles y permisos

- `role.created`;
- `role.updated`;
- `role.deactivated`;
- `role.deleted`;
- `role.assigned`;
- `role.unassigned`;
- `effective-permissions.changed`.

#### Eventos de operaciones

- `operation.created`;
- `operation.progressed`;
- `operation.completed`;
- `operation.partially-completed`;
- `operation.failed`;
- `operation.cancelled`;
- `operation.expired`.

#### Eventos de Billing

- `subscription.activated`;
- `subscription.suspended`;
- `subscription.cancelled`;
- `subscription.plan-changed`;
- `subscription.payment-approved`;
- `subscription.payment-rejected`;
- `subscription.grace-period-started`;
- `subscription.grace-period-ended`;
- `branch-addon.created`;
- `branch-addon.cancelled`;
- `platform-user-limit.changed`;
- `billing.administrative-correction-applied`.

### Paso 4: definir el envelope común

Todos los eventos deben incluir:

```json
{
  "eventId": "evt_01J...",
  "eventType": "company.updated",
  "eventVersion": "1.0",
  "occurredAt": "2026-08-26T20:00:00Z",
  "correlationId": "cor_01J...",
  "causationId": "cause_01J...",
  "accountId": "acc_01J...",
  "companyId": "com_01J...",
  "branchId": "brn_01J...",
  "actorUserId": "usr_01J...",
  "data": {}
}
```

Reglas:

- `branchId` será opcional cuando el evento aplique solo a la empresa.
- `actorUserId` podrá ser nulo en eventos automáticos de Billing.
- `data` contendrá solo el mínimo necesario.
- No incluir contraseñas, tokens, secretos ni recursos completos por defecto.
- Los identificadores serán públicos y contractuales.
- Las fechas usarán UTC e ISO 8601.

### Paso 5: definir canales WebSocket

Documentar en AsyncAPI o en un anexo técnico:

- conexión `WSS /ws/v1`;
- autenticación JWT;
- suscripción por canal;
- autorización por empresa y sucursal;
- revalidación al cambiar permisos;
- mensajes `SUBSCRIBE`, `NOTIFICATION` y `ACK`;
- `notificationId` único;
- persistencia temporal;
- reintentos controlados;
- retención de 30 días, 7 días y 24 horas según importancia.

### Paso 6: definir webhooks externos

Documentar:

- creación y administración de suscripciones;
- aislamiento por empresa;
- filtro opcional por sucursal y tipo de evento;
- versiones de evento;
- firma HMAC;
- timestamp y protección contra replay;
- secreto rotatorio;
- respuestas `2xx` como confirmación;
- backoff exponencial;
- clasificación de errores;
- estados `PENDING`, `DELIVERED`, `FAILED` y `EXPIRED`;
- reentrega manual autorizada.

### Paso 7: documentar reglas de orden y duplicidad

Definir formalmente:

- secuencia o versión por suscripción;
- comportamiento ante eventos fuera de orden;
- persistencia temporal de notificaciones críticas;
- confirmación de entrega;
- recuperación después de reconexión;
- reconciliación mediante REST;
- no duplicación visible para el usuario.

La deduplicación principal será responsabilidad del servidor mediante `notificationId` y estado de entrega persistido.

### Paso 8: revisar integración con Billing

Documentar:

- eventos consumidos;
- versión o secuencia de suscripción;
- detección de brechas;
- reconciliación automática y manual;
- último estado válido durante indisponibilidad;
- bloqueo de cambios que requieran confirmación;
- cobertura de sucursales incluidas y adicionales;
- periodo de gracia de 7 días.

### Paso 9: validar contratos

Ejecutar estas validaciones:

- YAML válido;
- referencias resolubles;
- `operationId` únicos;
- nombres de eventos únicos;
- schemas reutilizables;
- permisos declarados;
- empresas y sucursales correctamente aisladas;
- respuestas de error completas;
- compatibilidad de versiones;
- ejemplos consistentes con los schemas.

## 4. Entregables esperados

1. `openapi-company-management-v1.yaml` actualizado y validado.
2. `asyncapi-company-management-v1.yaml` creado y validado.
3. Matriz de permisos REST, eventos y canales.
4. Matriz de propietarios por contrato.
5. Documento de reglas de entrega, orden, reintentos y reconciliación.
6. Informe de validación sin errores críticos.

## 5. Criterios para cerrar la fase

La fase se considerará completa cuando:

- OpenAPI y AsyncAPI estén aprobados.
- Cada endpoint tenga propietario y permiso.
- Cada evento tenga versión y envelope común.
- Cada canal WebSocket tenga autorización definida.
- Cada webhook tenga seguridad y política de reintentos.
- No existan referencias directas entre tablas de módulos.
- Se hayan documentado errores, idempotencia y concurrencia.
- Se confirme que Personal/Nómina permanece fuera de `Company Management`.

## 6. Prohibiciones antes de la aprobación

No iniciar todavía:

- implementación de controladores REST;
- resolvers GraphQL;
- consumidores de eventos;
- servidores WebSocket;
- envío real de webhooks;
- entidades ORM;
- scripts SQL;
- migraciones Flyway;
- integración de módulos de Clinerya.

Después de aprobar esta fase, la siguiente orden será diseñar la estructura interna de `company-management` y posteriormente sus migraciones Flyway.
