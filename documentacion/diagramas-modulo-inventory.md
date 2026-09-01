# Diagramas del módulo Inventory / Warehouses

> Diagramas de referencia para ubicar la estructura actual y el flujo objetivo del módulo.

## Estado de implementación

### Ya creadas en H2

- `unit_of_measure`
- `product`
- `product_unit_conversion`
- `warehouse`
- `warehouse_branch_access`
- `warehouse_location`
- `stock_balance`
- `inventory_movement`

### Pendientes del modelo objetivo

- Reservas.
- Transferencias.
- Kits y fraccionamiento.
- Políticas de valuación.
- Outbox específico del módulo.

## 1. Mapa entidad-relación

El siguiente mapa representa las tablas del modelo objetivo. `company-management` y sus entidades no se relacionan mediante claves foráneas; sus identificadores son referencias públicas externas.

```mermaid
erDiagram
    UNIT_OF_MEASURE {
        string code PK
        string name
        string symbol
        string dimension
        string base_unit_code
        decimal conversion_factor
        int decimals
        string status
    }

    PRODUCT {
        uuid id PK
        uuid public_product_id UK
        string company_id
        string sku
        string name
        string stock_unit_code FK
        string purchase_unit_code FK
        string sale_unit_code FK
        string tracking_mode
        boolean allow_negative_stock
        string costing_method
        string status
    }

    PRODUCT_UNIT_CONVERSION {
        uuid id PK
        uuid product_id FK
        string from_unit_code FK
        string to_unit_code FK
        decimal factor
        string rounding_mode
        string status
    }

    WAREHOUSE {
        uuid id PK
        uuid public_warehouse_id UK
        string company_id
        string scope
        string branch_id
        string code
        string name
        string type
        string status
        boolean is_default
        long version
    }

    WAREHOUSE_BRANCH_ACCESS {
        uuid id PK
        uuid warehouse_id FK
        string branch_id
        string status
        string assigned_by
    }

    WAREHOUSE_LOCATION {
        uuid id PK
        uuid public_location_id UK
        uuid warehouse_id FK
        uuid parent_location_id FK
        string code
        string type
        string status
        decimal capacity
        string capacity_unit_code FK
    }

    STOCK_BALANCE {
        uuid id PK
        string company_id
        string branch_id
        uuid warehouse_id FK
        uuid location_id FK
        uuid product_id FK
        string lot_id
        string serial_number
        date expiration_date
        string inventory_status
        decimal on_hand
        decimal reserved
        decimal unit_cost
        string costing_method
        long version
    }

    INVENTORY_MOVEMENT {
        uuid id PK
        uuid public_movement_id UK
        string company_id
        string branch_id
        uuid warehouse_id FK
        uuid location_id FK
        uuid product_id FK
        string type
        decimal quantity
        string unit_code FK
        decimal unit_cost
        decimal total_cost
        string lot_id
        string serial_number
        string reference_id
        string actor_user_id
        string correlation_id
    }

    STOCK_RESERVATION {
        uuid id PK
        uuid warehouse_id FK
        uuid location_id FK
        uuid product_id FK
        decimal quantity
        string reference_id
        string status
        timestamp expires_at
    }

    PRODUCT_KIT {
        uuid id PK
        uuid kit_product_id FK
        string company_id
        int version
        string stock_mode
        string status
    }

    PRODUCT_KIT_COMPONENT {
        uuid id PK
        uuid kit_id FK
        uuid component_product_id FK
        decimal quantity_per_kit
        string unit_code FK
    }

    KIT_BREAKDOWN {
        uuid id PK
        uuid kit_product_id FK
        uuid warehouse_id FK
        uuid source_location_id FK
        decimal kit_quantity
        int kit_definition_version
        string cost_distribution_policy
        string status
    }

    WAREHOUSE_TRANSFER {
        uuid id PK
        string company_id
        string source_branch_id
        uuid source_warehouse_id FK
        string destination_branch_id
        uuid destination_warehouse_id FK
        string status
    }

    UNIT_OF_MEASURE ||--o{ PRODUCT : "stock/purchase/sale unit"
    UNIT_OF_MEASURE ||--o{ PRODUCT_UNIT_CONVERSION : "from/to"
    PRODUCT ||--o{ PRODUCT_UNIT_CONVERSION : defines
    WAREHOUSE ||--o{ WAREHOUSE_BRANCH_ACCESS : authorizes
    WAREHOUSE ||--o{ WAREHOUSE_LOCATION : contains
    WAREHOUSE_LOCATION ||--o{ WAREHOUSE_LOCATION : parent
    UNIT_OF_MEASURE ||--o{ WAREHOUSE_LOCATION : capacity
    PRODUCT ||--o{ STOCK_BALANCE : stored
    WAREHOUSE_LOCATION ||--o{ STOCK_BALANCE : holds
    WAREHOUSE ||--o{ STOCK_BALANCE : owns
    PRODUCT ||--o{ INVENTORY_MOVEMENT : moves
    WAREHOUSE ||--o{ INVENTORY_MOVEMENT : records
    WAREHOUSE_LOCATION ||--o{ INVENTORY_MOVEMENT : occurs_at
    UNIT_OF_MEASURE ||--o{ INVENTORY_MOVEMENT : measured_in
    PRODUCT ||--o{ STOCK_RESERVATION : reserved
    WAREHOUSE ||--o{ STOCK_RESERVATION : reserves
    PRODUCT ||--o{ PRODUCT_KIT : "can be kit"
    PRODUCT_KIT ||--o{ PRODUCT_KIT_COMPONENT : contains
    PRODUCT ||--o{ PRODUCT_KIT_COMPONENT : component
    PRODUCT ||--o{ KIT_BREAKDOWN : converted
    WAREHOUSE ||--o{ KIT_BREAKDOWN : occurs_in
    WAREHOUSE ||--o{ WAREHOUSE_TRANSFER : source
    WAREHOUSE ||--o{ WAREHOUSE_TRANSFER : destination
```

