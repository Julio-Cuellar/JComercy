# Bitácora de desarrollo de JComercy

Registro cronológico de lo que se hace en el proyecto. Se actualiza **en cada cambio**, sin que nadie lo pida.

## Cómo se llena

**Cada entrada de trabajo** lleva:

- **Qué se hizo:** el cambio, en una o dos líneas.
- **Cómo se hizo:** enfoque y archivos tocados. En cambios de código, la evidencia RED (la prueba falla) y GREEN (la prueba pasa).
- **Afectaciones:** qué otra parte del sistema cambia o podría cambiar (contratos, datos, eventos, pruebas).

**Cada error** lleva:

- **De qué trata:** descripción del problema.
- **Cuándo se manifiesta:** condiciones exactas en que aparece.
- **Cómo mitigarlo:** qué hacer mientras no esté resuelto.
- **Estado:** `ABIERTO` o `RESUELTO`. Si está resuelto, **cómo se resolvió**.

Los errores se numeran `ERR-001`, `ERR-002`… y las entradas de trabajo, `BIT-001`, `BIT-002`…

---

## Índice de errores

| ID | Resumen | Estado |
|---|---|---|
| ERR-001 | `AccountIntegrationTest` depende del orden de ejecución de las pruebas | RESUELTO (BIT-002) |
| ERR-002 | Prueba de reintentos del Outbox falla de forma intermitente por competir con el despachador real | RESUELTO (BIT-006) |
| ERR-003 | Rutas inexistentes y métodos no permitidos responden 500 en vez de 404/405 | RESUELTO (BIT-012 inventario, BIT-013 company-management) |
| ERR-004 | La prueba de concurrencia de reservas falla al repetirse por usar referencias fijas | RESUELTO (BIT-009) |
| ERR-005 | El 500 de company-management devuelve el mensaje interno de la excepción y no la registra | RESUELTO (BIT-014) |

---

## 2026-11-02

### BIT-001 · Creación de la bitácora

- **Qué se hizo:** se creó este archivo y se definió el formato de registro.
- **Cómo se hizo:** archivo nuevo en `documentacion/bitacora.md`. Contexto: se ejecuta la fase 0 (saneamiento) del plan del MVP de venta en línea.
- **Afectaciones:** ninguna sobre el código. A partir de aquí toda tarea termina con su entrada.

### BIT-002 · Prueba de registro de cuenta aislada del resto de la suite (fase 0)

- **Qué se hizo:** `AccountIntegrationTest.shouldRegisterAccountAndPublishOutboxEvent` ahora busca el evento Outbox de **su** cuenta y no el primero que encuentre. Resuelve ERR-001.
- **Cómo se hizo:** en [AccountIntegrationTest.java](../company-management/company-management-infra/src/test/java/com/jcode/jcomercy/companymanagement/infra/AccountIntegrationTest.java) se lee `publicAccountId` de la respuesta HTTP y se filtra `account.created` por los eventos cuyo `payloadJson` contiene ese ID.
  - **RED:** `mvn test` completo del 2026-11-02 → `AccountIntegrationTest` falla con `"name":"Holding Test"` (14 de 15 pruebas en `company-management-infra`).
  - **GREEN:** `mvn -B test -pl company-management/company-management-infra -am` → 15 de 15, `BUILD SUCCESS`, con `CompanyIntegrationTest` ejecutándose antes (el mismo orden que provocaba el fallo).
- **Afectaciones:** solo código de prueba. No cambia el comportamiento de producción ni los contratos. El informe de validación `informe-validacion-implementacion-company-management.md` ya afirmaba que todas las pruebas pasaban; con este cambio vuelve a ser cierto.

### BIT-003 · Los eventos de almacén registran al usuario real (fase 0)

- **Qué se hizo:** `warehouse.created` y `warehouse.activated` dejaron de publicarse con actor `"SYSTEM"`; ahora llevan el `sub` del JWT del usuario que ejecutó la acción.
- **Cómo se hizo:**
  - Prueba nueva: [WarehouseEventActorIntegrationTest.java](../inventory/inventory-infra/src/test/java/com/jcode/jcomercy/inventory/infra/WarehouseEventActorIntegrationTest.java). Crea y activa un almacén por REST con un JWT y revisa el `actorUserId` de los eventos Outbox de su propia empresa.
  - Código: en [WarehouseApplicationService.java](../inventory/inventory-application/src/main/java/com/jcode/jcomercy/inventory/application/service/WarehouseApplicationService.java), `CreateWarehouseCommand` y `CreateBranchWarehouseCommand` ganan el campo `actorUserId`, y `activate(UUID)` pasa a `activate(UUID, String actorUserId)`. En [WarehouseController.java](../inventory/inventory-infra/src/main/java/com/jcode/jcomercy/inventory/infra/web/controller/WarehouseController.java) los tres endpoints pasan `actor(authentication)`.
  - **RED:** `mvn -B test -pl inventory/inventory-infra -am -Dtest=WarehouseEventActorIntegrationTest` → falla con `["SYSTEM", "SYSTEM"]` en lugar del usuario.
  - **GREEN:** misma prueba dentro de la suite completa de `inventory-infra` → 20 de 20, `BUILD SUCCESS`.
