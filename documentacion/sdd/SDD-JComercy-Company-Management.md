# Software Design Description (SDD)
# Gestión de cuentas, empresas, sucursales y suscripciones

**Identificador:** SDD-JCOMERCY-COMPANY-001  
**Versión:** 0.1  
**Estado:** Borrador para revisión  
**Fecha:** 2026-08-21  
**Sistema:** JComercy  
**Producto:** JComercy Core  
**Documento padre:** SDD-JComercy-Core-001  
**Base normativa:** IEEE 1016-2009

---

## 1. Propósito

Definir el diseño del módulo responsable de administrar la cuenta propietaria, empresas, sucursales, membresías, asignaciones, planes y suscripciones de JComercy.

Este módulo será la base de aislamiento y autorización para todos los módulos empresariales y productos verticales.

## 2. Alcance

### Incluido

- Cuenta administrativa.
- Dueño de cuenta.
- Empresas.
- Sucursales.
- Membresías por empresa.
- Asignación de usuarios a sucursales.
- Planes comerciales.
- Suscripciones por empresa.
- Límites de sucursales.
- Complementos de sucursales adicionales.
- Estado de activación de empresas y sucursales.

### No incluido

- Inventario.
- Ventas.
- Caja.
- Contabilidad.
- Clientes.
- Recursos específicos de cada nicho.
- Facturación fiscal.
- Roles detallados de cada módulo operativo.

## 3. Actores

| Actor | Responsabilidad |
|---|---|
| Dueño de cuenta | Crea y administra sus empresas, suscripciones y operación empresarial |
| Administrador delegado | Gestiona empresas o configuraciones según permisos otorgados |
| Administrador de empresa | Administra una empresa específica |
| Empleado | Trabaja en empresas y sucursales asignadas |
| JCode | Gestiona catálogo de planes, módulos y soporte autorizado |
| Producto vertical | Consulta contexto empresarial y estado de capacidades |

## 4. Modelo conceptual

```text
User
  └── Account (owner)
        ├── AccountMembership
        └── Company
              ├── CompanyMembership
              │     └── BranchAssignment
              ├── Branch
              └── CompanySubscription
                    ├── Plan
                    └── SubscriptionAddon
```

## 5. Entidades de diseño

### 5.1 Account

Agrupa las empresas del propietario inicial.

Reglas:

- Tiene un propietario principal.
- Puede tener administradores delegados.
- No reemplaza a la empresa.
- No mezcla los datos operativos de sus empresas.

### 5.2 Company

Representa un negocio independiente dentro de la cuenta.

Reglas:

- Pertenece a una sola cuenta.
- Tiene su propia suscripción.
- Tiene sus propias sucursales, usuarios, roles y configuración.
- No comparte datos operativos con otras empresas por defecto.

### 5.3 Branch

Representa una unidad operativa de una empresa.

Reglas:

- Pertenece a una sola empresa.
- Consume una unidad de la capacidad de sucursales contratada.
- Puede estar activa, suspendida, en configuración o archivada.
- No tiene una suscripción independiente.

### 5.4 CompanyMembership

Relaciona un usuario con una empresa.

Reglas:

- Los permisos se definen en el contexto de la empresa.
- Un usuario puede pertenecer a varias empresas.
- Los roles personalizados de una empresa no se heredan a otra.

### 5.5 BranchAssignment

Determina en qué sucursales puede trabajar un miembro de la empresa.

Reglas:

- Es necesaria para empleados y administradores delegados operativos.
- Puede incluir una o varias sucursales.
- Puede ser revocada sin eliminar la membresía de empresa.
- No aplica al dueño de cuenta cuando ejerce su alcance global de propietario.

### 5.6 CompanySubscription

Representa el contrato de la empresa con JComercy.

Reglas:

- La suscripción pertenece a la empresa.
- Determina módulos, límites y sucursales incluidas.
- Puede tener complementos adicionales.
- Su estado controla la posibilidad de realizar nuevas operaciones.

### 5.7 Plan

Define las características comerciales disponibles para contratar.

Como mínimo debe determinar:

- Precio y periodicidad.
- Sucursales incluidas.
- Usuarios incluidos.
- Módulos incluidos.
- Límites de almacenamiento.
- Límites de integraciones u operaciones.