## 2. Mapa UML del dominio

Este diagrama muestra los agregados y objetos principales, no las clases JPA.

```mermaid
classDiagram
    class Product {
        +UUID id
        +UUID publicProductId
        +String companyId
        +String sku
        +String stockUnitCode
        +ProductTrackingMode trackingMode
        +boolean allowNegativeStock
        +ProductStatus status
        +boolean isSerialized()
    }

    class UnitOfMeasure {
        +String code
        +String name
        +UnitDimension dimension
        +String baseUnitCode
        +BigDecimal conversionFactor
        +int decimals
        +BigDecimal convertTo(target, quantity)
    }

    class ProductUnitConversion {
        +UUID id
        +UUID productId
        +String fromUnitCode
        +String toUnitCode
        +BigDecimal factor
    }

    class Warehouse {
        +UUID id
        +String companyId
        +WarehouseScope scope
        +String branchId
        +WarehouseStatus status
        +boolean isOperational()
        +boolean acceptsMovements()
    }

    class WarehouseLocation {
        +UUID id
        +UUID warehouseId
        +UUID parentLocationId
        +LocationType type
        +LocationStatus status
        +BigDecimal capacity
        +boolean acceptsStock()
    }

    class StockBalance {
        +UUID productId
        +UUID warehouseId
        +UUID locationId
        +BigDecimal onHand
        +BigDecimal reserved
        +BigDecimal available()
        +StockBalance receive(quantity, unitCost)
        +StockBalance issue(quantity, allowNegative)
        +StockBalance reserve(quantity)
        +StockBalance releaseReservation(quantity)
    }

    class InventoryMovement {
        +UUID id
        +MovementType type
        +BigDecimal quantity
        +BigDecimal unitCost
        +String correlationId
        +Instant occurredAt
    }

    class StockReservation {
        +UUID id
        +BigDecimal quantity
        +ReservationStatus status
        +Instant expiresAt
    }

    class ProductKit {
        +UUID id
        +UUID kitProductId
        +KitStockMode stockMode
        +int version
    }

    class WarehouseTransfer {
        +UUID id
        +UUID sourceWarehouseId
        +UUID destinationWarehouseId
        +TransferStatus status
    }

    Product --> UnitOfMeasure : stock unit
    Product --> ProductUnitConversion : conversions
    Warehouse --> WarehouseLocation : contains
    Warehouse --> StockBalance : owns
    WarehouseLocation --> StockBalance : holds
    Product --> StockBalance : tracked
    StockBalance --> InventoryMovement : explained by
    StockBalance --> StockReservation : reserved by
    ProductKit --> Product : kit product
    ProductKit --> Product : components
    WarehouseTransfer --> Warehouse : source/destination
```