- **Afectaciones:**
  - **Eventos:** `actorUserId` cambia de `"SYSTEM"` a un ID de usuario. Cualquier consumidor que filtre por `"SYSTEM"` verá otro valor. Hoy no existe ninguno.
  - **API pública:** no cambia (rutas, cuerpos y códigos HTTP iguales).
  - **API interna:** cambian las firmas de `activate` y de los dos comandos de creación. Los únicos llamadores eran el controlador y el propio servicio.
  - **Pendiente menor:** estos eventos siguen sin `correlationId` propio (el adaptador genera uno aleatorio). Los de stock sí lo reciben de la cabecera `X-Correlation-Id`.

### BIT-004 · El código HTTP de inventario depende del tipo de error, no del texto (fase 0)

- **Qué se hizo:** `InventoryExceptionHandler` dejó de decidir el 404 con `message.contains("not found")`. Ahora lo decide un tipo de error explícito.
- **Cómo se hizo:**
  - [InventoryApplicationException.java](../inventory/inventory-application/src/main/java/com/jcode/jcomercy/inventory/application/exception/InventoryApplicationException.java) gana `enum Kind { NOT_FOUND, CONFLICT }`, el método `kind()` y la fábrica `notFound(String)`. El constructor existente `new InventoryApplicationException(String)` sigue significando "conflicto" (409), así que los ~30 puntos que lo usan no cambian.
  - Los 10 puntos que lanzaban "…not found" por constructor pasan a `InventoryApplicationException.notFound(...)`: `ProductApplicationService` (3), `WarehouseApplicationService` (3) y `StockApplicationService` (4), con `sed` y verificación por `grep`.
  - [InventoryExceptionHandler.java](../inventory/inventory-infra/src/main/java/com/jcode/jcomercy/inventory/infra/web/exception/InventoryExceptionHandler.java) usa `exception.kind()`.
  - Pruebas nuevas en [InventoryExceptionHandlerTest.java](../inventory/inventory-infra/src/test/java/com/jcode/jcomercy/inventory/infra/web/exception/InventoryExceptionHandlerTest.java): un conflicto con "not found" en el texto responde 409, y `notFound(...)` responde 404 conservando `code` y `message`.
  - **RED:** `-Dtest=InventoryExceptionHandlerTest` → `expected: 409 CONFLICT but was: 404 NOT_FOUND`.
  - **GREEN:** suite completa de `inventory-infra` → 22 de 22, `BUILD SUCCESS`.
- **Afectaciones:**
  - **API pública:** ninguna. Mismos códigos HTTP para los casos existentes, mismo cuerpo (`code` y `message`).
  - **Riesgo evitado:** reformular o traducir un mensaje ya no cambia el código HTTP por accidente.
  - **Regla nueva para el equipo:** todo recurso inexistente debe lanzarse con `InventoryApplicationException.notFound(...)`; si se usa el constructor, responderá 409.

### BIT-005 · Se retira `.audit-medicloud-inventory/` del repositorio (fase 0)

- **Qué se hizo:** se eliminó la carpeta `.audit-medicloud-inventory/` (115 archivos, 792 KB), una copia de código de Clinerya (`clinerya-core`, `clinerya-app`, `clinerya-inventory`) que había entrado en el primer commit.
- **Cómo se hizo:** antes de borrar se comprobó que no tenía cambios sin guardar, que ningún archivo fuera de ella la referenciaba, que no formaba parte del `pom.xml` raíz y que no estaba en `.gitignore`. Luego `git rm -r -q .audit-medicloud-inventory`. El borrado queda en el índice, **sin commit**.
- **Afectaciones:**
  - **Compilación y pruebas:** ninguna, la carpeta nunca fue un módulo Maven.
  - **Regla de separación:** el árbol de trabajo vuelve a cumplir "Clinerya conserva su repositorio" (`README.md`).
  - **Historial:** el commit `1550609` **sigue conteniendo** ese código. Quitarlo del historial exigiría reescribirlo (`git filter-repo`), que no se hizo porque cambia los hashes de la rama compartida; decisión pendiente del equipo.
  - **Reversión:** `git restore --source=HEAD --staged --worktree .audit-medicloud-inventory`.

### BIT-006 · El despachador del Outbox de inventario se puede apagar; las pruebas lo apagan (fase 0)

