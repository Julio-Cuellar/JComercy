# Diseño del módulo de ventas

Plan aprobado el 2026-11-07 para el módulo `sales`. Contratos: [openapi-sales-v1.yaml](openapi-sales-v1.yaml) y [asyncapi-sales-v1.yaml](asyncapi-sales-v1.yaml). Cada etapa queda en la [bitácora](bitacora.md).

## 1. Qué es "ventas"

Lo que pasa en el mostrador de una sucursal cuando alguien compra: se eligen productos, se cobra y se entrega un ticket con folio. Es el trabajo diario de un negocio pequeño y no existía en el sistema.

**Entra ahora:** venta de mostrador de punta a punta (productos, cobro, folio, descuento de inventario, cancelación y eventos), con la costura lista para otros canales.

**No entra:** el conector de Mercado Libre (frente aparte, en el módulo de integraciones), clientes, devoluciones parciales, listas de precios, descuentos, crédito, turnos de caja, facturación fiscal (CFDI) y suscripciones (otro plan).

## 2. Un solo núcleo, varios canales

La venta es única: una venta de Mercado Libre y una de mostrador son lo mismo para el dueño. A cada venta se le guarda de dónde vino:

- `channel`: hoy solo `MOSTRADOR`.
- `externalReference`: el identificador de la venta en el canal de origen; vacío en mostrador. Hay unicidad por empresa, canal y referencia externa, y de ahí saldrá la protección contra duplicados de los canales externos.

Cada canal es una **puerta** que traduce al mismo núcleo y con **su propia política**. La política decide tres cosas que cambian por canal:

| | Mostrador | Un canal externo (futuro) |
|---|---|---|
| Precio | Sale de la lista de la empresa | Lo trae el canal |
| Pago | Se registra dentro de la venta | Ya está cobrado por el canal |
| Sin stock | Se rechaza la venta | No se puede rechazar: se registra y se avisa |

Hoy solo existe la política de mostrador, y no hay ningún endpoint externo. El estado de atención para ventas externas sin stock queda **reservado en el contrato** y se implementará con el conector.

## 3. Cómo se comunican Ventas e Inventario

- **Sales no toca las tablas, repositorios ni entidades de Inventory.** Le hace peticiones por su API REST y reenvía el token de quien vende, así que Inventory sigue decidiendo con sus propios permisos.
- **Apartar, confirmar y liberar stock** va por REST síncrono, porque quien cobra necesita la respuesta en ese momento.
- **Avisar lo ya ocurrido** va por eventos: `sales.sale.completed` y `sales.sale.cancelled`, por Outbox y RabbitMQ.

## 4. Decisiones

| # | Decisión | Respuesta |
|---|---|---|
| D1 | Quién puede vender | `STAFF`: `sales.create` y `sales.read`. `BRANCH_MANAGER`: además `sales.cancel` y `sales.prices.write`. `COMPANY_ADMIN`: `sales.*` |
| D2 | Dónde vive el precio | En Sales (`sales.product_price`); la petición de mostrador no trae precios |
| D3 | Permiso hacia Inventory | `inventory.reservations.write`, nuevo y angosto, aceptado por las rutas de reserva además de `inventory.stock.write` |
| D4 | Validación del token | Se copia el filtro JWT de Inventory; extraerlo a `jcomercy-core` queda como deuda |
| D5 | Almacén de la venta | La petición lleva `warehouseId`; ligar sucursal con almacén es otro frente |
| D6 | IVA | Los precios son finales, con IVA incluido y sin desglose |

## 5. Cómo funciona una venta de mostrador

1. Llega `POST /companies/{c}/branches/{b}/sales` con `Idempotency-Key`, líneas (producto y cantidad) y pagos.
2. La venta nace con la política de su canal. Ventas busca el precio de cada línea y calcula el total; si falta un precio, rechaza.
3. Guarda la venta en `PENDING` y aparta el stock en Inventory, una referencia `SALE` por línea, con expiración. Si falla una línea, libera las anteriores.
4. Valida los pagos. La suma debe cubrir el total. El efectivo da cambio; tarjeta y transferencia deben ser exactas. Nunca se guarda un número de tarjeta, solo una referencia opcional.
5. Pasa a `CONFIRMING` y confirma cada reserva con una llave de idempotencia derivada. Después queda `COMPLETED`, con su folio por sucursal (`TCK-000123`).
6. **No hay job de reintento** (se cambió al implementar S6, BIT-065). Si inventario no responde al confirmar, la venta queda en `CONFIRMING`, ya cobrada, y se retoma **repitiendo la misma petición con la misma `Idempotency-Key` y la misma persona**: cada llamada a inventario lleva una llave que sale de la venta y de la línea (`sale-<id>-commit-1`) y un cuerpo estable, así que inventario no repite lo que ya hizo. Un job no tiene el token de quien vende, que es con el que inventario decide, y guardar tokens ajenos sería peor.
7. Cancelar una venta `PENDING` libera su reserva. Cancelar una `COMPLETED` pide motivo y `sales.cancel`, y regresa el stock con la devolución nueva de Inventory. Se cancela completa.

## 6. Etapas

| Etapa | Qué se hace |
|---|---|
| S0 | Contratos y documentos (este) |
| S1 | Permisos: migración de roles en company-management y `inventory.reservations.write` en Inventory |
| S2 | Esqueleto del módulo `sales`: puerto 8082, esquema `sales`, Flyway, filtro JWT |
| S3 | Dominio: venta, línea, pago, estados, totales, cambio, política de canal |
| S4 | Precios, folio por sucursal y persistencia |
| S5 | Inventory: operación de devolución |
| S6 | Cobrar: adaptador HTTP a Inventory, apartado, pago, confirmación, compensación y reentrada con la misma llave |
| S7 | Cancelación |
| S8 | Eventos con Outbox y RabbitMQ |
| S9 | Cierre: prueba a mano con las tres aplicaciones, suite completa y documentos |

## 7. Riesgos

| Riesgo | Cómo se atiende |
|---|---|
| Ventas e Inventory no comparten transacción | Estado `CONFIRMING`, llaves deterministas y cuerpos estables hacia inventario, y reentrada repitiendo la petición con la misma llave. Una venta a la que inventario rechaza confirmar queda en `CONFIRMING` sin salida automática: pendiente de resolver a mano |
| Productos con lote o serie: la devolución no sabría a qué saldo volver | La primera versión solo vende productos sin seguimiento; los demás se rechazan con un mensaje claro |
| La política por canal se sobrediseña sin un segundo canal real | Solo un parámetro con tres reglas y una sola implementación |
| Tercera copia del filtro JWT, del Outbox y de la idempotencia | Deuda anotada en la bitácora; extraerlo es otro frente |
| Los roles propios que ya existan no reciben `sales.*` | Solo se siembran los roles del sistema; cada empresa concede lo demás |
| El frontend es un mock | Fuera de alcance; la pantalla de venta va en una sesión aparte |
