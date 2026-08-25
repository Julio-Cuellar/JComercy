# Plan de migraciones Flyway: Company Management

## 1. Alcance

Este documento define el orden y el propósito de las migraciones Flyway para el esquema `company_management`.

No contiene sentencias SQL ejecutables. Las migraciones se crearán después de aprobar este plan y resolver las decisiones técnicas pendientes.

El plan corresponde al nuevo ecosistema JComercy. Las migraciones históricas de Clinerya no se reutilizarán como modelo relacional del módulo.

## 2. Principios

- Cada módulo crea y administra únicamente su propio esquema.
- No se crearán claves foráneas hacia esquemas externos.
- Las migraciones serán pequeñas, ordenadas e irreversibles una vez aplicadas en un entorno compartido.
- No se modificarán migraciones ya ejecutadas; los cambios posteriores crearán una nueva versión.
- Las semillas de roles y permisos del sistema deberán ser deterministas e idempotentes.
- Las migraciones deberán poder ejecutarse en desarrollo, pruebas y producción con el mismo orden.
- La auditoría transversal y la infraestructura general del bus no se duplicarán dentro de este esquema.

## 3. Esquema propietario

```text
company_management
├── account
├── account_membership
├── company
├── branch
├── company_membership
├── branch_assignment
├── role
├── permission
├── company_membership_role
├── role_permission
└── outbox_event
```

`outbox_event` pertenece al módulo porque registra eventos que nacen de transacciones confirmadas en sus propias tablas.

## 4. Secuencia propuesta de migraciones

| Versión | Nombre conceptual | Propósito |
|---|---|---|
| `V1` | `create_company_management_schema` | Crear el esquema propietario y establecer permisos de propietario. |
| `V2` | `create_account` | Crear cuentas, propietario externo, estado e identificador público. |
| `V3` | `create_account_membership` | Crear membresías administrativas de cuenta. |
| `V4` | `create_company` | Crear empresas, configuración inicial, estado y relación con cuenta. |
| `V5` | `create_branch` | Crear sucursales, matriz, estados y relación con empresa. |
| `V6` | `create_company_membership` | Crear membresías operativas y administrativas de empresa. |
| `V7` | `create_branch_assignment` | Crear asignaciones de miembros a sucursales. |
| `V8` | `create_roles_and_permissions` | Crear roles, permisos y sus relaciones. |
| `V9` | `seed_system_roles_and_permissions` | Registrar roles y permisos reservados del sistema. |
| `V10` | `create_outbox_event` | Crear la bandeja transaccional de eventos publicados por el módulo. |
| `V11` | `add_company_management_indexes` | Agregar índices de aislamiento, búsqueda y consumo de contratos. |
| `V12` | `add_integrity_constraints` | Consolidar unicidad, estados válidos y restricciones de consistencia. |
| `V13` | `add_schema_metadata` | Registrar versión lógica del esquema y metadatos técnicos si se requiere. |

Las versiones son una secuencia conceptual. El nombre final deberá respetar la convención Flyway definida para todo JComercy.

## 5. Dependencias entre migraciones

```text
V1 schema
  ↓
V2 account → V3 account_membership
  ↓
V4 company → V5 branch
  ↓
V6 company_membership → V7 branch_assignment
  ↓
V8 roles/permissions → V9 system seeds
  ↓
V10 outbox → V11 indexes → V12 constraints
```

Las referencias a `user_id`, `owner_user_id` y `created_by_user_id` no requieren migraciones del módulo Identity porque no son FK físicas.

## 6. Contenido esperado por migración

### V1: esquema

- Crear `company_management`.
- Establecer el propietario del esquema.
- Definir la política de acceso de la aplicación.
- No crear tablas de otros módulos.

### V2–V7: estructura organizacional

- Crear las tablas en orden de dependencia.
- Incluir PK internas e identificadores públicos.
- Incluir estados y fechas de vigencia.
- Aplicar unicidad local de códigos y membresías.
- Mantener referencias externas como UUID/ULID sin FK.

### V8–V9: autorización

- Crear roles de sistema y personalizados.
- Crear permisos por módulo, acción y alcance.
- Sembrar `ACCOUNT_OWNER` y otros roles reservados.
- Impedir que una semilla del sistema se convierta en un rol personalizado.

### V10: outbox

La tabla deberá permitir, conceptualmente:

- Identificador único del evento.
- Tipo y versión del evento.
- Fecha de ocurrencia.
- Cuenta, empresa y sucursal afectadas.
- Correlation ID y causation ID.
- Payload serializado.
- Estado de publicación.
- Número de intentos y próximo reintento.
- Fechas de creación, publicación y error.

La escritura del evento deberá ocurrir en la misma transacción lógica que el cambio de negocio que lo origina.

### V11–V12: optimización e integridad

Los índices y restricciones deberán cubrir como mínimo:

- `public_account_id`, `public_company_id`, `public_branch_id` y `public_role_id`.
- Estado y empresa en tablas operativas.
- Código de sucursal único dentro de la empresa.
- Una sola sucursal matriz por empresa.
- Una membresía vigente por usuario y empresa.
- Una membresía vigente por usuario y cuenta.
- Una asignación única por membresía y sucursal.
- Deduplikación por `event_id` en outbox.

## 7. Estados y migraciones

Los estados se almacenarán como valores controlados por el modelo de dominio. No se recomienda crear una tabla de catálogo para cada estado en esta primera versión.

Si se utiliza una restricción de base de datos para estados, cualquier nuevo estado deberá agregarse mediante una migración posterior y compatible.

## 8. Migraciones y eventos

Las migraciones estructurales no publicarán eventos de negocio. Los eventos comenzarán a publicarse cuando la aplicación ejecute comandos sobre tablas ya existentes y registre el evento correspondiente en `outbox_event`.

Las semillas iniciales de roles y permisos tampoco deben simular eventos de operación para otros módulos.

## 9. Validación antes de crear SQL

Antes de escribir las migraciones deberán aprobarse:

- Convención final de nombres de archivos Flyway.
- Estrategia de generación de UUID/ULID.
- Tipos PostgreSQL definitivos.
- Política de propietario y permisos del esquema.
- Tipo de columna para payload de eventos.
- Retención de eventos publicados.
- Estrategia de despliegue cuando existan varias instancias de la aplicación.
- Reglas exactas para índices parciales y unicidad de registros activos.

## 10. Estado

**Estado: APROBADO por el propietario del proyecto.**

El plan de migraciones Flyway queda aprobado como base para preparar la estructura de migraciones del nuevo ecosistema JComercy.

La aprobación no autoriza todavía ejecutar migraciones en una base compartida ni reutilizar la historia heredada de Clinerya. Antes de crear SQL ejecutable se deberá confirmar la separación de la línea histórica de Clinerya y la nueva línea de JComercy.

## 11. Recomendaciones técnicas

### 11.1 Convención de nombres Flyway

Se recomienda una secuencia de versiones global para toda la base de datos, con el módulo en la descripción:

```text
V001__company_management_create_schema.sql
V002__company_management_create_account.sql
V003__company_management_create_account_membership.sql
```

La versión debe ser única en todo JComercy, aunque cada módulo tenga un esquema independiente. No se recomienda usar secuencias independientes por módulo porque Flyway administra el orden y el historial de la base de datos como un conjunto. Una migración aplicada no debe editarse; cualquier corrección debe avanzar mediante una nueva versión. Flyway registra el checksum y recomienda crear una nueva migración para cambios posteriores. ([Documentación oficial de migraciones versionadas de Flyway](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html))

Como el repositorio copiado conserva migraciones históricas de Clinerya, antes de crear `V001` de JComercy deberá definirse una historia Flyway limpia para el nuevo ecosistema. No se deben ejecutar las migraciones históricas de Clinerya contra la base de JComercy ni reutilizar sus números como si fueran parte del núcleo nuevo. Flyway contempla baselines para representar un estado inicial en entornos nuevos, pero esa estrategia deberá aplicarse solo después de separar claramente el legado del nuevo modelo. ([Documentación oficial de baselines de Flyway](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/baseline-migrations))

### 11.2 Estrategia UUID/ULID

Se recomienda:

- `id` interno: UUIDv7 para favorecer orden temporal e índices.
- `public_*_id`: UUIDv4 para evitar exponer una secuencia temporal.
- Generación en la aplicación, con validación del tipo `uuid` en PostgreSQL.