- **Qué se hizo:** `InventoryOutboxDispatcher` ahora se activa con la propiedad `outbox.publisher.enabled` (por defecto `true`) y las pruebas de `inventory-infra` la ponen en `false`. Resuelve ERR-002.
- **Cómo se hizo:**
  - [InventoryOutboxDispatcher.java](../inventory/inventory-infra/src/main/java/com/jcode/jcomercy/inventory/infra/outbox/InventoryOutboxDispatcher.java): se agrega `@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)`.
  - Archivo nuevo [application.properties](../inventory/inventory-infra/src/test/resources/application.properties) en los recursos de prueba, con `outbox.publisher.enabled=false`.
  - **RED (reproducción determinista):** `mvn … -Dtest=InventoryOutboxIntegrationTest -Doutbox.publisher.fixed-delay-ms=20` → falla 3 de 3 corridas con `retryCount` de 21, 18 y 20 en lugar de 1.
  - **GREEN:** la misma orden → 3 de 3 corridas con `Tests run: 3, Failures: 0`.
  - **Comprobación inversa:** con `-Doutbox.publisher.enabled=true` el despachador vuelve a girar (30 ticks) y la prueba vuelve a fallar con `but was: 15`, así que el interruptor funciona en ambos sentidos.
  - **Verificación final:** `mvn -B test` en la raíz → `BUILD SUCCESS`, 70 pruebas (core 3, company-management 8 + 7 + 15, inventory 15 + 22).
- **Afectaciones:**
  - **Producción:** ninguna por defecto. Sin la propiedad, el despachador se comporta igual que antes.
  - **Pruebas de inventario:** ya no se publican eventos al Outbox en segundo plano; quien necesite publicar debe llamar a `publishPending()` explícitamente, como ya hace `InventoryOutboxIntegrationTest`.
  - **Para el monolito (fase 0b):** el interruptor `outbox.publisher.enabled` queda disponible para decidir qué despachador corre en cada despliegue.
  - **No revisado:** `OutboxDispatcher` de `company-management` podría tener el mismo riesgo si algún día se prueba con el planificador activo; hoy sus pruebas no lo ejercitan.

### BIT-007 · Cierre de la fase 0 (saneamiento)

- **Qué se hizo:** se completó la fase 0 del plan del MVP: BIT-002 a BIT-006 más la bitácora (BIT-001) y su regla permanente.
- **Cómo se hizo:** la regla de registrar cada cambio se agregó como sección 39 de [AGENTS.md](../AGENTS.md), y la bitácora se enlazó desde [README.md](../README.md).
- **Estado verificado:** `mvn -B test` en la raíz → `BUILD SUCCESS`, 70 de 70 pruebas.
- **Pendiente de la fase 0 (no se hizo en esta tanda; queda por confirmar el orden con el equipo):**
  - Datos logísticos y de costo del producto (`V008__inventory_add_product_logistics_and_cost.sql`).
  - Reglas de OT-INV-002: un solo almacén predeterminado por sucursal verificado a fondo, permisos específicos y stock negativo.
  - Colección Postman de la carpeta `4. Stock`.
- **Sin commit:** todos los cambios están en el árbol de trabajo (incluido el borrado de BIT-005, que está en el índice). Nada se ha subido ni confirmado.

## Fase 1: reservas de stock

Contexto: las ventas en línea necesitan apartar stock al recibir el pedido, descontarlo al despachar y liberarlo si se cancela. Antes de esto el saldo ya tenía la columna `reserved` y `StockBalance.reserve()`, pero nada registraba *quién* reservó ni permitía confirmar o expirar.

### BIT-008 · Dominio de reservas (fase 1)

- **Qué se hizo:** se agregó el modelo de reserva y la confirmación de reservas sobre el saldo.
- **Cómo se hizo:**
  - [ReservationStatus.java](../inventory/inventory-domain/src/main/java/com/jcode/jcomercy/inventory/domain/model/stock/ReservationStatus.java): `ACTIVE`, `COMMITTED`, `RELEASED`, `EXPIRED`.
  - [StockReservation.java](../inventory/inventory-domain/src/main/java/com/jcode/jcomercy/inventory/domain/model/stock/StockReservation.java): una reserva apunta a **un saldo concreto** y a una **referencia de negocio** (por ejemplo una línea de pedido). Sale de `ACTIVE` una sola vez (`commit`, `release` o `expire`). Las referencias se normalizan a mayúsculas para que la búsqueda no distinga mayúsculas. `expire` solo procede si ya pasó el plazo.
  - [StockBalance.java](../inventory/inventory-domain/src/main/java/com/jcode/jcomercy/inventory/domain/model/stock/StockBalance.java): nuevo `commitReservation(qty)`, que descuenta existencias y reservado a la vez. No se reutilizó `issue()` porque ese método resta lo reservado de la disponibilidad y rechazaría el despacho.
  - **RED:** `mvn test -pl inventory/inventory-domain` → error de compilación por `StockReservation`, `ReservationStatus` y `commitReservation` inexistentes (RED de compilación; las pruebas nuevas son `StockReservationTest` y 4 casos en `StockBalanceTest`).
  - **GREEN:** 27 de 27 pruebas del dominio (15 anteriores + 12 nuevas).
