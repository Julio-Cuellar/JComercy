# Modelo de datos del módulo Inventory / Warehouses

> Documento base para el diseño del módulo de inventarios y almacenes.

Consulta los [diagramas del módulo Inventory](diagramas-modulo-inventory.md) para ver el mapa entidad-relación, el UML y los flujos de secuencia.

## Índice

1. [Propósito](#1-propósito)
2. [Principios y contexto](#2-principios-y-contexto)
3. [Entidades](#3-entidades)
4. [Métodos de costo](#4-métodos-de-costo)
5. [Permisos iniciales](#5-permisos-iniciales)
6. [Integración RabbitMQ](#6-integración-rabbitmq)
7. [Reglas transaccionales](#7-reglas-transaccionales)
8. [Orden de implementación](#8-orden-de-implementación)

## 1. Propósito

Define el modelo funcional y técnico inicial del módulo de inventarios y almacenes de JComercy. El módulo será dueño del esquema `inventory` y administrará productos, unidades de medida, almacenes, ubicaciones, existencias, movimientos, reservas, transferencias, kits y valuación operativa.

`company-management` solo se integrará mediante identificadores públicos y eventos. No se compartirán tablas, entidades JPA ni repositorios.

## 2. Principios y contexto

- El stock se controla por empresa, sucursal, almacén, ubicación, producto y, cuando aplique, lote o número de serie.
- `StockBalance` es una proyección; `InventoryMovement` es el historial inmutable.
- Movimiento, saldo y evento Outbox se persisten en la misma transacción.
- Toda operación mutable requiere usuario, empresa, sucursal y permisos.
- Inventarios calcula el costo operativo; contabilidad consumirá posteriormente los datos de valuación.
- El producto vertical aplica una configuración inicial de métodos de costo; después el usuario puede activarlos o desactivarlos.

```text
Company (referencia externa)
└── Branch (referencia externa)
    ├── Warehouse BRANCH_OWNED
    └── Warehouse COMPANY_SHARED
         └── WarehouseBranchAccess ──> una o más sucursales
```

El cliente registra el almacén después de crear la sucursal. Un almacén propio solo sirve a su sucursal; uno compartido puede atender varias sucursales de la misma empresa desde la primera versión.

## 3. Entidades

### 3.1 Warehouse

| Atributo | Tipo | Req. | Regla |
|---|---|---:|---|
| `id` / `publicWarehouseId` | UUID/string | Sí | Interno y público |
| `companyId` | string | Sí | Empresa propietaria |
| `scope` | enum | Sí | `BRANCH_OWNED`, `COMPANY_SHARED` |
| `branchId` | string | Cond. | Obligatorio para propio; nulo para compartido |
| `code` / `name` | string | Sí | Código único dentro de la empresa |
| `type` | enum | Sí | `GENERAL`, `RECEIVING`, `DISPATCH`, `TRANSIT`, `VIRTUAL` |
| `status` | enum | Sí | `DRAFT`, `ACTIVE`, `INACTIVE`, `BLOCKED` |
| `isDefault` | boolean | Sí | Máximo uno por sucursal |
| `address` | Address | No | Domicilio físico |
| `phone` / `email` / `managerId` | string | No | Contacto y responsable |
| `timezone` / `currency` | string | Sí | Operación |
| `version` / fechas | long/timestamp | Sí | Concurrencia y auditoría |

No se activa si la empresa o sucursal no están activas. Un almacén compartido requiere al menos una sucursal autorizada.

### 3.2 WarehouseBranchAccess

```text
id
warehouseId
branchId
status          // ACTIVE, INACTIVE
assignedAt
assignedBy
```

La combinación `warehouseId + branchId` es única y solo acepta sucursales de la misma empresa.

### 3.3 WarehouseLocation

Unidad física o lógica mínima para almacenar stock. Se organiza jerárquicamente mediante `parentLocationId`; no se requiere una entidad Zone independiente.

```text
id
publicLocationId
warehouseId
parentLocationId       // opcional
code
name
type                   // INTERNAL, RECEIVING, PICKING, DISPATCH, TRANSIT,
                       // SUPPLIER, CUSTOMER, INVENTORY_LOSS, QUARANTINE, CUSTOM
status                 // ACTIVE, INACTIVE, BLOCKED
capacity               // opcional
capacityUnitCode       // opcional
```

Una ubicación bloqueada no recibe stock nuevo.

### 3.4 UnitOfMeasure

Catálogo controlado; no se permiten unidades arbitrarias como texto libre.

```text
code
name
symbol
dimension              // MASS, VOLUME, LENGTH, AREA, COUNT, TIME
baseUnitCode
conversionFactor
decimals
status                 // ACTIVE, INACTIVE
```

Catálogo inicial: `G`, `KG`, `ML`, `L`, `MM`, `M`, `M2`, `PZA`. Solo se convierten unidades de la misma dimensión: `1 KG = 1000 G`, `1 L = 1000 ML`, `1 M = 1000 MM`.

### 3.5 Product

Catálogo independiente de productos del módulo.

```text
id / publicProductId
companyId
sku                    // único dentro de la empresa
name
categoryId             // opcional
stockUnitCode          // unidad del saldo; obligatoria
purchaseUnitCode       // opcional
saleUnitCode           // opcional
netQuantity            // opcional
netQuantityUnitCode    // opcional
trackingMode           // NONE, LOT, SERIAL, LOT_AND_EXPIRATION, SERIAL_AND_EXPIRATION
allowNegativeStock
costingMethod          // DEFAULT o método habilitado
status                 // ACTIVE, INACTIVE
```

`stockUnitCode` controla el saldo; la cantidad neta describe la presentación. Por ejemplo, una botella puede controlarse en `PZA` y tener `600 ML`.

Si el modo incluye `SERIAL`, cada unidad física debe tener identificador único. Si el usuario omite el serial, se genera secuencialmente, por ejemplo `SER-000001`. Lotes y caducidad son opcionales salvo que el modo configurado los incluya.

### 3.6 ProductUnitConversion

```text
id
productId
fromUnitCode
toUnitCode
factor
roundingMode
status
```

Ejemplo: `1 PAQUETE = 6 PZA`. El factor debe ser positivo y la dimensión compatible.

### 3.7 StockBalance

```text
id
companyId
branchId
warehouseId
locationId
productId
lotId                  // opcional
serialNumber           // único para productos serializados
expirationDate         // opcional
inventoryStatus        // AVAILABLE, QUARANTINED, DAMAGED
onHand
reserved
available              // onHand - reserved
unitCost
costingMethod
version
```

En productos serializados cada unidad física ocupa su propio saldo. No se modifica directamente: cambia por movimientos o reservas.

### 3.8 InventoryMovement

Registro inmutable de entradas, salidas, ajustes y operaciones derivadas.

```text
id / publicMovementId
companyId
branchId
warehouseId
locationId
productId
type                   // RECEIPT, ISSUE, ADJUSTMENT_IN, ADJUSTMENT_OUT,
                       // TRANSFER_OUT, TRANSFER_IN, RETURN,
                       // KIT_BREAKDOWN_OUT, KIT_BREAKDOWN_IN
quantity               // mayor que cero
unitCode
unitCost
totalCost
lotId / serialNumber / expirationDate
referenceType / referenceId
reason
occurredAt
actorUserId
correlationId
createdAt
```

Las correcciones se realizan con movimientos compensatorios; nunca se edita el histórico.

### 3.9 StockReservation

```text
id / publicReservationId
warehouseId
locationId
productId
quantity
referenceType / referenceId
status                 // ACTIVE, RELEASED, CONSUMED, EXPIRED, CANCELLED
expiresAt               // configurable
createdBy
createdAt / updatedAt
```

Una reserva activa incrementa `reserved` y reduce `available` en la misma transacción. La expiración es configurable por empresa y puede sobrescribirse por operación con permiso.

### 3.10 WarehouseTransfer

```text
id / publicTransferId
companyId
sourceBranchId
sourceWarehouseId
destinationBranchId
destinationWarehouseId
status                 // DRAFT, REQUESTED, APPROVED, DISPATCHED,
                       // IN_TRANSIT, RECEIVED, CANCELLED, REJECTED
requestedBy / approvedBy
dispatchedAt / receivedAt
notes
createdAt / updatedAt
```

Las líneas contienen producto y cantidades solicitada, enviada y recibida. Se registra salida en el origen y entrada en el destino.

### 3.11 ProductKit y ProductKitComponent

```text
ProductKit
id / publicKitId
companyId
kitProductId
name
version
stockMode               // STOCKED, VIRTUAL, BOTH
status                  // DRAFT, ACTIVE, INACTIVE

ProductKitComponent
id
kitId
componentProductId
quantityPerKit
unitCode
sequence
```

`STOCKED` conserva stock propio; `VIRTUAL` se calcula por componentes; `BOTH` permite ambos comportamientos. La composición se versiona y no modifica operaciones históricas.

### 3.12 KitBreakdown

```text
id / publicBreakdownId
companyId
branchId
warehouseId
sourceLocationId
kitProductId
kitQuantity
kitDefinitionVersion
costDistributionPolicy
status                  // POSTED, CANCELLED
actorUserId
correlationId
createdAt
```

Un fraccionamiento genera una salida del kit y entradas de sus componentes en una sola transacción. Si falta stock del kit, se rechaza completo. La política inicial será `PROPORTIONAL_STANDARD_VALUE`: distribuye el costo del kit proporcionalmente al valor estándar relativo de sus componentes y registra la política aplicada.

## 4. Métodos de costo

Son extensiones internas intercambiables; el núcleo de movimientos delega la fórmula a una estrategia.

- `WEIGHTED_AVERAGE_MOVING`: recalcula el costo medio con cada entrada.
- `FIFO`: consume primero los costos de entradas más antiguas.
- `SPECIFIC_IDENTIFICATION`: asigna costo a unidad, lote o serie; obligatorio para productos serializados.
- `STANDARD_COST`: utiliza costo estándar y registra variaciones.

```text
inventory
├── inventory-domain
├── inventory-application
├── inventory-infra
└── inventory-costing
    ├── costing-api
    ├── costing-average
    ├── costing-fifo
    ├── costing-specific
    └── costing-standard
```

### 4.1 Configuración por empresa

```text
InventoryCostingPolicy
companyId
enabledMethods
defaultMethod
effectiveFrom
configuredBy
version
```

Una empresa puede activar varios métodos; uno será predeterminado para productos nuevos. El usuario puede activar y desactivar métodos a su gusto.

### 4.2 Configuración por producto y vertical

```text
ProductCostingPolicy
productId
methodCode              // DEFAULT o método específico habilitado
source                  // COMPANY_DEFAULT, PRODUCT_OVERRIDE, VERTICAL_PROFILE
effectiveFrom
effectiveTo
changedBy
```

Al activar un producto vertical se aplica su perfil inicial: métodos compatibles, método predeterminado y restricciones técnicas. Posteriormente el usuario puede activar o desactivar los métodos compatibles. Un método específico por producto solo es válido si está habilitado para la empresa.

Resolución: producto específico válido, luego método predeterminado de empresa y finalmente perfil del vertical.

No se desactiva un método usado exclusivamente por productos sin reasignarlos; tampoco se desactivan todos si existen productos o saldos activos. Los cambios tienen fecha efectiva, quedan auditados y no reescriben el histórico. Para reportar un mismo stock bajo dos métodos se requiere un libro de valuación adicional; el saldo físico sigue siendo único.

La valuación operativa no es precio de venta. Contabilidad consumirá movimientos y costos cuando ese módulo exista.

## 5. Permisos iniciales

- `inventory.products.read`, `inventory.products.manage`
- `inventory.warehouses.read`, `inventory.warehouses.manage`
- `inventory.locations.manage`
- `inventory.stock.read`, `inventory.stock.adjust`, `inventory.stock.negative.enable`, `inventory.stock.reserve`
- `inventory.transfer.create`, `inventory.transfer.approve`, `inventory.transfer.dispatch`, `inventory.transfer.receive`
- `inventory.cross_branch.read`
- `inventory.kits.read`, `inventory.kits.manage`, `inventory.kits.breakdown`
- `inventory.costing.read`, `inventory.costing.configure`
- `inventory.audit.read`

Los permisos respetan alcance `COMPANY`, `BRANCH` o `CROSS_BRANCH_READ`.

## 6. Integración RabbitMQ

Exchange: `jcomercy.events`.

Consume: `company.created`, `branch.created`, `branch.activated`.

Publica: `inventory.warehouse.created`, `inventory.warehouse.activated`, `inventory.stock.received`, `inventory.stock.issued`, `inventory.stock.adjusted`, `inventory.stock.reserved`, `inventory.transfer.requested`, `inventory.transfer.dispatched`, `inventory.transfer.received`, `inventory.kit.breakdown.posted`, `inventory.kit.breakdown.cancelled`.

Envelope mínimo:

```text
eventId, eventType, schemaVersion, occurredAt, correlationId,
companyId, branchId, actorUserId, data
```

Se publica mediante Outbox. El consumidor confirma después de persistir el efecto y soporta redelivery e idempotencia.

## 7. Reglas transaccionales

- Movimiento, saldo y Outbox ocurren en una misma transacción.
- Stock negativo requiere configuración de empresa y permiso del usuario.
- Reservas y primera autorización de almacén compartido usan bloqueo o versión.
- Operaciones mutables reciben `Idempotency-Key`.
- Seriales son únicos por empresa; lotes y caducidad se validan según `trackingMode`.
- Transferencias, kits y ajustes dejan actor, motivo y correlación.

## 8. Orden de implementación

1. Módulo Maven con capas `domain`, `application` e `infra`.
2. Catálogo independiente, unidades y conversiones.
3. Almacenes, alcance por sucursal y ubicaciones.
4. Productos, lotes, seriales y generación secuencial.
5. Saldos, movimientos y ajustes.
6. Extensiones de valuación y configuración empresa/producto.
7. Reservas con expiración configurable.
8. Transferencias entre almacenes y sucursales.
9. Kits, versiones y fraccionamiento.
10. RabbitMQ mediante Outbox.
11. OpenAPI/AsyncAPI y pruebas unitarias, H2, concurrencia, idempotencia y redelivery.
