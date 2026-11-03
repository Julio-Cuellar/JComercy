# Registro de Decisiones Técnicas (ADR): Company Management

Este documento registra formalmente las decisiones de arquitectura, estructura de código, persistencia y eventos adoptadas durante la implementación interna del módulo `company_management` de JComercy.

---

## 1. Arquitectura de Módulos (Clean Architecture / Hexagonal)

- **Decisión:** El módulo se divide en 3 proyectos Maven con dependencias unidireccionales estrictas:
  - `company-management-domain`: Dominio puro Java 21 (Agregados, Entidades, Objetos de Valor, Invariantes y Puertos). **Sin Spring Boot ni frameworks**.
  - `company-management-application`: Servicios de aplicación, Orquestación de Casos de Uso, DTOs y Puertos de Salida.
  - `company-management-infra`: Adaptadores de persistencia JPA/PostgreSQL, controladores REST OpenAPI, migraciones Flyway y Outbox transaccional.

---

## 2. Estrategia de Identificadores (UUID / ULID)

- **Decisión:** 
  - **Identificador Interno (`id`):** `uuid` en base de datos.
  - **Identificadores Públicos (`public_*_id`):** Cadenas de texto basadas en `uuid` con prefijos explícitos de dominio:
    - Cuenta: `acc_...`
    - Empresa: `com_...`
    - Sucursal: `brn_...`
    - Membresía: `mem_...`
    - Asignación: `asg_...`
    - Rol: `rol_...`
    - Evento: `evt_...`

---

## 3. Secuencia de Migraciones Flyway Versión Limpia

- **Decisión:** Secuencia continua de migraciones `V001` a `V012` aplicadas sobre el esquema propietario `company_management`:
  - `V001__company_management_create_schema.sql`
  - `V002__company_management_create_account.sql`
  - `V003__company_management_create_account_membership.sql`
  - `V004__company_management_create_company.sql`
  - `V005__company_management_create_branch.sql`
  - `V006__company_management_create_company_membership.sql`
  - `V007__company_management_create_branch_assignment.sql`
  - `V008__company_management_create_roles_and_permissions.sql`
  - `V009__company_management_seed_system_roles_and_permissions.sql`
  - `V010__company_management_create_outbox_event.sql`
  - `V011__company_management_add_indexes.sql`
  - `V012__company_management_add_integrity_constraints.sql`

---

## 4. Patrón Transactional Outbox para Eventos de Integración

- **Decisión:** La escritura en la tabla `company_management.outbox_event` ocurre dentro de la misma transacción física/lógica `@Transactional` que modifica el agregado de negocio.
- **Estructura:** Cumple estrictamente los 14 campos del Envelope Común de JComercy (`eventId`, `eventType`, `schemaVersion`, `aggregateVersion`, `sequence`, `occurredAt`, `producerModule`, `correlationId`, `causationId`, `accountId`, `companyId`, `branchId`, `actorUserId`, `payloadJson`).

---

## 5. Control de Concurrencia e Idempotencia

- **Decisión:**
  - **Concurrencia Optimista:** Anotación `@Version` de JPA (`version` / `aggregate_version`) expuesta mediante el header de respuesta `ETag` y validada en peticiones de mutación mediante `If-Match`.
  - **Idempotencia:** Header `Idempotency-Key` interceptado y validado en todas las operaciones de registro y mutación.