### 5.8 SubscriptionAddon

Permite ampliar la capacidad contratada.

El primer complemento será la sucursal adicional.

Ejemplo: un plan con cuatro sucursales incluidas puede contratar tres sucursales adicionales.

## 6. Planes iniciales de referencia

Los precios son pendientes de definición.

| Plan | Sucursales incluidas | Uso esperado |
|---|---:|---|
| Básico | 1 | Negocio con una ubicación |
| Intermedio | Hasta 3 | Negocio pequeño con expansión |
| Pro/Enterprise | Hasta 4 | Negocio con varias sucursales |
| Complemento | Una o más adicionales | Expansión sobre el límite del plan |

La estructura comercial debe ser configurable y no quedar fija dentro del código de los módulos.

## 7. Casos de uso principales

### CU-01 Registrar cuenta

El usuario crea su identidad y se convierte en dueño de una cuenta administrativa.

### CU-02 Crear empresa

El dueño registra una empresa dentro de su cuenta.

La empresa debe poder crearse antes de activar módulos operativos, pero para operar requerirá una suscripción activa.

### CU-03 Contratar suscripción

El dueño selecciona un plan para una empresa.

### CU-04 Crear sucursal

El sistema valida que exista capacidad disponible en el plan o complementos contratados.

### CU-05 Invitar usuario a empresa

El administrador invita a un usuario y le asigna un rol de empresa.

### CU-06 Asignar usuario a sucursal

El administrador asigna una o varias sucursales de trabajo.

### CU-07 Agregar sucursal adicional

El dueño contrata un complemento y posteriormente puede activar nuevas sucursales.

### CU-08 Cambiar de plan

El dueño actualiza el plan. Si el nuevo límite es menor que las sucursales activas, se aplica el periodo de gracia definido por negocio.

## 8. Estados

### Empresa

- `DRAFT`: creada pero no configurada.
- `ACTIVE`: disponible para operación.
- `SUSPENDED`: sin nuevas operaciones por estado comercial o administrativo.
- `ARCHIVED`: fuera de operación, conserva histórico.

### Sucursal

- `SETUP`: en proceso de configuración.
- `ACTIVE`: disponible para operación.
- `SUSPENDED`: sin nuevas operaciones.
- `ARCHIVED`: fuera de operación, conserva histórico.

### Suscripción

- `PENDING`: creada, pendiente de activación.
- `TRIAL`: periodo de prueba.
- `ACTIVE`: vigente.
- `PAST_DUE`: pago pendiente.
- `GRACE_PERIOD`: periodo de regularización.
- `SUSPENDED`: operaciones bloqueadas.
- `CANCELLED`: terminada.
- `EXPIRED`: vencida.

## 9. Autorización

### Dueño de cuenta

Puede administrar y operar las empresas de su cuenta, siempre respetando los módulos incluidos en la suscripción.

### Administrador delegado

Solo puede operar en empresas y sucursales autorizadas mediante membresías, roles y asignaciones.

### Empleado

Solo puede operar en sucursales asignadas y en módulos permitidos por sus roles.

La pertenencia a la cuenta no equivale a una membresía operativa en todas las empresas.

## 10. Reducción de plan

Se aplicará un periodo de gracia de 7 días cuando una empresa reduzca su plan y mantenga más sucursales que las permitidas.

Durante el periodo de gracia:

- Se mantienen las sucursales actuales durante los 7 días.
- No se pueden activar nuevas sucursales.
- Se notifican las sucursales excedentes.
- El dueño puede cancelar sucursales, contratar complementos o cambiar nuevamente de plan.

Al finalizar el periodo, las sucursales excedentes pasan a estado restringido o suspendido, sin eliminar datos históricos.

## 11. Interfaces con otros módulos

Los módulos empresariales y verticales podrán consultar:

- Empresa activa.
- Sucursal activa.
- Usuario y membresía.
- Permisos efectivos.
- Estado de suscripción.
- Módulos habilitados.
- Capacidad disponible de sucursales.

No podrán modificar directamente las entidades de este módulo.

Tampoco podrán consultar directamente sus tablas. El acceso se realizará mediante contratos síncronos definidos por el módulo o mediante eventos asíncronos publicados por este. El módulo de gestión empresarial conserva la propiedad exclusiva de sus esquemas, tablas y reglas de consistencia.

