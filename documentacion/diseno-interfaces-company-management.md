# Diseño de interfaces de Company Management

## 1. Propósito

Este documento consolida las decisiones aprobadas para las interfaces del módulo `company_management` de JComercy. Define reglas comunes para REST, GraphQL, WebSockets, operaciones asíncronas, archivos, auditoría y webhooks.

No contiene código ni implementa endpoints. El catálogo detallado de endpoints se definirá en una fase posterior a partir de estas reglas.

## 2. Alcance y propiedad

`company_management` es propietario de las operaciones relacionadas con:

- cuentas administrativas;
- empresas;
- sucursales;
- membresías;
- asignaciones de usuarios a sucursales;
- roles y permisos propios del ámbito empresarial;
- operaciones administrativas del módulo.

Cada endpoint se organizará por recurso y declarará explícitamente su módulo propietario. Las APIs se clasificarán como públicas, internas o administrativas.

Ninguna interfaz accederá directamente a tablas de otro módulo. Las dependencias se resolverán mediante contratos síncronos o eventos asíncronos.

## 3. Selección del transporte

| Transporte | Uso principal |
|---|---|
| REST | CRUD, consultas operativas, comandos explícitos, integraciones y operaciones asíncronas |
| GraphQL | Consultas compuestas, composición autorizada y selección flexible de campos |
| WebSockets | Notificaciones, operaciones asíncronas y actualizaciones operativas en tiempo real |

GraphQL podrá ejecutar mutaciones controladas, pero REST conservará los comandos críticos, integraciones y operaciones asíncronas.

## 4. Convenciones REST

Las rutas técnicas estarán en inglés y usarán versionado mayor:

```text
/api/v1/accounts/{publicAccountId}/companies
/api/v1/companies/{publicCompanyId}/branches
```

La cuenta administrativa usará rutas anidadas para administrar sus empresas. Las sucursales se gestionarán bajo su empresa correspondiente.

Se usará un estilo híbrido:

- recursos y operaciones CRUD para operaciones simples;
- comandos explícitos para acciones de negocio, cambios de estado y operaciones críticas.

La autenticación usará `access_token` JWT. El `refresh token` se almacenará en una cookie `HttpOnly` y `Secure`.

La autorización será doble: validación inicial en el Gateway y validación definitiva en el módulo propietario. También se aplicará a operaciones internas.

## 5. Respuestas, errores y paginación

Las respuestas individuales devolverán el recurso directamente. Las colecciones devolverán `items`, paginación y `meta` cuando corresponda. Las operaciones asíncronas devolverán `operationId`.

La correlación se propagará mediante headers y metadatos de contrato.

Los errores seguirán estas reglas:

- REST: RFC 9457;
- GraphQL: `errors.extensions`;
- WebSockets: contrato propio con códigos comunes de JComercy.

La paginación será basada en cursor:

```http
GET /api/v1/companies?limit=25&after={cursor}
```

GraphQL usará el modelo de `connections`. WebSockets no será un mecanismo de paginación.

Los filtros simples estarán disponibles para consultas comunes y los filtros avanzados se habilitarán únicamente cuando el caso de uso lo justifique. El ordenamiento será explícito y validado por endpoint.

## 6. Idempotencia y concurrencia

`Idempotency-Key` será obligatorio para:

- creaciones mediante `POST`;
- comandos críticos;
- cambios de estado;
- pagos y operaciones financieras.

Las consultas no requieren idempotencia. `PUT` será idempotente por diseño y `PATCH` usará control de concurrencia.

REST utilizará `ETag` e `If-Match`. Los contratos internos y GraphQL incluirán una propiedad `version`. Un conflicto de versión devolverá `409 Conflict`.

## 7. Operaciones masivas y asíncronas

Se usará un enfoque híbrido:

- operaciones pequeñas: respuesta síncrona;
- importaciones, exportaciones y procesos prolongados: respuesta asíncrona con `operationId`.

Los estados permitidos son:

`PENDING`, `PROCESSING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED` y `EXPIRED`.

Las operaciones tendrán tiempo máximo fijo de ejecución. La cancelación será posible en `PENDING` y, cuando sea seguro, en `PROCESSING`. El iniciador podrá cancelar con permiso; propietarios y administradores autorizados también.

Los reintentos serán manuales y controlados. Podrán reutilizar registros válidos y reprocesar únicamente fallos cuando la operación lo permita. Cada reintento tendrá un nuevo `operationId` relacionado con la operación original.

Se conservará una cadena completa de relaciones entre operaciones, incluyendo importaciones, exportaciones, reintentos y cancelaciones.

El historial se consultará según el alcance del usuario:

- iniciador: operaciones propias;
- usuario autorizado: operaciones de su sucursal;
- propietario o administrador autorizado: operaciones de la empresa.

## 8. WebSockets y notificaciones

WebSockets será el canal principal para notificaciones, pero REST será la fuente oficial para consultar el estado persistido.

La autorización se validará:

1. al establecer la conexión;
2. al suscribirse a cada canal;
3. cuando cambien roles, permisos, membresías o asignaciones.

Los canales tendrán alcance por empresa, sucursal y tipo de evento.

