# Catálogo de interfaces de Company Management

## 1. Propósito y alcance

Este documento define el catálogo inicial de interfaces del módulo `company_management` de JComercy. Se basa en las decisiones aprobadas en [diseno-interfaces-company-management.md](diseno-interfaces-company-management.md).

El módulo es propietario de cuentas administrativas, empresas, sucursales, membresías, asignaciones, roles, permisos empresariales, operaciones de archivo y suscripciones webhook. No expone tablas ni contratos internos de otros módulos.

## 2. Convenciones comunes

- Base REST: `/api/v1`.
- Rutas técnicas en inglés.
- Identificadores públicos en las rutas; no se exponen identificadores internos.
- `Authorization: Bearer {accessToken}`.
- `X-Correlation-Id` para correlación de solicitudes.
- `Idempotency-Key` obligatorio en creaciones y comandos críticos.
- `ETag` e `If-Match` para modificaciones con concurrencia optimista.
- Colecciones con paginación por cursor: `limit` y `after`.
- Errores REST con RFC 9457.
- Fechas y horas en UTC, ISO 8601.

Los endpoints se clasifican por alcance:

- cuenta administrativa;
- empresa;
- sucursal;
- membresía y asignación;
- roles y permisos;
- operación asíncrona;
- auditoría;
- webhook.

## 3. Cuentas administrativas

### 3.1 Registro y administración

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `POST` | `/accounts` | síncrono | Crear cuenta administrativa durante el registro |
| `GET` | `/accounts/{publicAccountId}` | síncrono | Consultar datos básicos de la cuenta |
| `PATCH` | `/accounts/{publicAccountId}` | síncrono | Actualizar datos y preferencias autorizadas |
| `POST` | `/accounts/{publicAccountId}/deactivation-requests` | asíncrono | Solicitar desactivación de la cuenta |
| `POST` | `/accounts/{publicAccountId}/reactivation` | síncrono | Reactivar una cuenta autorizada |
| `GET` | `/accounts/{publicAccountId}/history` | síncrono | Consultar historial de cambios |
| `POST` | `/accounts/{publicAccountId}/invitations` | síncrono | Crear invitación para una administración delegada |

La desactivación requiere propietario, confirmación reforzada y validación de empresas, sucursales, suscripciones y obligaciones pendientes. Identity administra credenciales, recuperación y autenticación.

### 3.2 Empresas de una cuenta

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/accounts/{publicAccountId}/companies` | síncrono | Listar empresas visibles para el usuario |
| `POST` | `/accounts/{publicAccountId}/companies` | síncrono | Crear una empresa |

La primera empresa podrá crearse durante el registro junto con su sucursal matriz. La empresa permanecerá en `CONFIGURING` hasta completar la información obligatoria.

## 4. Empresas

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/companies/{publicCompanyId}` | síncrono | Consultar una empresa |
| `PATCH` | `/companies/{publicCompanyId}` | síncrono | Actualizar datos autorizados |
| `POST` | `/companies/{publicCompanyId}/configuration-completion` | síncrono | Completar configuración obligatoria |
| `POST` | `/companies/{publicCompanyId}/activation` | síncrono | Activar una empresa configurada |
| `POST` | `/companies/{publicCompanyId}/suspension` | síncrono | Solicitar suspensión |
| `POST` | `/companies/{publicCompanyId}/reactivation` | síncrono | Solicitar reactivación |
| `POST` | `/companies/{publicCompanyId}/deletion-requests` | asíncrono | Solicitar baja lógica |
| `GET` | `/companies/{publicCompanyId}/history` | síncrono | Consultar historial de cambios |
| `GET` | `/companies/{publicCompanyId}/capabilities` | síncrono | Consultar capacidades recibidas de Billing |

Los cambios de estado requieren transiciones válidas, permisos, datos completos, suscripción aplicable y validación de dependencias.

## 5. Configuración de empresas

Los permisos de actualización se separan por categoría:

| Categoría | Ruta sugerida | Observación |
|---|---|---|
| Datos básicos | `/companies/{id}/profile` | Nombre y datos generales |
| Datos fiscales | `/companies/{id}/tax-profile` | Requiere permiso sensible |
| Contacto | `/companies/{id}/contact` | Datos de comunicación |
| Configuración operativa | `/companies/{id}/operational-settings` | Parámetros del negocio |
| Zona horaria | `/companies/{id}/localization` | Zona usada por reglas locales |
| Preferencias | `/companies/{id}/preferences` | Preferencias no críticas |

