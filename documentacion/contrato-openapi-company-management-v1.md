# Contrato OpenAPI inicial de Company Management

## Estado del documento

**Estado:** borrador para revisión.

Este documento inicia la definición formal de contratos OpenAPI para `company_management`. La primera operación definida es el registro de una cuenta administrativa con su primera empresa y sucursal matriz.

## 1. Decisión de diseño

El registro inicial será una operación compuesta y síncrona desde el punto de vista del contrato HTTP, pero aplicará una transacción interna para garantizar que se creen correctamente los elementos mínimos del contexto empresarial.

La operación creará:

1. la cuenta administrativa;
2. la membresía del usuario propietario;
3. la primera empresa;
4. la sucursal matriz;
5. la relación entre empresa y sucursal matriz.

La identidad y las credenciales del usuario serán responsabilidad del módulo `Identity`. `Company Management` recibirá una referencia contractual al usuario autenticado o registrado.

## 2. Endpoint

```http
POST /api/v1/accounts/registration
```

### 2.1 Autenticación

La solicitud inicial no requiere `access_token`, porque forma parte del registro. Deberá estar protegida mediante controles antifraude, limitación por IP, validación de correo y, cuando corresponda, CAPTCHA o desafío equivalente.

Headers requeridos:

```http
Content-Type: application/json
Idempotency-Key: {unique-key}
X-Correlation-Id: {correlation-id}
```

`Idempotency-Key` es obligatorio para evitar cuentas duplicadas si el cliente reintenta la solicitud.

## 3. Solicitud

```json
{
  "owner": {
    "identityReference": "usr_01J...",
    "displayName": "Julio Pérez"
  },
  "account": {
    "name": "Grupo Pérez"
  },
  "company": {
    "legalName": "Comercializadora Pérez, S.A. de C.V.",
    "tradeName": "Comercializadora Pérez",
    "countryCode": "MX",
    "taxProfile": {
      "rfc": "XAXX010101000"
    }
  },
  "headOffice": {
    "name": "Sucursal matriz",
    "countryCode": "MX",
    "timeZone": "America/Mexico_City"
  },
  "terms": {
    "accepted": true,
    "version": "2026-01"
  }
}
```

### 3.1 Reglas de solicitud

- `identityReference` debe corresponder a una identidad válida proporcionada por `Identity`.
- `account.name` es obligatorio.
- `company.legalName`, `countryCode` y los datos fiscales requeridos por el país son obligatorios.
- El RFC se validará sintácticamente; la validación fiscal definitiva podrá delegarse al módulo tributario.
- La zona horaria debe pertenecer al catálogo IANA soportado.
- `terms.accepted` debe ser `true`.
- La primera sucursal se crea como matriz y pertenece exclusivamente a la empresa recién creada.
- No se permitirá enviar una empresa o sucursal de otra cuenta mediante este endpoint.

Los datos de contacto, configuración operativa y demás información opcional podrán completarse posteriormente. No se debe sobrecargar el registro inicial con todos los datos de operación del negocio.

## 4. Respuesta exitosa

### 201 Created

```json
{
  "account": {
    "publicAccountId": "acc_01J...",
    "status": "ACTIVE"
  },
  "ownerMembership": {
    "membershipId": "mem_01J...",
    "role": "ACCOUNT_OWNER",
    "status": "ACTIVE"
  },
  "company": {
    "publicCompanyId": "com_01J...",
    "status": "CONFIGURING"
  },
  "headOffice": {
    "publicBranchId": "brn_01J...",
    "status": "CONFIGURING",
    "isHeadOffice": true
  },
  "nextActions": [
    "COMPLETE_COMPANY_PROFILE",
    "COMPLETE_HEAD_OFFICE_PROFILE"
  ],
  "correlationId": "cor_01J..."
}
```

La cuenta y la membresía del propietario pueden quedar activas, mientras que la empresa y la sucursal matriz permanecen en `CONFIGURING` hasta completar la información obligatoria. La cuenta administrativa no concede acceso automático a futuras empresas o sucursales distintas de las que el propietario administre explícitamente.

## 5. Idempotencia

La clave se asociará al resultado final de la operación durante el periodo de retención definido por la plataforma.

- Misma clave y mismo payload: devolver el resultado original.
- Misma clave y payload diferente: `409 Conflict`.
- Solicitud ya procesada: devolver el mismo conjunto de identificadores.

## 6. Errores

### 400 Bad Request

Payload malformado o campos requeridos ausentes.

### 409 Conflict

- `Idempotency-Key` reutilizada con payload diferente.
- La identidad ya tiene una cuenta propietaria incompatible.
- RFC o nombre fiscal ya registrado cuando la regla tributaria lo prohíba.

### 422 Unprocessable Content

Datos con formato válido pero reglas de negocio incumplidas.

### 429 Too Many Requests

Se excedieron los límites antifraude o de registro.

### 503 Service Unavailable

`Identity` no está disponible o no puede confirmarse la identidad. No se creará una cuenta parcialmente identificada.

Los errores seguirán RFC 9457 e incluirán `type`, `title`, `status`, `code`, `detail`, `correlationId` y, cuando aplique, errores por campo.

## 7. Consistencia y fallos

La operación no debe devolver éxito hasta que `Company Management` confirme la creación de sus recursos propios.

Si falla la creación de cualquier elemento interno, se hará rollback transaccional. Si la confirmación con `Identity` depende de una operación externa no transaccional, se usará un flujo de compensación y una operación de registro con estado controlado; no se expondrá una cuenta utilizable a medias.

Los eventos posteriores, como `account.created`, `company.created` y `branch.created`, se publicarán mediante el patrón outbox después de confirmar la transacción.

## 8. Autorización posterior

Después del registro, el propietario podrá:

- completar los datos de la empresa y sucursal matriz;
- administrar sus empresas;
- crear o habilitar sucursales conforme a la cobertura del plan;
- invitar administradores y personal con permisos definidos.

El propietario no obtendrá permisos implícitos sobre empresas de otros propietarios ni sobre empresas futuras que no estén asociadas a su cuenta.

## 9. Decisiones pendientes para revisión

1. Si `Identity` crea primero al usuario y luego llama a este endpoint, o si un orquestador coordina ambos pasos.
2. Campos fiscales mínimos obligatorios para una empresa en estado `CONFIGURING`.
3. Duración exacta de retención de la respuesta idempotente.
4. Catálogo de códigos de error antifraude.
5. Si la cuenta debe quedar `ACTIVE` o `CONFIGURING` hasta verificar el correo del propietario.

## 10. Criterio de aceptación

