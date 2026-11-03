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
| ERR-006 | Una `Idempotency-Key` vacía responde 500 en company-management | RESUELTO (BIT-016) |
| ERR-007 | Repetir una `Idempotency-Key` ya reclamada por el almacén (proceso nuevo) lanza una excepción sin manejar, que termina en 500, en company-management | RESUELTO (BIT-017 reproducción, BIT-018 solución) |
| ERR-008 | Los endpoints REST de company-management solo exigen un JWT válido: no comprueban que la cuenta o empresa del token sea la que se consulta o modifica | RESUELTO (BIT-022) |
| ERR-009 | GraphQL ejecutaba cualquier consulta aunque `persisted-only` estuviera activo, y la introspección del esquema estaba abierta | RESUELTO (BIT-020) |
| ERR-010 | Los permisos del contrato no se exigen en ninguna ruta REST de company-management, y a todo usuario se le conceden cinco permisos fijos | ABIERTO (leído en el código, no reproducido) |

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
  - **Posible ruido (no verificado en este paso):** las peticiones con JSON mal formado o que fallan la validación de campos probablemente también llegan a este manejador general y responden 500 (inventario las trata aparte con 400). Si es así, ahora además dejarán un `ERROR`. **Verificado y corregido en BIT-015:** era cierto, las 6 pruebas de reproducción respondían 500.
  - **Mensajes distintos entre módulos:** inventario sigue respondiendo "Error interno del servidor"; no se unificó.
  - **Sin cambios:** los demás manejadores de `company-management` siguen devolviendo el mensaje de su excepción, porque son reglas de negocio pensadas para mostrarse.

### BIT-015 · Las solicitudes mal hechas dejan de responder 500 en company-management

- **Qué se hizo:** en `company-management`, una solicitud mal hecha por quien llama responde ahora un error 4xx con `ProblemDetails` y un mensaje que dice qué revisar, en vez de un 500: JSON ilegible, cuerpo ausente o dato con formato inválido → 400 `MALFORMED_REQUEST`; encabezado obligatorio ausente → 400 `MISSING_HEADER`; dueño ausente en el registro de cuenta → 400 `VALIDATION_ERROR`; tipo de contenido que no es JSON → 415 `UNSUPPORTED_MEDIA_TYPE` (con la cabecera `Accept`); contraseña de menos de 8 caracteres → 422 `DOMAIN_RULE_VIOLATION`.
- **Cómo se hizo:**
  - Prueba nueva: [CompanyMalformedRequestsIntegrationTest.java](../company-management/company-management-infra/src/test/java/com/jcode/jcomercy/companymanagement/infra/web/exception/CompanyMalformedRequestsIntegrationTest.java), 7 pruebas con `MockMvc` sobre rutas públicas (`/auth/login` y `/accounts/registration`), sin token.
  - **RED:** `mvn -B -pl company-management/company-management-infra -am test -Dtest=CompanyMalformedRequestsIntegrationTest` → las 6 primeras fallan con `Status expected:<400> but was:<500>` (`<415>` en la de tipo de contenido). Revisé el log de cada 500 para confirmar que la causa era la prevista y no un problema de la prueba: `HttpMessageNotReadableException` ×3 (JSON ilegible, cuerpo ausente, fecha `"ayer"`), `MissingRequestHeaderException` (`Idempotency-Key`), `HttpMediaTypeNotSupportedException` y un `NullPointerException` en `request.owner().firstName()` cuando el registro no trae `owner`. La séptima (contraseña corta) se agregó después y también falló en RED: `Status expected:<422> but was:<500>` por `IllegalArgumentException: La contraseña debe tener al menos 8 caracteres.`
  - Ajustes a las pruebas entre RED y GREEN (no a la lógica): (1) el prefijo del mensaje de validación quedó neutro ("Revisa estos datos de la solicitud: …") porque el manejador también servirá para errores de formato, no solo para faltantes; (2) la prueba del encabezado ausente mandaba además un cuerpo sin `owner`, es decir dos defectos a la vez, y Spring resuelve los parámetros en orden y reportó primero el cuerpo (`VALIDATION_ERROR`); se le dio un `owner` para que el encabezado sea lo único que falta.
  - Código: en [GlobalExceptionHandler.java](../company-management/company-management-infra/src/main/java/com/jcode/jcomercy/companymanagement/infra/web/exception/GlobalExceptionHandler.java) cuatro manejadores nuevos (`HttpMessageNotReadableException`, `MissingRequestHeaderException`, `MethodArgumentNotValidException`, `HttpMediaTypeNotSupportedException`); en [AccountController.java](../company-management/company-management-infra/src/main/java/com/jcode/jcomercy/companymanagement/infra/web/controller/AccountController.java) `@Valid` en el cuerpo del registro y `@NotNull` en `owner` (la dependencia `spring-boot-starter-validation` ya estaba en el módulo); en [AccountApplicationService.java](../company-management/company-management-application/src/main/java/com/jcode/jcomercy/companymanagement/application/service/AccountApplicationService.java) la regla de la contraseña lanza `DomainRuleViolationException` (que ya se traducía a 422) en lugar de `IllegalArgumentException`, con el mismo mensaje.
  - **GREEN:** las 7 pruebas pasan; `mvn -B test` en la raíz → `BUILD SUCCESS`, **115 pruebas** (company-management infra pasa de 19 a 26).
  - Sondeo con una prueba temporal (ya eliminada, no queda en el código): un registro con `name` ausente respondió 422 con "El nombre de la cuenta no puede estar vacío." (esa ruta no se tocó; no se probó antes del cambio); `{}` respondió 400; `"owner": {}` seguía en 500 por la regla de la contraseña (el motivo de agregar la séptima prueba); una `Idempotency-Key` vacía respondía 500 → ERR-006, **no corregido en este paso** (resuelto en BIT-016).