Esta regla aplica igualmente a los módulos de inventario, ventas, caja, contabilidad, fiscalidad, integraciones y productos verticales: cada uno será propietario de sus datos y expondrá únicamente los contratos necesarios para colaborar con los demás.

### 11.1 Diagrama de secuencia: alta inicial del propietario

El siguiente flujo representa el registro inicial del propietario, la creación de la cuenta, la empresa, la primera sucursal y la activación de la suscripción. El Portal coordina la operación, pero no persiste directamente en los esquemas de los módulos.

```mermaid
sequenceDiagram
    autonumber
    actor Owner as Propietario
    participant Portal as Portal JComercy
    participant Identity as Módulo Identity
    participant Company as Módulo Company
    participant Billing as Módulo Billing
    participant Events as Bus de eventos
    participant Notification as Módulo Notifications

    Owner->>Portal: Enviar registro y credenciales
    Portal->>Identity: Crear usuario
    Identity->>Identity: Persistir en esquema identity
    Identity-->>Portal: Confirmar usuario y public_user_id

    Portal->>Company: Crear cuenta para el propietario
    Company->>Company: Persistir Account y AccountMembership
    Company-->>Portal: Confirmar cuenta creada

    Owner->>Portal: Capturar empresa y primera sucursal
    Portal->>Company: Crear empresa y sucursal
    Company->>Company: Persistir Company y Branch
    Company->>Company: Crear membresía del propietario
    Company-->>Portal: Confirmar empresa y sucursal

    Owner->>Portal: Seleccionar plan y método de pago
    Portal->>Billing: Solicitar suscripción para la empresa
    Billing->>Company: Validar empresa mediante contrato síncrono
    Company-->>Billing: Confirmar empresa válida
    Billing->>Billing: Persistir suscripción en esquema billing
    Billing->>Events: Publicar CompanySubscriptionActivated
    Events-->>Company: Actualizar estado operativo mediante evento
    Events-->>Notification: Solicitar confirmación de activación
    Notification-->>Owner: Enviar confirmación
    Billing-->>Portal: Confirmar suscripción activa
    Portal-->>Owner: Mostrar configuración inicial completada
```

Reglas observables en el flujo:

- `Identity`, `Company` y `Billing` escriben únicamente en sus propios esquemas.
- `Billing` valida la empresa mediante un contrato, nunca consultando sus tablas directamente.
- La activación se comunica mediante el evento `CompanySubscriptionActivated`.
- El `public_user_id` es el identificador que puede circular entre módulos y contratos externos.

## 12. Trazabilidad

| Necesidad | Diseño |
|---|---|
| Propietario inicial | `Account` y dueño principal |
| Varias empresas | Relación `Account` a `Company` |
| Suscripción por empresa | `CompanySubscription` |
| Planes con sucursales incluidas | `Plan` |
| Sucursales adicionales | `SubscriptionAddon` |
| Empleados por sucursal | `BranchAssignment` |
| Roles por empresa | `CompanyMembership` y roles empresariales |
| Aislamiento | Contexto de empresa y sucursal |
| Propiedad de datos | Esquema propio por módulo y contratos síncronos/asíncronos |

## 13. Decisiones resueltas

1. **Periodo de gracia:** será de 7 días. Durante este periodo el cliente podrá completar la configuración inicial, registrar su personal y regularizar el pago.

2. **Suscripción `PAST_DUE`:** se recomienda mantener una ventana operativa de 7 días. Durante ella se permitirán las operaciones esenciales y la configuración inicial, pero se bloquearán nuevas sucursales, módulos, complementos y usuarios que excedan el límite contratado. Se mostrarán avisos de pago pendiente y se conservará el acceso del propietario y administradores autorizados.

   Al terminar la ventana, la empresa pasará a estado `SUSPENDED`: no podrá crear nuevas operaciones, pero conservará acceso de consulta, descarga de información y regularización del pago. La reactivación deberá restaurar las operaciones sin eliminar datos históricos.

3. **Creación inicial:** la primera empresa y su sucursal matriz se crearán automáticamente durante el registro, pero permanecerán en estado de configuración hasta que el propietario complete los datos obligatorios de la empresa y de la sucursal.