Cada grupo podrá exponerse mediante `PATCH`. Los cambios fiscales, de zona horaria y de configuración sensible serán auditados.

## 6. Sucursales

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/companies/{publicCompanyId}/branches` | síncrono | Listar sucursales visibles |
| `POST` | `/companies/{publicCompanyId}/branches` | síncrono | Crear sucursal |
| `GET` | `/companies/{publicCompanyId}/branches/{publicBranchId}` | síncrono | Consultar sucursal |
| `PATCH` | `/companies/{publicCompanyId}/branches/{publicBranchId}` | síncrono | Actualizar sucursal |
| `POST` | `/companies/{publicCompanyId}/branches/{publicBranchId}/configuration-completion` | síncrono | Completar configuración |
| `POST` | `/companies/{publicCompanyId}/branches/{publicBranchId}/activation` | síncrono | Activar sucursal |
| `POST` | `/companies/{publicCompanyId}/branches/{publicBranchId}/suspension` | síncrono | Suspender sucursal |
| `POST` | `/companies/{publicCompanyId}/branches/{publicBranchId}/reactivation` | síncrono | Reactivar sucursal |
| `POST` | `/companies/{publicCompanyId}/branches/{publicBranchId}/deletion-requests` | asíncrono | Solicitar baja lógica |
| `POST` | `/companies/{publicCompanyId}/branches/{publicBranchId}/head-office-designation` | síncrono | Designar nueva sucursal matriz |
| `GET` | `/companies/{publicCompanyId}/branches/{publicBranchId}/history` | síncrono | Consultar historial |

Si el plan no cubre una sucursal nueva, podrá crearse en `PENDING_PLAN`, pero no activarse hasta que exista cobertura mediante plan o complemento. No existe un límite absoluto de sucursales.

## 7. Consultas entre sucursales

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/companies/{publicCompanyId}/cross-branch-queries` | síncrono | Consultar información permitida entre sucursales |
| `POST` | `/companies/{publicCompanyId}/cross-branch-query-authorizations` | síncrono | Autorizar consultas de solo lectura |
| `DELETE` | `/companies/{publicCompanyId}/cross-branch-query-authorizations/{authorizationId}` | síncrono | Revocar autorización |

Estas interfaces no permiten vender, consumir, reservar ni modificar recursos de otra sucursal. La consulta de información sensible o existencias cross-branch requiere permiso explícito y auditoría.

## 8. Membresías y asignaciones

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/companies/{publicCompanyId}/memberships` | síncrono | Listar membresías según alcance |
| `POST` | `/companies/{publicCompanyId}/memberships/invitations` | síncrono | Invitar usuario con empresa, sucursales y roles iniciales |
| `POST` | `/membership-invitations/{invitationToken}/acceptance` | síncrono | Aceptar invitación de un solo uso |
| `POST` | `/companies/{publicCompanyId}/memberships/{membershipId}/resend` | síncrono | Reenviar invitación controladamente |
| `POST` | `/companies/{publicCompanyId}/memberships/{membershipId}/revocation` | síncrono | Revocar membresía |
| `POST` | `/companies/{publicCompanyId}/memberships/{membershipId}/suspension` | síncrono | Suspender membresía |
| `POST` | `/companies/{publicCompanyId}/memberships/{membershipId}/reactivation` | síncrono | Reactivar membresía |
| `PATCH` | `/companies/{publicCompanyId}/memberships/{membershipId}` | síncrono | Actualizar alcance y roles |
| `GET` | `/companies/{publicCompanyId}/memberships/{membershipId}/history` | síncrono | Consultar historial |
| `GET` | `/companies/{publicCompanyId}/memberships/{membershipId}/assignments` | síncrono | Listar asignaciones |
| `POST` | `/companies/{publicCompanyId}/memberships/{membershipId}/assignments` | síncrono | Asignar sucursal y alcance |
| `PATCH` | `/companies/{publicCompanyId}/memberships/{membershipId}/assignments/{assignmentId}` | síncrono | Modificar asignación |
| `DELETE` | `/companies/{publicCompanyId}/memberships/{membershipId}/assignments/{assignmentId}` | síncrono | Retirar asignación |

Una persona tendrá una membresía por empresa y podrá tener varias asignaciones y roles dentro de ella. Las invitaciones tendrán token de un solo uso, expiración, revocación, reenvío controlado y auditoría.

## 9. Contexto operativo

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/companies/{publicCompanyId}/memberships/me/operational-contexts` | síncrono | Listar sucursales operables por el usuario |
| `POST` | `/companies/{publicCompanyId}/memberships/me/operational-context` | síncrono | Seleccionar sucursal activa |
| `GET` | `/companies/{publicCompanyId}/memberships/me/operational-context` | síncrono | Consultar contexto activo |