Este contrato estará listo cuando se aprueben los campos de solicitud, estados iniciales, responsabilidad de `Identity`, reglas de idempotencia, rollback, errores y eventos resultantes.

---

## 11. Consulta de cuenta administrativa

### 11.1 Endpoint

```http
GET /api/v1/accounts/{publicAccountId}
```

### 11.2 Autorización

Requiere `access_token` y permiso de consulta de la cuenta. El propietario podrá consultar su cuenta. Un administrador delegado solo podrá hacerlo si cuenta con el permiso explícito correspondiente.

La respuesta no incluirá credenciales, tokens, secretos ni datos privados administrados por `Identity`.

### 11.3 Parámetros

| Parámetro | Ubicación | Requerido | Descripción |
|---|---|---:|---|
| `publicAccountId` | path | sí | Identificador público de la cuenta |
| `fields` | query | no | Campos permitidos para la vista solicitada |

### 11.4 Respuesta

```json
{
  "publicAccountId": "acc_01J...",
  "name": "Grupo Pérez",
  "status": "ACTIVE",
  "ownerUserId": "usr_01J...",
  "timeZone": "America/Mexico_City",
  "locale": "es-MX",
  "createdAt": "2026-08-26T15:30:00Z",
  "updatedAt": "2026-08-26T15:30:00Z",
  "version": 3,
  "correlationId": "cor_01J..."
}
```

Headers de respuesta:

```http
ETag: "account-version-3"
Cache-Control: private, max-age=60
```

Si el cliente envía un `If-None-Match` vigente, el servidor podrá responder `304 Not Modified`.

## 12. Actualización de cuenta administrativa

### 12.1 Endpoint

```http
PATCH /api/v1/accounts/{publicAccountId}
```

La operación actualizará únicamente datos propios de la cuenta. No modificará credenciales, correo verificado, identidad legal del usuario ni datos de empresas.

### 12.2 Campos permitidos

```json
{
  "name": "Grupo Pérez",
  "timeZone": "America/Mexico_City",
  "locale": "es-MX",
  "contactPreferences": {
    "email": true,
    "webSocket": true
  }
}
```

Los campos fiscales y los datos de empresa no se actualizarán mediante este endpoint. Tendrán contratos separados y permisos específicos.

### 12.3 Headers requeridos

```http
Content-Type: application/json
If-Match: "account-version-3"
X-Correlation-Id: {correlation-id}
```

`If-Match` será obligatorio para impedir que un cambio sobrescriba una actualización concurrente.

### 12.4 Respuesta exitosa

```http
200 OK
ETag: "account-version-4"
```

```json
{
  "publicAccountId": "acc_01J...",
  "name": "Grupo Pérez",
  "status": "ACTIVE",
  "timeZone": "America/Mexico_City",
  "locale": "es-MX",
  "version": 4,
  "updatedAt": "2026-08-26T16:00:00Z",
  "correlationId": "cor_01J..."
}
```

### 12.5 Errores específicos

- `401 Unauthorized`: token ausente, inválido o expirado.
- `403 Forbidden`: el usuario no tiene permiso sobre la cuenta.
- `404 Not Found`: la cuenta no pertenece al alcance visible del usuario.
- `409 Conflict`: conflicto de versión o cambio incompatible.
- `422 Unprocessable Content`: zona horaria, idioma o preferencia no soportados.

## 13. Historial de cuenta

```http
GET /api/v1/accounts/{publicAccountId}/history?limit=25&after={cursor}
```

La consulta devolverá cambios de configuración y acciones administrativas, no credenciales ni secretos. El acceso al historial será independiente del permiso de consulta básica.

Ejemplo de elemento:

```json
{
  "items": [
    {
      "auditRecordId": "aud_01J...",
      "action": "ACCOUNT_UPDATED",
      "actorUserId": "usr_01J...",
      "changedFields": ["timeZone"],
      "occurredAt": "2026-08-26T16:00:00Z"
    }
  ],
  "page": {
    "nextCursor": "cursor_..."
  },
  "meta": {
    "count": 1
  }
}
```

## 14. Eventos resultantes

La actualización exitosa podrá publicar:

- `account.updated`;
- `account.preferences.updated`;
- `account.audit-record.created`.

Los eventos se publicarán después de confirmar la transacción mediante outbox. WebSocket y webhook recibirán proyecciones autorizadas; no se enviarán secretos ni el recurso completo por defecto.

---

## 15. Listado de empresas de una cuenta

### 15.1 Endpoint

```http
GET /api/v1/accounts/{publicAccountId}/companies
```

El listado devolverá únicamente empresas dentro del alcance autorizado del usuario. Ser miembro de una cuenta administrativa no implica visualizar automáticamente todas sus empresas.

### 15.2 Parámetros

| Parámetro | Ubicación | Requerido | Descripción |
|---|---|---:|---|
| `publicAccountId` | path | sí | Identificador público de la cuenta |
| `limit` | query | no | Cantidad máxima; valor predeterminado 25 |
| `after` | query | no | Cursor de continuación |
| `status` | query | no | Estado de empresa permitido |
| `sort` | query | no | Campo de ordenamiento permitido, por defecto `name` |
| `direction` | query | no | `asc` o `desc` |
| `fields` | query | no | Selección limitada de campos |

Los filtros avanzados podrán incorporarse posteriormente mediante operadores validados por el endpoint. No se permitirá ordenar ni filtrar por campos no autorizados.

### 15.3 Respuesta

```http
200 OK
Cache-Control: private, max-age=60
```

```json
{
  "items": [
    {
      "publicCompanyId": "com_01J...",
      "legalName": "Comercializadora Pérez, S.A. de C.V.",
      "tradeName": "Comercializadora Pérez",
      "status": "ACTIVE",
      "visibleBranchCount": 2,
      "userAccess": "OWNER"
    }
  ],
  "page": {
    "limit": 25,
    "nextCursor": "cursor_..."
  },
  "meta": {
    "count": 1
  },
  "correlationId": "cor_01J..."
}
```

La respuesta podrá utilizar `ETag`. La cantidad total de empresas no se calculará por defecto si implica una consulta costosa; podrá solicitarse mediante una capacidad específica posteriormente.

## 16. Creación de una empresa desde una cuenta

### 16.1 Endpoint

```http
POST /api/v1/accounts/{publicAccountId}/companies
```

La operación creará la empresa y su sucursal matriz inicial. No creará sucursales adicionales ni membresías de empleados.

### 16.2 Headers requeridos

```http
Content-Type: application/json
Authorization: Bearer {accessToken}
Idempotency-Key: {unique-key}
X-Correlation-Id: {correlation-id}
```