Las notificaciones críticas usarán entrega confiable:

- `notificationId` único;
- persistencia temporal;
- confirmación de recepción (`ack`);
- reintentos controlados;
- no reenvío después de la confirmación.

La interfaz no dependerá de la deduplicación del cliente. Las notificaciones efímeras podrán enviarse sin persistencia ni reintentos.

La retención será:

- críticas: 30 días;
- operativas: 7 días;
- informativas: 24 horas.

## 9. GraphQL

GraphQL tendrá límites de profundidad, complejidad, costo por campo, tiempo de ejecución y consumo según plan.

Las mutaciones estarán sujetas a autorización, idempotencia y concurrencia igual que sus contratos subyacentes. GraphQL no será propietario de datos ni accederá directamente a esquemas de otros módulos.

## 10. Archivos e importaciones

Se admitirán inicialmente CSV y XLSX. Los límites dependerán del tipo de operación y del plan, con posibilidad de incorporar otros formatos.

Las importaciones pequeñas podrán ser síncronas y las grandes asíncronas. Antes de modificar datos se realizará validación previa y se requerirá confirmación.

El comportamiento será configurable por operación:

- transaccional: todos los registros o ninguno;
- parcial: aplicar válidos y rechazar inválidos.

Se validarán extensión, MIME, tamaño, estructura, encabezados, fórmulas peligrosas, contenido malformado y datos sensibles. Antes del procesamiento productivo se deberá contar con escaneo antimalware.

Los permisos de importar, exportar, descargar y eliminar serán independientes y se validarán por empresa y sucursal.

No habrá un límite funcional fijo de registros exportables. Se conservarán controles técnicos de tiempo, concurrencia, consumo y seguridad. Las exportaciones grandes serán asíncronas.

Los archivos se almacenarán de forma privada, con URLs firmadas de un solo uso, expiración, autorización al descargar y auditoría.

Retención:

- exportaciones operativas: 7 días;
- reportes críticos: 30 días;
- eliminación anticipada mediante solicitud autorizada.

## 11. Webhooks

JComercy ofrecerá webhooks para integraciones externas. Cada suscripción pertenecerá a una sola empresa y podrá restringirse por sucursal y tipo de evento.

El propietario y los administradores con permisos explícitos podrán crear, consultar, modificar, probar, pausar y eliminar suscripciones.

La seguridad usará:

- secreto rotatorio;
- firma HMAC;
- timestamp;
- `eventId`;
- protección contra replay;
- validación del endpoint antes de activar la suscripción.

Los eventos usarán versiones independientes, por ejemplo `eventVersion: 1.0`. Las versiones anteriores tendrán un periodo de transición documentado.

El payload será mínimo e incluirá tipo de evento, identificadores, metadatos, versión y referencia para consultar datos mediante API autorizada. No se enviará el recurso completo por defecto.

Una respuesta `2xx` confirmará la entrega. Se registrarán código, latencia e intento. Los demás resultados activarán backoff exponencial, clasificación de errores y límite de reintentos. La suscripción podrá terminar en `FAILED` o `EXPIRED`.

Las pruebas podrán utilizar eventos simulados y eventos reales controlados, sin modificar datos del negocio.

## 12. Auditoría y observabilidad

Se auditarán consultas sensibles, escrituras, permisos, membresías, suscripciones, acciones administrativas, exportaciones y consultas cross-branch autorizadas.

Las consultas ordinarias conservarán observabilidad técnica básica sin generar necesariamente una auditoría completa.

Los registros incluirán metadatos y, para cambios sensibles, valores anteriores y posteriores. Se aplicará una lista global de campos prohibidos y reglas específicas por módulo. Nunca se almacenarán contraseñas, tokens ni secretos.

La auditoría se implementará progresivamente:

1. tablas locales protegidas de `company_management`;
2. publicación de eventos de auditoría;
3. almacenamiento central inmutable.

## 13. Fechas y compatibilidad

REST, GraphQL, WebSockets, eventos y auditoría usarán UTC en formato ISO 8601. Cada empresa podrá configurar su zona horaria para presentación y reglas de negocio.

Dentro de una misma versión no se eliminarán ni renombrarán campos publicados; solo se agregarán campos opcionales. Los transformadores y valores por defecto se incorporarán cuando existan consumidores antiguos que los necesiten.

Los cambios incompatibles requerirán una nueva versión mayor, periodo de depreciación, encabezados de aviso, métricas de uso y guía de migración.

## 14. Documentación y validación

OpenAPI será el contrato técnico principal para REST. Markdown documentará reglas de negocio, flujos, ejemplos y decisiones.

La validación incluirá:

- pruebas automatizadas de endpoints;
- validación del esquema OpenAPI;
- pruebas de contrato entre módulos;
- pruebas de permisos, errores, idempotencia y concurrencia.

La documentación será privada por defecto y separará APIs públicas, internas y administrativas.

## 15. Estado

Las decisiones de este documento quedan aprobadas como base para definir el catálogo detallado de endpoints de `company_management`.

La aprobación no autoriza todavía generar controladores, resolvers, migraciones SQL ni implementaciones de webhooks.
