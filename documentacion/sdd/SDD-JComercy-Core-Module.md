# Software Design Description (SDD)

## Módulo técnico `jcomercy-core`

**Versión:** 1.0.0-draft  
**Estado:** Propuesta pendiente de aprobación  
**Proyecto:** JComercy  
**Referencia:** IEEE 1016-2009

## 1. Propósito

Este documento define el diseño del módulo técnico `jcomercy-core`, responsable de proporcionar tipos, contratos y mecanismos compartidos por los módulos de JComercy.

El módulo funcionará como una biblioteca común del monolito modular. No representa un contexto de negocio y no será propietario de información empresarial, usuarios, suscripciones ni operaciones.

## 2. Alcance

### 2.1 Incluido

- Identificadores técnicos y públicos.
- Contexto de cuenta, empresa y sucursal.
- Contratos síncronos entre módulos.
- Contrato base de eventos.
- Tipos de comandos, consultas y respuestas.
- Errores y códigos de fallo comunes.
- Idempotencia y correlación de mensajes.
- Tipos compartidos de paginación y metadatos.
- Interfaces abstractas para publicación y consumo de mensajes.

### 2.2 Excluido

- Entidades de negocio.
- Persistencia y repositorios.
- Tablas y esquemas de base de datos.
- Migraciones Flyway.
- Usuarios y autenticación.
- Empresas y sucursales.
- Roles y permisos concretos.
- Planes y suscripciones.
- Clientes, inventario, ventas o contabilidad.
- Reglas clínicas de Clinerya.

## 3. Objetivos de diseño

- Mantener contratos consistentes entre módulos.
- Reducir duplicación de tipos técnicos.
- Evitar dependencias directas entre módulos de negocio.
- Permitir comunicación síncrona y asíncrona.
- Mantener el módulo pequeño, estable y de baja frecuencia de cambio.
- Evitar que se convierta en un depósito de lógica de negocio.

## 4. Consumidores

| Consumidor | Uso esperado |
|---|---|
| Company Management | Contextos, contratos y eventos empresariales. |
| Identity | Identificadores y errores comunes. |
| Billing | Eventos, correlación y respuestas. |
| Módulos operativos | Contexto de empresa/sucursal y autorización contractual. |
| Productos verticales | Contratos de integración y referencias públicas. |
| Aplicación JComercy | Manejo uniforme de respuestas y errores. |

## 5. Arquitectura interna

El módulo se organizará en componentes conceptuales:

```text
jcomercy-core/
├── identifiers/
├── context/
├── contracts/
│   ├── commands/
│   ├── queries/
│   └── responses/
├── events/
├── errors/
├── messaging/
└── pagination/
```

### 5.1 `identifiers`

Define los tipos utilizados para distinguir identificadores internos de identificadores públicos.

Tipos conceptuales:

- `InternalId`.
- `PublicUserId`.
- `PublicAccountId`.
- `PublicCompanyId`.
- `PublicBranchId`.
- `PublicRoleId`.
- `CorrelationId`.
- `CausationId`.
- `EventId`.

Los tipos públicos no deben exponer secuencias internas ni permitir inferir autorización.

### 5.2 `context`

Define el contexto mínimo que acompaña una operación:

```text
RequestContext
├── publicUserId
├── publicAccountId (opcional)
├── publicCompanyId (opcional)
├── publicBranchId (opcional)
├── correlationId
└── causationId (opcional)
```

El contexto identifica el alcance de una solicitud, pero no concede permisos por sí mismo. La autorización será responsabilidad de Company Management y de cada módulo propietario.

### 5.3 `contracts.commands`

Representa intenciones de modificar información mediante una operación síncrona.

Características:

- Nombre explícito de la intención.
- Identificador de idempotencia cuando corresponda.
- Contexto de solicitud.
- Datos mínimos de entrada.
- Resultado o error definido.

El módulo Core no ejecuta comandos; solo define sus tipos contractuales.

### 5.4 `contracts.queries`

Representa solicitudes de consulta síncrona.

Características:

- Alcance explícito.
- Datos mínimos de consulta.
- Respuesta tipada.
- Paginación cuando corresponda.
- Denegación uniforme cuando el alcance no sea válido.

### 5.5 `contracts.responses`

Define respuestas comunes para evitar formatos incompatibles entre módulos:

- Respuesta exitosa.
- Respuesta paginada.
- Respuesta de validación.
- Respuesta de error.
- Metadatos de correlación.

### 5.6 `events`

Define el sobre común de los eventos:

```text
DomainEvent<T>
├── eventId
├── eventType
├── eventVersion
├── occurredAt
├── producerModule
├── correlationId
├── causationId (opcional)
├── publicAccountId (opcional)
├── publicCompanyId (opcional)
├── publicBranchId (opcional)
├── actorPublicUserId (opcional)
└── payload
```

El Core define el sobre, no los eventos concretos. Cada módulo propietario define sus propios tipos de evento y payloads.

### 5.7 `errors`

Define códigos y categorías comunes:

- `UNAUTHORIZED`.
- `FORBIDDEN`.
- `NOT_FOUND`.
- `INVALID_SCOPE`.
- `INVALID_STATE`.
- `VALIDATION_ERROR`.
- `CAPACITY_EXCEEDED`.
- `CONFLICT`.
- `DUPLICATE_MESSAGE`.
- `DEPENDENCY_UNAVAILABLE`.
- `INTERNAL_ERROR`.

Los errores no deben incluir información que revele datos de otra empresa o sucursal.

### 5.8 `messaging`

Define abstracciones, no implementaciones:

- Publicador de eventos.
- Despachador de comandos.
- Ejecutor de consultas.
- Registro de idempotencia.
- Manejador de confirmaciones y reintentos.

RabbitMQ, Kafka, HTTP, REST u otra tecnología pertenecerán a la infraestructura de la aplicación o del módulo adaptador correspondiente.

### 5.9 Transportes de infraestructura

JComercy utilizará distintos transportes según la naturaleza de la interacción:

| Transporte | Uso recomendado |
|---|---|
| REST | Comandos, consultas operativas, integraciones externas y APIs con recursos claramente definidos. |
| GraphQL | Consultas flexibles, composición de información de varios contratos y pantallas con campos variables. |
| WebSockets | Actualizaciones en tiempo real, notificaciones, estados operativos y colaboración interactiva. |

Estos transportes pertenecen a las capas de aplicación e infraestructura. `jcomercy-core` solo define contratos neutrales y metadatos; no implementa controladores REST, resolvers GraphQL ni endpoints WebSocket.

La elección del transporte no modifica la propiedad de los datos ni las reglas de autorización. Toda solicitud debe validar el contexto de cuenta, empresa, sucursal y usuario en el módulo propietario.

### 5.10 `pagination`

Define estructuras comunes para consultas paginadas:

```text
PageRequest
├── page
├── size
└── sort

PageResponse<T>
├── items
├── page
├── size
├── totalItems
└── totalPages
```

## 6. Dependencias permitidas

```text
jcomercy-core
├── no depende de módulos de negocio
├── no depende de persistencia
├── no depende de Spring MVC
├── no depende de JPA
└── no depende de Clinerya
```

Podrá depender únicamente de bibliotecas técnicas mínimas y estables, preferentemente de la biblioteca estándar de Java.

Los módulos de negocio podrán depender de `jcomercy-core`; la dependencia inversa queda prohibida.

## 7. Reglas de uso

- Los contratos deben ser neutrales al módulo consumidor.
- No se agregarán entidades de negocio por conveniencia.
- No se agregarán DTOs exclusivos de un módulo.
- No se agregarán anotaciones de persistencia.
- No se agregarán controladores REST.
- No se agregará configuración de base de datos.
- Los eventos concretos serán propiedad del módulo que los origina.
- Las interfaces deben expresar contratos, no detalles de infraestructura.
- Los cambios incompatibles requerirán una nueva versión del contrato.

## 8. Compatibilidad de eventos

- `eventType` identifica el evento.
- `eventVersion` identifica la estructura del payload.
- Los consumidores deben ignorar campos opcionales desconocidos.
- Los productores no deben eliminar campos obligatorios dentro de una misma versión.
- Un cambio incompatible requiere una nueva versión.
- Los eventos deben incluir `eventId` para deduplicación.

## 9. Seguridad

- Los identificadores públicos no sustituyen la autorización.
- El Core no almacenará secretos, tokens ni datos clínicos.
- Los contratos no deben transportar información más allá de la necesaria.
- Los errores deben evitar enumeración de recursos de otras empresas.
- El contexto recibido debe validarse en el módulo propietario.

## 10. Pruebas requeridas

El módulo deberá contar como mínimo con pruebas para:

- Creación y comparación de identificadores.
- Serialización y deserialización de contratos.
- Compatibilidad de versiones de eventos.
- Validación de contexto.
- Clasificación de errores.
- Idempotencia de mensajes.
- Paginación y ordenamiento.

No se requieren pruebas de base de datos porque el módulo no posee persistencia.

## 11. Decisiones de implementación

- **Empaquetado:** biblioteca Maven `jcomercy-core`.
- **Paquete base:** `com.jcode.jcomercy.core`.
- **Persistencia:** ninguna.
- **Esquema de base de datos:** ninguno.
- **Migraciones Flyway:** ninguna.
- **Eventos:** contratos únicamente; publicación en adaptadores.
- **Transportes:** REST, GraphQL y WebSockets mediante adaptadores externos al Core.
- **Serialización:** definida por los adaptadores de transporte.

## 12. Criterios de aceptación

El módulo se considerará listo cuando:

- Compile de forma independiente.
- No dependa de módulos de negocio.
- No contenga entidades de negocio ni persistencia.
- Defina identificadores públicos y correlación.
- Defina el sobre común de eventos.
- Defina errores y respuestas comunes.
- Defina contratos de comandos y consultas.
- Tenga pruebas unitarias de sus tipos y contratos.
- Sea consumible por `company-management` sin dependencias inversas.

## 13. Estado de aprobación

**Estado: PROPUESTA PENDIENTE DE APROBACIÓN.**

Este SDD debe aprobarse antes de implementar el módulo `jcomercy-core`.