El `Idempotency-Key` será obligatorio. El usuario deberá tener permiso para crear empresas dentro de la cuenta.

### 16.3 Solicitud

```json
{
  "company": {
    "legalName": "Servicios del Centro, S.A. de C.V.",
    "tradeName": "Servicios del Centro",
    "countryCode": "MX",
    "taxProfile": {
      "rfc": "ABC010101AB1"
    }
  },
  "headOffice": {
    "name": "Matriz Centro",
    "countryCode": "MX",
    "timeZone": "America/Mexico_City"
  }
}
```

### 16.4 Reglas de negocio

- La cuenta debe estar activa.
- El actor debe tener permiso para crear empresas.
- La empresa se crea en estado `CONFIGURING`.
- La sucursal matriz se crea en estado `CONFIGURING`.
- El propietario de la cuenta recibirá membresía inicial sobre la nueva empresa.
- No se asignarán empleados automáticamente.
- La capacidad de sucursales se evaluará al activar la matriz, no al crearla.
- La empresa no podrá pertenecer simultáneamente a otra cuenta.
- Los datos fiscales se validarán mediante el contrato tributario correspondiente cuando aplique.

### 16.5 Respuesta exitosa

#### 201 Created

```json
{
  "publicCompanyId": "com_01J...",
  "companyStatus": "CONFIGURING",
  "headOffice": {
    "publicBranchId": "brn_01J...",
    "status": "CONFIGURING",
    "isHeadOffice": true
  },
  "ownerMembership": {
    "membershipId": "mem_01J...",
    "status": "ACTIVE"
  },
  "nextActions": [
    "COMPLETE_COMPANY_PROFILE",
    "COMPLETE_HEAD_OFFICE_PROFILE",
    "ACTIVATE_COMPANY"
  ],
  "correlationId": "cor_01J..."
}
```

### 16.6 Errores específicos

- `401 Unauthorized`: token ausente, inválido o expirado.
- `403 Forbidden`: el usuario no tiene permiso para crear empresas.
- `404 Not Found`: la cuenta no pertenece al alcance del usuario.
- `409 Conflict`: RFC ya asociado, clave idempotente incompatible o empresa duplicada según reglas de negocio.
- `422 Unprocessable Content`: datos fiscales, nombre o zona horaria inválidos.
- `503 Service Unavailable`: dependencia tributaria o de identidad no disponible cuando sea obligatoria.

### 16.7 Consistencia y eventos

La creación será transaccional dentro de `Company Management`. Si falla la empresa, la matriz o la membresía inicial, se revertirán los recursos propios creados.

Después de confirmar la transacción se publicarán, mediante outbox:

- `company.created`;
- `branch.created`;
- `membership.created`.

Los consumidores recibirán únicamente sus contratos de evento y no accederán directamente a las tablas del módulo.

## 17. Consulta de una empresa

```http
GET /api/v1/companies/{publicCompanyId}
```

La consulta devolverá la vista de empresa autorizada para el usuario, con `fields` opcional, `ETag` y caché privada. Los datos fiscales, capacidades de Billing, membresías y auditoría se expondrán mediante permisos y contratos específicos; no se incluirán automáticamente en la vista básica.

---

## 18. Actualización de empresa

La actualización se dividirá por categorías para aplicar permisos específicos y evitar que una operación general modifique datos sensibles accidentalmente.

### 18.1 Datos básicos

```http
PATCH /api/v1/companies/{publicCompanyId}/profile
```

Solicitud:

```json
{
  "legalName": "Servicios del Centro, S.A. de C.V.",
  "tradeName": "Servicios del Centro"
}
```

### 18.2 Datos fiscales

```http
PATCH /api/v1/companies/{publicCompanyId}/tax-profile
```

Requiere permiso fiscal específico. La información será validada por el contrato del módulo tributario y cualquier cambio relevante quedará auditado.

### 18.3 Contacto

```http
PATCH /api/v1/companies/{publicCompanyId}/contact
```

### 18.4 Configuración operativa

```http
PATCH /api/v1/companies/{publicCompanyId}/operational-settings
```

### 18.5 Zona horaria y preferencias

```http
PATCH /api/v1/companies/{publicCompanyId}/localization
PATCH /api/v1/companies/{publicCompanyId}/preferences
```

Todos los endpoints de actualización requieren `If-Match` y devolverán un nuevo `ETag`. Un cambio concurrente producirá `409 Conflict`.

## 19. Finalización de configuración

### 19.1 Endpoint

```http
POST /api/v1/companies/{publicCompanyId}/configuration-completion
```

La operación validará los datos mínimos de empresa y de la sucursal matriz. No activará automáticamente la empresa si existe una condición pendiente de suscripción, validación tributaria o dependencia externa.

### 19.2 Respuesta exitosa

```json
{
  "publicCompanyId": "com_01J...",
  "status": "READY_FOR_ACTIVATION",
  "completedSections": [
    "PROFILE",
    "TAX_PROFILE",
    "HEAD_OFFICE"
  ],
  "pendingSections": [],
  "correlationId": "cor_01J..."
}
```

Si faltan datos:

```http
422 Unprocessable Content
```

El detalle incluirá las secciones pendientes sin revelar información sensible.

## 20. Activación de empresa

### 20.1 Endpoint

```http
POST /api/v1/companies/{publicCompanyId}/activation
```

### 20.2 Validaciones

- La empresa debe estar en `CONFIGURING` o `READY_FOR_ACTIVATION`.
- La información obligatoria debe estar completa.
- La sucursal matriz debe estar configurada.
- La empresa debe tener cobertura válida de suscripción.
- Las dependencias requeridas deben estar disponibles o confirmadas.
- El usuario debe tener permiso de activación.

### 20.3 Respuesta

```json
{
  "publicCompanyId": "com_01J...",
  "status": "ACTIVE",
  "activatedAt": "2026-08-26T18:00:00Z",
  "correlationId": "cor_01J..."
}
```

La activación publicará `company.activated` después de confirmar la transacción.

## 21. Suspensión de empresa

### 21.1 Endpoint

```http
POST /api/v1/companies/{publicCompanyId}/suspension
```

Solicitud:

```json
{
  "reasonCode": "OWNER_REQUEST",
  "confirmation": true,
  "expectedImpactAccepted": true
}
```

La suspensión requiere permiso administrativo y confirmación reforzada. La API deberá informar el impacto esperado sobre sucursales, usuarios y operaciones.

Una empresa suspendida no podrá iniciar nuevas operaciones de negocio. Se conservarán las capacidades de consulta, descarga y regularización permitidas por las reglas del producto.

La suspensión publicará `company.suspended`.