- **Afectaciones:** solo se agregan tipos y un método; nada existente cambia de comportamiento. También se agregaron pruebas de `releaseReservation`, que no tenía ninguna.

### BIT-009 · Persistencia, servicio y REST de reservas (fase 1)

- **Qué se hizo:** reservar, liberar y confirmar por referencia, más la consulta de disponibilidad, con eventos en el Outbox.
- **Cómo se hizo:**
  - **Migración** [V008__inventory_create_stock_reservation.sql](../inventory/inventory-infra/src/main/resources/db/migration/V008__inventory_create_stock_reservation.sql): tabla `inventory.stock_reservation` con claves foráneas, restricciones de cantidad y estado, e índices por referencia, por vencimiento y por saldo. El plan decía `V009`, pero `V008` (datos logísticos del producto) no se ha hecho, así que ésta tomó el siguiente número libre.
  - **Puertos y adaptadores:** `StockReservationRepositoryPort` (nuevo) y dos métodos nuevos en `StockBalanceRepositoryPort` (`findAllocatableForUpdate`, `findByIdForUpdate`) con bloqueo pesimista ordenado por id.
  - **Servicio** [ReservationApplicationService.java](../inventory/inventory-application/src/main/java/com/jcode/jcomercy/inventory/application/service/ReservationApplicationService.java):
    - `reserve` reparte la cantidad entre los saldos del producto en el almacén si ninguno alcanza solo; opcionalmente se limita a una ubicación.
    - `release` y `commit` operan sobre **toda la referencia**. `commit` descuenta el saldo y crea el movimiento `ISSUE` en el kárdex con la referencia.
    - **Orden de bloqueo:** primero se bloquean los saldos y recién después se comprueba la referencia duplicada y se leen las reservas. Así dos reservas simultáneas se serializan y la segunda ve lo que la primera confirmó.
    - **Regla:** una referencia cubre un producto en un almacén. Un pedido con varios productos usa una referencia por línea (por ejemplo `ML-123-1`, `ML-123-2`).
  - **REST** [ReservationController.java](../inventory/inventory-infra/src/main/java/com/jcode/jcomercy/inventory/infra/web/controller/ReservationController.java), bajo `/api/v1/companies/{companyId}`:
    - `POST /branches/{branchId}/stock/reservations`, `…/release`, `…/commit`: permiso `inventory.stock.write`, `Idempotency-Key` obligatorio.
    - `GET /stock/availability?productId=`: permiso `inventory.stock.read`.
    - Códigos: 201 reserva creada, 422 stock insuficiente, 409 referencia duplicada, 404 si no hay reservas activas, 403 sin permiso o sin acceso a la empresa o sucursal.
  - **Eventos (Outbox):** `inventory.reservation.created|released|committed`, y `inventory.stock.issued` al confirmar. Todos llevan el usuario real y el `available` resultante.
  - **RED:** `ReservationIntegrationTest` (9 pruebas) + `InventoryMigrationTest` → 10 de 10 fallan: la migración con `expected: 13 but was: 12` y las reservas con `Status expected:<201> but was:<500>` (ver ERR-003 sobre ese 500).
  - **GREEN:** 10 de 10 al primer intento. Luego la prueba de concurrencia se convirtió en `@RepeatedTest(10)`; con referencias fijas fallaron 9 de 10 repeticiones (ERR-004). Corregida, **18 de 18**.
  - **Comprobación de que la prueba detecta el defecto (mutación):** se quitó temporalmente el `@Lock` de `findAllocatableForUpdate` → la prueba concurrente **falla**: una de las dos peticiones responde 500 por `ObjectOptimisticLockingFailureException`. Se restauró el archivo y se verificó con `cmp` que quedó idéntico al respaldo.
- **Afectaciones:**
  - **Sobre-reserva:** imposible incluso sin el bloqueo, porque `@Version` en `stock_balance` rechaza la segunda escritura; pero sin el bloqueo el cliente recibiría un 500. El bloqueo convierte ese caso en un 422 limpio.
  - **Datos:** tabla nueva; `stock_balance.reserved` ahora se mueve también por estas operaciones.
  - **Contratos:** **no** se actualizó `openapi-inventory-v1.yaml` ni el esquema GraphQL (pendiente). Los endpoints existen pero no están documentados en el contrato.
  - **Duplicación consciente:** las validaciones de almacén (empresa, activo, sucursal) se copiaron de `StockApplicationService` en vez de extraerlas, para no tocar ese servicio en esta fase. Conviene unificarlas después.

### BIT-010 · Expiración de reservas (fase 1)

