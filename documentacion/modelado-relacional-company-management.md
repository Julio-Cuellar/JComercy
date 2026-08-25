# Modelo relacional interno: Company Management

## 1. Alcance

Este documento define el modelo relacional interno v1 del esquema `company_management` de JComercy.

No contiene código, SQL ni migraciones Flyway. Su objetivo es establecer tablas, atributos, relaciones y restricciones antes de implementar la base del proyecto.

El módulo será propietario de:

- Cuentas administrativas.
- Empresas.
- Sucursales.
- Membresías de cuenta y empresa.
- Asignaciones de usuarios a sucursales.
- Roles y permisos de autorización empresarial.

No será propietario de usuarios y autenticación, planes, suscripciones, empleados, clientes, inventario, ventas, caja, contabilidad, ni de ningun dato de los modulos para productos verticales.

## 2. Reglas de persistencia modular

- Cada módulo tendrá su propio esquema.
- Las claves foráneas solo podrán apuntar a tablas del mismo esquema.
- Las referencias a otros módulos serán identificadores contractuales, no FK físicas.
- Cada tabla tendrá un `id` técnico interno.
- Las entidades expuestas por APIs tendrán además un identificador público inmutable.
- No se eliminarán físicamente entidades con historial operativo.
- Todas las fechas se almacenarán con zona horaria.

## 3. Convenciones de identificadores

| Identificador | Uso |
|---|---|
| `id` | PK interna y relaciones dentro de `company_management`. |
| `public_*_id` | Contratos, APIs, URLs, eventos y referencias externas. |
| `user_id` | Referencia contractual al módulo Identity; no es FK. |
| `company_id` / `branch_id` | FK interna cuando la tabla pertenece a este esquema. |

Se recomienda UUID o ULID para los identificadores públicos. Ningún identificador público deberá reutilizarse.

## 4. Tablas propietarias

| Tabla | Responsabilidad |
|---|---|
| `account` | Agrupar las empresas del propietario principal. |
| `account_membership` | Dar acceso administrativo a una cuenta. |
| `company` | Identidad y configuración general de la empresa. |
| `branch` | Identidad y configuración general de la sucursal. |
| `company_membership` | Relacionar un usuario con una empresa. |
| `branch_assignment` | Asignar una membresía a una o más sucursales. |
| `role` | Roles de sistema y roles personalizados. |
| `permission` | Catálogo de permisos. |
| `company_membership_role` | Asignar roles a membresías. |
| `role_permission` | Asociar permisos con roles. |

## 5. Definición de tablas

### 5.1 `account`

| Campo | Tipo conceptual | Reglas |
|---|---|---|
| `id` | UUID | PK interna, inmutable. |
| `public_account_id` | UUID/ULID | Único, público e inmutable. |
| `owner_user_id` | UUID/ULID externo | Referencia contractual a Identity. |
| `name` | Texto | Requerido. |
| `status` | Enumeración | `ACTIVE`, `SUSPENDED`, `ARCHIVED`. |
| `created_at` | Fecha-hora | Requerido, inmutable. |
| `updated_at` | Fecha-hora | Requerido. |

La cuenta tiene un propietario principal. El propietario puede operar sus empresas, respetando los módulos y capacidades contratadas.

### 5.2 `account_membership`

| Campo | Tipo conceptual | Reglas |
|---|---|---|
| `id` | UUID | PK interna. |
| `account_id` | UUID | FK a `account`. |
| `user_id` | UUID/ULID externo | Referencia contractual a Identity. |
| `status` | Enumeración | `PENDING`, `ACTIVE`, `SUSPENDED`, `REVOKED`. |
| `scope_type` | Enumeración | Alcance administrativo de cuenta. |
| `invited_at` | Fecha-hora | Requerido al invitar. |
| `accepted_at` | Fecha-hora | Requerido al aceptar. |
| `created_at` / `updated_at` | Fecha-hora | Requeridos. |

Restricción: combinación única `account_id + user_id` para membresías no revocadas. Pertenecer a una cuenta no otorga acceso operativo automático a sus empresas.

### 5.3 `company`

