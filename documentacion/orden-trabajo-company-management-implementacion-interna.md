# Orden de trabajo: implementación interna de Company Management

## 1. Objetivo

Diseñar e implementar la primera versión interna del módulo `company_management` de JComercy, utilizando como contratos de referencia:

- `documentacion/openapi-company-management-v1.yaml`;
- `documentacion/asyncapi-company-management-v1.yaml`;
- `documentacion/contratos-company-management.md`;
- `documentacion/plan-migraciones-flyway-company-management.md`;
- `documentacion/modelo-autorizacion-jcomercy.md`.

La fase debe entregar una base ejecutable y probada para administrar cuentas, empresas, sucursales, membresías, asignaciones, roles, permisos y eventos de integración.

## 2. Alcance

### Incluido

- Estructura Maven de `company-management` con separación de dominio, aplicación e infraestructura.
- Modelo de dominio y casos de uso de cuentas, empresas, sucursales, membresías y autorización.
- Persistencia del esquema propietario `company_management`.
- Migraciones Flyway versionadas.
- Adaptadores REST conforme al contrato OpenAPI aprobado.
- Publicación transaccional de eventos mediante `outbox_event`.
- Validación de contexto, aislamiento, permisos e idempotencia.
- Pruebas unitarias, de integración y de contrato.

### No incluido en esta fase

- Migración de tablas o datos históricos de Clinerya.
- Implementación completa de Billing, Identity o Personal/Nómina.
- Operación productiva de webhooks externos.
- Servidor WebSocket productivo.
- Facturación, cobros o reglas fiscales.
- Acceso directo de otros módulos al esquema `company_management`.

## 3. Restricciones obligatorias

- No modificar los módulos existentes de Clinerya.
- No reutilizar entidades, tablas ni migraciones clínicas.
- `company_management` será propietario exclusivo de sus tablas y contratos.
- No crear claves foráneas físicas hacia esquemas externos.
- Identity seguirá siendo propietario de usuarios y credenciales.
- Billing seguirá siendo propietario de planes, pagos, suscripciones y capacidades contratadas.
- Personal/Nómina permanecerá separado de membresías y permisos de plataforma.
- El runtime no tendrá permisos DDL ni acceso a tablas de otros módulos.
- Las migraciones ya aplicadas no se modificarán.
- No publicar eventos de negocio durante la ejecución de migraciones o semillas iniciales.

## 4. Orden de ejecución

### Paso 1: proteger y preparar el repositorio

- Confirmar el commit de referencia de los contratos aprobados.
- Trabajar en la rama `contracts-v1` o en una rama derivada con nombre `codex/`.
- Confirmar que los cambios existentes de `Clinerya` no formen parte de esta fase.
- Registrar decisiones técnicas pendientes antes de crear SQL o código dependiente de ellas.

### Paso 2: definir la estructura del módulo

Crear la estructura base de `company-management` con los módulos:

- `company-management-domain`;
- `company-management-application`;
- `company-management-infra`.

Definir puertos de entrada y salida para comandos, consultas, autorización, persistencia, reloj, identificadores, publicación de eventos y comunicación con Billing e Identity.

### Paso 3: implementar el dominio mínimo

Implementar primero las reglas sin infraestructura:

- alta y estado de la cuenta;
- creación y configuración de empresa;
- creación, configuración, activación y suspensión de sucursal;
- membresías y asignaciones de sucursal;
- roles del sistema y roles personalizados;
- permisos efectivos y aislamiento por cuenta, empresa y sucursal;
- designación de sucursal matriz;
- validación de capacidad recibida de Billing;
- transiciones de estado y reglas de baja lógica.

Cada regla deberá tener pruebas unitarias que cubran casos válidos, denegaciones y conflictos.

### Paso 4: cerrar decisiones de persistencia

Antes de generar migraciones SQL, aprobar:

- convención global de versiones Flyway;
- estrategia UUID/ULID;
- nombres definitivos de tablas y columnas;
- estados y restricciones de transición;
- índices únicos parciales;
- propietario, migrador y runtime del esquema;
- tipo y retención del payload de outbox;
- estrategia para múltiples instancias.

La referencia principal será `plan-migraciones-flyway-company-management.md`.

### Paso 5: crear migraciones Flyway

Crear las migraciones en el orden aprobado:

1. esquema propietario;
2. cuenta;
3. membresía de cuenta;
4. empresa;
5. sucursal;
6. membresía de empresa;
7. asignación de sucursal;
8. roles, permisos y relaciones;
9. semillas deterministas de roles y permisos;
10. `outbox_event`;
11. índices;
12. restricciones de integridad;
13. metadatos técnicos, si son necesarios.

Validar cada migración en una base temporal limpia y verificar que pueda ejecutarse en desarrollo y pruebas con el mismo orden.

### Paso 6: implementar persistencia y casos de uso

- Crear entidades de persistencia internas y mapeadores.
- Implementar repositorios mediante los puertos definidos en dominio.
- Aplicar transacciones en comandos que modifiquen más de una entidad.
- Evitar exponer entidades ORM en las respuestas REST.
- Mantener la autorización en el módulo propietario, aunque exista validación previa en Gateway.

### Paso 7: implementar REST e idempotencia

Implementar por bloques:

1. cuentas y empresas;
2. sucursales;
3. membresías y asignaciones;
4. roles, permisos y contexto operativo;
5. operaciones asíncronas;
6. importaciones, exportaciones y auditoría base.

Cada mutación deberá respetar `Idempotency-Key` cuando el contrato lo exija, y cada actualización deberá respetar `ETag` e `If-Match` cuando corresponda.

Las respuestas y errores deben conservar los códigos y esquemas definidos en OpenAPI.

### Paso 8: implementar outbox y contratos de integración

- Registrar el evento en la misma transacción lógica que el cambio de negocio.
- Publicar únicamente eventos confirmados.
- Mantener `eventId` único e idempotente.
- Generar `schemaVersion`, `aggregateVersion` y `sequence` conforme al envelope común.
- No incluir secretos, credenciales, tokens ni datos clínicos.
- Implementar reintentos, deduplicación y estados de publicación.

La publicación real hacia el bus podrá iniciar en modo controlado después de validar la persistencia del outbox.

### Paso 9: validar seguridad y aislamiento

Comprobar como mínimo:

- acceso entre empresas de la misma cuenta;
- acceso entre sucursales;
- permisos globales del dueño de cuenta;
- permisos delegados de administradores;
- revocación de membresías y asignaciones;
- aislamiento de archivos, operaciones y auditoría;
- respuestas indistinguibles para recursos no autorizados;
- ausencia de acceso SQL entre módulos.

### Paso 10: validar y cerrar la fase

Ejecutar validaciones de compilación, migraciones, contratos, permisos, idempotencia, concurrencia y eventos. Registrar resultados en un informe de validación actualizado.

## 5. Entregables

1. Estructura Maven de `company-management`.
2. Modelo de dominio y casos de uso iniciales.
3. Migraciones Flyway ejecutables del esquema propietario.
4. Repositorios y adaptadores de persistencia.
5. Endpoints REST implementados por bloques.
6. Tabla y proceso de outbox transaccional.
7. Pruebas unitarias, de integración y de contrato.
8. Informe de validación de la fase.
9. Registro de decisiones técnicas adoptadas.

## 6. Criterios de aceptación

La fase se considerará completa cuando:

- el módulo compile de forma independiente;
- las migraciones se ejecuten desde una base limpia;
- no se modifique la historia Flyway de Clinerya;
- las pruebas de dominio y autorización pasen;
- las operaciones REST implementadas coincidan con OpenAPI;
- las mutaciones sean idempotentes y las actualizaciones controlen concurrencia;
- las reglas de aislamiento estén cubiertas por pruebas;
- los eventos se registren en outbox dentro de la transacción de negocio;
- los eventos cumplan el envelope común y sus payloads tipados;
- no existan accesos directos a tablas externas;
- el informe de validación no tenga errores críticos abiertos.

## 7. Prohibiciones de cierre

No considerar terminada la fase si:

- existen endpoints sin permiso explícito;
- hay mutaciones sin idempotencia donde el contrato la exige;
- se mezclan identificadores internos con identificadores públicos;
- se publican eventos antes de confirmar la transacción;
- se reutilizan migraciones o tablas de Clinerya;
- el runtime puede ejecutar DDL;
- las pruebas no cubren accesos cruzados entre empresas o sucursales.

## 8. Estado

**Estado: PROPUESTA PARA EJECUCIÓN.**

La aprobación de esta orden autoriza iniciar el diseño interno, las migraciones y la implementación controlada de `company-management`, respetando los criterios y restricciones definidos anteriormente.
