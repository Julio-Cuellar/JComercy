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
| ERR-010 | Los permisos no se exigen en ninguna ruta REST de company-management, el token de un inicio de sesión real no trae los permisos que inventario exige, y los comodines de los roles no los interpreta ningún módulo | RESUELTO (BIT-023 reproducción, BIT-024 solución) |
| ERR-011 | El token lleva juntos los permisos de todas las empresas de la persona: quien es dueña de una y miembro de otra lleva `*` también para la ajena | RESUELTO (BIT-026 a BIT-031) |
| ERR-012 | El repositorio git quedó con archivos de objeto vacíos y la rama `feat/token-por-empresa` apuntando a un commit ilegible; se perdió el commit de BIT-027, no su trabajo | RESUELTO (2026-11-03) |

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

### BIT-023 · ERR-010 reproducido y comodines de permisos en el núcleo y en inventario (fases 1 y 2)

- **Qué se hizo:** (1) se reprodujo ERR-010 con pruebas por HTTP y se descubrió que es más amplio de lo que decía la entrada; (2) `jcomercy-core` tiene ahora `Permissions`, la regla única para decidir si un permiso concedido cubre el que pide una operación (`*`, `familia.*` y exacto), e inventario la usa en `hasPermission`. Esto último es la base del resto de la corrección; ERR-010 sigue ABIERTO.
- **Cómo se hizo:**
  - **Fase 1, reproducción.** `PermissionEnforcementIntegrationTest` (nueva, company-management, 8 pruebas): registra cuentas reales, crea empresa y sucursal, mete membresías por SQL (no hay API que las cree) e inicia sesión de verdad. **RED (ejecución):** 7 de 8 fallan y 1 pasa (la del administrador de empresa, que protege lo que debe seguir permitido). Con un rol que no lo permite: el rol `STAFF` leyó la empresa (200 en vez de 403); `BRANCH_MANAGER` creó una sucursal (201, sin tener `branches.create`); una membresía suspendida siguió leyendo con el mismo token (200); un rol con el mismo nombre de otra empresa y `["*"]` dio acceso (200). Del token: el del dueño no llevaba `*`, y el de una cuenta sin empresas ni membresías llevaba los cinco permisos fijos (`accounts.read`, `companies.create`, `companies.activate`, `branches.create`, `branches.activate`).
  - **Fase 2, núcleo.** `PermissionsTest` (nueva, 10 pruebas). **RED:** error de compilación, `cannot find symbol: variable Permissions`. **GREEN:** 10 de 10 con `Permissions` (nueva): `*` concede todo, `familia.*` concede lo que cuelga de esa familia (no otras familias, no el nombre pelado, no un prefijo parecido como `inventoryx.read`), y el comodín solo cuenta al final (`inventory.*.read` y `*.read` no son comodines).
  - **Fase 2, inventario.** `JwtAuthorizationServiceTest` (nueva, 8 pruebas). **RED (ejecución):** 3 de 6 fallan, las de `*`, `inventory.*` y `inventory.stock.*`; pasaban las de permiso exacto, heredados y sin permisos. Se añadieron después 2 pruebas (forma `PERM_INVENTORY_STOCK_WRITE` y autoridad sin prefijo `PERM_`). **GREEN:** 8 de 8 con `JwtAuthorizationService.hasPermission`, que ahora quita el prefijo `PERM_` de cada autoridad y la compara con `Permissions.matches`, manteniendo la forma normalizada y la rama de compatibilidad con `INVENTORY_WRITE` e `INVENTORY_READ`. `inventory-infra/pom.xml` declara ahora la dependencia de `jcomercy-core`, que ya llegaba por transitividad.
  - **Mutaciones temporales, revertidas:** (A) quitar la forma normalizada → cae `theNormalizedFormStillWorks`; (B) quitar el filtro del prefijo `PERM_` → cae `anAuthorityWithoutThePermissionPrefixGrantsNothing` (como error de índice). El archivo quedó idéntico a antes de mutar.
  - **GREEN de los módulos tocados:** `mvn -B -pl inventory/inventory-infra -am test` → `BUILD SUCCESS`: núcleo 13 (3 + 10), inventory dominio 27, inventory infraestructura 72 (64 + 8). La suite de company-management no se ejecutó en esta fase (solo se añadió la prueba roja, sin commitear).
- **Afectaciones:**
  - **Inventario:** un token con `PERM_*` o `PERM_inventory.*` ahora pasa los chequeos de permiso de unas 25 rutas REST y de GraphQL. Hasta que el login los emita (fase 5) ninguna cuenta real lo trae, así que en la práctica no cambia el acceso.
  - **Dependencias:** `inventory-infra` declara `jcomercy-core`. `company-management-infra` la declarará en la fase 3.
  - **Prueba roja pendiente:** `PermissionEnforcementIntegrationTest` queda sin commitear con 7 pruebas en rojo hasta que se completen las fases 3 a 5.

### BIT-024 · Cada rol hace solo lo que su permiso permite en REST y GraphQL de company-management, y el login emite los permisos que inventario necesita (resuelve ERR-010; fases 3 a 5)

