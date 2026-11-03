# Orden de trabajo: siguiente etapa del módulo Inventory

## Identificación

| Campo | Valor |
|---|---|
| Orden | OT-INV-002 |
| Módulo | Inventory / Warehouses |
| Prioridad | Alta |
| Estado | Pendiente de ejecución |
| Ambiente | H2 local |
| Rama de trabajo | Desarrollo |
| Dependencias | JWT vigente, módulo `company-management` y RabbitMQ local |

## Objetivo

Completar la base operativa de inventarios para que las operaciones de stock puedan probarse manualmente, sean idempotentes, publiquen eventos mediante Outbox hacia RabbitMQ y estén disponibles también para el cliente móvil mediante GraphQL.

## Situación actual

Actualmente el módulo cuenta con:

- Catálogo independiente de productos.
- Unidades globales y unidades personalizadas por empresa.
- Conversiones de unidades por producto.
- Almacenes propios y compartidos.
- Acceso de almacenes compartidos por sucursal.
- Ubicaciones jerárquicas.
- Activación explícita de almacenes.
- Entradas, salidas y ajustes de inventario.
- Saldos y kardex.
- Control de lotes, caducidades y números seriales.
- Generación secuencial de seriales.
- Bloqueo pesimista para actualizar saldos.
- Logs de peticiones, operaciones y errores.
- Pruebas de integración con H2.

## Alcance

### 1. Completar la colección Postman

Agregar una carpeta `4. Stock` a:

`documentacion/postman/JComercy-Company-Management.postman_collection.json`

Debe contener, en orden:

1. Activar almacén.
2. Registrar entrada de inventario.
3. Registrar salida de inventario.
4. Registrar ajuste de entrada.
5. Registrar ajuste de salida.
6. Consultar saldos.
7. Consultar movimientos.
8. Probar stock por lote.
9. Probar stock serializado con generación automática.
10. Probar rechazo por stock insuficiente.
11. Probar rechazo por sucursal sin acceso a almacén compartido.

Cada petición deberá:

- Usar `{{inventoryBaseUrl}}`.
- Propagar `{{accessToken}}`.
- Generar un `Idempotency-Key` único.
- Guardar automáticamente `warehouseId`, `locationId` y `productId` cuando corresponda.
- Validar el código HTTP y los campos relevantes de la respuesta.

### 2. Idempotencia persistente

Implementar en Inventory un registro persistente de idempotencia por operación mutable.

Requisitos:

- Guardar empresa, usuario, clave, huella de la petición, estado y respuesta.
- Repetir la misma clave con la misma petición debe devolver la respuesta original.
- Reutilizar la clave con una petición diferente debe responder conflicto.
- La clave debe estar aislada por empresa y usuario.
- La reserva de la clave debe ser atómica bajo concurrencia.
- Aplicar la regla a almacenes, ubicaciones, productos y movimientos.
- No persistir contraseñas, JWT ni información sensible dentro de la respuesta almacenada.

### 3. Publicación Outbox para eventos de inventario

Crear el Outbox propio del módulo Inventory y conectarlo al exchange RabbitMQ:

`jcomercy.events`

Eventos mínimos:

- `warehouse.created`
- `warehouse.activated`
- `inventory.stock.received`
- `inventory.stock.issued`
- `inventory.stock.adjusted`

Reglas:

- El movimiento, el saldo y el Outbox deben confirmarse en la misma transacción.
- El despachador debe publicar eventos pendientes y marcar únicamente los publicados.
- Debe conservarse el evento cuando RabbitMQ no esté disponible.
- El consumidor debe soportar redelivery e idempotencia.
- Cada evento debe incluir `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `correlationId`, `companyId`, `branchId`, `actorUserId` y `data`.
- Agregar logs de publicación, reintento, confirmación y error.

### 4. Autorización y reglas de negocio pendientes

Completar las siguientes validaciones:

- Verificar que el acceso a un almacén compartido esté en estado `ACTIVE`.
- Validar que el almacén compartido tenga al menos una sucursal autorizada antes de activarse.
- Validar que empresa y sucursal estén activas antes de activar un almacén.
- Impedir más de un almacén predeterminado por sucursal.
- Exigir permisos específicos: `inventory.stock.read`, `inventory.stock.adjust`, `inventory.stock.negative.enable` y `inventory.warehouses.manage`.
- Permitir stock negativo únicamente con configuración y permiso explícito.
- Mantener separación entre `companyId`, `branchId` y alcance del almacén.

### 5. GraphQL para consumo móvil

Agregar consultas protegidas para:

- `stockBalances(companyId, productId)`.
- `inventoryMovements(companyId, productId)`.
- `companyUnits(companyId)`.

Aplicar:

- Autorización por empresa y sucursal.
- DataLoader para evitar consultas N+1.
- Límites de profundidad y complejidad ya configurados.
- Persisted queries.
- Rate limiting.
- Pruebas de consultas no autorizadas, profundas y excesivamente complejas.

## Fuera de alcance

No se incluyen en esta orden:

- Reservas de inventario.
- Transferencias entre almacenes.
- Kits y fraccionamiento.
- Métodos de valuación FIFO, promedio ponderado, estándar o identificación específica.
- Migración a PostgreSQL.
- Desarrollo de la aplicación móvil.

## Criterios de aceptación

La orden se considerará terminada cuando:

- La colección Postman ejecute el flujo completo de stock sin editar manualmente los identificadores generados.
- Repetir una petición con la misma clave no cree un segundo movimiento ni cambie el saldo.
- Una misma clave con un cuerpo diferente sea rechazada.
- Se pueda detener RabbitMQ sin perder eventos registrados en Outbox.
- Al restablecer RabbitMQ, los eventos pendientes se publiquen correctamente.
- Los eventos de stock sean visibles en el exchange `jcomercy.events`.
- Las operaciones no autorizadas respondan `403` sin modificar datos.
- GraphQL devuelva únicamente información dentro del alcance del JWT.
- Las pruebas cubran éxito, duplicidad, concurrencia, permisos, redelivery y errores de RabbitMQ.
- Las migraciones H2 ejecuten correctamente.
- La compilación y las pruebas Maven terminen sin errores.

## Pruebas requeridas

### Unitarias

- Huella y reutilización de claves de idempotencia.
- Conflicto por clave con cuerpo diferente.
- Reglas de almacén compartido.
- Reglas de almacén predeterminado.
- Reglas de stock negativo.
- Autorización por permiso y alcance.
- Construcción del envelope de eventos.

### Integración

- Entrada idempotente.
- Salida idempotente.
- Ajustes idempotentes.
- Dos operaciones concurrentes sobre el mismo saldo.
- Publicación Outbox con RabbitMQ disponible.
- Persistencia Outbox con RabbitMQ no disponible.
- Redelivery del mismo evento.
- Consultas GraphQL autorizadas y rechazadas.

## Entregables

- Código fuente de idempotencia, Outbox, autorización y GraphQL.
- Migraciones Flyway nuevas.
- Colección Postman actualizada.
- Contrato OpenAPI actualizado.
- Esquema GraphQL actualizado.
- Pruebas unitarias y de integración.
- Documentación de eventos y ejecución manual.
- Informe de validación con resultados de Maven y pruebas de RabbitMQ.

## Siguiente orden posterior

Una vez aceptada esta orden, la siguiente etapa será implementar reservas con expiración configurable. Después continuarán transferencias, métodos de valuación y kits con fraccionamiento.