- **Afectaciones:**
  - **API:** esas respuestas pasan de 500 a 400/415/422. Ningún flujo conocido dependía del 500.
  - **Registros:** estos casos quedan en nivel `WARN` con la ruta, no como `ERROR` con traza. Para el cuerpo ilegible se registra solo la clase de la excepción, **no su mensaje**: el lector de JSON puede citar fragmentos de lo enviado y estas rutas reciben contraseñas.
  - **Mensajes:** el `detail` no incluye el texto interno del lector de JSON (nombres de clases, posiciones).
  - **Contrato:** `openapi-company-management-v1.yaml` no se actualizó. Una búsqueda en el archivo encontró `'400'` solo en la línea 148 y ningún `'415'`; las demás respuestas de error nuevas tampoco están declaradas por operación.
  - **Cobertura parcial de campos:** solo el registro de cuenta usa `@Valid`, y solo `owner` es obligatorio por anotación. No se verificó qué pasa si `owner` viene pero sin otros campos (por ejemplo `firstName` nulo con una contraseña válida) ni si los cuerpos de `CompanyController`, `BranchController` y `AuthController` (login, refresh, recuperación) fallan con un puntero nulo ante campos ausentes; el login sí compara contra nulos a mano.
  - **Orden de los errores:** si una solicitud trae un cuerpo inválido y además le falta un encabezado, se reporta primero el cuerpo.
  - **Excepción por tipo:** una búsqueda en `company-management` no encontró código de producción que capture `IllegalArgumentException` por tipo; la única prueba que la menciona es `IdempotencyRegistryTest`, que no cambió.
  - **Inventario:** no se tocó; sus manejadores de 400 ya existían (ver ERR-003 sobre lo que sigue sin verificar allí).

## 2026-11-03

### BIT-016 · La `Idempotency-Key` vacía responde 400 y no 500 (resuelve ERR-006)

- **Qué se hizo:** en `company-management`, un encabezado `Idempotency-Key` presente pero vacío o en blanco responde 400 `MISSING_HEADER` con "El encabezado Idempotency-Key está vacío. Envía un valor único para cada operación e intenta de nuevo.", en vez de 500.
- **Cómo se hizo:**
  - Pruebas: `IdempotencyRegistryTest` cambia `rejectsBlankKeys` por `rejectsMissingEmptyAndBlankKeysWithTheirOwnException` (ahora cubre `null`, `""` y `" "`, y exige el tipo de excepción propio); `CompanyMalformedRequestsIntegrationTest` suma `blankIdempotencyKeyRespondsBadRequest` (`POST /api/v1/accounts/registration` con el encabezado vacío).
  - **RED (compilación):** `mvn -B -pl company-management/company-management-application -am test -Dtest=IdempotencyRegistryTest` → `cannot find symbol … class IdempotencyKeyRequiredException`.
  - **RED (ejecución):** se creó la clase sin conectarla al registro y se corrieron ambas pruebas: la del registro falla con `Expecting actual throwable to be an instance of` (con la clave `null`, la primera del arreglo) y la de integración con `Status expected:<400> but was:<500>`, con `IllegalArgumentException: Idempotency-Key es obligatorio` en el log.
  - Código: [IdempotencyKeyRequiredException.java](../company-management/company-management-application/src/main/java/com/jcode/jcomercy/companymanagement/application/service/IdempotencyKeyRequiredException.java) (nueva, extiende `IllegalArgumentException` con el mismo mensaje); [IdempotencyRegistry.java](../company-management/company-management-application/src/main/java/com/jcode/jcomercy/companymanagement/application/service/IdempotencyRegistry.java) la lanza; [GlobalExceptionHandler.java](../company-management/company-management-infra/src/main/java/com/jcode/jcomercy/companymanagement/infra/web/exception/GlobalExceptionHandler.java) suma `handleBlankIdempotencyKey`.
  - **GREEN:** `mvn -B test` en la raíz → `BUILD SUCCESS`, **116 pruebas** (company-management infra pasa de 26 a 27; `IdempotencyRegistryTest` sigue en 3).
- **Afectaciones:**
  - **API:** esa respuesta pasa de 500 a 400. Reutiliza el código `MISSING_HEADER` del encabezado ausente (BIT-015) porque la recuperación es la misma: mandar la clave con un valor.
  - **Alcance:** la excepción sale del registro, así que cubre todas las operaciones que lo usan (registro de cuenta, compañías, sucursales). Solo se probó por HTTP el registro de cuenta; el resto se apoya en que comparten el registro y el manejador.
  - **Compatibilidad:** la excepción nueva es subclase de `IllegalArgumentException` con el mismo mensaje; una búsqueda previa (BIT-015) no encontró código que capture ese tipo.
  - **Registros:** queda en `WARN` con la ruta, sin traza.
  - **Contrato:** `openapi-company-management-v1.yaml` sigue sin declarar estas respuestas 400 por operación.
  - **Sin cambios:** la repetición de una clave ya reclamada (ERR-007) no se tocó.

### BIT-017 · Prueba que reproduce ERR-007 con un almacén falso

- **Qué se hizo:** `IdempotencyRegistryTest` suma `err007RepeatedKeyAlreadyClaimedByTheStoreFailsWithIllegalStateAfterARestart`, que reproduce a nivel del registro lo que antes solo estaba leído en el código. **No corrige nada:** documenta el comportamiento actual.
- **Cómo se hizo:**
  - Un `FakeIdempotencyStore` (clase interna de la prueba) imita el contrato de `JpaIdempotencyStore`: `claim` solo funciona una vez por pareja operación-clave y `release` la libera. Dos `IdempotencyRegistry` comparten ese almacén; el segundo representa el proceso reiniciado.
  - La prueba comprueba que (1) el primer registro ejecuta la operación, (2) repetida en ese mismo proceso devuelve el resultado anterior, (3) el registro nuevo lanza `IllegalStateException("La solicitud con Idempotency-Key ya fue procesada")` y (4) la operación se ejecutó una sola vez.
  - **Esta prueba pasa a la primera, a propósito** (describe lo que el código ya hace). Para comprobar que no es vacía se hizo una mutación temporal: se desactivó la comprobación `store.claim` en `IdempotencyRegistry` y la prueba cayó con `Expecting code to raise a throwable`; se restauró y `git status` confirmó que `IdempotencyRegistry.java` quedó sin diferencias.
  - `mvn -B test` en la raíz → `BUILD SUCCESS`, **117 pruebas** (`IdempotencyRegistryTest` pasa de 3 a 4).