- **Qué se hizo:** las reservas pueden llevar un plazo (`expiresAt`); al vencer se libera el stock y se publica `inventory.reservation.expired` con actor `SYSTEM`.
- **Cómo se hizo:**
  - `ReservationApplicationService.expireDue(now, limit)` y el trabajo [ReservationExpirationJob.java](../inventory/inventory-infra/src/main/java/com/jcode/jcomercy/inventory/infra/reservation/ReservationExpirationJob.java), que corre cada 60 s por defecto (`inventory.reservations.expiration.fixed-delay-ms`).
  - Se puede apagar con `inventory.reservations.expiration.enabled=false`; las pruebas lo apagan en [application.properties](../inventory/inventory-infra/src/test/resources/application.properties) para evitar la carrera de ERR-002.
  - Un plazo ya vencido al reservar se rechaza (422).
  - **Honestidad sobre el proceso:** `expireDue` se escribió junto con el servicio, **antes** que su prueba, así que no hay RED real. Para compensarlo, tras escribir las 2 pruebas (`expiredReservationGivesTheStockBackAndCannotBeCommitted`, `deadlineInThePastIsRejected`) se hizo una **mutación**: `expireDue` marcando vencida sin devolver el stock → la prueba falla con `expected: 7 but was: 3.0` y las otras 19 siguen pasando. Se restauró el archivo y se verificó con `cmp`.
  - **GREEN:** 20 de 20 en `ReservationIntegrationTest`.
- **Afectaciones:** en producción el trabajo está **activo por defecto**. Si una reserva se confirma justo mientras el trabajo la procesa, `@Version` en la reserva hace fallar una de las dos transacciones y se revierte; el trabajo reintenta en el siguiente ciclo.

### BIT-011 · Verificación de la fase 1

- **Estado verificado:** `mvn -B test` en la raíz → `BUILD SUCCESS`, **102 pruebas**: core 3, company-management 8 + 7 + 15, inventory dominio 27 e infraestructura 42.
- **Desviaciones respecto al plan (decididas por mí al diseñar; quedan por confirmar):**
  - No hay evento `inventory.stock.available_changed`: el `available` viaja dentro de cada evento de reserva.
  - No hay `inventory.reservation.failed`: por REST el rechazo es síncrono (422). Hará falta cuando el canal de venta reserve de forma asíncrona.
  - Solo se puede liberar o confirmar una referencia completa, no parcialmente.
  - Los saldos con lote o serie se reparten sin criterio de caducidad (no hay FEFO).
- **Pendiente:** contrato OpenAPI y GraphQL, y los pendientes de la fase 0 (datos logísticos del producto, reglas de OT-INV-002, colección Postman). ERR-003 se atendió en BIT-012.
- **Commits (sin push):** `787dd67` dominio, `1de6118` persistencia (V008), `05d0694` servicio, REST y trabajo de expiración, `ef19401` esta bitácora.

### BIT-012 · Rutas inexistentes y métodos no permitidos dejan de responder 500 (resuelve ERR-003 en inventario)

- **Qué se hizo:** en inventario, una URL que no existe responde ahora `404 NOT_FOUND` y un método HTTP no permitido responde `405 METHOD_NOT_ALLOWED`, en lugar de `500 INTERNAL_SERVER_ERROR`.
- **Cómo se hizo:**
  - Prueba nueva: [InventoryRoutingErrorsIntegrationTest.java](../inventory/inventory-infra/src/test/java/com/jcode/jcomercy/inventory/infra/web/exception/InventoryRoutingErrorsIntegrationTest.java), con un JWT válido: `GET …/stock/reservaciones` (no existe) → 404 y `DELETE …/stock/availability` (solo existe como GET) → 405.
  - **RED:** `mvn -B -pl inventory/inventory-infra -am test -Dtest=InventoryRoutingErrorsIntegrationTest` → fallan `Status expected:<404> but was:<500>` y `Status expected:<405> but was:<500>`.
  - **Una prueba descartada:** la tercera que escribí (consultar disponibilidad sin `productId` → 400) dio 200, no 500. El parámetro es opcional a propósito (sin él devuelve todos los productos); mi suposición estaba mal, no el código. Se eliminó en lugar de forzar un comportamiento que no corresponde.
  - Código: dos manejadores en [InventoryExceptionHandler.java](../inventory/inventory-infra/src/main/java/com/jcode/jcomercy/inventory/infra/web/exception/InventoryExceptionHandler.java), para `NoResourceFoundException` y `HttpRequestMethodNotSupportedException`. El 405 conserva la cabecera `Allow` que indica qué métodos sí acepta la ruta. Los mensajes dicen qué hacer ("Revisa que esté bien escrita").
  - **GREEN:** las 2 pruebas nuevas y `InventoryExceptionHandlerTest` (2) pasan; `mvn -B test` en la raíz → `BUILD SUCCESS`, **104 pruebas** (inventory infra pasa de 42 a 44).