El cambio de contexto valida membresía, estado, asignación y permisos. Las operaciones posteriores deberán transportar la sucursal activa y no podrán reutilizar silenciosamente el contexto anterior.

## 10. Roles y permisos

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/companies/{publicCompanyId}/roles` | síncrono | Listar roles disponibles |
| `POST` | `/companies/{publicCompanyId}/roles` | síncrono | Crear rol personalizado |
| `GET` | `/companies/{publicCompanyId}/roles/{roleId}` | síncrono | Consultar rol |
| `PATCH` | `/companies/{publicCompanyId}/roles/{roleId}` | síncrono | Modificar rol |
| `POST` | `/companies/{publicCompanyId}/roles/{roleId}/deactivation` | síncrono | Desactivar rol |
| `DELETE` | `/companies/{publicCompanyId}/roles/{roleId}` | síncrono | Solicitar eliminación de rol |
| `GET` | `/companies/{publicCompanyId}/permissions` | síncrono | Consultar catálogo de permisos |
| `GET` | `/companies/{publicCompanyId}/effective-permissions/{userId}` | síncrono | Consultar permisos efectivos autorizados |

Los roles personalizados pertenecen a una empresa. Los permisos se definen por módulo, acción y sucursal. La administración de roles requiere permisos separados y no podrá dejar a la empresa sin un administrador válido.

## 11. Operaciones asíncronas

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/operations/{operationId}` | síncrono | Consultar estado y progreso |
| `GET` | `/operations/{operationId}/history` | síncrono | Consultar cadena de operaciones relacionadas |
| `GET` | `/operations/{operationId}/errors` | síncrono | Consultar errores y registros afectados |
| `POST` | `/operations/{operationId}/cancellation` | síncrono | Cancelar una operación permitida |
| `POST` | `/operations/{operationId}/retry` | asíncrono | Reintentar fallos |
| `POST` | `/operations/{operationId}/recovery` | asíncrono | Recuperar operación expirada |
| `GET` | `/operations/{operationId}/result` | síncrono | Consultar resultado |
| `GET` | `/operations/{operationId}/result/download` | síncrono | Descargar resultado autorizado |

Los estados son `PENDING`, `PROCESSING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED` y `EXPIRED`. El acceso depende de quién inició la operación y del permiso sobre la empresa o sucursal.

## 12. Importaciones y exportaciones

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `POST` | `/companies/{publicCompanyId}/imports/validation` | síncrono/asíncrono | Validar archivo sin modificar datos |
| `POST` | `/companies/{publicCompanyId}/imports` | síncrono/asíncrono | Aplicar importación confirmada |
| `POST` | `/companies/{publicCompanyId}/exports` | síncrono/asíncrono | Generar exportación |
| `GET` | `/companies/{publicCompanyId}/files/{fileId}` | síncrono | Consultar archivo temporal |
| `GET` | `/companies/{publicCompanyId}/files/{fileId}/download` | síncrono | Descargar archivo autorizado |
| `DELETE` | `/companies/{publicCompanyId}/files/{fileId}` | síncrono | Eliminar archivo temporal |

Se admitirán CSV y XLSX. Las importaciones indicarán modo `TRANSACTIONAL` o `PARTIAL`. Se validarán estructura, MIME, tamaño, fórmulas peligrosas, contenido, datos sensibles y malware.

