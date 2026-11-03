# Modelado de datos: Empresas

## 1. Alcance

Este documento define el modelo de datos inicial para el contexto de empresas de JComercy.

En esta fase no se definen clases, controladores, APIs ni sentencias SQL. El objetivo es establecer entidades, relaciones, restricciones y reglas de negocio antes de diseñar la base del proyecto y las migraciones Flyway.

> El modelo relacional aprobado se desarrolla en [modelado-relacional-company-management.md](modelado-relacional-company-management.md). Este documento conserva el modelo conceptual inicial y sirve como referencia de alcance.

## 2. Responsabilidad del contexto

El contexto de empresas será responsable de:

- Registrar empresas.
- Mantener sus datos generales.
- Asociar la empresa con el usuario que la crea.
- Controlar el estado de la empresa.
- Preparar la relación con sucursales.
- Preparar la relación con membresías, suscripciones y módulos.

No será responsable de:

- Usuarios y autenticación.
- Sucursales como unidad operativa completa.
- Clientes.
- Facturación fiscal.
- Inventario, ventas o contabilidad.
- Datos clínicos de Clinerya.

## 3. Entidad `Company`

`Company` representa una empresa o negocio registrado en JComercy. Es el tenant principal y el límite de aislamiento de sus datos.

### 3.1 Atributos propuestos

| Campo | Tipo conceptual | Requerido | Descripción |
|---|---|---:|---|
| `id` | UUID | Sí | Identificador técnico de la empresa. |
| `legalName` | Texto | Sí | Razón social o nombre legal. |
| `commercialName` | Texto | No | Nombre comercial mostrado en la aplicación. |
| `taxId` | Texto | No | RFC u otro identificador fiscal según el país. |
| `email` | Texto | No | Correo general de la empresa. |
| `phone` | Texto | No | Teléfono general. |
| `address` | Estructura de dirección | No | Domicilio general de la empresa. |
| `timezone` | Texto | Sí | Zona horaria operativa. |
| `currency` | Texto | Sí | Moneda principal de operación. |
| `status` | Enumeración | Sí | Estado operativo de la empresa. |
| `createdByUserId` | UUID | Sí | Usuario que creó la empresa. |
| `createdAt` | Fecha-hora | Sí | Fecha de creación. |
| `updatedAt` | Fecha-hora | Sí | Última modificación. |

### 3.2 Observaciones

- `legalName` no debe confundirse con `commercialName`.
- `taxId` puede ser opcional durante el alta inicial; su obligatoriedad dependerá del flujo fiscal y del país.
- La configuración fiscal detallada no debe crecer dentro de `Company`; pertenecerá al módulo tributario por país.
- `timezone` y `currency` deben tener valores predeterminados configurables, pero deben quedar almacenados por empresa.
- La dirección debe modelarse de forma que pueda evolucionar para distintos países sin acoplarla a México.

## 4. Estados de la empresa

Estados iniciales propuestos:

| Estado | Significado |
|---|---|
| `ACTIVE` | La empresa puede utilizar sus módulos contratados. |
| `SUSPENDED` | La empresa conserva sus datos, pero no puede realizar nuevas operaciones restringidas. |
| `ARCHIVED` | La empresa dejó de operar y solo queda disponible para consulta autorizada. |

### 4.1 Reglas de transición

```text
ACTIVE → SUSPENDED
ACTIVE → ARCHIVED
SUSPENDED → ACTIVE
SUSPENDED → ARCHIVED
```

No se recomienda eliminar físicamente una empresa que ya tenga sucursales, usuarios, suscripciones u operaciones.

La suspensión de la empresa y la suspensión de su suscripción son conceptos relacionados, pero no idénticos:

- La empresa puede estar activa mientras su suscripción está pendiente o suspendida.
- La empresa puede conservar datos históricos aunque su suscripción no permita nuevas operaciones.
- La autorización final deberá considerar ambos estados.

## 5. Relación con usuarios

La identidad del usuario pertenece al contexto de usuarios. El contexto de empresas solo almacenará referencias a usuarios mediante UUID.

Relaciones iniciales:

```text
User
  └── CompanyMembership
        └── Company
```