4. **Suscripciones históricas:** una empresa podrá conservar múltiples suscripciones históricas, pero solo tendrá una suscripción activa por cada momento. Los cambios de plan generarán una nueva versión o registro de suscripción, manteniendo el historial de facturación, fechas y estado anterior.

5. **Usuarios incluidos:** el módulo de Billing será responsable de controlar los límites y el consumo. Se contabilizarán usuarios de plataforma activos, es decir, personas con credenciales habilitadas; los empleados registrados en Personal y Nómina sin acceso al sistema no consumirán este límite. Company Management será responsable de membresías y asignaciones, mientras Billing determinará si existe capacidad disponible.

## 14. Criterio de aprobación

**Estado: APROBADO por el propietario del proyecto.**

El SDD queda aprobado para pasar al modelado relacional, con los siguientes elementos validados:

- [x] Entidades y relaciones.
- [x] Estados.
- [x] Reglas de suscripción.
- [x] Alcances de propietario y administradores.
- [x] Reglas de reducción de plan.
- [x] Contratos de consulta para módulos dependientes.
- [x] Política de gracia y suspensión por pago vencido.
- [x] Control de usuarios de plataforma incluidos en el plan.

La aprobación no autoriza todavía la implementación de código ni la creación de migraciones. La siguiente fase consiste en definir el modelo relacional interno del esquema propietario de este módulo.

## 15. Diagrama UML de clases

```mermaid
classDiagram
    class User {
        +UUID id
        +String email
        +String fullName
        +UserStatus status
    }

    class Account {
        +UUID id
        +UUID ownerUserId
        +String name
        +AccountStatus status
    }

    class AccountMembership {
        +UUID id
        +UUID accountId
        +UUID userId
        +AccountRole role
        +MembershipStatus status
    }

    class Company {
        +UUID id
        +UUID accountId
        +String legalName
        +String commercialName
        +String taxId
        +CompanyStatus status
    }

    class CompanyMembership {
        +UUID id
        +UUID companyId
        +UUID userId
        +MembershipStatus status
    }

    class Branch {
        +UUID id
        +UUID companyId
        +String code
        +String name
        +BranchStatus status
    }

    class BranchAssignment {
        +UUID id
        +UUID companyMembershipId
        +UUID branchId
        +AssignmentStatus status
    }

    class Role {
        +UUID id
        +UUID companyId
        +String name
        +RoleType type
        +RoleStatus status
    }

    class Permission {
        +UUID id
        +String code
        +String module
        +String action
    }

    class CompanyMembershipRole {
        +UUID companyMembershipId
        +UUID roleId
    }

    class RolePermission {
        +UUID roleId
        +UUID permissionId
    }

    class Plan {
        +UUID id
        +String name
        +int includedBranches
        +int includedUsers
        +Money basePrice
        +BillingCycle billingCycle
    }

    class Module {
        +UUID id
        +String code
        +String name
        +ModuleStatus status
    }

    class PlanModule {
        +UUID planId
        +UUID moduleId
    }

    class CompanySubscription {
        +UUID id
        +UUID companyId
        +UUID planId
        +SubscriptionStatus status
        +Date startDate
        +Date renewalDate
    }

    class SubscriptionAddon {
        +UUID id
        +UUID subscriptionId
        +String type
        +int quantity
        +Money recurringPrice
        +AddonStatus status
    }

    class CompanyModuleEntitlement {
        +UUID id
        +UUID companyId
        +UUID moduleId
        +EntitlementStatus status
        +String source
    }

    User "1" --> "0..1" Account : owns
    Account "1" --> "0..*" AccountMembership : has
    User "1" --> "0..*" AccountMembership : joins
    Account "1" --> "0..*" Company : contains
    Company "1" --> "0..*" Branch : operates
    User "1" --> "0..*" CompanyMembership : joins
    Company "1" --> "0..*" CompanyMembership : has
    CompanyMembership "1" --> "0..*" BranchAssignment : receives
    Branch "1" --> "0..*" BranchAssignment : allows work
    CompanyMembership "1" --> "0..*" CompanyMembershipRole : has
    Role "1" --> "0..*" CompanyMembershipRole : assigned through
    Role "1" --> "0..*" RolePermission : contains
    Permission "1" --> "0..*" RolePermission : granted through
    Company "1" --> "0..*" Role : defines
    Company "1" --> "0..*" CompanySubscription : contracts
    Plan "1" --> "0..*" CompanySubscription : selected by
    CompanySubscription "1" --> "0..*" SubscriptionAddon : extends
    Plan "1" --> "0..*" PlanModule : includes
    Module "1" --> "0..*" PlanModule : included in
    Company "1" --> "0..*" CompanyModuleEntitlement : enables
    Module "1" --> "0..*" CompanyModuleEntitlement : enabled for
```