PostgreSQL ofrece soporte nativo para UUIDv4 y UUIDv7 en versiones recientes, pero generar los identificadores desde la aplicación permite mantener la estrategia si se cambia de versión o motor. ([Documentación oficial de UUID de PostgreSQL](https://www.postgresql.org/docs/current/datatype-uuid.html))

### 11.3 Tipos PostgreSQL

Se recomienda PostgreSQL 16 o superior y los siguientes tipos:

- `uuid` para identificadores.
- `timestamptz` para fechas y horas.
- `numeric` para importes futuros.
- `text` para nombres y descripciones, salvo límites de negocio explícitos.
- `jsonb` únicamente para estructuras flexibles, como dirección o payload de eventos.
- `boolean` para indicadores simples.

No se recomienda crear tipos enum PostgreSQL para estados en esta primera versión; se usarán valores controlados por la aplicación y restricciones revisables mediante migraciones. `jsonb` valida que el contenido sea JSON válido y permite operaciones específicas sobre el documento. ([Documentación oficial de tipos JSON de PostgreSQL](https://www.postgresql.org/docs/16/datatype-json.html))

### 11.4 Propietario y permisos del esquema

Se recomienda separar tres roles técnicos:

```text
company_management_owner     Dueño de objetos; no usado por la aplicación
company_management_migrator  Ejecuta Flyway y cambios DDL
company_management_runtime   Ejecuta operaciones de negocio
```

El runtime tendrá permisos DML únicamente sobre `company_management`, sin `CREATE`, `ALTER` ni acceso a tablas de otros esquemas. Se deberá revocar el `CREATE` público y otorgar explícitamente `USAGE` y permisos necesarios. PostgreSQL documenta que el acceso a objetos de un esquema depende de los privilegios otorgados y que el `CREATE` público debe restringirse en configuraciones seguras. ([Privilegios de PostgreSQL](https://www.postgresql.org/docs/current/ddl-priv.html), [Esquemas y privilegios de PostgreSQL](https://www.postgresql.org/docs/18/ddl-schemas.html))

### 11.5 Payload de eventos

Se recomienda separar el sobre del evento en columnas normales y almacenar únicamente `payload` como `jsonb`:

```text
event_id, event_type, event_version, occurred_at,
account_public_id, company_public_id, branch_public_id,
correlation_id, causation_id, status, payload
```

No se deben guardar credenciales, tokens ni datos clínicos en el payload. Los consumidores deberán interpretar el payload según `event_type` y `event_version`.

### 11.6 Retención de eventos

Se recomienda:

- Mantener eventos pendientes o fallidos hasta su procesamiento exitoso o resolución manual.
- Mantener eventos publicados en la tabla outbox durante 30 días.
- Archivar o eliminar eventos publicados después de ese periodo, según las necesidades de soporte.
- Mantener la auditoría de negocio en un módulo de auditoría separado; el outbox no será la fuente histórica principal.

### 11.7 Despliegue con múltiples instancias

Flyway deberá ejecutarse una sola vez desde el pipeline de despliegue, antes de iniciar las nuevas instancias de la aplicación:

```text
CI/CD → validate → migrate → health checks → iniciar/actualizar instancias
```

No se recomienda que cada instancia ejecute migraciones automáticamente al arrancar. La base de datos y `flyway_schema_history` serán la fuente de coordinación; el pipeline deberá probar primero en una base temporal y posteriormente en staging. Flyway mantiene el historial, versiones y checksums de las migraciones aplicadas. ([Migraciones versionadas de Flyway](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html), [Despliegue basado en migraciones de Flyway](https://documentation.red-gate.com/flyway/deploying-database-changes-using-flyway/rolling-out-updates-from-a-single-schema-to-multiple-production-databases/tutorial-fleet-rollout-with-migrations-based-deployment))

### 11.8 Índices parciales y unicidad

Se recomienda usar índices únicos parciales para reglas como:

- Una sola sucursal matriz activa por empresa.
- Una membresía vigente por usuario y empresa.
- Una membresía vigente por usuario y cuenta.
- Una asignación activa por membresía y sucursal.

La condición deberá excluir estados históricos como `REVOKED` o `ARCHIVED`. Para garantizar que una membresía y una sucursal pertenezcan a la misma empresa, se recomienda reforzar el modelo con claves únicas compuestas y claves foráneas compuestas internas, no con validaciones únicamente en la aplicación. PostgreSQL soporta índices parciales con predicados sobre la misma tabla indexada. ([Documentación oficial de índices de PostgreSQL](https://www.postgresql.org/docs/current/indexes.html), [Índices parciales de PostgreSQL](https://www.postgresql.org/docs/17/indexes-partial.html))