La relación de membresía y los roles se modelarán en el contexto de autorización, no como una lista de usuarios serializada dentro de `Company`.

La empresa debe conservar `createdByUserId` para trazabilidad. La propiedad y los permisos efectivos se resolverán mediante membresías y roles.

## 6. Relación con sucursales

Una empresa puede tener cero, una o varias sucursales durante su ciclo de vida.

```text
Company 1 ─── N Branch
```

La empresa puede registrarse antes de contratar un plan o activar una sucursal. La creación y activación de sucursales estará sujeta a los límites de la suscripción empresarial.

El contexto de empresas no debe almacenar campos propios de una sucursal, como caja, almacén, consultorio, mesa, inventario o agenda.

## 7. Restricciones de integridad

- `id` debe ser UUID y no reutilizable.
- `legalName` debe ser obligatorio.
- `status` debe tener un valor válido.
- `timezone` debe ser una zona horaria reconocida.
- `currency` debe utilizar un código estándar de moneda.
- `createdByUserId` debe conservarse aunque el usuario cambie de estado.
- `createdAt` no debe modificarse después de crear la empresa.
- `updatedAt` debe cambiar en cada modificación válida.
- No se debe permitir una empresa duplicada por el mismo identificador fiscal dentro del alcance fiscal aplicable.
- La unicidad de `taxId` debe considerar el país y, si fuera necesario, el tipo de persona fiscal.
- No se debe permitir modificar o archivar una empresa sin validar permisos administrativos.

## 8. Aislamiento

Toda entidad operativa posterior deberá relacionarse con `companyId` cuando sus datos pertenezcan a una empresa.

El aislamiento debe garantizar que:

- Una empresa no pueda consultar datos de otra.
- Un UUID válido de otra empresa no sea suficiente para acceder a sus datos.
- Las consultas y comandos validen el contexto de usuario y membresía.
- Los filtros de empresa se apliquen en backend y repositorios.
- Los reportes consolidados solo estén disponibles con permiso explícito.

## 9. Auditoría mínima

El contexto debe permitir identificar:

- Usuario que creó la empresa.
- Usuario que modificó sus datos.
- Fecha y hora de cada cambio.
- Estado anterior y nuevo cuando cambie `status`.
- Motivo de suspensión o archivado, cuando aplique.

El diseño detallado de la bitácora de auditoría será transversal y no se duplicará en cada módulo.

## 10. Relación con suscripciones

La suscripción pertenece a la empresa y define la capacidad de sucursales y módulos contratados.

```text
Company 1 ─── 1..N Subscription
Subscription ─── Plan
Subscription ─── AdditionalBranchItems
```

El modelo debe permitir conservar el historial de suscripciones. No se recomienda sobrescribir indefinidamente el plan anterior de una empresa.

La validación de capacidad debe considerar:

```text
sucursales activas ≤ sucursales incluidas en el plan + sucursales adicionales contratadas
```

## 11. Índices conceptuales

Antes de crear la migración se deberán considerar índices para:

- `status`.
- `createdByUserId`.
- `taxId` junto con el país fiscal.
- Nombre comercial para búsquedas autorizadas.
- Relaciones de membresía por `companyId`.
- Relaciones de sucursal por `companyId`.

## 12. Decisiones pendientes

1. Si la dirección se almacena como columnas, objeto embebido o tabla independiente.
2. Si `taxId` y los datos fiscales iniciales pertenecen a `Company` o a un perfil fiscal separado.
3. Si la empresa puede tener más de un propietario formal.
4. Si se requiere una entidad superior `Account` para agrupar varias empresas del mismo usuario.
5. Si se permite crear una empresa sin completar datos fiscales.
6. Si una empresa archivada puede reactivarse o solo consultarse.
7. Si el historial de nombres comerciales debe conservarse como auditoría o como historial funcional.

## 13. Próximo paso

Después de validar este modelo se definirá:

1. El modelo de `Branch`.
2. El modelo de `CompanyMembership` y asignaciones.
3. El modelo de `Subscription` y planes.
4. El orden de migraciones Flyway.
5. La estructura base del proyecto para implementar el contexto.