- **Afectaciones:**
  - **Pruebas:** al resolver ERR-007 esta prueba debe cambiar, porque fija la excepción actual. Es deliberado; el comentario de la prueba lo dice.
  - **Alcance:** no se probó por HTTP ni con el almacén real (`JpaIdempotencyStore`); el falso replica su contrato leído en el código, no su comportamiento bajo transacciones.
  - **Sin cambios de código de producción.**

### BIT-018 · Repetir una `Idempotency-Key` ya reclamada responde 409 y no 500 (resuelve ERR-007)

- **Qué se hizo:** en `company-management`, repetir una `Idempotency-Key` que el almacén durable ya reclamó, cuando la memoria del proceso no recuerda el resultado, responde 409 `IDEMPOTENCY_KEY_ALREADY_USED` con "Esta operación ya se procesó. Consulta el recurso para ver el resultado.", en vez de 500.
- **Cómo se hizo:**
  - Decisión: el usuario eligió el camino 1 de ERR-007 (responder 409) tras ver cómo funcionaría; el camino 2 (guardar y devolver el resultado) queda sin hacer.
  - Pruebas: `IdempotencyRegistryTest` cambia `err007…FailsWithIllegalStateAfterARestart` por `err007…FailsWithItsOwnExceptionAfterARestart` (exige `IdempotencyKeyAlreadyUsedException` con el mismo mensaje y una sola ejecución). `CompanyRepeatedKeyIntegrationTest` (nueva, 1 prueba) reclama `account.register` con una clave en el almacén real (H2 en memoria con Flyway) usando `IdempotencyStore.claim` y luego hace `POST /api/v1/accounts/registration` con esa clave: la memoria del registro no la conoce, igual que en un proceso nuevo.
  - **RED (ejecución, registro):** se creó la clase sin conectarla al registro y `mvn -B -pl company-management/company-management-application -am test -Dtest=IdempotencyRegistryTest` dio 3 de 4 en verde y la de ERR-007 cayó con `Expecting actual throwable to be an instance of … IdempotencyKeyAlreadyUsedException but was: java.lang.IllegalStateException: La solicitud con Idempotency-Key ya fue procesada`. Tras el cambio en `IdempotencyRegistry`: 4 de 4.
  - **RED (ejecución, HTTP):** `mvn -B -pl company-management/company-management-infra -am test -Dtest=CompanyRepeatedKeyIntegrationTest` → `Status expected:<409> but was:<500>`. Con esto se observó por primera vez el 500 de ERR-007 de extremo a extremo con el almacén real. Matiz: se vio con la excepción nueva ya lanzada por el registro pero sin manejador propio (la misma ruta que tenía la `IllegalStateException` original hacia el manejador general); no se corrió con la excepción original.
  - Código: [IdempotencyKeyAlreadyUsedException.java](../company-management/company-management-application/src/main/java/com/jcode/jcomercy/companymanagement/application/service/IdempotencyKeyAlreadyUsedException.java) (nueva, extiende `IllegalStateException` con el mismo mensaje); [IdempotencyRegistry.java](../company-management/company-management-application/src/main/java/com/jcode/jcomercy/companymanagement/application/service/IdempotencyRegistry.java) la lanza donde antes lanzaba `IllegalStateException`; [GlobalExceptionHandler.java](../company-management/company-management-infra/src/main/java/com/jcode/jcomercy/companymanagement/infra/web/exception/GlobalExceptionHandler.java) suma `handleRepeatedIdempotencyKey` (409, tipo `…/idempotency-key-already-used`, título "Operación Ya Procesada", registra `WARN` con la ruta, sin traza).
  - **GREEN:** `mvn -B test` en la raíz → `BUILD SUCCESS`, **118 pruebas** (3 + 8 + 8 + 28 + 27 + 44; company-management infra pasa de 27 a 28, el resto igual).
- **Afectaciones:**
  - **API:** esa respuesta pasa de 500 a 409, con un código propio distinto de `RESOURCE_ALREADY_EXISTS` (el de `DuplicateResourceException`). La excepción sale del registro, así que cubre todas las operaciones que lo usan (registro de cuenta, compañías, sucursales, suspensión y activaciones); solo se probó por HTTP el registro de cuenta.
  - **Compatibilidad:** la excepción nueva extiende `IllegalStateException`. Una búsqueda de `catch (…IllegalStateException` en `company-management` no encontró código que la capture (la única coincidencia es un `catch (Exception e)` de `AuthController` que lanza una, no que la capture).
  - **Contrato:** `openapi-company-management-v1.yaml` no declara este 409 ni los 400/415 de BIT-015; sigue pendiente.
  - **Lo que no cambia:** repetir la clave en el mismo proceso sigue devolviendo el resultado anterior. Por eso el mismo reintento da el resultado anterior o un 409 según si la memoria del proceso lo conserva, y el cliente no recibe el recurso creado en el 409. La clave tampoco está atada al contenido de la solicitud.
  - **Sin verificar:** que `claim` se una a la transacción del servicio (se leyó en las anotaciones `@Transactional`; la prueba reclama la clave fuera de toda transacción y no lo demuestra), y la carrera entre instancias (la prueba usa un solo proceso con H2 en memoria).