## 22. Reactivación de empresa

### 22.1 Endpoint

```http
POST /api/v1/companies/{publicCompanyId}/reactivation
```

### 22.2 Validaciones

- La empresa debe estar suspendida.
- La suscripción debe cubrir la empresa y sus sucursales operativas.
- No deben existir bloqueos pendientes de Billing o del módulo tributario.
- El usuario debe tener permiso de reactivación.
- Los datos obligatorios deben continuar vigentes.

La respuesta devolverá `ACTIVE` si todas las validaciones son satisfactorias. Si existe una condición pendiente, devolverá `409 Conflict` o `422 Unprocessable Content` según la naturaleza del problema.

La reactivación publicará `company.reactivated`.

## 23. Solicitud de baja lógica

### 23.1 Endpoint

```http
POST /api/v1/companies/{publicCompanyId}/deletion-requests
```

La solicitud será asíncrona y devolverá `operationId`. No se eliminarán físicamente los datos de la empresa. La operación validará:

- permiso del solicitante;
- operaciones pendientes;
- suscripciones y obligaciones activas;
- dependencias con otros módulos;
- existencia de otras sucursales;
- requisitos de auditoría y conservación.

La baja podrá quedar en `REQUESTED`, `BLOCKED` o `COMPLETED`. Mientras se valida, la empresa conservará su estado actual.

## 24. Eventos del ciclo de vida

| Evento | Momento |
|---|---|
| `company.updated` | Cambio de datos no fiscales |
| `company.tax-profile.updated` | Cambio fiscal autorizado |
| `company.configuration.completed` | Datos mínimos completados |
| `company.activated` | Activación confirmada |
| `company.suspended` | Suspensión confirmada |
| `company.reactivated` | Reactivación confirmada |
| `company.deletion.requested` | Solicitud de baja creada |
| `company.deleted` | Baja lógica completada |

Todos los eventos incluirán `eventId`, `eventType`, `eventVersion`, `occurredAt`, `companyId`, actor y `correlationId`, sin exponer el recurso completo por defecto.

---

## 25. Listado de sucursales

### 25.1 Endpoint

```http
GET /api/v1/companies/{publicCompanyId}/branches
```

El resultado mostrará únicamente sucursales dentro del alcance del usuario. Un propietario puede consultar todas las sucursales de sus empresas; otros usuarios requieren asignación o permiso de consulta explícito.

### 25.2 Parámetros

| Parámetro | Ubicación | Requerido | Descripción |
|---|---|---:|---|
| `publicCompanyId` | path | sí | Empresa propietaria |
| `limit` | query | no | Cantidad máxima, predeterminada en 25 |
| `after` | query | no | Cursor de continuación |
| `status` | query | no | Estado permitido |
| `isHeadOffice` | query | no | Filtrar sucursal matriz |
| `sort` | query | no | Campo autorizado, por defecto `name` |
| `direction` | query | no | `asc` o `desc` |
| `fields` | query | no | Selección limitada de campos |

### 25.3 Respuesta

```json
{
  "items": [
    {
      "publicBranchId": "brn_01J...",
      "name": "Matriz Centro",
      "status": "ACTIVE",
      "isHeadOffice": true,
      "coverage": "PLAN_INCLUDED",
      "operable": true
    }
  ],
  "page": {
    "limit": 25,
    "nextCursor": "cursor_..."
  },
  "meta": {
    "count": 1
  },
  "correlationId": "cor_01J..."
}
```

## 26. Creación de sucursal

### 26.1 Endpoint

```http
POST /api/v1/companies/{publicCompanyId}/branches
```

La creación será síncrona para los datos propios del módulo. Podrá devolver `PENDING_PLAN` cuando la sucursal no esté cubierta por las sucursales incluidas o por un complemento vigente.

### 26.2 Solicitud

```json
{
  "name": "Sucursal Norte",
  "code": "NORTE-01",
  "address": {
    "street": "Av. Central 100",
    "city": "Monterrey",
    "state": "NL",
    "postalCode": "64000",
    "countryCode": "MX"
  },
  "timeZone": "America/Monterrey",
  "coverage": {
    "requested": "ADD_ON"
  }
}
```

### 26.3 Reglas

- La empresa debe estar activa o en configuración permitida.
- El actor debe tener permiso para crear sucursales.
- La sucursal pertenece a una sola empresa.
- `code` debe ser único dentro de la empresa.
- No se podrá crear otra matriz si ya existe una matriz activa.
- La sucursal podrá quedar en `PENDING_PLAN` si no existe cobertura inmediata.
- La cobertura podrá ser `PLAN_INCLUDED` o `ADD_ON`.
- La sucursal no podrá operar hasta estar configurada, activa y cubierta.
- No se asignarán empleados automáticamente.

### 26.4 Respuesta

#### 201 Created

```json
{
  "publicBranchId": "brn_01J...",
  "publicCompanyId": "com_01J...",
  "status": "PENDING_PLAN",
  "coverage": "AWAITING_BILLING",
  "operable": false,
  "nextActions": [
    "COMPLETE_BRANCH_PROFILE",
    "CONTRACT_BRANCH_COVERAGE",
    "ACTIVATE_BRANCH"
  ],
  "correlationId": "cor_01J..."
}
```

Si la sucursal queda cubierta desde su creación, su estado inicial será `CONFIGURING` y su activación seguirá requiriendo los datos obligatorios.

## 27. Consulta y actualización de sucursal

```http
GET /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}
PATCH /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}
```

La consulta devolverá una vista básica con `ETag` y caché privada. La actualización requerirá `If-Match` y permiso sobre la sucursal.

Los campos se separarán por categorías:

```http
PATCH /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/profile
PATCH /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/contact
PATCH /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/localization
PATCH /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/operational-settings
```

Los cambios de dirección, zona horaria, código e información operativa quedarán auditados según su sensibilidad.

## 28. Finalización y activación

### 28.1 Finalización

```http
POST /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/configuration-completion
```

Valida datos obligatorios y devuelve `READY_FOR_ACTIVATION` cuando la sucursal puede activarse.

### 28.2 Activación

```http
POST /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/activation
```

Validaciones mínimas:

- configuración completa;
- cobertura de plan o complemento confirmada por `Billing`;
- empresa habilitada;
- permisos del actor;
- ausencia de bloqueo operativo.

Si no existe cobertura, la respuesta será `409 Conflict` con código `BRANCH_COVERAGE_REQUIRED`.

## 29. Suspensión y reactivación

```http
POST /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/suspension
POST /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/reactivation
```

