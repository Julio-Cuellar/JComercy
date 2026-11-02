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