### BIT-019 · Contratos OpenAPI al día y una prueba que los vigila (fase 1)

- **Qué se hizo:** `openapi-inventory-v1.yaml` documenta las 4 operaciones de reservas y `openapi-company-management-v1.yaml` documenta la suspensión de cuenta y las respuestas 400 y 415 de las operaciones con cuerpo. Dos pruebas nuevas, `OpenApiContractTest` (una por módulo), fallan si una ruta implementada no está en el contrato.
- **Cómo se hizo:**
  - **Prueba:** compara `GET /v3/api-docs` (springdoc: lo que el código expone) con `static/openapi-*.yaml` del classpath (copiado de `documentacion/` en `process-resources`), con las variables de ruta unificadas como `{}`. Lee el YAML con SnakeYAML, sin dependencias nuevas. Solo exige código → contrato: el YAML de company-management documenta 85 rutas y el código implementa 15, el resto son anticipadas. company-management suma una segunda prueba: toda operación implementada con cuerpo declara `400` y `415`.
  - **RED (ejecución):** `mvn -B -pl inventory/inventory-infra,company-management/company-management-infra -am test -Dtest=OpenApiContractTest` → inventario: faltaban `GET …/stock/availability` y `POST …/stock/reservations`, `…/commit` y `…/release`. Company-management: faltaba `POST /api/v1/accounts/{}/suspension`, y la segunda prueba listó 13 respuestas ausentes (el registro sin 415; y sin 400 ni 415 crear empresa, login, refresh, password-recovery, password-recovery/confirm y crear sucursal).
  - **Contrato de inventario:** las 4 rutas, respuestas comunes `BadRequest`, `Forbidden`, `NotFound`, `UnprocessableEntity` (y `Conflict` ahora con cuerpo) y los esquemas `ErrorResponse`, `ReserveStockRequest`, `ReservationReferenceRequest`, `StockReservation`, `InventoryMovement`, `ReservationResult`, `CommitResult` y `Availability`. Los códigos salen de BIT-009 y de `InventoryExceptionHandler`; no se ejecutó cada ruta para confirmar su respuesta.
  - **Contrato de company-management:** `POST /accounts/{id}/suspension` con un esquema propio `AccountSuspensionRequest` (`reason`), porque el `SuspensionRequest` anticipado exige `reasonCode` y `confirmation` y el controlador recibe `{reason}`; respuesta común `UnsupportedMediaType` nueva; `BadRequest` y `Conflict` describen sus códigos (`MALFORMED_REQUEST`, `VALIDATION_ERROR`, `MISSING_HEADER`, `IDEMPOTENCY_KEY_ALREADY_USED`); 400 y 415 en las operaciones con cuerpo. A la suspensión no se le puso `x-required-permission`: `accounts.suspend` no existe en el seed V009.
  - **GREEN:** `OpenApiContractTest` 2 de 2 en company-management y 1 de 1 en inventario; `mvn -B test` en la raíz → `BUILD SUCCESS`, **121 pruebas** (3 + 8 + 8 + 30 + 27 + 45; company-management infra +2, inventario infra +1).
- **Afectaciones:**
  - **Contratos:** cierra el pendiente de OpenAPI de reservas (BIT-009) y el de los 400, 409 y 415 (BIT-015, BIT-018). GraphQL sigue pendiente (fases 2 y 3).
  - **Pruebas:** toda ruta REST nueva deberá documentarse para que la suite pase.
  - **Límites:** la prueba compara método y ruta (y 400/415 en company-management); no valida cuerpos, códigos de respuesta ni la forma de los esquemas. Inventario no declara 415 porque no se verificó (ERR-003 sigue sin verificar allí). Los 400, 403 y 404 de las operaciones de inventario anteriores a las reservas siguen sin documentarse.
  - **Discrepancia contrato ↔ código:** el `SuspensionRequest` anticipado para empresas, sucursales y membresías no coincide con lo que acepta hoy la cuenta.
  - **Hallazgo:** ERR-008.

### BIT-020 · GraphQL cerrado por defecto: solo se ejecutan las operaciones registradas (fase 2, resuelve ERR-009)