La suspensión requiere permiso administrativo y confirmación reforzada. Una sucursal suspendida no podrá recibir nuevas operaciones de negocio, pero conservará las consultas y descargas permitidas.

La reactivación validará empresa, cobertura, configuración, permisos y bloqueos provenientes de `Billing`. Las operaciones publicarán `branch.suspended` y `branch.reactivated`.

## 30. Designación de sucursal matriz

### 30.1 Endpoint

```http
POST /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/head-office-designation
```

La sucursal indicada se convertirá en matriz mediante una operación transaccional. Solo podrá ejecutarse si:

- pertenece a la empresa;
- está configurada y activa;
- el actor tiene permiso administrativo;
- se identifica la matriz actual, si existe;
- no se deja más de una matriz activa.

La matriz anterior pasará a ser sucursal ordinaria sin perder su historial. El cambio publicará `branch.head-office.changed`.

## 31. Solicitud de baja lógica

```http
POST /api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/deletion-requests
```

La solicitud será asíncrona y devolverá `operationId`. Se validarán operaciones pendientes, asignaciones activas, dependencias de módulos, cobertura de Billing y reglas de conservación.

No se podrá eliminar la única matriz sin designar previamente una nueva. La baja será lógica y auditable.

## 32. Eventos de sucursal

| Evento | Momento |
|---|---|
| `branch.created` | Sucursal creada |
| `branch.updated` | Datos actualizados |
| `branch.configuration.completed` | Configuración completa |
| `branch.activated` | Activación confirmada |
| `branch.suspended` | Suspensión confirmada |
| `branch.reactivated` | Reactivación confirmada |
| `branch.coverage.required` | Falta cobertura de plan o complemento |
| `branch.coverage.confirmed` | Billing confirma cobertura |
| `branch.head-office.changed` | Cambia la matriz |
| `branch.deletion.requested` | Se solicita baja |
| `branch.deleted` | Baja lógica completada |

Los eventos incluirán identificadores, versión, actor, causa y correlación. No permitirán a otro módulo modificar directamente la sucursal.

---

## 33. Membresías de empresa

Una membresía representa la relación de un usuario con una empresa. Cada usuario tendrá como máximo una membresía por empresa. Las sucursales y roles se administrarán mediante asignaciones asociadas a esa membresía.

### 33.1 Listado

```http
GET /api/v1/companies/{publicCompanyId}/memberships
```

Parámetros:

| Parámetro | Ubicación | Descripción |
|---|---|---|
| `limit` | query | Cantidad máxima, predeterminada en 25 |
| `after` | query | Cursor de continuación |
| `status` | query | Estado de la membresía |
| `branchId` | query | Filtrar por asignación de sucursal |
| `roleId` | query | Filtrar por rol |
| `fields` | query | Campos permitidos |

La API nunca devolverá credenciales ni información privada administrada por `Identity`.

### 33.2 Consulta individual

```http
GET /api/v1/companies/{publicCompanyId}/memberships/{membershipId}
```

La respuesta incluirá usuario, estado, roles, asignaciones autorizadas y fechas relevantes. Las asignaciones se devolverán resumidas; el detalle podrá consultarse mediante el endpoint específico.

## 34. Invitaciones

### 34.1 Crear invitación

```http
POST /api/v1/companies/{publicCompanyId}/memberships/invitations
```

Solicitud:

```json
{
  "identityReference": "usr_01J...",
  "email": "empleado@empresa.com",
  "branchIds": ["brn_01J..."],
  "roleIds": ["role_01J..."],
  "permissions": [
    "INVENTORY_READ",
    "SALES_CREATE"
  ],
  "expiresInDays": 7
}
```

La invitación podrá dirigirse a una identidad existente o a un correo que posteriormente será vinculado por `Identity`. El módulo no creará credenciales directamente.

Validaciones:

- el actor puede invitar usuarios;
- la empresa está dentro de su alcance;
- las sucursales pertenecen a la empresa;
- los roles y permisos solicitados pueden ser delegados por el actor;
- no existe una membresía activa incompatible;
- el límite de usuarios del plan no se excede, si la política de Billing lo requiere;
- el token será de un solo uso y tendrá expiración.

### 34.2 Aceptar invitación

```http
POST /api/v1/membership-invitations/{invitationToken}/acceptance
```

La aceptación requerirá identidad autenticada o verificada. Al completarse, la invitación pasará a `ACCEPTED`, se creará o activará la membresía y el token quedará inutilizable.

### 34.3 Administrar invitación

```http
POST /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/resend
POST /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/invitation-revocation
```

El reenvío será controlado para evitar spam. La revocación invalidará el token pendiente y quedará auditada.

## 35. Actualización de membresía

```http
PATCH /api/v1/companies/{publicCompanyId}/memberships/{membershipId}
```

Solicitud:

```json
{
  "roleIds": ["role_01J...", "role_02J..."],
  "status": "ACTIVE"
}
```

La actualización no modificará la identidad del usuario. Los cambios de roles requerirán permisos separados y recalcularán los permisos efectivos.

## 36. Suspensión, reactivación y revocación

```http
POST /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/suspension
POST /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/reactivation
POST /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/revocation
```

La suspensión será reversible. La revocación será definitiva para el acceso, pero conservará la membresía, asignaciones e historial.

Al suspender o revocar:

- se invalidarán sesiones y contextos operativos relacionados;
- se cerrarán o revalidarán canales WebSocket afectados;
- se bloquearán nuevas operaciones en la empresa;
- se conservará la trazabilidad.

La reactivación validará membresía, empresa, permisos, límite de usuarios y estado de suscripción.

## 37. Asignaciones de sucursal

### 37.1 Listar asignaciones

```http
GET /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments
```

### 37.2 Crear asignación

```http
POST /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments
```

Solicitud:

```json
{
  "branchId": "brn_01J...",
  "roleIds": ["role_01J..."],
  "permissions": [
    "INVENTORY_READ",
    "SALES_CREATE"
  ],
  "status": "ACTIVE"
}
```

Un usuario podrá tener múltiples asignaciones, roles y permisos diferenciados por sucursal. La asignación no concede acceso a empresas distintas.

### 37.3 Actualizar o retirar asignación

```http
PATCH /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments/{assignmentId}
DELETE /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments/{assignmentId}
```

La eliminación será lógica. Antes de retirar la última asignación operativa se validarán operaciones pendientes y sesiones activas.

## 38. Contexto operativo

### 38.1 Consultar contextos disponibles

```http
GET /api/v1/companies/{publicCompanyId}/memberships/me/operational-contexts
```

### 38.2 Seleccionar sucursal activa

```http
POST /api/v1/companies/{publicCompanyId}/memberships/me/operational-context
```