## 16. Diagrama entidad–relación conceptual

```mermaid
erDiagram
    USER ||--o| ACCOUNT : owns
    USER ||--o{ ACCOUNT_MEMBERSHIP : has
    ACCOUNT ||--o{ ACCOUNT_MEMBERSHIP : grants
    ACCOUNT ||--o{ COMPANY : contains
    COMPANY ||--o{ BRANCH : has
    USER ||--o{ COMPANY_MEMBERSHIP : joins
    COMPANY ||--o{ COMPANY_MEMBERSHIP : includes
    COMPANY_MEMBERSHIP ||--o{ BRANCH_ASSIGNMENT : receives
    BRANCH ||--o{ BRANCH_ASSIGNMENT : permits
    COMPANY ||--o{ ROLE : defines
    COMPANY_MEMBERSHIP ||--o{ COMPANY_MEMBERSHIP_ROLE : receives
    ROLE ||--o{ COMPANY_MEMBERSHIP_ROLE : assigned
    ROLE ||--o{ ROLE_PERMISSION : grants
    PERMISSION ||--o{ ROLE_PERMISSION : belongs
    PLAN ||--o{ COMPANY_SUBSCRIPTION : selected
    COMPANY ||--o{ COMPANY_SUBSCRIPTION : contracts
    COMPANY_SUBSCRIPTION ||--o{ SUBSCRIPTION_ADDON : extends
    PLAN ||--o{ PLAN_MODULE : includes
    MODULE ||--o{ PLAN_MODULE : belongs
    COMPANY ||--o{ COMPANY_MODULE_ENTITLEMENT : enables
    MODULE ||--o{ COMPANY_MODULE_ENTITLEMENT : enabled

    USER {
        uuid id PK
        string email
        string full_name
        string status
    }

    ACCOUNT {
        uuid id PK
        uuid owner_user_id FK
        string name
        string status
    }

    ACCOUNT_MEMBERSHIP {
        uuid id PK
        uuid account_id FK
        uuid user_id FK
        string role
        string status
    }

    COMPANY {
        uuid id PK
        uuid account_id FK
        string legal_name
        string commercial_name
        string tax_id
        string status
    }

    BRANCH {
        uuid id PK
        uuid company_id FK
        string code
        string name
        string status
    }

    COMPANY_MEMBERSHIP {
        uuid id PK
        uuid company_id FK
        uuid user_id FK
        string status
    }

    BRANCH_ASSIGNMENT {
        uuid id PK
        uuid company_membership_id FK
        uuid branch_id FK
        string status
    }

    ROLE {
        uuid id PK
        uuid company_id FK
        string name
        string role_type
        string status
    }

    PERMISSION {
        uuid id PK
        string code UK
        string module
        string action
    }

    COMPANY_MEMBERSHIP_ROLE {
        uuid company_membership_id PK, FK
        uuid role_id PK, FK
    }

    ROLE_PERMISSION {
        uuid role_id PK, FK
        uuid permission_id PK, FK
    }

    PLAN {
        uuid id PK
        string name
        integer included_branches
        integer included_users
        decimal base_price
        string billing_cycle
    }

    MODULE {
        uuid id PK
        string code UK
        string name
        string status
    }

    PLAN_MODULE {
        uuid plan_id PK, FK
        uuid module_id PK, FK
    }

    COMPANY_SUBSCRIPTION {
        uuid id PK
        uuid company_id FK
        uuid plan_id FK
        string status
        date start_date
        date renewal_date
    }

    SUBSCRIPTION_ADDON {
        uuid id PK
        uuid subscription_id FK
        string addon_type
        integer quantity
        decimal recurring_price
        string status
    }

    COMPANY_MODULE_ENTITLEMENT {
        uuid id PK
        uuid company_id FK
        uuid module_id FK
        string source
        string status
    }
```