- **Qué se hizo:** en `inventory` y en `company-management`, `/graphql/v1` ejecuta solo las operaciones que viven como archivos `.graphql` en `src/main/resources/graphql/operations/` de cada módulo. Cualquier otra consulta, la introspección o una consulta con su propio hash se rechaza con `errors[0].extensions.code = OPERATION_NOT_ALLOWED` y "Esta consulta no está permitida. Actualiza la aplicación e intenta de nuevo.". La introspección queda apagada.
- **Cómo se hizo:**
  - **RED del hueco (ejecución):** `PersistedQueryInterceptorTest#rejectsAnUnlistedOperationEvenWithItsOwnValidHash`, contra el interceptor anterior (`new PersistedQueryInterceptor(true, "")`), dio 3 de 4 en verde y esta cayó con `Expecting actual not to be empty`, en inventario y en company-management: con `persisted-only=true` y sin lista, una consulta con el SHA-256 de su propio texto pasaba.
  - **RED por HTTP (ejecución):** `GraphQlClosedByDefaultIntegrationTest` (nueva) y `GraphQlOpenModeIntegrationTest` (nueva) contra el código abierto: en cerrado fallaron 4 de 5 (la consulta no registrada, la del hash propio y la introspección se ejecutaron sin errores: `Missing property in path $['errors']`; la operación registrada falló con `FileNotFoundException` porque el archivo no existía; sin token responde 401, y esa ya pasaba); en abierto 1 de 2 (la introspección devolvía datos). Igual en ambos módulos.
  - **RED de compilación:** las pruebas nuevas del registro y del interceptor reescrito fallaron con `cannot find symbol class PersistedOperationRegistry`.
  - **Código (uno por módulo, copiado):** `PersistedOperationRegistry` (nuevo) normaliza el documento con el analizador de graphql-java en forma compacta, calcula su SHA-256 y lo busca entre los registrados; `PersistedOperationConfig` (nuevo) carga `classpath:graphql/operations/*.graphql` al arrancar; `PersistedQueryInterceptor` reescrito (ya no usa el encabezado `X-GraphQL-Operation-Hash`; registra en `WARN` el hash y no el documento). `application.yml`: `graphql.security.persisted-only` pasa a `true` por defecto, se retira `allowed-operation-hashes` y se agrega `spring.graphql.schema.introspection.enabled: false`.
  - **Operaciones iniciales:** inventario 9 (`Warehouse`, `Locations`, `Product`, `Products`, `UnitsOfMeasure`, `ProductUnitConversions`, `StockBalances`, `InventoryMovements`, `CompanyUnits`); company-management 2 (`Company`, `CompaniesByAccount`). Cada una pide los campos escalares de su tipo (`Warehouse` sin `locations` anidadas).
  - **Prueba de consistencia** (`GraphQlOperationsConsistencyTest`, una por módulo): cada operación valida contra el esquema y cada consulta del esquema tiene al menos una operación.
  - **GREEN:** las clases GraphQL de cada módulo pasaron al primer intento (inventario 17 de 17 en las cuatro primeras clases, company-management 19 de 19 en las cinco); `mvn -B test` en la raíz con solo esta fase → `BUILD SUCCESS`, **153 pruebas** (3 + 8 + 8 + 46 + 27 + 61; infra de company-management e inventario suben 16 cada una).
  - **Mutaciones para comprobar que las pruebas pueden fallar** (todas temporales; ninguna dejó archivos): (1) registro vacío con `-Dgraphql.security.operations-location='classpath:graphql/*.nomatch'` → cae exactamente `registeredOperationRunsAndReturnsData` en ambos módulos (con una carpeta inexistente el contexto de Spring no arranca); (2) solo las operaciones `Product*` → la prueba de consistencia lista las consultas sin operación; (3) una operación inválida temporal en `target/classes` → `FieldUndefined` señalando su archivo, borrada después.
  - **Un tropiezo de la verificación:** con la fase 3 apartada, la suite cayó una vez porque `StockAvailability.graphql` seguía en `target/classes` (Maven no borra recursos quitados). Se borró ese archivo de la salida de compilación y la suite pasó. Es un resto de compilación; con `clean` no ocurre.
- **Afectaciones:**
  - **Comportamiento:** por defecto, cualquier cliente que mande otro texto recibe `OPERATION_NOT_ALLOWED` (HTTP 200 con `errors`, como es convención en GraphQL). `GRAPHQL_PERSISTED_ONLY=false` abre las consultas en local; la introspección sigue apagada, así que GraphiQL y Postman que dependen de ella dejan de funcionar.
  - **Configuración:** desaparece `GRAPHQL_ALLOWED_OPERATION_HASHES`. El encabezado `X-GraphQL-Operation-Hash` ya no se usa (inventario aún lo permite en CORS; es inofensivo).
  - **Producto:** una pantalla nueva que necesite otros campos exige una operación nueva (archivo) y despliegue; el cliente lleva copia de los `.graphql`. Los documentos `diseno-interfaces-company-management.md` (§9) y `catalogo-endpoints-company-management.md` (§15) lo recogen.
  - **Duplicación consciente:** registro, configuración e interceptor están copiados en los dos módulos; el Core no puede depender de spring-graphql por su SDD.
  - **Lo que no cambia:** profundidad máxima 8, complejidad 100, 60 peticiones por minuto por dirección y la autorización de cada resolver.
  - **Sin verificar:** solo se ejecutó por HTTP `UnitsOfMeasure` (inventario) y `CompaniesByAccount` (company-management); las demás operaciones se validaron contra el esquema pero no se corrieron. No existe todavía un cliente real que las use.

### BIT-021 · Consulta GraphQL de disponibilidad en inventario (fase 3)

- **Qué se hizo:** inventario suma la consulta GraphQL `stockAvailability(companyId, productId)`, que devuelve por almacén y producto la existencia (`onHand`), lo reservado (`reserved`) y lo disponible (`available`). Reservar, liberar y confirmar siguen solo en REST, porque el diseño deja los comandos críticos en REST.
- **Cómo se hizo:**
  - **Prueba:** `GraphQlAvailabilityIntegrationTest` (nueva, 3 pruebas). Crea por REST un almacén, una ubicación y un producto, recibe 10 y reserva 4; consulta por GraphQL con JWT, en modo cerrado y leyendo la operación de `StockAvailability.graphql`. Comprueba `onHand 10.0`, `reserved 4.0` y `available 6.0`; otra empresa, y quien no tiene el permiso de lectura de stock, reciben una lista vacía.
  - **RED (ejecución):** (1) sin el archivo de operación, 3 de 3 con `FileNotFoundException`; (2) con el archivo y sin la consulta en el esquema, 3 de 3 con `Validation error (FieldUndefined@[stockAvailability])`, y `GraphQlOperationsConsistencyTest#everyRegisteredOperationIsValidAgainstTheSchema` cayó con el mismo error.
  - **Código:** [inventory.graphqls](../inventory/inventory-infra/src/main/resources/graphql/inventory.graphqls) (consulta y tipo `Availability`), `InventoryGraphQlController#stockAvailability` (el mismo control que el REST, `canAccessCompany` y `inventory.stock.read`, con lista vacía si no cumple; delega en `ReservationApplicationService.availability`) y `StockAvailability.graphql`.
  - **GREEN:** esas tres pruebas más la de consistencia y la de modo cerrado → 10 de 10 al primer intento; `mvn -B test` en la raíz → `BUILD SUCCESS`, **156 pruebas** (3 + 8 + 8 + 46 + 27 + 64; infra de inventario +3).
  - **Mutación (temporal):** reemplazar en `stockAvailability` la condición `canAccessCompany && hasPermission` por `true` hizo caer exactamente `anotherCompanyGetsAnEmptyList` y `withoutReadPermissionGetsAnEmptyList`, y la de datos siguió pasando. Se restauró y se comprobó que no quedó ningún `return true`.
