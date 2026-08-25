# Estructura base del proyecto JComercy

## 1. Alcance

Este documento define la estructura objetivo del nuevo proyecto JComercy y la separación entre el núcleo empresarial y los productos verticales.

La estructura propuesta es modular, pero inicialmente se desplegará como un monolito modular. La separación de esquemas y contratos deberá existir desde la primera versión.

## 2. Situación actual del repositorio copiado

El repositorio todavía conserva la organización Maven y los nombres de Clinerya:

- `clinerya-app` como aplicación principal.
- Módulos clínicos y operativos con prefijo `clinerya-*`.
- Migraciones históricas en `clinerya-app/src/main/resources/db/migration`.
- Configuración de aplicación, variables y artefactos con nombres de MediCloud/Clinerya.

Esta estructura se considera una base técnica heredada, no el diseño final de JComercy. No se renombrarán módulos masivamente antes de tener contratos y pruebas de compilación definidos.

## 3. Estructura objetivo conceptual

```text
JComercy/
├── jcomercy-core/
│   ├── jcomercy-shared-kernel/
│   └── jcomercy-messaging-contracts/
├── jcomercy-identity/
├── jcomercy-company-management/
├── jcomercy-billing/
├── jcomercy-customers/
├── jcomercy-inventory/
├── jcomercy-sales/
├── jcomercy-cash/
├── jcomercy-accounting/
├── jcomercy-tax-mx-sat/
├── jcomercy-personnel/
├── jcomercy-notifications/
├── jcomercy-integrations/
├── jcomercy-vertical-clinerya/
├── jcomercy-vertical-restaurants/
├── jcomercy-vertical-retail/
├── jcomercy-app/
└── documentacion/
```

## 4. Propiedad de módulos

| Módulo | Esquema propietario | Responsabilidad inicial |
|---|---|---|
| Identity | `identity` | Usuarios, autenticación y verificación. |
| Company Management | `company_management` | Cuenta, empresas, sucursales, membresías y autorización empresarial. |
| Billing | `billing` | Planes, suscripciones, complementos y capacidad contratada. |
| Customers | `customers` | Clientes comerciales generales. |
| Inventory | `inventory` | Productos, existencias, movimientos y almacenes. |
| Sales | `sales` | Ventas, pedidos y líneas de venta. |
| Cash | `cash` | Cajas, sesiones, cobros y movimientos. |
| Accounting | `accounting` | Catálogo contable, asientos y reportes operativos. |
| Tax MX SAT | `tax_mx_sat` | Reglas fiscales y CFDI de México. |
| Personnel | `personnel` | Empleados, contratos y nómina. |
| Notifications | `notifications` | Plantillas, entregas y estados de comunicación. |
| Integrations | `integrations` | Conectores con terceros. |
| Clinerya | `clinerya` | Clínica, paciente, expediente, tratamiento y agenda clínica. |

## 5. Reglas de dependencias

- Un módulo puede depender de contratos, no de tablas de otro módulo.
- Las dependencias síncronas deben apuntar a interfaces de aplicación o APIs.
- Los cambios confirmados deben comunicarse mediante eventos.
- Los módulos no compartirán entidades JPA ni repositorios de persistencia.
- Las referencias externas utilizarán identificadores públicos.
- `company_id` y `branch_id` externos no tendrán FK física si pertenecen a otro esquema.
- El núcleo no dependerá de Clinerya.
- Clinerya podrá depender de Company Management, Customers, Billing y Notifications mediante contratos.

## 6. Ubicación de migraciones

La línea nueva de JComercy utilizará una ubicación separada:

```text
jcomercy-app/src/main/resources/db/migration/jcomercy/
```

Las migraciones se organizarán por una secuencia global:

```text
V001__company_management_create_schema.sql
V002__company_management_create_account.sql
V003__company_management_create_account_membership.sql
```

La historia heredada de Clinerya no deberá estar dentro de la ubicación activa de JComercy.

## 7. Organización interna de los módulos

Cuando un módulo requiera separación hexagonal, se organizará conceptualmente así:

```text
jcomercy-company-management/
├── company-management-domain/
├── company-management-application/
└── company-management-infra/
```

- `domain`: entidades, reglas y puertos del dominio.
- `application`: casos de uso, comandos, consultas y contratos de entrada.
- `infra`: persistencia del esquema propio, adaptadores y transporte.

La capa de infraestructura de un módulo no podrá reutilizar repositorios de otro módulo.

## 8. Estrategia de transición

1. Mantener intactos los módulos heredados durante la definición.
2. Crear la documentación y contratos de JComercy.
3. Crear el módulo `company-management` nuevo de forma aislada.
4. Crear el esquema y migraciones propias en una base JComercy limpia.
5. Validar compilación y pruebas del nuevo módulo.
6. Incorporar progresivamente Identity y Billing.
7. Integrar Clinerya como vertical mediante contratos.
8. Renombrar o retirar módulos heredados únicamente cuando exista cobertura funcional suficiente.

## 9. No incluido en esta fase

- Renombrado masivo de módulos Maven.
- Cambio de paquetes Java.
- Cambio de configuración activa de Clinerya.
- Creación de migraciones SQL.
- Implementación de entidades o repositorios.
- Migración de clientes activos de Clinerya.

## 10. Estado

**Estado: PROPUESTA PENDIENTE DE APROBACIÓN.**

Esta estructura deberá aprobarse antes de crear módulos nuevos, modificar el `pom.xml` raíz o cambiar la configuración de ejecución de Flyway.