Solicitud:

```json
{
  "branchId": "brn_01J..."
}
```

Respuesta:

```json
{
  "contextId": "ctx_01J...",
  "companyId": "com_01J...",
  "branchId": "brn_01J...",
  "expiresAt": "2026-08-26T22:00:00Z",
  "permissionsHash": "sha256:...",
  "correlationId": "cor_01J..."
}
```

El contexto no sustituye al token de autenticación. Solo establece la sucursal operativa para las solicitudes posteriores. Cada operación deberá volver a validar permisos, estado y alcance.

### 38.3 Consultar contexto activo

```http
GET /api/v1/companies/{publicCompanyId}/memberships/me/operational-context
```

Si cambia la asignación, membresía o rol, el contexto quedará invalidado o requerirá revalidación.

## 39. Eventos de membresía y asignación

| Evento | Momento |
|---|---|
| `membership.invitation.created` | Invitación creada |
| `membership.invitation.accepted` | Invitación aceptada |
| `membership.invitation.revoked` | Invitación revocada |
| `membership.created` | Membresía creada |
| `membership.updated` | Membresía modificada |
| `membership.suspended` | Membresía suspendida |
| `membership.reactivated` | Membresía reactivada |
| `membership.revoked` | Acceso revocado |
| `branch-assignment.created` | Asignación creada |
| `branch-assignment.updated` | Asignación modificada |
| `branch-assignment.revoked` | Asignación retirada |
| `operational-context.changed` | Cambia sucursal activa |

Los eventos no incluirán credenciales. Los cambios de permisos y membresías invalidarán o actualizarán los contextos WebSocket correspondientes.

---

## 40. Roles y permisos personalizados

Los roles personalizados pertenecen a una empresa específica. No pueden compartirse automáticamente entre empresas, aunque posteriormente se podrá ofrecer una función explícita de duplicación o plantilla.

Los permisos se expresan mediante:

```text
módulo + acción + alcance
```

Ejemplos:

```text
inventory:read:branch
sales:create:branch
memberships:manage:company
audit:read:company
```

Los roles predefinidos por JComercy tendrán permisos administrados por la plataforma. Los roles personalizados solo podrán asignar permisos que el actor pueda delegar.

## 41. Catálogo de permisos

### 41.1 Endpoint

```http
GET /api/v1/companies/{publicCompanyId}/permissions
```

La respuesta devolverá el catálogo de permisos disponibles para la empresa y el plan activo, incluyendo módulo, acción, alcance y si el permiso es delegable.

Ejemplo:

```json
{
  "items": [
    {
      "permissionCode": "memberships:manage:branch",
      "module": "company_management",
      "action": "MANAGE",
      "scope": "BRANCH",
      "delegable": true
    },
    {
      "permissionCode": "audit:read:company",
      "module": "company_management",
      "action": "READ",
      "scope": "COMPANY",
      "delegable": false
    }
  ],
  "correlationId": "cor_01J..."
}
```

El catálogo no incluirá permisos de módulos que la empresa no tenga habilitados. La consulta estará sujeta a autorización y no expondrá permisos internos no delegables.

## 42. Roles

### 42.1 Listar roles

```http
GET /api/v1/companies/{publicCompanyId}/roles
```

Parámetros:

- `type`: `SYSTEM` o `CUSTOM`;
- `status`: `ACTIVE` o `INACTIVE`;
- `limit` y `after`;
- `fields`.

### 42.2 Crear rol personalizado

```http
POST /api/v1/companies/{publicCompanyId}/roles
```

Solicitud:

```json
{
  "name": "Supervisor de sucursal",
  "description": "Puede operar ventas e inventario en sucursales asignadas.",
  "permissions": [
    {
      "permissionCode": "inventory:read:branch",
      "scope": {
        "branchIds": ["brn_01J..."]
      }
    },
    {
      "permissionCode": "sales:create:branch",
      "scope": {
        "branchIds": ["brn_01J..."]
      }
    }
  ]
}
```

Reglas:

- `name` debe ser único dentro de la empresa.
- El rol debe indicar si es `SYSTEM` o `CUSTOM`; el cliente no puede crear roles de sistema.
- Los permisos deben existir y ser delegables.
- El alcance no puede superar el alcance del actor.
- Un rol personalizado no puede otorgar acceso a otra empresa.
- La creación requiere permiso `roles:create:company` o equivalente.

Respuesta:

```http
201 Created
```

```json
{
  "roleId": "role_01J...",
  "type": "CUSTOM",
  "name": "Supervisor de sucursal",
  "status": "ACTIVE",
  "version": 1,
  "correlationId": "cor_01J..."
}
```

### 42.3 Consulta individual

```http
GET /api/v1/companies/{publicCompanyId}/roles/{roleId}
```

La respuesta incluirá los permisos y alcances visibles para el usuario. Los permisos no delegables solo se mostrarán a actores autorizados.

### 42.4 Actualización

```http
PATCH /api/v1/companies/{publicCompanyId}/roles/{roleId}
```

Solicitud:

```json
{
  "name": "Supervisor regional",
  "description": "Supervisión operativa autorizada.",
  "permissions": [
    {
      "permissionCode": "inventory:read:branch",
      "scope": {
        "branchIds": ["brn_01J...", "brn_02J..."]
      }
    }
  ]
}
```

Requiere `If-Match`. La actualización recalculará permisos efectivos y podrá invalidar contextos operativos afectados.

## 43. Desactivación y eliminación lógica

```http
POST /api/v1/companies/{publicCompanyId}/roles/{roleId}/deactivation
DELETE /api/v1/companies/{publicCompanyId}/roles/{roleId}
```

La desactivación impedirá nuevas asignaciones. El rol continuará visible en el historial y en membresías históricas.

La eliminación será lógica y no estará permitida si:

- es un rol de sistema;
- tiene usuarios activos asignados sin alternativa;
- es el último rol administrativo válido;
- existen operaciones pendientes que dependan de él.

La eliminación requerirá confirmación reforzada y permiso específico.

## 44. Asignación de roles a membresías

La asignación se administrará mediante la membresía o asignación de sucursal:

```http
PATCH /api/v1/companies/{publicCompanyId}/memberships/{membershipId}
PATCH /api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments/{assignmentId}
```

El actor solo podrá asignar roles dentro de su propio alcance. Toda asignación validará empresa, sucursal, estado del rol y permisos delegables.

## 45. Permisos efectivos

### 45.1 Endpoint

```http
GET /api/v1/companies/{publicCompanyId}/effective-permissions/{userId}
```