- **Afectaciones:**
  - **API:** el GraphQL de inventario pasa de 9 a 10 consultas. El contrato OpenAPI no cambia: la ruta REST equivalente ya se documentó en BIT-019.
  - **Alcance:** solo lectura. Las mutaciones de reservas por GraphQL quedan fuera.
  - **Datos expuestos:** los mismos cinco campos que el REST de disponibilidad, sin identificadores de reserva.
  - **Sin verificar:** el comportamiento con muchos almacenes o productos; la prueba usa un almacén y un producto.

### BIT-022 · Las rutas REST de cuentas, empresas y sucursales comprueban a quién pertenecen (resuelve ERR-008)

- **Qué se hizo:** las 10 rutas REST de company-management bajo `/api/v1/accounts/{publicAccountId}` y `/api/v1/companies/{publicCompanyId}` responden 403 `INSUFFICIENT_PERMISSIONS` a quien trae un token válido de otra cuenta o sin acceso a esa empresa, y no modifican nada. Los mensajes son "No tienes acceso a esta cuenta. Inicia sesión con la cuenta que corresponde." y "No tienes acceso a esta empresa. Pide acceso a quien administra la cuenta."
- **Cómo se hizo:**
  - **Regla:** para una cuenta, el sujeto del token debe ser esa cuenta (`JwtAuthorizationService.canAccessAccount`). Para una empresa entra quien trae su id en el claim `companyIds` (miembros) o la cuenta dueña de la empresa, aunque la haya creado después de iniciar sesión (consulta con `CompanyRepositoryPort.findByPublicCompanyId`). Sin esa segunda cláusula, el dueño no podría activar la empresa que acaba de crear hasta renovar el token. Una empresa que no existe responde 403 a quien no es miembro, para no revelar si existe.
  - **Implementación:** un interceptor MVC y no un `if` por controlador, porque el bug nació justo por olvidar el control en cada ruta. `RestOwnershipInterceptor` y `RestOwnershipConfig` (nuevos) lo registran para `/api/v1/accounts/{publicAccountId}/**` y `/api/v1/companies/{publicCompanyId}/**`, excluyendo `/api/v1/accounts/registration`. Una ruta nueva en esos prefijos nace protegida. Reutiliza `UnauthorizedAccessException`, que `GlobalExceptionHandler` ya traduce a 403, y registra un `WARN` con actor, método y ruta, sin el token.
  - **RED (ejecución):** `RestOwnershipIntegrationTest` (nueva) → 12 de 14 fallan y 2 pasan (las del dueño). Con el token de otra cuenta se leyeron la cuenta, su lista de empresas, la empresa, sus sucursales y una sucursal (200); se suspendió la cuenta, se activó la empresa y se activó la sucursal (200); se creó una empresa en la cuenta ajena y una sucursal en la empresa ajena (201); una empresa inexistente respondió 404; y un miembro con solo el claim de la empresa entró a la cuenta del dueño (200).
  - **GREEN:** 14 de 14 al primer intento. La suite de company-management mostró entonces 2 fallos esperados en pruebas existentes que fijaban el comportamiento defectuoso, porque usaban un token de sujeto inventado: `AccountIntegrationTest#shouldReturn404ForNonExistentAccount` (403 en vez de 404) y `CompanyIntegrationTest#shouldCreateAndActivateCompany` (403 en vez de 201). Se ajustaron para usar el token de la cuenta correspondiente, sin debilitar ninguna aserción.
  - **Pruebas añadidas después:** el guardia `everyRestRouteIsPublicOrCoveredByTheOwnershipCheck` (lee `/v3/api-docs`: toda ruta es pública o cuelga de las variables que el interceptor comprueba) y `tokenFromLoginWorksForTheOwnAccountOnly` (usa el `accessToken` real de `/auth/login`). Ambas pasaron a la primera, por diseño.
  - **Mutaciones temporales, todas revertidas:** (1) quitar la cláusula del dueño → caen las 15 pruebas de pertenencia (su fixture usa el flujo de alta) y `shouldCreateAndActivateCompany` (403 al activar); (2) quitar el acceso por claim → cae solo `memberWithTheCompanyClaimReadsTheCompanyButNotTheAccount`; (3) un controlador temporal en `/api/v1/zz-temporal/{id}` → el guardia lo nombra (`Expecting empty but was: ["/api/v1/zz-temporal/{id}"]`); el archivo y su clase compilada se borraron.
  - **Contrato:** la respuesta común `Forbidden` de [openapi-company-management-v1.yaml](openapi-company-management-v1.yaml) explica ahora cuándo ocurre. Las 10 operaciones ya declaraban 403 (comprobado con un script, no con una prueba).
  - **GREEN final:** `mvn -B test` en la raíz → `BUILD SUCCESS`, **172 pruebas** (3 + 8 + 8 + 62 + 27 + 64; company-management infra +16).