| Campo | Tipo conceptual | Reglas |
|---|---|---|
| `id` | UUID | PK interna. |
| `public_company_id` | UUID/ULID | Único, público e inmutable. |
| `account_id` | UUID | FK a `account`. |
| `legal_name` | Texto | Requerido al completar configuración. |
| `commercial_name` | Texto | Opcional. |
| `tax_id` | Texto | Opcional durante configuración. |
| `tax_country_code` | Código país | Requerido cuando exista `tax_id`. |
| `email` / `phone` | Texto | Opcionales. |
| `timezone` / `currency` | Código | Requeridos. |
| `status` | Enumeración | `CONFIGURING`, `ACTIVE`, `SUSPENDED`, `ARCHIVED`. |
| `created_by_user_id` | UUID/ULID externo | Referencia contractual a Identity. |
| `created_at` / `updated_at` | Fecha-hora | Requeridos. |

La primera empresa se crea durante el registro, pero permanece en `CONFIGURING` hasta completar sus datos obligatorios y los de su sucursal matriz.

### 5.4 `branch`

| Campo | Tipo conceptual | Reglas |
|---|---|---|
| `id` | UUID | PK interna. |
| `public_branch_id` | UUID/ULID | Único, público e inmutable. |
| `company_id` | UUID | FK a `company`. |
| `code` | Texto | Único dentro de la empresa. |
| `name` | Texto | Requerido. |
| `is_headquarters` | Booleano | Solo una matriz por empresa. |
| `address` | Estructura | Ubicación general de la sucursal. |
| `timezone` | Código | Puede heredar la zona de la empresa. |
| `status` | Enumeración | `CONFIGURING`, `ACTIVE`, `SUSPENDED`, `ARCHIVED`. |
| `created_at` / `updated_at` | Fecha-hora | Requeridos. |

El módulo no almacenará recursos propios de nicho como cajas, almacenes, consultorios, mesas o estaciones de cocina.

### 5.5 `company_membership`

| Campo | Tipo conceptual | Reglas |
|---|---|---|
| `id` | UUID | PK interna. |
| `company_id` | UUID | FK a `company`. |
| `user_id` | UUID/ULID externo | Referencia contractual a Identity. |
| `status` | Enumeración | `PENDING`, `ACTIVE`, `SUSPENDED`, `REVOKED`. |
| `membership_type` | Enumeración | `OWNER`, `ADMINISTRATOR`, `EMPLOYEE`, `EXTERNAL`. |
| `created_at` / `updated_at` | Fecha-hora | Requeridos. |

Restricción: combinación única `company_id + user_id` para membresías no revocadas. Un empleado puede tener una membresía sin pertenecer necesariamente al módulo de Personal y Nómina.

### 5.6 `branch_assignment`

| Campo | Tipo conceptual | Reglas |
|---|---|---|
| `id` | UUID | PK interna. |
| `company_id` | UUID | FK a `company`. |
| `company_membership_id` | UUID | FK a `company_membership`. |
| `branch_id` | UUID | FK a `branch`. |
| `is_primary` | Booleano | Una asignación principal por membresía. |
| `status` | Enumeración | `ACTIVE`, `SUSPENDED`, `REVOKED`. |
| `starts_at` / `ends_at` | Fecha-hora | Vigencia de la asignación. |

Restricciones:

- La membresía y la sucursal deben pertenecer a la misma empresa.
- No puede existir una asignación activa para una membresía revocada.
- Los empleados deben tener asignación explícita para operar en una sucursal.
- Un permiso global puede permitir consulta administrativa, pero no elimina las reglas de operación por sucursal.

### 5.7 `role`

| Campo | Tipo conceptual | Reglas |
|---|---|---|
| `id` | UUID | PK interna. |
| `public_role_id` | UUID/ULID | Único, público e inmutable. |
| `company_id` | UUID nullable | FK a `company` para roles personalizados; nulo para roles de sistema. |
| `code` | Texto | Único según el tipo de rol. |
| `name` | Texto | Requerido. |
| `role_type` | Enumeración | `SYSTEM`, `CUSTOM`. |
| `status` | Enumeración | `ACTIVE`, `INACTIVE`. |
| `created_at` / `updated_at` | Fecha-hora | Requeridos. |