La consulta requiere permiso para revisar accesos de otros usuarios. El propio usuario podrá consultar sus permisos efectivos mediante:

```http
GET /api/v1/companies/{publicCompanyId}/memberships/me/effective-permissions
```

Respuesta:

```json
{
  "userId": "usr_01J...",
  "companyId": "com_01J...",
  "permissions": [
    {
      "permissionCode": "sales:create:branch",
      "branchIds": ["brn_01J..."]
    }
  ],
  "calculatedAt": "2026-08-26T19:00:00Z",
  "permissionsVersion": 12,
  "correlationId": "cor_01J..."
}
```

Los permisos efectivos combinan roles y asignaciones aplicables, pero siempre respetan el menor privilegio, el alcance de sucursal y las denegaciones explícitas. Si existe una denegación explícita, prevalecerá sobre una concesión heredada.

## 46. Auditoría y eventos

Se auditarán la creación, modificación, asignación, desactivación y eliminación lógica de roles, así como cambios de permisos efectivos.

Eventos:

- `role.created`;
- `role.updated`;
- `role.deactivated`;
- `role.deleted`;
- `role.assigned`;
- `role.unassigned`;
- `effective-permissions.changed`.

Los eventos provocarán revalidación de sesiones y canales afectados. No incluirán credenciales ni secretos.

---

## 47. Operaciones asíncronas

Las operaciones prolongadas devolverán inmediatamente un `operationId`. El estado persistido será la fuente oficial; WebSocket notificará cambios cuando exista una conexión autorizada.

### 47.1 Consulta de operación

```http
GET /api/v1/operations/{operationId}
```

Respuesta:

```json
{
  "operationId": "op_01J...",
  "type": "COMPANY_EXPORT",
  "status": "PROCESSING",
  "progress": {
    "processed": 450,
    "total": 1000,
    "percentage": 45
  },
  "companyId": "com_01J...",
  "initiatedBy": "usr_01J...",
  "parentOperationId": null,
  "createdAt": "2026-08-26T19:00:00Z",
  "expiresAt": "2026-08-26T20:00:00Z",
  "correlationId": "cor_01J..."
}
```

Estados permitidos: `PENDING`, `PROCESSING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED` y `EXPIRED`.

El acceso al detalle dependerá de la empresa, sucursal y permisos del usuario. El iniciador podrá consultar sus operaciones; usuarios autorizados podrán consultar las de su sucursal; propietarios y administradores autorizados, las de la empresa.

### 47.2 Historial y errores

```http
GET /api/v1/operations/{operationId}/history
GET /api/v1/operations/{operationId}/errors?limit=25&after={cursor}
```

El historial mostrará la cadena de operación original, reintentos, cancelaciones, recuperación y operaciones derivadas. Los errores incluirán registro afectado, código, detalle seguro y posibilidad de reintento cuando aplique.

### 47.3 Cancelación

```http
POST /api/v1/operations/{operationId}/cancellation
```

La cancelación solo estará disponible en `PENDING` y, cuando el proceso sea detenible de forma segura, en `PROCESSING`. Requiere permiso para cancelar y podrá solicitar confirmación reforzada.

### 47.4 Reintento y recuperación

```http
POST /api/v1/operations/{operationId}/retry
POST /api/v1/operations/{operationId}/recovery
```

Ambas operaciones serán asíncronas y generarán un nuevo `operationId`. El nuevo registro conservará la relación con la operación origen. Cuando sea seguro, reutilizará resultados válidos y reprocesará únicamente registros fallidos o pendientes.

## 48. Importaciones

### 48.1 Validación previa

```http
POST /api/v1/companies/{publicCompanyId}/imports/validation
```

La solicitud se realizará como `multipart/form-data` con CSV o XLSX. La validación no modificará información del negocio.

Respuesta para un archivo pequeño:

```json
{
  "validationId": "val_01J...",
  "status": "VALIDATED",
  "totalRows": 100,
  "validRows": 98,
  "invalidRows": 2,
  "errorReportFileId": "file_01J...",
  "correlationId": "cor_01J..."
}
```

Para archivos grandes se devolverá `operationId` y la validación será asíncrona.

### 48.2 Aplicación de importación

```http
POST /api/v1/companies/{publicCompanyId}/imports
```

Solicitud:

```json
{
  "validationId": "val_01J...",
  "mode": "PARTIAL",
  "confirmation": true
}
```

Los modos permitidos son:

- `TRANSACTIONAL`: todos los registros o ninguno;
- `PARTIAL`: aplicar válidos y rechazar inválidos.

La operación validará nuevamente autorización, vigencia de la validación, empresa, sucursal y permisos antes de aplicar cambios.

## 49. Exportaciones

### 49.1 Crear exportación

```http
POST /api/v1/companies/{publicCompanyId}/exports
```

Solicitud:

```json
{
  "resource": "MEMBERSHIPS",
  "format": "XLSX",
  "filters": {
    "status": "ACTIVE"
  },
  "fields": [
    "membershipId",
    "status",
    "branchIds"
  ]
}
```

No existirá un límite funcional fijo de registros, pero se aplicarán límites técnicos de tiempo, concurrencia, consumo y seguridad. Las exportaciones grandes serán asíncronas.

### 49.2 Resultado y descarga

```http
GET /api/v1/operations/{operationId}/result
GET /api/v1/operations/{operationId}/result/download
```

Consultar el estado y descargar el archivo serán permisos independientes. La descarga devolverá una URL privada firmada de un solo uso, con expiración y validación de permisos.

## 50. Archivos temporales

```http
GET /api/v1/companies/{publicCompanyId}/files/{fileId}
GET /api/v1/companies/{publicCompanyId}/files/{fileId}/download
DELETE /api/v1/companies/{publicCompanyId}/files/{fileId}
```

Los archivos se almacenarán de forma privada. Las exportaciones operativas se conservarán 7 días y los reportes críticos 30 días. La eliminación anticipada requerirá permiso y quedará auditada.

Los archivos de entrada se validarán por extensión, MIME, tamaño, estructura, encabezados, fórmulas peligrosas, datos sensibles y malware antes de procesarse.

## 51. Auditoría

### 51.1 Consulta

```http
GET /api/v1/companies/{publicCompanyId}/audit-records?limit=25&after={cursor}
```

Filtros permitidos:

- `resourceType`;
- `resourceId`;
- `actorUserId`;
- `action`;
- `branchId`;
- `from` y `to`;
- `result`.

La consulta requerirá permiso de auditoría y se limitará al alcance del actor.

### 51.2 Registro individual

```http
GET /api/v1/companies/{publicCompanyId}/audit-records/{auditRecordId}
```