- **Afectaciones:**
  - **API:** esas respuestas cambian de 500 a 404/405 con el mismo formato `{code, message}`. Ningún flujo conocido dependía del 500.
  - **Registros:** una URL mal escrita ahora deja un `WARN` de una línea y no un `ERROR` con traza completa. Cualquier alerta basada en nivel `ERROR` ya no se dispara por un tecleo.
  - **Clientes con reintentos:** un 404/405 es definitivo; un cliente bien hecho ya no reintentará como lo haría ante un 500.
  - **`company-management` sigue igual** (ver ERR-003).

### BIT-013 · Rutas inexistentes y métodos no permitidos también en company-management (cierra ERR-003)

- **Qué se hizo:** en `company-management`, una URL que no existe responde ahora `404` (código `NOT_FOUND`) y un método no permitido responde `405` (código `METHOD_NOT_ALLOWED`, con la cabecera `Allow`), en el formato `ProblemDetails` que ya usa ese módulo.
- **Cómo se hizo:**
  - Prueba nueva: [CompanyRoutingErrorsIntegrationTest.java](../company-management/company-management-infra/src/test/java/com/jcode/jcomercy/companymanagement/infra/web/exception/CompanyRoutingErrorsIntegrationTest.java), con un JWT válido: `GET /api/v1/ruta-que-no-existe` → 404 y `GET /api/v1/auth/login` (solo acepta POST) → 405 con `Allow: POST`.
  - **Corrección de mi prueba antes del RED válido:** la primera versión usaba `GET /api/v1/accounts/registration` para el 405 y respondió 404. No era un defecto: esa URL coincide con `GET /accounts/{publicAccountId}` (con el id "registration") y la cuenta no existe. Se cambió a `/api/v1/auth/login`.
  - **RED:** `mvn -B -pl company-management/company-management-infra -am test -Dtest=CompanyRoutingErrorsIntegrationTest` → `Status expected:<404> but was:<500>` y `expected:<405> but was:<500>`. Con esto el defecto queda **reproducido** y no solo leído en el código.
  - Código: dos manejadores en [GlobalExceptionHandler.java](../company-management/company-management-infra/src/main/java/com/jcode/jcomercy/companymanagement/infra/web/exception/GlobalExceptionHandler.java). Los códigos y mensajes son los mismos que en inventario, para que un cliente trate igual el error en ambos módulos.
  - **GREEN:** las 2 pruebas nuevas pasan; `mvn -B test` en la raíz → `BUILD SUCCESS`, **106 pruebas** (company-management infra pasa de 15 a 17).
- **Afectaciones:**
  - **API:** esas respuestas cambian de 500 a 404/405 con el cuerpo `ProblemDetails`. Ningún flujo conocido dependía del 500.
  - **Mensajes:** el 404/405 usa un mensaje fijo en español; no depende del texto de la excepción.
  - **Sin cambios en este paso:** el manejador general de `Exception` seguía igual; se corrigió después en BIT-014 (ERR-005).

### BIT-014 · El 500 de company-management ya no muestra el mensaje interno y deja registro (resuelve ERR-005)

- **Qué se hizo:** `GlobalExceptionHandler.handleGeneral` de `company-management` responde ahora un mensaje fijo ("No pudimos completar la operación por un problema nuestro. Intenta de nuevo en unos minutos.") y registra la excepción completa con `log.error`, incluyendo la ruta pedida.
- **Cómo se hizo:**
  - Prueba nueva: [GlobalExceptionHandlerTest.java](../company-management/company-management-infra/src/test/java/com/jcode/jcomercy/companymanagement/infra/web/exception/GlobalExceptionHandlerTest.java), unitaria (sin Spring). Pasa una excepción con un texto que simula un fallo de base de datos y comprueba (1) que el `detail` de la respuesta no contiene ese texto y (2) que queda un evento `ERROR` con la ruta y la traza de la excepción, capturado con un `ListAppender` de logback.
  - **RED:** `mvn -B -pl company-management/company-management-infra -am test -Dtest=GlobalExceptionHandlerTest` → las 2 fallan: `expected: "No pudimos completar…" but was: "could not execute statement [insert into account_credential …]"` y `Expected size: 1 but was: 0` (cero eventos de log). Con esto los dos defectos de ERR-005 quedan **reproducidos**, no solo leídos en el código.
  - Código: en [GlobalExceptionHandler.java](../company-management/company-management-infra/src/main/java/com/jcode/jcomercy/companymanagement/infra/web/exception/GlobalExceptionHandler.java), `handleGeneral` llama a `log.error(…, ex)` y usa un texto fijo en `detail`. El mensaje dice qué pasó y qué hacer, sin pedir disculpas (AGENTS.md §23).
  - **GREEN:** las 2 pruebas nuevas pasan; `mvn -B test` en la raíz → `BUILD SUCCESS`, **108 pruebas** (company-management infra pasa de 17 a 19).