- **Qué se hizo:** las 10 rutas REST de cuentas, empresas y sucursales exigen el permiso que el rol de la persona le da en esa empresa; si no, 403 `INSUFFICIENT_PERMISSIONS` con "Tu rol no permite <acción>. Pide acceso a quien administra la cuenta." Las 2 consultas GraphQL exigen `companies.read` y `accounts.companies.read`. El token de un inicio de sesión lleva `*` para quien es dueña de una empresa y los permisos de su rol para un miembro, con los de inventario incluidos, e inventario acepta esos comodines.
- **Cómo se hizo:**
  - **Regla.** `EffectivePermissions` (nueva) decide en cada petición, desde la base de datos: la dueña de la cuenta tiene `*`; quien entra por una membresía ACTIVA recibe los permisos de sus roles, del sistema o de esa empresa y nunca de otra. Se leen de la base y no del token para que una membresía suspendida o un rol cambiado surtan efecto en la siguiente petición.
  - **Rutas.** `@RequiresPermission(value, action)` (nueva) en las 10 rutas; `RestPermissionInterceptor` (nuevo) la comprueba después del de pertenencia. Una ruta bajo una cuenta o empresa sin anotación se rechaza con un `ERROR` en el registro, para que olvidarla no la deje abierta. `RestOwnershipConfig` pasó a `RestAccessConfig` (con `git mv`) y registra ambos interceptores en orden.
  - **Token.** `PermissionResolver.resolve` es ahora la unión de `EffectivePermissions.inCompany` sobre las empresas de la persona; dejó de conceder los cinco permisos fijos. Una cuenta sin empresas ni membresías ya no lleva permisos.
  - **GraphQL.** `CompanyGraphQlController` usa `EffectivePermissions`; ya no depende del claim `companyIds`.
  - **Datos.** V022 siembra permisos de inventario en los roles del sistema: `COMPANY_ADMIN` +`inventory.*`; `BRANCH_MANAGER` +`inventory.stock.*`, `inventory.warehouses.read`, `inventory.products.read`; `STAFF` +`inventory.stock.read`. No se editó V009 (Flyway guarda su checksum).
  - **Contrato.** `suspendAccount` declara `x-required-permission: accounts.suspend`, que ningún rol del sistema concede: solo lo cubre el `*` de la dueña. `company-management-infra/pom.xml` declara `jcomercy-core`.
  - **RED (ejecución).** El de BIT-023 (7 de 8). Además `RequiredPermissionsContractTest` (nueva, 2 pruebas) listó las 10 rutas sin anotación; tras anotarlas, la segunda falló solo por `POST /accounts/{}/suspension` (el contrato no declaraba permiso), y se corrigió en el YAML. Se endureció esa prueba para que no pase por vacío (exige comparar al menos 10 rutas).
  - **GREEN.** `PermissionEnforcementIntegrationTest` 11 de 11 (las 8 de BIT-023 y 3 de GraphQL añadidas), `RequiredPermissionsContractTest` 2 de 2. Una de las 8 originales, `ownerTokenCarriesEveryPermission`, estaba mal escrita (pedía el token emitido antes de crear la empresa) y se corrigió para iniciar sesión después; se validó con la mutación M5.
  - **Prueba existente ajustada.** `RestOwnershipIntegrationTest#memberWithTheCompanyClaimReadsTheCompanyButNotTheAccount` usaba un miembro solo con claim, sin membresía; ahora tiene una con el rol `COMPANY_ADMIN`. Fijaba una regla más laxa: el claim abre la puerta de la empresa y el rol permite actuar.
  - **Inventario por HTTP.** `WildcardTokenIntegrationTest` (nueva, 3): un token con `*` crea un almacén (201); uno con `inventory.stock.*` consulta el stock (200) y no crea almacenes (403); uno sin permisos no consulta el stock (403). Con `hasPermission` revertido a comparación exacta fallan 2 de 3 (403 en vez de 201 y 200); la tercera pasa con y sin la mutación porque protege contra el exceso de permisos, no contra su falta.
  - **Mutaciones temporales (script, 7), todas revertidas; las huellas de los archivos coinciden con las de antes:** M1 sin el interceptor de permisos → fallan 4; M2 la membresía suspendida cuenta → falla la de suspensión; M3 los roles de otra empresa cuentan → falla la del rol ajeno; M4 GraphQL pide otro permiso → falla la de ocultar la empresa; M5 el token no lleva permisos → fallan las 2 del token; M6 una ruta declara otro permiso que el contrato → falla la prueba de contrato (y 3 de rol); M7 un controlador temporal sin anotación → el guardia lo nombra y una prueba temporal confirmó que el interceptor lo rechaza con 403 aunque la persona pertenezca a la empresa. Los archivos temporales de M7 se borraron.
  - **GREEN final:** `mvn -B test` en la raíz → `BUILD SUCCESS`, **206 pruebas** (13 + 8 + 8 + 75 + 27 + 75; +34 respecto a BIT-022).
  - **Verificación temporal que no se conserva:** el token de quien es dueña de una empresa y `STAFF` en otra llevó `[inventory.stock.read, operational_context.select, *]` y los ids de ambas empresas. Es el origen de ERR-011.
- **Afectaciones:**
  - **API:** quien tiene un rol que no permite la acción recibe 403 con un mensaje de negocio; antes pasaba. Una ruta nueva bajo `/accounts/{…}` o `/companies/{…}` sin `@RequiresPermission` se rechaza, y `RequiredPermissionsContractTest` falla antes de llegar a ejecutarse.
  - **Token:** ya no trae los cinco permisos fijos. Company-management no lo lee para decidir (calcula por petición); lo leen otros módulos.
  - **GraphQL:** una empresa creada después de iniciar sesión ya se ve sin renovar el token, lo que cierra el límite anotado en BIT-022.
  - **Rendimiento:** por petición, una lectura de la empresa y, para un miembro, de su membresía y de sus roles.
  - **Documentación:** `modelo-autorizacion-jcomercy.md` tiene una sección 11 con cómo se aplica hoy. `openapi-company-management-v1.yaml`, el permiso de `suspendAccount`.
  - **Decisiones:** (1) `BRANCH_MANAGER` no puede consultar la empresa, porque el seed V009 le da `branches.read` pero no `companies.read`. **Confirmada por el usuario el 2026-11-03:** el encargado de sucursal no ve la empresa a menos que se le otorgue ese permiso. (2) `inventory.stock.*` incluye `inventory.stock.adjust` e `inventory.stock.negative.enable`; sigue lo aprobado en el plan y **está por confirmar con el negocio**.
  - **Sin verificar:** el comportamiento con Postgres (las pruebas usan H2 y V022 es un `UPDATE` simple); un recorrido real entre las dos aplicaciones (en pruebas, la mitad de company-management y la mitad de inventario se encuentran en el claim `permissions`, no en una llamada de punta a punta); y los miembros se crearon por SQL, porque ninguna ruta crea membresías.
  - **Pendiente:** ERR-011.

### BIT-025 · Workflow de GitHub Actions que corre las pruebas en cada PR (parte A del plan)

- **Qué se hizo:** `.github/workflows/ci.yml` corre `mvn -B -ntp test` en cada PR hacia `main` y en cada push a `main`. Java 21 (Temurin) con caché de Maven, permisos de solo lectura, límite de 15 minutos, cancelación de la corrida anterior del mismo PR, y subida de los reportes de pruebas si algo falla.
- **Cómo se hizo:**
  - **Versiones.** `actions/checkout@v7`, `actions/setup-java@v6` y `actions/upload-artifact@v7`, las últimas mayores según la API de GitHub (v7.0.1, v6.1.0 y v7.0.2). Se leyó el `action.yml` de cada una y existen las entradas que se usan (`distribution`, `java-version`, `cache`, `name`, `path`, `if-no-files-found`). Las tres corren sobre Node 24. El borrador que se mostró en el artefacto decía v4; se cambió por estas.
  - **Medición.** `mvn -B -q test` en local con las dependencias en caché: 1 min 5 s. Por eso el límite de 15 minutos, que deja margen para una descarga completa.
  - **Validación local.** PyYAML cargó el archivo y se comprobaron 9 puntos: disparadores, permisos mínimos, cancelación de corridas viejas, límite de tiempo, Java 21 con caché, el comando de pruebas, la subida solo si falla, y que no hay secretos. No hay `actionlint` instalado. **Un workflow no se puede ejecutar en local, así que no hay un rojo previo.**
  - **Incidente.** El plan decía que la primera corrida sería la del PR #1, pero ese PR se fusionó (`4ff598b`, 2026-11-03 20:00:38 UTC) con la cabeza `bae22a3`, antes de que subiera el commit `fdcef9a` del workflow. `main` no lo tiene, y `gh run list` está vacío: **nunca corrió**. Se abrió el PR #2, de `contracts-v1` hacia `main`, que contiene solo `ci.yml` (45 líneas), para que sea su primera ejecución real.