La respuesta podrá incluir valores anteriores y posteriores únicamente para cambios sensibles y cuando el actor tenga permiso. Contraseñas, tokens, secretos y datos prohibidos nunca serán devueltos.

### 51.3 Exportación de auditoría

```http
POST /api/v1/companies/{publicCompanyId}/audit-exports
```

Será asíncrona, requerirá permiso independiente y respetará el mismo control de archivos, URLs firmadas, retención y auditoría.

## 52. Eventos y WebSockets

Las operaciones asíncronas publicarán cambios mediante eventos del bus y proyecciones autorizadas por WebSocket. Ejemplos:

- `operation.created`;
- `operation.progressed`;
- `operation.completed`;
- `operation.partially-completed`;
- `operation.failed`;
- `operation.cancelled`;
- `operation.expired`.

Los eventos críticos usarán `notificationId`, persistencia temporal, confirmación y reintentos controlados. La retención será de 30 días para críticos, 7 días para operativos y 24 horas para informativos.

---

## 53. WebSockets

### 53.1 Conexión

```text
WSS /ws/v1
```

La conexión utilizará el `access_token` JWT. La autenticación se validará al conectarse y la autorización se volverá a comprobar en cada suscripción.

### 53.2 Suscripción a canales

Canales iniciales:

```text
account.{accountId}
company.{companyId}
company.{companyId}.branch.{branchId}
company.{companyId}.operations
company.{companyId}.notifications
```

Mensaje de suscripción:

```json
{
  "action": "SUBSCRIBE",
  "channel": "company.com_01J.operations",
  "subscriptionId": "sub_01J..."
}
```

La suscripción validará cuenta, empresa, sucursal, membresía, rol y permiso. Si cambian permisos, membresías o asignaciones, los canales afectados se cerrarán o requerirán revalidación.

### 53.3 Evento de notificación

```json
{
  "messageType": "NOTIFICATION",
  "notificationId": "ntf_01J...",
  "eventId": "evt_01J...",
  "eventType": "operation.completed",
  "eventVersion": "1.0",
  "companyId": "com_01J...",
  "branchId": "brn_01J...",
  "occurredAt": "2026-08-26T20:00:00Z",
  "data": {
    "operationId": "op_01J...",
    "status": "COMPLETED"
  }
}
```

El payload será una proyección autorizada y no sustituirá la consulta REST del recurso o de la operación.

### 53.4 Confirmación

```json
{
  "action": "ACK",
  "notificationId": "ntf_01J..."
}
```

El servidor conservará la notificación hasta recibir confirmación o alcanzar su periodo de retención. No se confiará exclusivamente en que el cliente deduplique mensajes.

## 54. Webhooks externos

### 54.1 Crear suscripción

```http
POST /api/v1/companies/{publicCompanyId}/webhook-subscriptions
```

Solicitud:

```json
{
  "endpointUrl": "https://cliente.example.com/jcomercy/events",
  "branchIds": ["brn_01J..."],
  "eventTypes": [
    "company.updated",
    "branch.activated",
    "operation.completed"
  ],
  "eventVersion": "1.0"
}
```

La URL se validará antes de activar la suscripción. La respuesta no devolverá el secreto en texto plano más de una vez.

### 54.2 Administración

```http
GET    /api/v1/companies/{publicCompanyId}/webhook-subscriptions
GET    /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}
PATCH  /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}
DELETE /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}
POST   /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/pause
POST   /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/resume
POST   /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/secret-rotation
```

Los permisos se separarán para crear, consultar, modificar, probar, pausar, reactivar, rotar secretos y eliminar.

### 54.3 Pruebas y entregas

```http
POST /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/test
POST /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/real-event-test
GET  /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/deliveries
POST /api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/deliveries/{deliveryId}/redelivery
```

Las pruebas simuladas no modificarán datos. Las pruebas reales serán controladas y auditadas.

### 54.4 Seguridad y entrega

Cada mensaje incluirá `eventId`, `eventType`, `eventVersion`, `occurredAt`, identificadores de alcance y metadatos mínimos. Se firmará con HMAC y contendrá timestamp para protección contra replay.

Una respuesta `2xx` confirmará la entrega. Los demás resultados se clasificarán como temporales o permanentes y podrán activar backoff exponencial. Los estados de entrega serán `PENDING`, `DELIVERED`, `FAILED` y `EXPIRED`.

La suscripción estará aislada por empresa y podrá limitarse a sucursales y eventos. Nunca recibirá eventos de otra empresa.

## 55. Contrato con Billing

`Company Management` no administrará pagos ni modificará planes directamente. Recibirá capacidades y cambios de suscripción mediante contratos con `Billing`.

### 55.1 Consulta interna de capacidades

```http
GET /internal/v1/companies/{publicCompanyId}/billing-capabilities
```

Esta interfaz será interna, autenticada entre módulos y de solo lectura para `Company Management`. Podrá usarse como respaldo o reconciliación, no como sustituto principal del bus de eventos.

### 55.2 Solicitud de cambio

```http
POST /internal/v1/companies/{publicCompanyId}/billing-change-requests
```

La solicitud podrá representar cambio de plan o contratación de complemento de sucursal. `Billing` será responsable de aceptar, rechazar, cobrar y confirmar el resultado.

### 55.3 Eventos consumidos

`Company Management` consumirá, como mínimo:

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

Cada evento incluirá `subscriptionId`, versión o secuencia, capacidades resultantes, fecha efectiva y `correlationId`.

### 55.4 Procesamiento fuera de orden

Se descartarán eventos antiguos ya procesados. Si se detecta una brecha de secuencia, el módulo marcará el estado como pendiente de reconciliación y consultará a `Billing`.

### 55.5 Indisponibilidad de Billing

Mientras `Billing` no esté disponible:

- se conservará el último estado válido;
- continuarán operaciones dentro de capacidades conocidas;
- se bloquearán cambios que requieran confirmar suscripción;
- se marcará la información como pendiente de reconciliación;
- no se suspenderá ni reactivará únicamente por falta de confirmación.

La reconciliación podrá ejecutarse automáticamente al detectar inconsistencias o reconexión, y manualmente por administradores autorizados.

## 56. Cierre del contrato

Con esta sección queda cubierto el catálogo inicial de interfaces de `Company Management`: recursos empresariales, sucursales, acceso, roles, operaciones asíncronas, archivos, auditoría, WebSockets, webhooks y Billing.

La siguiente fase consiste en transformar este contrato conceptual en una especificación OpenAPI formal, definiendo schemas reutilizables, parámetros, respuestas, códigos de error y seguridad sin implementar todavía controladores ni lógica de negocio.