- **Afectaciones:**
  - **API:** solo cambia el campo `detail` del 500; `type`, `title`, `status` y `code` siguen igual. Ningún flujo conocido leía ese texto.
  - **Registros:** cada excepción inesperada de `company-management` deja ahora un `ERROR` con traza, algo que antes no pasaba en este manejador. Es lo buscado, pero sube el ruido si hay peticiones que hoy caen aquí sin deber (ver siguiente punto).
  - **Posible ruido (no verificado):** las peticiones con JSON mal formado o que fallan la validación de campos probablemente también llegan a este manejador general y responden 500 (inventario las trata aparte con 400). Si es así, ahora además dejarán un `ERROR`. No se probó; conviene revisarlo.
  - **Mensajes distintos entre módulos:** inventario sigue respondiendo "Error interno del servidor"; no se unificó.
  - **Sin cambios:** los demás manejadores de `company-management` siguen devolviendo el mensaje de su excepción, porque son reglas de negocio pensadas para mostrarse.

---

## Errores

### ERR-001 · `AccountIntegrationTest` depende del orden de ejecución

- **Estado:** RESUELTO el 2026-11-02 (BIT-002)
- **De qué trata:** la prueba `shouldRegisterAccountAndPublishOutboxEvent` busca el primer evento `account.created` de toda la tabla Outbox y espera que sea el de su cuenta. Las pruebas de integración comparten la misma base H2 en memoria, así que el primer evento puede pertenecer a otra prueba.
- **Cuándo se manifiesta:** al correr la suite completa de `company-management-infra`, si otra prueba registra una cuenta antes. Falla con `Expecting actual: {"name":"Holding Test",…} to contain: "Empresa Corporativa JComercy"` ([AccountIntegrationTest.java:81](../company-management/company-management-infra/src/test/java/com/jcode/jcomercy/companymanagement/infra/AccountIntegrationTest.java)). Detectado el 2026-11-02 al correr `mvn test`.
- **Cómo mitigarlo:** correr la prueba sola (pasa en aislamiento), o no tomar un fallo de esa prueba como regresión del código de producción.
- **Cómo se resolvió:** la prueba filtra el evento Outbox por el `publicAccountId` devuelto en la respuesta de su propio registro, así que ignora los eventos de otras pruebas. Verificado con la suite completa del módulo (15 de 15).
- **Lección:** en pruebas que comparten la base H2, nunca asumir que una tabla contiene solo los datos de la prueba en curso; identificar siempre los registros por un ID propio.

### ERR-002 · Prueba de reintentos del Outbox falla de forma intermitente

- **Estado:** RESUELTO el 2026-11-02 (BIT-006)
- **De qué trata:** `InventoryOutboxIntegrationTest.dispatcherRetainsPendingStatusAndIncrementsRetryWhenRabbitMqFails` publica un evento y llama a `publishPending()` a mano esperando `retryCount == 1`. Pero cada contexto de Spring de las pruebas trae su propio `InventoryOutboxDispatcher` con `@Scheduled` (cada 5 s) y un `RabbitTemplate` real sin RabbitMQ disponible. Si ese planificador dispara dentro de la ventana de la prueba, procesa el mismo evento, falla y suma otro reintento antes que la prueba.
- **Cuándo se manifiesta:** de forma intermitente, al correr la suite completa de `inventory-infra`, cuando un tick de 5 s coincide con la prueba (≈1 s). Detectado el 2026-11-02 en una corrida completa de `mvn test`, tras haber pasado tres corridas previas del mismo módulo. Falla con `expected: 1 but was: 2` en [InventoryOutboxIntegrationTest.java:116](../inventory/inventory-infra/src/test/java/com/jcode/jcomercy/inventory/infra/InventoryOutboxIntegrationTest.java). Evidencia en el log: el hilo `scheduling-1` y el hilo `main` procesaron el mismo `eventId` con 0,5 s de diferencia.
- **Cómo mitigarlo:** volver a ejecutar la suite (suele pasar a la segunda), o correr la prueba sola. Un fallo de esta prueba con `retryCount` mayor que 1 no indica un defecto del Outbox.
- **Cómo se resolvió:** el despachador se activa con `outbox.publisher.enabled` (por defecto `true`) y los recursos de prueba lo ponen en `false`. Se reprodujo de forma determinista acelerando el despachador a 20 ms (falla 3 de 3, `retryCount` entre 18 y 21) y, con el interruptor apagado, esa misma reproducción pasa 3 de 3.
- **Lección:** una prueba que invoca a mano un componente que también corre en segundo plano debe apagar el segundo; un tick de 5 s es lo bastante raro como para pasar desapercibido varias corridas seguidas.

### ERR-003 · Rutas inexistentes y métodos no permitidos responden 500 en vez de 404/405