## 3. Secuencia: registrar una entrada de inventario

Este es el flujo objetivo cuando un usuario registra mercancía recibida.

```mermaid
sequenceDiagram
    actor Usuario
    participant API as Inventory API
    participant App as Application Service
    participant Product as Product Repository
    participant Warehouse as Warehouse Repository
    participant Balance as StockBalance Repository
    participant Movement as Movement Repository
    participant Cost as Costing Strategy
    participant Outbox as Outbox Repository
    participant DB as H2 / PostgreSQL
    participant Rabbit as RabbitMQ

    Usuario->>API: POST /inventory/stock/receipts
    API->>API: Validar JWT, permisos e Idempotency-Key
    API->>App: RegisterReceipt(command)
    App->>Product: Buscar producto y unidad
    Product-->>App: Producto válido
    App->>Warehouse: Validar almacén, ubicación y sucursal
    Warehouse-->>App: Contexto operativo válido
    App->>Cost: Calcular costo de entrada
    Cost-->>App: unitCost y totalCost
    App->>Balance: Obtener saldo con bloqueo/version
    Balance-->>App: Saldo actual
    App->>Balance: receive(quantity, unitCost)
    App->>Movement: Crear movimiento RECEIPT
    App->>Outbox: Registrar inventory.stock.received
    App->>DB: Commit saldo + movimiento + Outbox
    DB-->>App: Transacción confirmada
    App-->>API: 201 Created
    API-->>Usuario: Movimiento y saldo resultantes
    Outbox->>Rabbit: Publicar evento
    Rabbit-->>Outbox: Confirmación del broker
```

## 4. Secuencia: fraccionar un kit

```mermaid
sequenceDiagram
    actor Usuario
    participant API as Inventory API
    participant App as Kit Breakdown Service
    participant Kit as Kit Repository
    participant Balance as StockBalance Repository
    participant Cost as Costing Strategy
    participant Movement as Movement Repository
    participant Outbox as Outbox Repository
    participant DB as Base de datos

    Usuario->>API: POST /inventory/kits/{kitId}/breakdowns
    API->>App: BreakdownKit(command)
    App->>Kit: Obtener composición versionada
    Kit-->>App: Kit y componentes
    App->>Balance: Bloquear stock del kit
    Balance-->>App: Stock suficiente
    App->>Cost: Distribuir costo proporcionalmente
    Cost-->>App: Costos de componentes
    App->>Movement: Crear KIT_BREAKDOWN_OUT
    App->>Movement: Crear KIT_BREAKDOWN_IN por componente
    App->>Outbox: Registrar inventory.kit.breakdown.posted
    App->>DB: Commit atómico
    DB-->>App: Confirmación
    App-->>API: 201 Created
    API-->>Usuario: Fraccionamiento registrado
```

## 5. Diagrama de eventos síncronos

Los eventos síncronos forman parte de la operación actual. El emisor espera la respuesta del consumidor antes de completar el flujo. Se muestran en naranja.