## 13. Auditoría

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/companies/{publicCompanyId}/audit-records` | síncrono | Consultar auditoría filtrada |
| `GET` | `/companies/{publicCompanyId}/audit-records/{auditRecordId}` | síncrono | Consultar registro autorizado |
| `POST` | `/companies/{publicCompanyId}/audit-exports` | asíncrono | Generar exportación de auditoría |

Los filtros permitidos incluyen recurso, usuario, acción, fecha, sucursal y resultado. Los valores anteriores y posteriores solo se mostrarán para cambios sensibles y según permisos.

## 14. WebSockets

Endpoint de conexión:

```text
WSS /ws/v1
```

Canales iniciales:

| Canal | Propósito |
|---|---|
| `account.{accountId}` | Cambios administrativos autorizados |
| `company.{companyId}` | Cambios de empresa, membresías y capacidades |
| `company.{companyId}.branch.{branchId}` | Operaciones de una sucursal |
| `company.{companyId}.operations` | Progreso y resultado de operaciones |
| `company.{companyId}.notifications` | Notificaciones de la empresa |

La conexión y cada suscripción se autorizan por separado. Los cambios de permisos invalidan canales afectados.

Las notificaciones críticas tendrán `notificationId`, persistencia temporal, `ack`, reintentos controlados y retención según importancia. REST seguirá siendo la fuente oficial para recuperar el estado.

## 15. GraphQL

GraphQL se expondrá mediante un endpoint versionado y autenticado:

```text
POST /graphql/v1
```

Consultas iniciales:

- `account(id)`;
- `companies(accountId, filter, after, limit)`;
- `company(id)`;
- `branches(companyId, filter, after, limit)`;
- `membership(id)`;
- `effectivePermissions(userId, companyId)`;
- `operation(id)`;
- `auditRecords(companyId, filter, after, limit)`.

Mutaciones controladas:

- `createCompany`;
- `updateCompany`;
- `createBranch`;
- `selectOperationalContext`;
- `updateMembership`;
- `createCustomRole`.

Los comandos críticos, operaciones asíncronas, integraciones y cambios de suscripción se mantendrán en REST. GraphQL tendrá límites de profundidad, complejidad, costo por campo y tiempo de ejecución.

Solo se ejecutan las operaciones registradas como archivos `.graphql` (ver el diseño de interfaces, §9). Hoy están registradas `Company` y `CompaniesByAccount`; el resto de las consultas y mutaciones de esta lista todavía no existen en el código.

## 16. Webhooks

| Método | Ruta | Tipo | Propósito |
|---|---|---|---|
| `GET` | `/companies/{publicCompanyId}/webhook-subscriptions` | síncrono | Listar suscripciones autorizadas |
| `POST` | `/companies/{publicCompanyId}/webhook-subscriptions` | síncrono | Crear suscripción |
| `GET` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}` | síncrono | Consultar suscripción |
| `PATCH` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}` | síncrono | Modificar alcance o eventos |
| `POST` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/test` | síncrono | Enviar evento simulado |
| `POST` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/real-event-test` | síncrono | Ejecutar prueba real controlada |
| `POST` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/pause` | síncrono | Pausar suscripción |
| `POST` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/resume` | síncrono | Reactivar suscripción |
| `POST` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/secret-rotation` | síncrono | Rotar secreto |
| `DELETE` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}` | síncrono | Eliminar suscripción |
| `GET` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/deliveries` | síncrono | Consultar entregas y reintentos |
| `POST` | `/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/deliveries/{deliveryId}/redelivery` | asíncrono | Reentregar evento |

Cada suscripción se limita a una empresa y opcionalmente a sucursales y tipos de evento. Los payloads incluyen metadatos mínimos, identificadores, `eventId`, `eventType`, `eventVersion`, `occurredAt` y referencia API.

## 17. Contratos de Billing

`Company Management` no modifica planes, pagos ni cargos. Consumirá eventos de `Billing` sobre:

- activación, suspensión y cancelación;
- pagos aprobados o rechazados;
- cambios de plan;
- periodo de gracia;
- complementos de sucursales;
- límites de usuarios;
- correcciones administrativas.

También dispondrá de una consulta interna de reconciliación cuando detecte brechas o inconsistencias. Los eventos se procesarán con secuencia o versión para evitar aplicar estados antiguos.

## 18. Aprobación

Este catálogo queda aprobado como base para elaborar los contratos OpenAPI, esquemas GraphQL, mensajes WebSocket y contratos webhook. La aprobación no autoriza todavía generar controladores, resolvers, migraciones SQL ni código de integración.