- **Afectaciones:**
  - **API:** quien usaba un token de otra cuenta, o de un sujeto inventado, recibe 403. Hoy solo afecta a las pruebas, porque no hay clientes.
  - **Rendimiento:** una lectura de la empresa por petición cuando el claim no la incluye.
  - **Sin cambios:** GraphQL, el registro, `/auth/*` y las rutas inexistentes (404). GraphQL sigue comprobando la empresa solo por claim, así que una empresa creada después de iniciar sesión no se ve por GraphQL hasta renovar el token.
  - **Decisiones tomadas:** suspender una cuenta queda para la propia cuenta, porque no existe un rol de administración de la plataforma; y el control es a nivel de empresa, sin limitar por sucursal asignada (`branchIds`).
  - **Sin verificar:** el comportamiento con Postgres (las pruebas usan H2) y el caso de un miembro con acceso a una sola sucursal.
  - **Pendiente:** ERR-010.

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
- **Otros errores del framework:** en `company-management`, `HttpMediaTypeNotSupportedException` (415) se reprodujo como 500 y quedó corregida en BIT-015. En **inventario** siguen sin verificar `HttpMediaTypeNotSupportedException` (415) y `MissingServletRequestParameterException` (400): no tienen manejador propio, así que es esperable que terminen en 500, pero no se probaron. En `company-management` tampoco se probó `MissingServletRequestParameterException`; sus controladores no usan `@RequestParam`.

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

### ERR-006 · Una `Idempotency-Key` vacía responde 500 en company-management

- **Estado:** RESUELTO el 2026-11-03 (BIT-016). Detectado el 2026-11-02 con una prueba temporal mientras se atendía BIT-015.
- **De qué trata:** `IdempotencyRegistry.execute` lanza `IllegalArgumentException("Idempotency-Key es obligatorio")` cuando la clave es nula o está en blanco. Spring acepta un encabezado presente con valor vacío (solo rechaza el ausente, que ya responde 400 desde BIT-015), así que la excepción llega al manejador general: el cliente recibe un 500 "problema nuestro" y el registro guarda un `ERROR` con traza, aunque el error es de quien llama.
- **Cuándo se manifiesta:** `POST /api/v1/accounts/registration` con el encabezado `Idempotency-Key` presente pero vacío. Reproducido con `MockMvc`: 500 y `IllegalArgumentException: Idempotency-Key es obligatorio` en el log. Las demás operaciones idempotentes (compañías, sucursales, suspensión de cuenta) usan el mismo registro, pero no se probaron.
- **Cómo mitigarlo:** los clientes deben generar siempre una clave no vacía (por ejemplo un UUID por operación). Un 500 en estas rutas con ese encabezado vacío no indica un fallo del servidor.
- **Cómo se resolvió:** el registro lanza `IdempotencyKeyRequiredException` (subclase de `IllegalArgumentException`, mismo mensaje) cuando la clave es nula, vacía o en blanco, y `GlobalExceptionHandler` la traduce a 400 `MISSING_HEADER`: "El encabezado Idempotency-Key está vacío. Envía un valor único para cada operación e intenta de nuevo." Como la excepción sale del registro, el arreglo cubre todas las operaciones que lo usan, no solo el registro de cuenta. Pruebas: `IdempotencyRegistryTest` y `CompanyMalformedRequestsIntegrationTest#blankIdempotencyKeyRespondsBadRequest`.
- **Lo que sigue abierto de este tema:** ver ERR-007.

### ERR-007 · Repetir una `Idempotency-Key` ya reclamada responde 500 en company-management

- **Estado:** RESUELTO el 2026-11-03 (BIT-018). Visto leyendo el código el 2026-11-02 al atender ERR-006, reproducido el 2026-11-03 a nivel del registro con un almacén falso (BIT-017) y por HTTP con el almacén real al resolverlo (BIT-018).
- **De qué trata:** `IdempotencyRegistry.execute` recuerda los resultados en un mapa en memoria del proceso y, además, reclama la clave en el almacén durable (`store.claim`). Si el almacén ya tiene reclamada la pareja operación-clave pero el mapa en memoria no tiene el resultado, lanza `IllegalStateException("La solicitud con Idempotency-Key ya fue procesada")`. Esa excepción no tiene manejador propio, así que llegaría al manejador general: 500 "problema nuestro" y un `ERROR` con traza. El registro solo usa `claim` y `release` del almacén; el resultado no se guarda ahí.
- **Cuándo se manifiesta:** al repetir una solicitud con una clave que el almacén durable ya reclamó cuando el registro en memoria no la recuerda. **Reproducido** con dos registros que comparten un almacén falso: el primero ejecuta la operación y, en el mismo proceso, devuelve el resultado anterior sin problema; el segundo (el proceso "reiniciado") lanza `IllegalStateException` y la operación no se vuelve a ejecutar. **Inferido, no probado:** que en producción ocurra por un reinicio o por tener más de una instancia. El 500 se observó por HTTP con el almacén real al resolverlo (BIT-018), con la excepción ya subclasificada pero sin manejador propio; antes se apoyaba en que `GlobalExceptionHandlerTest` comprueba que el manejador general responde 500 ante una `IllegalStateException`.
- **Cómo mitigarlo:** antes de reintentar tras un reinicio, consultar si el recurso ya se creó; reintentar con la misma clave podría dar un 500 aunque la operación original sí se haya completado.
- **Cómo se resolvió:** el usuario eligió responder 409 con un mensaje claro. El registro lanza `IdempotencyKeyAlreadyUsedException` (subclase de `IllegalStateException`, mismo mensaje) y `GlobalExceptionHandler` la traduce a 409 `IDEMPOTENCY_KEY_ALREADY_USED`: "Esta operación ya se procesó. Consulta el recurso para ver el resultado." Pruebas: `IdempotencyRegistryTest#err007…FailsWithItsOwnExceptionAfterARestart` y `CompanyRepeatedKeyIntegrationTest`.
- **Lo que no resuelve:** el cliente que reintenta tras un corte no recibe el recurso creado y tiene que consultarlo aparte, y el mismo reintento da el resultado anterior o un 409 según si la memoria del proceso lo conserva. Para devolver siempre el resultado queda el otro camino: guardarlo en el almacén durable y devolverlo en la repetición, que exige una migración Flyway y serializar la respuesta. No se hizo.