Los roles personalizados pertenecen a una sola empresa y no pueden utilizarse en otra.

### 5.8 `permission`

| Campo | Tipo conceptual | Reglas |
|---|---|---|
| `id` | UUID | PK interna. |
| `code` | Texto | Único; catálogo controlado por JComercy. |
| `module_code` | Texto | Identifica el módulo protegido. |
| `action` | Texto | Acción autorizable. |
| `scope_type` | Enumeración | `ACCOUNT`, `COMPANY`, `BRANCH`, `CROSS_BRANCH_READ`. |
| `status` | Enumeración | `ACTIVE`, `INACTIVE`. |

Los permisos no contienen reglas de negocio de otros módulos; solo describen capacidades autorizables que los módulos validarán mediante sus contratos.

### 5.9 Tablas de relación

`company_membership_role`:

| Campo | Tipo conceptual | Regla |
|---|---|---|
| `company_membership_id` | UUID | FK a `company_membership`. |
| `role_id` | UUID | FK a `role`. |
| `assigned_at` | Fecha-hora | Requerido. |
| `assigned_by_user_id` | UUID/ULID externo | Referencia contractual a Identity. |

PK conceptual: `company_membership_id + role_id`.

`role_permission`:

| Campo | Tipo conceptual | Regla |
|---|---|---|
| `role_id` | UUID | FK a `role`. |
| `permission_id` | UUID | FK a `permission`. |

PK conceptual: `role_id + permission_id`.

## 6. Relaciones internas

```text
Account 1 ─── N AccountMembership
Account 1 ─── N Company
Company 1 ─── N Branch
Company 1 ─── N CompanyMembership
CompanyMembership 1 ─── N BranchAssignment
Branch 1 ─── N BranchAssignment
Company 1 ─── N CustomRole
CompanyMembership N ─── N Role
Role N ─── N Permission
```

No existirán relaciones físicas con `identity`, `billing`, `personnel`, `inventory`, `sales`, `accounting`, `fiscal` o módulos verticales.

## 7. Integración con módulos externos

- **Identity:** valida existencia, estado y autenticación del `user_id`.
- **Billing:** informa suscripción, límites de sucursales, módulos y usuarios de plataforma activos.
- **Personal y Nómina:** relaciona opcionalmente un empleado con un `user_id`, sin duplicar la propiedad del empleado.
- **Módulos operativos:** consultan empresa, sucursal, membresía y permisos mediante contratos síncronos.
- **Eventos:** notifican cambios como empresa creada, sucursal activada, membresía revocada o empresa suspendida.

## 8. Reglas de integridad y aislamiento

- Cada consulta debe validar el contexto de cuenta, empresa y sucursal.
- Conocer un `public_*_id` no concede acceso.
- Una empresa no puede consultar ni modificar datos de otra.
- Una sucursal no puede operar sobre otra sin autorización explícita.
- La consulta cruzada autorizada será de solo lectura, salvo que exista un flujo formal de transferencia.
- El propietario no puede ser eliminado por operaciones administrativas ordinarias.
- Una empresa archivada conserva sus datos históricos.
- Los cambios relevantes deberán generar eventos y registros de auditoría transversal.

## 9. Orden conceptual para futuras migraciones

1. Crear esquema `company_management`.
2. Crear `account`.
3. Crear `account_membership`.
4. Crear `company`.
5. Crear `branch`.
6. Crear `company_membership`.
7. Crear `branch_assignment`.
8. Crear `role` y `permission`.
9. Crear `company_membership_role` y `role_permission`.
10. Crear índices, restricciones de unicidad y reglas de estado.

Este orden es conceptual y deberá revisarse antes de redactar las migraciones Flyway.

## 10. Estado de aprobación

**Estado: APROBADO por el propietario del proyecto.**

El modelo relacional interno v1 queda aprobado para continuar con el diseño de los contratos entre módulos y la planificación de migraciones Flyway. La aprobación no autoriza todavía la implementación de código ni la creación de scripts SQL.

Los contratos se documentan en [contratos-company-management.md](contratos-company-management.md).

El orden de migraciones se propone en [plan-migraciones-flyway-company-management.md](plan-migraciones-flyway-company-management.md).