- **Estado:** RESUELTO el 2026-11-02 en inventario (BIT-012) y en `company-management` (BIT-013).
- **De qué trata:** `InventoryExceptionHandler.handleUnexpected` captura `Exception`, y eso incluye `NoResourceFoundException` (la que Spring lanza cuando ninguna ruta coincide) y `HttpRequestMethodNotSupportedException` (método no permitido). Resultado: una URL que no existe responde `500 INTERNAL_SERVER_ERROR` con "Error interno del servidor" y registra un `ERROR` con traza completa.
- **Cuándo se manifiesta:** con cualquier petición a una ruta inexistente de la aplicación de inventario, por ejemplo un error de tecleo en la URL. Se vio al correr las pruebas de reservas *antes* de que existieran sus endpoints: `Status expected:<201> but was:<500>`, con `No static resource api/v1/companies/.../stock/reservations` en el log.
- **Cómo mitigarlo:** no interpretar un 500 en una ruta recién escrita como un fallo del servidor: revisar primero que la URL exista. Un cliente con reintentos automáticos (como el futuro canal de venta) no debe reintentar indefinidamente ante un 500 sin revisar la ruta.
- **Cómo se resolvió (inventario):** se agregaron manejadores específicos para `NoResourceFoundException` (404, código `NOT_FOUND`) y `HttpRequestMethodNotSupportedException` (405, código `METHOD_NOT_ALLOWED`, con la cabecera `Allow`). Spring elige el manejador más específico, así que el de `Exception` queda para lo realmente inesperado. Prueba: `InventoryRoutingErrorsIntegrationTest`.
- **Cómo se resolvió (`company-management`):** [GlobalExceptionHandler.java](../company-management/company-management-infra/src/main/java/com/jcode/jcomercy/companymanagement/infra/web/exception/GlobalExceptionHandler.java) tenía el mismo `@ExceptionHandler(Exception.class)` y ningún manejador de enrutamiento; se reprodujo con `CompanyRoutingErrorsIntegrationTest` (500 antes del arreglo) y se agregaron los mismos dos manejadores (404 y 405 con `ProblemDetails`).
- **Otros errores del framework (no verificado):** `HttpMediaTypeNotSupportedException` (415) y `MissingServletRequestParameterException` (400) no tienen manejador propio en ninguno de los dos módulos, así que es esperable que también terminen en 500. No se probaron.

### ERR-004 · La prueba de concurrencia de reservas falla al repetirse

- **Estado:** RESUELTO el 2026-11-02 (BIT-009)
- **De qué trata:** `onlyOneOfTwoConcurrentReservationsFitsInTheStock` usaba las referencias fijas `ML-7-A` y `ML-7-B`. Como una referencia solo puede tener una reserva activa por empresa, las reservas de una ejecución bloqueaban a la siguiente con `409 an active reservation already exists for this reference`.
- **Cuándo se manifiesta:** al repetir la prueba (`@RepeatedTest`) o correrla otra vez contra la misma base H2 en memoria sin reiniciarla. Con `@RepeatedTest(10)` pasó la primera repetición y fallaron las otras 9.
- **Cómo mitigarlo:** reiniciar la base (nueva JVM) o usar referencias únicas por ejecución.
- **Cómo se resolvió:** las referencias llevan como sufijo los 8 primeros caracteres del `productId` de la prueba, que es único por repetición. Resultado: 18 de 18 (8 pruebas + 10 repeticiones).
- **Lección:** es la misma de ERR-001: en una base compartida, los datos de una prueba se identifican con IDs propios. Además, el fallo confirmó que la regla de referencia única funciona.

### ERR-005 · El 500 de company-management devuelve el mensaje interno de la excepción y no la registra

- **Estado:** RESUELTO el 2026-11-02 (BIT-014). Detectado el mismo día al atender ERR-003; primero confirmado leyendo el código y luego reproducido con `GlobalExceptionHandlerTest`.
- **De qué trata:** `GlobalExceptionHandler.handleGeneral` de `company-management` pone `ex.getMessage()` en el campo `detail` de la respuesta 500, y el manejador no tiene ningún `Logger`: no registra la excepción. Dos consecuencias: (1) una excepción inesperada (SQL, puntero nulo, etc.) puede mostrar al cliente texto interno como nombres de tablas o valores; (2) ese error queda sin traza en los registros, salvo que otro componente la escriba, cosa que no se verificó. Inventario sí responde un mensaje fijo ("Error interno del servidor") y registra el error con su traza.
- **Cuándo se manifiesta:** ante cualquier excepción no prevista en `company-management`, por ejemplo un fallo de base de datos durante un registro de cuenta.
- **Cómo mitigarlo:** mientras no se corrija, no exponer ese módulo directamente a internet sin un intermediario que reescriba los 500, y no depender de los registros del módulo para diagnosticar errores inesperados.
- **Cómo se resolvió:** `handleGeneral` responde un mensaje fijo y registra la excepción con `log.error` (como hace `InventoryExceptionHandler`). Prueba: `GlobalExceptionHandlerTest` (2 pruebas, ambas fallaban antes del cambio).