- **Afectaciones:** cada PR y cada push a `main` muestran verde o rojo, una vez que el workflow esté en `main`. No cambia ningún código ni contrato.
- **Sin verificar:** la corrida real en GitHub (pendiente del PR #2), la versión de Maven del runner, el tiempo en frío, y que el camino rojo funcione (subir los reportes). Exigir el chequeo antes de fusionar es un ajuste de protección de ramas en GitHub, que no se tocó.

### BIT-026 · El login entrega un token de cuenta y la pertenencia a la empresa sale de la base de datos (parte B, fase B1 de ERR-011)

- **Qué se hizo:** `POST /auth/login` y `/auth/refresh` entregan un token de cuenta, que lleva solo la identidad: sin empresas, sucursales ni permisos. `JwtTokenService` sabe emitir también un token de empresa (`issueCompanyToken`), con el claim nuevo `ctx`, que aún no usa ninguna ruta. El control de pertenencia a una empresa deja de leer el claim `companyIds` del token y consulta la base de datos: dueña de la cuenta o membresía activa.
- **Cómo se hizo:**
  - **RED (ejecución).** `PermissionEnforcementIntegrationTest` y `RestOwnershipIntegrationTest`, 3 de 25 fallan: el token del login aún nombraba empresas y permisos; una membresía suspendida se rechazaba por el control de permisos y no por el de pertenencia; y un miembro sin claim recibía 403. **RED (compilación):** `JwtTokenServiceTest` (nueva, 3 pruebas), porque faltaban `issueAccountToken` e `issueCompanyToken`.
  - **GREEN.** Infraestructura de company-management: 76 de 76 (eran 75: se reemplazaron 3 pruebas del token del login por 1, y se añadieron 3 de `JwtTokenServiceTest`). `BUILD SUCCESS` de los módulos de company-management. Un error de compilación de mi prueba (`containsExactly` sobre `List<?>`) se corrigió con un `List<String>` explícito.
  - **Código.** `JwtTokenService`: `issueAccountToken`, `issueCompanyToken` y un método privado común. `AuthController`: login y renovación emiten el token de cuenta y el controlador ya no depende de `PermissionResolver`. `EffectivePermissions.belongsToCompany` (nuevo): dueña o membresía activa, desde la base. `RestOwnershipInterceptor` lo usa en lugar del claim y ya no depende de `CompanyRepositoryPort`.
  - **Pruebas cambiadas.** Las 3 pruebas del token de login por una (`loginTokenIsAnAccountTokenWithoutCompaniesOrPermissions`); las equivalentes del token de empresa llegan en B2. El miembro de ERR-008 ya no lleva claim. La membresía suspendida ahora espera el mensaje de pertenencia, "No tienes acceso a esta empresa…", que es más correcto: una persona suspendida ya no pertenece.
- **Afectaciones:**
  - **API:** el `accessToken` del login ya no nombra empresas ni permisos. Las rutas de empresa siguen aceptando el token de cuenta hasta B3.
  - **Regresión temporal:** entre B1 y B2, un token de login real **no abre inventario**, porque ya no nombra empresas. Hoy no hay clientes. Se recupera en B2, con el token de empresa.
  - **Código que queda sin uso:** `PermissionResolver` (lo retoma B2) y `JwtAuthorizationService.canAccessCompany` con el claim de `JwtContext` (solo lo ejercita `JwtContextTest`); se limpian en B3.
  - **Sin verificar:** no se ejecutó la suite de inventario en esta fase, porque no cambió; se ejecuta completa en B6.

### BIT-027 · «Tus empresas» y entrar a una empresa entregan el token de esa empresa (parte B, fase B2 de ERR-011)

- **Qué se hizo:** dos rutas nuevas, `GET /api/v1/accounts/{id}/company-accesses` (las empresas que la cuenta posee y aquellas donde es miembro) y `POST …/company-accesses/{targetCompanyId}/session` (entra a una y entrega el token de esa empresa). El token nombra solo esa empresa, con las sucursales y los permisos que la persona tiene en ella, y nunca los de otra. Es el reproductor permanente de ERR-011.
- **Cómo se hizo:**
  - **Diseño.** `CompanyAccessService` (nuevo) lista y entra. La dueña recibe `*` y todas las sucursales activas de la empresa; un miembro, los permisos de sus roles en esa empresa y solo las sucursales que tiene asignadas. Una empresa ajena responde lo mismo que una que no existe: 403 con el mensaje de pertenencia, que ahora es una constante pública de `RestOwnershipInterceptor`. Un miembro suspendido no entra ni aparece en la lista. `EffectivePermissions` expone `ownsTheCompany`, `rolesInCompany` y `activeMembershipCompanyIds`, y `PermissionResolver` se eliminó porque quedó sin uso (`git rm`).
  - **Trampa evitada.** La variable de la empresa en la ruta de entrada se llama `targetCompanyId` y no `publicCompanyId`. Con ese nombre, `RestPermissionInterceptor` trataría la ruta como una ruta de empresa y un miembro, sin ese permiso en su rol, no podría entrar a su propia empresa.
  - **RED (ejecución).** `CompanyAccessIntegrationTest` (nueva, 9 pruebas): 9 de 9 fallan con 404, porque las rutas no existían.
  - **GREEN.** 9 de 9. Infraestructura de company-management: 85 de 85 (eran 76), con `OpenApiContractTest` y `RequiredPermissionsContractTest` incluidos: las rutas nuevas ya están en el contrato (`listCompanyAccesses`, `enterCompany`, esquemas `CompanyAccess` y `CompanySession`) y sus permisos, `accounts.company_accesses.read` y `accounts.company_accesses.enter`, coinciden.
  - **Prueba que ajusté antes de las mutaciones.** La segunda sucursal del fixture no estaba activa, así que dar a un miembro «todas las sucursales activas» habría pasado igual. Se activó. Con eso la mutación M2 se detecta.
  - **Mutaciones temporales (script, 7), todas revertidas; las huellas de los archivos coinciden con las de antes:** M1 el token lleva `*` aunque sea miembro → falla la prueba de ERR-011; M2 un miembro recibe todas las sucursales activas → falla la de sucursales asignadas; M3 entrar sin comprobar pertenencia → fallan las 2 de rechazo; M4 la lista incluye membresías suspendidas → falla la de la lista; M5 la lista pierde los roles → falla la de la lista; M6 el token sin `ctx` → fallan 2 de acceso y `JwtTokenServiceTest`; M7 la ruta declara otro permiso que el contrato → falla `RequiredPermissionsContractTest`.
- **Afectaciones:**
  - **API:** dos rutas nuevas bajo `/accounts/{id}`. Solo la propia cuenta las usa; otra cuenta recibe 403 por el control de pertenencia.
  - **Recupera lo que B1 quitó:** con un token de empresa, inventario vuelve a abrirse con un login real: el token trae `companyIds`, `branchIds` y `permissions`, que es todo lo que inventario lee.
  - **Rendimiento:** entrar hace unas 5 lecturas; listar, 2 más por cada empresa donde la persona es miembro.
  - **Sin verificar:** no se probó el token de empresa contra inventario en esta fase; se prueba en B6 con la suite completa y una prueba de punta a punta del claim. El estado de la empresa (`CONFIGURING`, `ACTIVE`…) no limita la entrada: quien la creó necesita entrar para configurarla.

### BIT-028 · Cada ruta exige el token que le corresponde: el de la cuenta o el de esa empresa (parte B, fase B3 de ERR-011)

- **Qué se hizo:** las rutas bajo `/accounts/{id}` exigen el token de cuenta y rechazan el de una empresa; las rutas bajo `/companies/{id}` exigen el token de **esa** empresa y rechazan el de cuenta y el de otra empresa. GraphQL aplica lo mismo: `companiesByAccount` solo al token de cuenta y `company` solo al token de esa empresa. Un token de empresa robado ya no administra la cuenta ni lista las demás empresas. Se elimina el código del claim `companyIds` que B1 dejó sin uso.
- **Cómo se hizo:**
  - **Diseño.** `JwtAuthenticationFilter` lee el claim `ctx` y lo deja en `JwtContext(accountId, companyContext)`; sin `ctx` es token de cuenta. Un `ctx` que no sea texto o venga en blanco deja la petición sin autenticar (401). `RestOwnershipInterceptor` comprueba, en este orden: (1) que la cuenta sea la del token (`ACCOUNT_DENIED`); (2) en rutas de cuenta, que el token sea de cuenta (`ACCOUNT_ONLY`); (3) en rutas de empresa, que el token sea el de esa empresa (`COMPANY_NOT_CHOSEN` si trae el de cuenta, `OTHER_COMPANY_SESSION` si trae el de otra); (4) que la persona pertenezca a la empresa según la base de datos (`COMPANY_DENIED`). El tipo de token se revisa antes de buscar la empresa, así la respuesta no dice cuáles existen. `EffectivePermissions.canInAccount` y `canInCompany` (las usa GraphQL) exigen el mismo tipo. Los tres mensajes nuevos no mencionan «token»: hablan de «tus empresas» y de «tu cuenta».
  - **Código eliminado:** `JwtAuthorizationService.canAccessCompany`, `JwtContext.canAccessCompany` y `JwtContext.claimValues`, y los campos `companyIds` y `branchIds` del record. `PermissionResolver` ya había salido en BIT-027.
  - **RED (ejecución).** `TokenKindEnforcementIntegrationTest` (nueva): 5 de 7 fallan, con `200` donde se esperaba `403` y GraphQL entregando la lista y la empresa; las otras 2 ya pasaban y quedan de guardia.
  - **GREEN.** 7 de 7, y `JwtContextTest` reescrita, 5 de 5. Al correr el módulo completo con el código nuevo, **35 de 94 pruebas existentes fallaron con `403`**, todas por el mismo motivo: su montaje llamaba a rutas de empresa con el token de la cuenta. Es el cambio rompiente del plan. Se ajustaron 4 clases (`CompanyIntegrationTest`, `CompanyAccessIntegrationTest`, `PermissionEnforcementIntegrationTest`, `RestOwnershipIntegrationTest`) con una ayuda común, `RestWorld.enter`: solo cambia **con qué token** se llama. En `RestOwnershipIntegrationTest`, quien no pertenece usa un token fabricado que nombra la empresa, para seguir probando que la pertenencia la decide la base y no el token. **Una aserción sí cambió:** la de «el token del login no sirve en la empresa de otro» esperaba `COMPANY_DENIED` y ahora espera `COMPANY_NOT_CHOSEN`, porque el token de cuenta se rechaza antes por su tipo.
  - **Prueba escrita después del código.** `aTokenWithAMalformedCompanyContextIsNotAccepted` se escribió cuando la guarda del filtro ya existía, sin un rojo previo. Se validó con la mutación M8.
  - **Mutaciones temporales (script, 10), todas detectadas y revertidas; las huellas de los cuatro archivos coinciden con las de antes:** M1 las rutas de empresa aceptan cualquier token → caen 3; M2 el token de una empresa vale en cualquiera → caen 3; M3 las rutas de la cuenta aceptan el token de empresa → cae 1; M4 GraphQL entrega la lista de la cuenta a cualquier token → cae 1; M5 GraphQL entrega la empresa a cualquier token → cae 1; M6 el filtro ignora `ctx` → caen 40; M7 la pertenencia deja de comprobarse en la base → caen 8; M8 el filtro acepta un `ctx` malformado → cae la prueba del `ctx` malformado; M9 los mensajes de tipo van invertidos → caen 3; M10 se busca la empresa antes de revisar el tipo (revelaría cuáles existen) → caen 2.
  - **GREEN final:** `mvn -B -ntp test` en la raíz → `BUILD SUCCESS`, **226 pruebas** (13 + 8 + 8 + 95 + 27 + 75; +10 respecto a BIT-027: 8 de la clase nueva y 2 más en `JwtContextTest`).
- **Afectaciones:**
  - **API (rompiente):** quien llame a rutas de empresa con el token del login recibe 403 y debe entrar antes con `POST /accounts/{id}/company-accesses/{empresa}/session`. Las rutas de cuenta rechazan el token de empresa: para volver a la cuenta se usa el token de cuenta. Mismo `403` y código `INSUFFICIENT_PERMISSIONS`; tres mensajes nuevos. Hoy no hay clientes.
  - **Renovar la sesión:** `/auth/refresh` recibe el refresh token y siempre emite un token de cuenta, por lo que un token de empresa no sirve para obtener uno de cuenta. Está leído en el código; no hay una prueba que lo ejerza.
  - **GraphQL:** con el token equivocado, `company` responde `null` y `companiesByAccount`, una lista vacía.
  - **Inventario:** sin cambios de código. Su suite (27 + 75) pasa, pero sus pruebas fabrican sus propios tokens; el recorrido real de iniciar sesión, entrar a una empresa y llamar a inventario sigue sin probarse de punta a punta (B6).
  - **Contrato y documentación:** no se actualizó el OpenAPI ni `modelo-autorizacion-jcomercy.md`; es B5. Las pruebas de contrato pasan porque el conjunto de rutas no cambió.
  - **Sin eliminar, fuera de alcance:** `JwtTokenService.issue(...)` (dos sobrecargas) ya no lo usa producción; solo lo usan tres pruebas para fabricar tokens.
  - **Pendiente:** ERR-011 sigue ABIERTO hasta B5.

### BIT-029 · Contraseña opcional por empresa, con bloqueo tras cinco intentos fallidos (parte B, fase B4 de ERR-011)

- **Qué se hizo:** cada persona puede ponerle, si quiere, una contraseña propia a su acceso a una empresa. Con ella, entrar la pide; cinco intentos fallidos bloquean ese acceso quince minutos, y el contador vive en la base de datos. Rutas nuevas: `PUT` y `DELETE` sobre `/accounts/{id}/company-accesses/{empresa}/password`; el `POST …/session` acepta un cuerpo opcional `{password}`; y «Tus empresas» marca con `passwordProtected` los accesos protegidos. Sin contraseña, nada cambia. La contraseña no sustituye los permisos: solo protege la puerta.
- **Cómo se hizo:**
  - **Reglas** (las del plan aprobado más la decisión 3): se guarda solo la huella bcrypt; cambiarla exige la actual; **quitarla exige la contraseña de la cuenta**, que es la salida si se olvidó la de la empresa; la nueva debe tener al menos 8 caracteres y ser distinta a la de la cuenta; equivocarse al entrar, al cambiar o al quitar gasta el **mismo** contador, para que ninguna de las tres vías sirva para adivinar; bloqueado, ni la contraseña correcta entra; entrar bien o pasar el bloqueo lo borra. Quien no pertenece a la empresa recibe el mismo `403` de siempre, antes de tocar nada.
  - **Respuestas** (el plan no las fijaba; las elegí yo): contraseña ausente o equivocada, `403` con `COMPANY_PASSWORD_REQUIRED` o `COMPANY_PASSWORD_INVALID`, y el mensaje dice cuántos intentos quedan; contraseña de cuenta ausente o equivocada al quitar, `403` con `ACCOUNT_PASSWORD_REQUIRED` o `ACCOUNT_PASSWORD_INVALID`; bloqueo, `429` con `COMPANY_ACCESS_LOCKED` y `Retry-After` en segundos. Ausente no gasta intento; equivocada sí.
  - **Código:** `V023` (tabla `company_access_password`, única por persona y empresa), `CompanyAccessPasswords` (el almacén y las reglas), `CompanyAccessPasswordException`, y cambios en `CompanyAccessService`, `CompanyAccessController` y `GlobalExceptionHandler`. Cada paso del contador es una sentencia propia, sin transacción compartida, para que el intento fallido quede guardado aunque la petición termine en error; el incremento es atómico (`failed_attempts + 1`).
  - **RED (ejecución).** `CompanyAccessPasswordIntegrationTest` (nueva): 16 de 16 fallan, 15 con `404` porque las rutas no existían y 1 por la falta de `passwordProtected`.
  - **GREEN.** 16 de 16. Al correr el módulo entero fallaron 3 pruebas de contrato (`OpenApiContractTest` y `RequiredPermissionsContractTest`), como se esperaba, porque el OpenAPI no describía las rutas nuevas; se actualizó `openapi-company-management-v1.yaml` (rutas, cuerpo de `enterCompany`, tres esquemas de petición, respuesta `AccessLocked` y `passwordProtected`) y quedó en 111 de 111. Esas pruebas exigen además 400 y 415 en toda operación con cuerpo, y se declararon.
  - **Hueco encontrado antes de mutar:** ninguna prueba comprobaba que al bloquearse el contador vuelve a cero, y sin eso bastaría un solo fallo para bloquear de nuevo. Se añadió esa aserción; la mutación M18 lo confirma.
  - **Mutaciones temporales (script, 18), todas detectadas y revertidas; las huellas de los tres archivos coinciden con las de antes:** M1 el bloqueo llega a los 6 intentos → caen 7; M2 dura 1 minuto → cae 1; M3 un acceso bloqueado no se rechaza → caen 5; M4 el fallo no se anota → caen 8; M5 entrar bien no borra el contador → caen 2; M6 cambiar no exige la actual → caen 3; M7 quitar acepta la contraseña de la empresa → cae 1; M8 se acepta la igual a la de la cuenta → caen 2; M9 se acepta de menos de 8 caracteres → caen 2; M10 se guarda sin cifrar → caen 8; M11 el registro incluye la contraseña → cae 1; M12 poner no comprueba la pertenencia → caen 2; M13 la lista no marca los accesos protegidos → caen 3; M14 entrar no pide la contraseña → caen 9; M15 el bloqueo no envía `Retry-After` → cae 1; M16 quitar sin la contraseña de la cuenta no se pide → cae 1; M17 el bloqueo ya pasado se sigue respetando → caen 2; M18 al bloquearse el contador no vuelve a cero → cae 1.
  - **GREEN final:** `mvn -B -ntp test` en la raíz → `BUILD SUCCESS`, **242 pruebas** (13 + 8 + 8 + 111 + 27 + 75; +16 respecto a BIT-028).
- **Afectaciones:**
  - **API (aditiva):** dos rutas nuevas, un cuerpo opcional y un campo nuevo en la lista; quien no use contraseñas no nota nada. Códigos nuevos: `COMPANY_PASSWORD_REQUIRED`, `COMPANY_PASSWORD_INVALID`, `ACCOUNT_PASSWORD_REQUIRED`, `ACCOUNT_PASSWORD_INVALID` (403) y `COMPANY_ACCESS_LOCKED` (429).
  - **Datos:** tabla nueva `company_management.company_access_password`. Guarda solo la huella; ninguna contraseña llega al registro (lo comprueba una prueba que captura la salida).
  - **Contrato:** permisos nuevos `accounts.company_accesses.password.set` y `accounts.company_accesses.password.remove`. No se siembran en ningún rol: solo la propia cuenta usa estas rutas y dentro de lo suyo puede todo. El resto de la documentación (`modelo-autorizacion-jcomercy.md`) queda para B5.
  - **Inventario y GraphQL:** sin cambios.
  - **Riesgos aceptados o no cubiertos:**
    - **El bloqueo se puede usar como arma** (ya advertido en el plan): quien tenga tu token de cuenta puede fallar a propósito y dejarte quince minutos fuera de una empresa.
    - **Poner la primera contraseña no pide la de la cuenta** (el plan no lo exige): quien tenga tu sesión de cuenta podría proteger un acceso y dejarte fuera. La salida es quitarla con la contraseña de la cuenta.
    - **La contraseña de la cuenta se puede probar por el `DELETE`**, pero solo con el contador del acceso y solo si ese acceso tiene contraseña. El inicio de sesión no tiene límite de intentos; ya era así y no se tocó.
    - **`DELETE` con cuerpo:** OpenAPI 3.1 lo permite, pero algunos clientes y proxies descartan el cuerpo de un `DELETE`. Sin cuerpo, la respuesta es `ACCOUNT_PASSWORD_REQUIRED`, no un fallo.
  - **Sin verificar:** el comportamiento con Postgres (todo corre con H2; el SQL es estándar); dos intentos simultáneos de verdad (el incremento es atómico, pero no hay prueba de concurrencia); un reinicio real de la aplicación (se comprueba que el contador está en la base, no un reinicio); y la espera de quince minutos reales (se simula moviendo `locked_until`).

### BIT-030 · Documentación del token por empresa y cierre de ERR-011 (parte B, fase B5)

- **Qué se hizo:** se puso al día la documentación que describía el token anterior: el OpenAPI (qué clase de token entrega cada ruta y cuándo responde 403), la sección 11 de `modelo-autorizacion-jcomercy.md` y el catálogo de interfaces. ERR-011 pasa a RESUELTO.
- **Cómo se hizo:** solo documentación, sin código. En `openapi-company-management-v1.yaml`: una sección «Cómo se autentica» en la descripción del contrato, la descripción de `bearerAuth`, qué entregan `login` y `refresh` (un token de cuenta, y que renovar no renueva el de una empresa) y los casos de la respuesta `Forbidden` por clase de token. En `modelo-autorizacion-jcomercy.md` sección 11: se reemplazó la viñeta del token con permisos mezclados y su «límite conocido (ERR-011)» por las dos clases de token, la regla de cada ruta, que el token no decide si la persona sigue perteneciendo, la contraseña por empresa, el límite que sí queda y las membresías por API como pendiente. En `catalogo-endpoints-company-management.md`: la convención de qué token va en cada ruta y la sección 3.3 con las cuatro rutas de «Tus empresas» y la contraseña. En esta bitácora: el estado de ERR-011, su «Cómo se resolvió» y lo que no cubre.
  - **Verificación.** Al no haber código no hay RED. El YAML se cargó con PyYAML sin error y `mvn -B -ntp test` en la raíz terminó en `BUILD SUCCESS` con las mismas **242 pruebas**; `OpenApiContractTest` y `RequiredPermissionsContractTest` leen este YAML y pasan.
- **Afectaciones:** ninguna sobre el código ni sobre el conjunto de rutas. Cambia lo que quien consuma el contrato lee sobre qué token usar.
- **Sin revisar:** `contrato-openapi-company-management-v1.md`, `informe-validacion-implementacion-company-management.md`, `diseno-interfaces-company-management.md` y las órdenes de trabajo solo los busqué por palabras clave y no los leí enteros. El primero es la prosa de diseño anterior a la implementación y habla de un `access_token` con ramas y contexto operativo; es probable que no coincida con el flujo actual. Tampoco se probó el OpenAPI contra un cliente real.
- **Pendiente:** B6 (la prueba de punta a punta contra inventario) y la parte C (acotar `BRANCH_MANAGER`).

### BIT-031 · Prueba de punta a punta del token por empresa contra inventario (parte B, fase B6)

- **Qué se hizo:** `e2e/token-por-empresa/` arranca company-management e inventario como **procesos reales**, recorre con HTTP el caso de ERR-011 (iniciar sesión, entrar a una empresa, llamar a inventario con ese token) y los detiene al terminar. `ejecutar.sh` construye, arranca, verifica y limpia; `verificar.py` hace las 18 verificaciones. Cierra lo único que ERR-011 dejaba sin ejercitar: hasta aquí las pruebas de inventario fabricaban sus propios tokens.
- **Cómo se hizo:**
  - **Por qué un script y no una prueba JUnit.** Son dos aplicaciones independientes: ningún módulo depende del otro, y las dos traen `application.yml` y `db/migration` en la misma ruta del classpath, así que no caben en una sola JVM de pruebas sin refactorizar. Se descartó ese refactor (por ejemplo, mover el acuerdo de claims a `jcomercy-core`) por quedar fuera de alcance; queda anotado como mejora posible.
  - **Montaje.** Dos cuentas: Alice, dueña de la empresa A, y Bob, dueño de la B. Alice se hace personal (`STAFF`) de B con su sucursal asignada, por SQL sobre una base H2 en archivo con `AUTO_SERVER`, porque ninguna ruta crea membresías. Cada dueño crea su empresa y su sucursal activa, y vuelve a entrar para que el token lleve esa sucursal.
  - **Qué se verificó (18):** el token del login no nombra empresas ni permisos; el de Alice en A nombra solo A y lleva `*`; el de Alice en B nombra solo B y **no** lleva `*`. En inventario: el token de cuenta recibe 403 al consultar stock y crear almacenes; el de A abre A (200 y 201) y recibe 403 en B; el de Alice en B consulta el stock (200) pero recibe **403 al crear almacenes y al ajustar stock**. Control: el dueño de B sí crea almacén (201) y pasa el control de permisos al ajustar.
  - **Contraste.** Se fabrica el token que se emitía antes de ERR-011 (`*` y las dos empresas) y se prueba contra inventario: Alice crea un almacén en B (201) y pasa el control de permisos para ajustar. Es la prueba de que los 403 del token nuevo se deben al token y no al montaje.
  - **Mutación real.** Se cambió temporalmente `CompanyAccessService.enter` para conceder `*` en cualquier empresa, se reconstruyó y se corrió el recorrido completo: **cayeron 4 de 18** (el token de Alice en B lleva `*`, y crear almacén y ajustar stock dejan de dar 403; la cuarta es un efecto en cascada sobre Bob). Se restauró el archivo con la misma huella de antes (`e964fbb…`), se reconstruyó y volvió a dar 18 de 18.
  - **Lo que encontró la primera ejecución.** Falló 1 de 18, y era un defecto de mi propio script: el almacén del contraste daba 409 porque la sucursal ya tenía un predeterminado. En el registro de inventario se ve que el token antiguo sí pasó el control de permisos; se corrigió creando el almacén como no predeterminado.
  - **Dónde vive.** `.gitignore` ignora `scripts/` (heredado de la plantilla de Clinerya, junto a `nginx.conf` y `postman/`), así que la prueba está en `e2e/`, que sí se versiona.
  - **GREEN final:** `ejecutar.sh` → 18 de 18, sin puertos ni procesos sobrantes (tarda unos minutos, sobre todo en construir; no se cronometró); `mvn -B -ntp test` en la raíz → `BUILD SUCCESS`, **242 pruebas**, sin cambios respecto a BIT-029 porque no se tocó código Java.
- **Afectaciones:**
  - **Código y contrato:** ninguno. Solo se añade `e2e/` y se actualiza la documentación.
  - **Límite que pasa de inferido a verificado:** tras suspender la membresía de Alice en B, company-management ya no la deja entrar (403), pero inventario sigue aceptando el token de B que ya tenía (200) hasta que expire, como máximo una hora.
  - **Cómo se corre:** `e2e/token-por-empresa/ejecutar.sh [--sin-construir]`. Necesita Java 21, Maven y python3, y los puertos 18080 y 18081 libres. Funciona con el secreto local por defecto de las dos aplicaciones; si se cambia `SECURITY_JWT_SECRET`, hay que exportarlo.
  - **Riesgo operativo que deja ver:** las dos aplicaciones deben compartir el secreto del JWT. Si difieren, inventario rechaza todo token de company-management. No se probó ese caso.
  - **Sin verificar:** Postgres (todo corre con H2); RabbitMQ (las aplicaciones corrieron sin él, así que el Outbox no se ejercitó); y que el script no está en el CI de GitHub Actions: un cambio que rompa el acuerdo de claims entre módulos solo se detecta si alguien lo ejecuta.
- **Pendiente:** la parte C (acotar `BRANCH_MANAGER`, migración `V024`).

### BIT-032 · El encargado de sucursal recibe y consulta mercancía, pero no ajusta el stock (parte C)

- **Qué se hizo:** la migración `V024` deja al rol `BRANCH_MANAGER` con `inventory.stock.read` e `inventory.stock.write` y le quita `inventory.stock.adjust` e `inventory.stock.negative.enable`. El resto de su rol y los demás roles del sistema no cambian. Cierra la decisión (2) que BIT-024 dejó «por confirmar con el negocio» y que se aprobó como decisión 4 del plan.
- **Cómo se hizo:**
  - **Origen.** V022 le dio al encargado `inventory.stock.*`, que cubre los cuatro permisos de stock que inventario exige: `read`, `write`, `adjust` y `negative.enable`.
  - **Migración nueva y no edición de V022**, porque V022 ya pudo aplicarse en otras bases. Es un `UPDATE` por `public_role_id = 'rol_sys_branch_manager'`, del mismo estilo que V022. Conserva `branches.read`, `branches.update`, `operations.*`, `inventory.warehouses.read` e `inventory.products.read`. **Sobrescribe el rol entero**: un cambio hecho a mano a ese rol del sistema se perdería.
  - **RED (ejecución).** `BranchManagerDefaultPermissionsIntegrationTest` (nueva, 3 pruebas): 2 de 3 fallan, porque el token del encargado traía `inventory.stock.*` y `negative.enable` daba `true`. La tercera, «los otros roles conservan lo suyo», ya pasaba y queda de guarda.
  - **GREEN.** 3 de 3; `company-management-infra`, 114 de 114 (eran 111). Ninguna prueba existente se rompió.
  - **Hueco encontrado antes de mutar:** la prueba no comprobaba que el encargado conserva `operations.*`, y la migración promete no tocar el resto de su rol. Se añadió; la mutación M6 lo confirma.
  - **Mutaciones temporales sobre la migración (script, 7), todas detectadas y revertidas; la huella coincide con la de antes (`bec3297…`):** M1 sin `WHERE`, actualiza todos los roles → caen 3; M2 conserva `adjust` → cae 1; M3 conserva el stock negativo → caen 2; M4 vuelve a `inventory.stock.*` → caen 2; M5 pierde `inventory.stock.write` → caen 2; M6 pierde `operations.*` → cae 1; M7 actualiza otro rol → caen 3.
  - **De punta a punta.** `e2e/token-por-empresa` pasa de 18 a **24 verificaciones**: Carla, `BRANCH_MANAGER` de la empresa B con su sucursal asignada, recibe un token con `inventory.stock.read` e `inventory.stock.write` y sin `adjust`, `negative.enable` ni comodines de stock. En inventario real consulta el stock (200), **recibe mercancía (201)** y **recibe 403 al ajustarlo**; el dueño de B sí ajusta lo recibido (201). Para eso el script ahora crea un almacén activo, una ubicación y un producto.
  - **Mutación real de punta a punta.** Con la migración puesta de nuevo en `inventory.stock.*`, se reconstruyó y se corrió el recorrido: **cayeron 3 de 24** y inventario respondió **201 al ajuste del encargado**, que es el daño que esta fase evita. Restaurado el archivo (misma huella) y reconstruido, vuelve a 24 de 24.
  - **GREEN final:** `mvn -B -ntp test` en la raíz → `BUILD SUCCESS`, **245 pruebas** (13 + 8 + 8 + 114 + 27 + 75; +3).
- **Afectaciones:**
  - **Comportamiento:** quien ya era encargado y ajustaba stock deja de poder hacerlo cuando recibe un token nuevo. Hoy no hay clientes. Los tokens ya emitidos con `inventory.stock.*` siguen valiendo hasta que expiren (una hora).
  - **Datos:** `V024` modifica una fila de `company_management.role`.
  - **Documentación:** la sección 11 de `modelo-autorizacion-jcomercy.md` dice ahora qué recibe cada rol del sistema en inventario.
  - **Límite importante:** «cada empresa podrá concedérselo con un rol propio» **todavía no se puede hacer por la API**. Las rutas para crear roles y membresías están en el contrato pero no existen; la prueba lo demuestra insertando el rol directamente en la base. Hasta que existan, nadie puede devolverle el permiso de ajustar a un encargado sin tocar la base.
  - **Sin verificar:** Postgres (todo corre con H2); el stock negativo de punta a punta (se prueba que el permiso no llega, pero no se ejercitó en inventario una salida que deje el saldo en negativo); y el script sigue sin correr en el CI.
- **Pendiente:** las rutas de roles y de membresías, que son un plan propio.

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

- **Estado:** RESUELTO el 2026-11-03 (BIT-024). Visto leyendo el código al corregir ERR-008 (BIT-022) y **reproducido** ese mismo día con `PermissionEnforcementIntegrationTest` (BIT-023).
- **Cómo se resolvió:** `EffectivePermissions` calcula en cada petición lo que cada persona puede hacer en cada empresa (la dueña, `*`; un miembro, los permisos de sus roles en esa empresa), cada ruta declara su permiso con `@RequiresPermission` y `RestPermissionInterceptor` lo exige, el login emite esos permisos en el token, inventario interpreta los comodines y V022 siembra los permisos de inventario de los roles del sistema. Pruebas: `PermissionEnforcementIntegrationTest`, `RequiredPermissionsContractTest`, `JwtAuthorizationServiceTest`, `WildcardTokenIntegrationTest` y `PermissionsTest`. Lo que queda abierto del tema, la unión global de permisos en el token, es ERR-011.
- **Alcance ampliado (leído en el código al planear la corrección):** además de lo anterior, (1) ningún código crea membresías: hoy solo existen dueños, y el rol `ACCOUNT_OWNER` (`["*"]`) está sembrado pero no se asigna a nadie; (2) el token de un inicio de sesión real no trae ningún permiso `inventory.*`, así que no abre inventario por REST ni por GraphQL (las pruebas de inventario fabrican tokens con el permiso heredado `INVENTORY_WRITE`); (3) los cinco permisos fijos no incluyen `companies.read`, `branches.read` ni `accounts.companies.read`; (4) los roles sembrados traen comodines (`*`, `companies.*`) que se copian como texto y ningún módulo interpreta; (5) el claim `permissions` es global, la unión de todas las membresías, y no por empresa como pide `modelo-autorizacion-jcomercy.md`.
- **De qué trata:** `PermissionResolver.resolve` concede a todo usuario cinco permisos fijos (`accounts.read`, `companies.create`, `companies.activate`, `branches.create` y `branches.activate`), además de los de su rol, y ningún controlador ni interceptor exige las autoridades `PERM_…` que construye el filtro JWT. Los `x-required-permission` del contrato (por ejemplo `companies.create`) no se aplican, y permisos como `accounts.suspend` ni siquiera existen en el seed V009. Desde BIT-022 se comprueba la pertenencia a la cuenta o empresa, pero no el permiso: dentro de lo suyo, quien tenga un token válido puede hacer cualquier operación REST.
- **Cuándo se manifiesta:** **reproducido (BIT-023):** un miembro con el rol `STAFF` lee la empresa (200); uno con `BRANCH_MANAGER` crea una sucursal (201) sin tener `branches.create`; una membresía suspendida sigue funcionando con el mismo token; y el token de un inicio de sesión real no lleva `*` para el dueño ni permisos de inventario para nadie.
- **Cómo mitigarlo:** hasta que se exijan permisos, dar acceso por REST solo a dueños y personas de confianza.
- **Para resolverlo:** definir qué permiso exige cada ruta, sembrar los que falten, hacer que `PermissionResolver` conceda solo los del rol y exigirlos con una prueba por ruta. Es una orden de trabajo propia, porque toca el modelo de roles.

### ERR-011 · El token lleva juntos los permisos de todas las empresas de la persona

- **Estado:** RESUELTO el 2026-11-03 (BIT-026 a BIT-031). Visto al planear ERR-010 y **reproducido** el 2026-11-03 en el token (verificación temporal de BIT-024, que no se conserva como prueba).
- **De qué trata:** el claim `permissions` del token es la unión de lo que la persona puede hacer en cada una de sus empresas, y los demás módulos, como inventario, solo leen el token: `hasPermission` no distingue la empresa, y la empresa se comprueba aparte con `canAccessCompany`. Company-management no se ve afectado, porque decide en cada petición desde la base de datos.
- **Cuándo se manifiesta:** **reproducido en el token:** quien es dueña de su propia empresa y además miembro `STAFF` de otra recibe `permissions = [inventory.stock.read, operational_context.select, *]` con los ids de las dos empresas. **Inferido del código, no ejecutado contra inventario:** ese token da en inventario todos los permisos también en la empresa donde solo es `STAFF`. Hoy no es alcanzable por la API, porque ninguna ruta crea membresías; solo por inserción directa en la base.
- **Cómo mitigarlo:** no crear membresías, ni por SQL, para personas que sean dueñas de otra empresa, hasta resolverlo. Hasta entonces el riesgo se limita a los datos de inventario de la empresa ajena.
- **Para resolverlo:** que el token lleve los permisos por empresa y que inventario evalúe `hasPermission` con la empresa de la ruta; o que inventario los consulte a company-management. Debe resolverse antes de construir las rutas de membresías (`memberships.invite` y las demás). Toca el contrato del token entre módulos y unas 25 comprobaciones en inventario.
- **Cómo se resolvió:** de las dos vías que proponía «Para resolverlo» se tomó una tercera, más simple: el token nunca junta empresas, así inventario no necesita cambios. El login entrega un token de cuenta sin empresas ni permisos (BIT-026); entrar a una empresa entrega un token que nombra solo esa empresa, con los permisos que la persona tiene en ella (BIT-027); cada ruta exige su clase de token, de modo que uno robado no administra la cuenta ni lista las otras empresas (BIT-028); y cada persona puede proteger su acceso a una empresa con una contraseña propia (BIT-029). El contrato y la documentación se pusieron al día en BIT-030, y el recorrido real contra inventario se verificó en BIT-031 (`e2e/token-por-empresa`). El reproductor permanente en las pruebas de Maven es `CompanyAccessIntegrationTest#enteringAsAMemberNeverMixesTheOtherCompany`: quien es dueña de una empresa y personal de otra recibe en la segunda solo los permisos de su rol, nunca `*` ni la otra empresa.
- **Lo que no cubre:** (1) **Un token de empresa sigue vigente hasta que expire** (una hora) aunque la persona deje de pertenecer a la empresa: company-management lo rechaza en la siguiente petición, pero inventario solo lee el token. Está verificado de punta a punta en BIT-031. (2) Siguen existiendo en `JwtTokenService` las sobrecargas `issue(...)`, que pueden fabricar un token con varias empresas; ninguna ruta las usa, solo tres pruebas. (3) La prueba de punta a punta de BIT-031 es un script manual: no corre con `mvn test` ni en el CI, así que un cambio que rompa el acuerdo de claims entre los dos módulos solo se detecta si alguien la ejecuta.

### ERR-012 · El repositorio git quedó con archivos de objeto vacíos y la rama apuntando a un commit ilegible

- **Estado:** RESUELTO el 2026-11-03. Visto al retomar el trabajo después de cerrar BIT-027. No tiene entrada BIT: no cambió código ni contratos.
- **De qué trata:** 32 archivos de objeto sueltos en `.git/objects` medían 0 bytes, todos con la misma hora (14:42 del 2026-11-03, la del cierre de BIT-027). La rama `feat/token-por-empresa` apuntaba a uno de ellos (`5f1e5b3…`), el índice tenía preparados 8 archivos de BIT-027 cuya copia en `.git` estaba vacía (`git fsck`: «invalid sha1 pointer in cache-tree»), y `git status` y `git log` fallaban con «bad object HEAD». El reflog de `HEAD` no registra ese commit: su última entrada es el de BIT-026 (`fa8c434`).
- **Cuándo se manifiesta:** cualquier comando de git que lea `HEAD`. **Causa no determinada.** Había espacio en disco (144 GB libres) y solo se afectó lo escrito en ese minuto: los objetos de `fa8c434` y los 3189 que alcanza estaban sanos. La rama no estaba en `origin`, así que no había copia remota del commit perdido.
- **Cómo mitigarlo:** si reaparece, no borrar `.git` ni volver a clonar. Primero copiar `.git`, listar los objetos vacíos con `find .git/objects -type f -size 0` y comprobar que el árbol de trabajo está completo; mientras tanto no hacer commit.
- **Cómo se resolvió:** con una copia previa de `.git` (7,8 MB), (1) la rama volvió a `fa8c434` con `git update-ref`, (2) se borraron los 32 archivos vacíos (lista guardada antes), (3) se rehízo el índice desde `HEAD` con `git reset -q` y se volvieron a preparar desde el árbol de trabajo los 9 caminos de BIT-027 (8 archivos y el borrado de `PermissionResolver`); los hashes de los archivos no cambiaron. `git fsck --full` terminó sin errores y `git status` volvió a funcionar.
  - **GREEN tras la reparación:** `mvn -B -ntp test` en la raíz → `BUILD SUCCESS`, **216 pruebas** (13 + 8 + 8 + 85 + 27 + 75; +10 respecto a BIT-024: 1 de BIT-026 y 9 de BIT-027), con `CompanyAccessIntegrationTest` 9 de 9.
- **Lo que no se verificó:** la causa; el contenido exacto del commit perdido, que se vuelve a crear con otro hash; y que las 7 mutaciones de BIT-027 se repitan, porque no se volvieron a ejecutar.