### ERR-008 · Los endpoints REST de company-management no comprueban a quién pertenece lo que consultan o modifican

- **Estado:** RESUELTO el 2026-11-03 (BIT-022). Visto leyendo el código al documentar la suspensión de cuenta (BIT-019) y **reproducido** ese mismo día con `RestOwnershipIntegrationTest`.
- **De qué trata:** `JwtAuthenticationFilter` acepta cualquier JWT válido y la configuración de seguridad solo exige "autenticado" fuera de las rutas públicas. Ningún controlador REST de company-management llama a `JwtAuthorizationService` ni usa `@PreAuthorize`, y los servicios de aplicación tampoco comprueban pertenencia (búsqueda de `UnauthorizedAccessException`, `canAccess`, `ownerUserId` y `membership` sin resultados). Las comprobaciones `canAccessAccount` y `canAccessCompany` solo se usan en las consultas GraphQL (`CompanyGraphQlController`). Los permisos del token se convierten en autoridades `PERM_…`, pero ninguna ruta REST las exige; los `x-required-permission` del contrato no se aplican. Ninguna prueba cubre un 403 por pertenecer a otra cuenta.
- **Cuándo se manifiesta:** **reproducido (BIT-022):** una persona autenticada con un JWT válido de una cuenta podía consultar o modificar por REST (`GET /accounts/{id}`, `POST /accounts/{id}/suspension`, `GET /accounts/{id}/companies`, `GET /companies/{id}`, crear y activar sucursales) recursos de otra cuenta si conoce su identificador público.
- **Cómo mitigarlo:** hasta corregirlo, no exponer el módulo a usuarios distintos del dueño de cada cuenta y tratar los identificadores públicos como sensibles.
- **Cómo se resolvió:** un interceptor comprueba la pertenencia en todo lo que cuelga de `/api/v1/accounts/{publicAccountId}` y `/api/v1/companies/{publicCompanyId}`: la cuenta debe ser la del token, y la empresa debe estar en el claim del token o ser de la cuenta del token. Si no, 403 `INSUFFICIENT_PERMISSIONS` sin modificar nada. Pruebas: `RestOwnershipIntegrationTest` (una por ruta, más el guardia de rutas y el login real). Lo que no cubre, la exigencia de permisos por ruta, queda en ERR-010.

### ERR-009 · GraphQL ejecutaba cualquier consulta aunque `persisted-only` estuviera activo, y la introspección estaba abierta

- **Estado:** RESUELTO el 2026-11-03 (BIT-020). Visto leyendo el código al responder una duda sobre contratos GraphQL fijos y reproducido ese mismo día con `PersistedQueryInterceptorTest` y con pruebas por HTTP.
- **De qué trata:** el `PersistedQueryInterceptor` dejaba pasar cualquier consulta si el encabezado `X-GraphQL-Operation-Hash` traía el SHA-256 de su propio texto; solo `allowed-operation-hashes` (una variable de entorno, vacía por defecto) restringía de verdad. Con los valores por defecto (`persisted-only=false`, lista vacía) pasaba todo, y con `persisted-only=true` sin lista no se negaba nada. Además la introspección estaba activa (`spring.graphql.schema.introspection.enabled` vale `true` por defecto en Spring Boot 3.3.4) y ninguna prueba tocaba `/graphql` por HTTP.
- **Cuándo se manifiesta:** cualquier petición autenticada a `/graphql/v1` con cualquier texto de consulta, con la configuración por defecto de inventario o de company-management; y con `persisted-only=true` sin lista, cualquier consulta acompañada del hash de sí misma. **Reproducido** en ambos módulos (ver BIT-020).
- **Cómo mitigarlo:** mientras no se corrigió, configurar `GRAPHQL_ALLOWED_OPERATION_HASHES` con los hashes de las consultas legítimas, y limitarse a los límites de profundidad y complejidad y al limitador de 60 peticiones por minuto.
- **Cómo se resolvió:** las operaciones permitidas viven como archivos del proyecto y todo lo demás se rechaza por defecto, con la introspección apagada. Pruebas: `PersistedOperationRegistryTest`, `PersistedQueryInterceptorTest`, `GraphQlClosedByDefaultIntegrationTest`, `GraphQlOpenModeIntegrationTest` y `GraphQlOperationsConsistencyTest` en cada módulo.

### ERR-010 · Los permisos del contrato no se exigen en ninguna ruta REST de company-management

- **Estado:** ABIERTO. Visto leyendo el código el 2026-11-03 al corregir ERR-008 (BIT-022). **No reproducido** con una prueba.
- **De qué trata:** `PermissionResolver.resolve` concede a todo usuario cinco permisos fijos (`accounts.read`, `companies.create`, `companies.activate`, `branches.create` y `branches.activate`), además de los de su rol, y ningún controlador ni interceptor exige las autoridades `PERM_…` que construye el filtro JWT. Los `x-required-permission` del contrato (por ejemplo `companies.create`) no se aplican, y permisos como `accounts.suspend` ni siquiera existen en el seed V009. Desde BIT-022 se comprueba la pertenencia a la cuenta o empresa, pero no el permiso: dentro de lo suyo, quien tenga un token válido puede hacer cualquier operación REST.
- **Cuándo se manifiesta:** **inferido del código, no probado:** un miembro de una empresa con un rol de solo lectura podría, con el claim de esa empresa, crear o activar sucursales por REST.
- **Cómo mitigarlo:** hasta que se exijan permisos, dar acceso por REST solo a dueños y personas de confianza.
- **Para resolverlo:** definir qué permiso exige cada ruta, sembrar los que falten, hacer que `PermissionResolver` conceda solo los del rol y exigirlos con una prueba por ruta. Es una orden de trabajo propia, porque toca el modelo de roles.