```mermaid
flowchart LR
    classDef sync fill:#fff0d9,stroke:#d97706,stroke-width:2px,color:#7c2d12
    classDef syncStore fill:#ffedd5,stroke:#ea580c,stroke-width:2px,color:#7c2d12
    classDef syncResult fill:#dcfce7,stroke:#16a34a,stroke-width:2px,color:#14532d

    API[Inventory API]:::sync
    APP[Application Service]:::sync
    VALIDATE[Validar empresa, sucursal, producto, almacén y permisos]:::sync
    DB[(Base de datos)]:::syncStore
    RESULT[Respuesta HTTP: éxito o error de negocio]:::syncResult

    API -->|comando| APP
    APP --> VALIDATE
    VALIDATE -->|válido| DB
    DB -->|commit confirmado| RESULT
    VALIDATE -->|inválido| RESULT
    LEGEND["Síncrono: naranja = ejecución inmediata"]:::sync
```

Ejemplos: registrar un producto, crear o activar un almacén, registrar una entrada/salida/ajuste, crear una reserva, recibir una transferencia o fraccionar un kit.

## 6. Diagrama de eventos asíncronos

Los eventos asíncronos se persisten en Outbox dentro de la transacción y se publican posteriormente en RabbitMQ. Se muestran en azul; los consumidores pueden procesarlos sin bloquear la respuesta HTTP.

```mermaid
flowchart LR
    classDef async fill:#e0f2fe,stroke:#0284c7,stroke-width:2px,color:#0c4a6e
    classDef broker fill:#dbeafe,stroke:#2563eb,stroke-width:2px,color:#1e3a8a
    classDef consumer fill:#ede9fe,stroke:#7c3aed,stroke-width:2px,color:#4c1d95
    classDef external fill:#f3f4f6,stroke:#6b7280,stroke-width:2px,color:#1f2937

    COMMAND[Operación confirmada]:::async
    TX[(Transacción)]:::async
    OUTBOX[(Outbox inventory)]:::async
    DISPATCHER[Outbox Dispatcher]:::async
    RABBIT[[RabbitMQ<br/>jcomercy.events]]:::broker
    WAREHOUSE[Consumidor de almacenes]:::consumer
    SALES[Consumidor de ventas]:::consumer
    ACCOUNTING[Contabilidad futura]:::consumer
    COMPANY[company-management<br/>company.created / branch.created]:::external

    COMMAND --> TX
    TX -->|persistir cambio y evento| OUTBOX
    OUTBOX -->|publicación posterior| DISPATCHER
    DISPATCHER -->|publish + confirm| RABBIT
    RABBIT --> WAREHOUSE
    RABBIT --> SALES
    RABBIT --> ACCOUNTING
    COMPANY -->|eventos de contexto| RABBIT
    LEGEND["Asíncrono: azul = Outbox/RabbitMQ; morado = consumidor"]:::async
```

### Eventos publicados por Inventory

| Evento | Productor | Consumidores previstos |
|---|---|---|
| `warehouse.created` | Inventory | Almacenes, auditoría |
| `warehouse.activated` | Inventory | Almacenes, módulos contratados |
| `inventory.stock.received` | Inventory | Compras, contabilidad, reportes |
| `inventory.stock.issued` | Inventory | Ventas, contabilidad, reportes |
| `inventory.stock.adjusted` | Inventory | Auditoría, contabilidad |
| `inventory.stock.reserved` | Inventory | Ventas, pedidos |
| `inventory.transfer.*` | Inventory | Almacenes, logística |
| `inventory.kit.breakdown.*` | Inventory | Ventas, contabilidad |

### Eventos consumidos por Inventory

| Evento | Productor | Uso en Inventory |
|---|---|---|
| `company.created` | company-management | Crear contexto de empresa |
| `branch.created` | company-management | Registrar sucursal disponible |
| `branch.activated` | company-management | Habilitar operación de inventario |

## 7. Leyenda de implementación

- Las referencias a `companyId` y `branchId` no son FK porque pertenecen a `company-management`.
- Las flechas hacia `Outbox` representan persistencia transaccional; RabbitMQ se publica después mediante el dispatcher.
- La estrategia de costo no modifica el stock físico: calcula costos para movimientos, saldos y reportes.
- Los diagramas del modelo objetivo incluyen entidades aún no migradas a H2.
