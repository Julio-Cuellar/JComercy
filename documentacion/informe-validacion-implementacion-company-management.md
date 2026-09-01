# Informe de Validación: Implementación Interna de Company Management

Este documento registra el estado verificable de la implementación interna ejecutada para el módulo `company_management` de JComercy. El alcance implementado es parcial y no representa las 97 operaciones del contrato completo.

---

## 1. Resumen de Entregables Creados

| Entregable | Ubicación / Componente | Estado | Descripción |
|---|---|:---:|---|
| **Estructura Maven** | `company-management/` | **Aprobado** | Estructura multi-módulo (`company-management-domain`, `company-management-application`, `company-management-infra`) integrada al POM raíz. |
| **Dominio Mínimo** | `company-management-domain` | **Aprobado** | Agregados `Account`, `OwnerPerson`, `Company`, `DomicilioFiscal`, `Branch`, `CompanyMembership`, `BranchAssignment`, `Role` en **Java 21 puro** sin dependencias de Spring. |
| **Casos de Uso** | `company-management-application` | **Aprobado** | Servicios de aplicación `AccountApplicationService`, `CompanyApplicationService`, `BranchApplicationService` y DTOs correspondientes. |
| **Migraciones Flyway** | `company-management-infra/src/main/resources/db/migration` | **Aprobado** | Migraciones secuenciales `V001` a `V017` en el esquema propietario `company_management`; `V013` agrega `owner_person`, `V014` datos iniciales de sucursal, `V015` datos fiscales/contacto de empresa, `V016` reutiliza `DomicilioFiscal` en sucursales y `V017` amplía el modelo operativo/fiscal de sucursal. |
| **Adaptadores REST** | `company-management-infra` | **Implementado en alcance v1** | Controladores de cuentas, empresas y sucursales con autenticación JWT, `Principal`, `Idempotency-Key` obligatorio en mutaciones y `ETag` en las respuestas implementadas. |
| **Documentación interactiva** | `Swagger UI` | **Implementado** | El contrato `openapi-company-management-v1.yaml` se empaqueta y se sirve con Swagger UI; permite configurar Bearer JWT mediante `Authorize`. |
| **Outbox Transaccional** | `JpaOutboxAdapter` | **Implementado en alcance v1** | Registro de eventos dentro de la transacción del caso de uso; la publicación/dispatch posterior queda pendiente. |
| **Pruebas y Validación** | `src/test` de los tres módulos | **Verificado** | Suite unitaria y de integración ejecutada sin fallos; se cubren login local, JWT válido/inválido, autenticación requerida, persona propietaria, datos de contacto/ubicación de sucursal, aislamiento, idempotencia y cuenta inexistente. |

---

## 2. Métricas del Build y Cobertura de Pruebas

```text
=== Reactor Build Summary ===
JComercy Parent ........................................... SUCCESS
JComercy Core ...................................... SUCCESS
Company Management (Parent) ............................... SUCCESS
Company Management - Domain ........................ SUCCESS (11 Unit Tests, 0 Failures)
Company Management - Application ................... SUCCESS (6 Unit Tests, 0 Failures)
Company Management - Infrastructure ................ SUCCESS (7 Integration Tests, 0 Failures)
------------------------------------------------------------------------
BUILD SUCCESS (Total time: 19.62s)
```

---

## 3. Verificación de Criterios de Aceptación y Restricciones

- [x] **Dominio Puro e Independiente:** `company-management-domain` compila sin dependencias de frameworks ni anotaciones ORM.
- [x] **Aislamiento de Clinerya:** El repositorio `Clinerya` no ha sido modificado y las migraciones históricas fueron respetadas (0 alteraciones).
- [x] **Migraciones Flyway Limpias:** Ejecutadas exitosamente desde una base limpia H2 con las 17 migraciones, incluyendo persona propietaria, domicilio fiscal compartido y la ampliación operativa de sucursal.
- [x] **Contrato REST del alcance v1:** Operaciones implementadas respondiendo con DTOs aislados; no se declara implementado el catálogo completo.
- [x] **Autenticación JWT:** Tokens Bearer con firma HMAC, emisor y expiración validados por Spring Security; el actor se obtiene del claim `sub`.
- [x] **Login local para pruebas:** `POST /api/v1/auth/login` emite tokens compatibles con el filtro JWT usando credenciales configurables por entorno.
- [x] **Matriz obligatoria:** La primera sucursal registrada para cada empresa debe ser `MATRIZ` y no se permite registrar una segunda matriz.
- [x] **Identidad única del propietario:** El RFC y el correo electrónico de la persona propietaria no pueden repetirse entre cuentas.
- [x] **Idempotencia del alcance v1:** Las mutaciones implementadas exigen `Idempotency-Key` y evitan repetir la operación dentro de la instancia activa.
- [x] **Transactional Outbox del alcance v1:** Eventos registrados en `company_management.outbox_event` dentro de la transacción del caso de uso.
- [x] **Asociación del propietario:** La cuenta se relaciona con `owner_person` mediante `owner_person_id`; no depende de una identidad externa para almacenar los datos de la persona.

---

## 4. Dictamen Final

```text
=== Dictamen de Implementación ===
Estado: ALCANCE V1 IMPLEMENTADO Y VERIFICADO (0 Errors, 0 Failures)
Rama Git: contracts-v1

Pendientes explícitos para producción: sustituir el login local por el módulo de identidad, persistir las claves de idempotencia para escenarios multi-instancia y desplegar el dispatcher de outbox. El login actual está pensado para pruebas locales y no consulta una tabla de usuarios.
```
