# Modelo de autorización y aislamiento de JComercy

## 1. Objetivo

Definir cómo se determina el acceso de un usuario a empresas, sucursales, módulos y operaciones sin permitir fuga de información entre sucursales.

La autorización debe resolverse en backend mediante la combinación de:

1. Identidad del usuario.
2. Membresía en la empresa.
3. Rol y permisos.
4. Asignación a sucursales.
5. Estado de la suscripción de la empresa y capacidad de sucursales contratada.
6. Alcance específico de la operación.

## 2. Alcances de acceso

### Global de cuenta

Permite administrar todas las empresas asociadas a la cuenta administrativa. Corresponde al propietario de la cuenta o a un usuario al que se le haya otorgado explícitamente este alcance.

### Empresa

Permite consultar o administrar la configuración general de una empresa. No otorga automáticamente acceso operativo a todas sus sucursales.

### Sucursal

Permite trabajar en una sucursal específica. Es el alcance operativo normal para empleados.

### Cruzado de sucursales

Permite consultar información puntual de otra sucursal. Debe otorgarse por permiso explícito y no concede capacidad de modificación.

### Consolidado

Permite consultar reportes agregados de varias sucursales. No equivale a tener permiso para operar individualmente en cada sucursal.

## 3. Roles del sistema

| Rol | Alcance principal | Capacidades |
|---|---|---|
| `ACCOUNT_OWNER` | Todas las empresas de la cuenta | Administración completa y facturación de suscripciones |
| `COMPANY_ADMIN` | Una empresa | Configuración, usuarios, sucursales y módulos autorizados |
| `BRANCH_MANAGER` | Una o varias sucursales asignadas | Operación y administración de sus sucursales |
| `OPERATOR` | Sucursal asignada | Operaciones permitidas por módulo |
| `ACCOUNTANT` | Empresa o sucursales autorizadas | Consultas y operaciones contables |
| `AUDITOR` | Empresa o sucursales autorizadas | Consulta y auditoría sin modificar operaciones |
| `CROSS_BRANCH_VIEWER` | Sucursales autorizadas | Consultas puntuales de solo lectura |

Los roles del sistema son definidos por JComercy y proporcionan una base segura para la operación. Algunos roles críticos, como `ACCOUNT_OWNER`, no deben poder eliminarse ni perder sus permisos esenciales mediante la configuración ordinaria de la empresa.

## 4. Roles personalizados

Cada empresa podrá crear roles propios y asignarles los permisos que necesite para su operación.

Un rol personalizado debe incluir:

- `id`.
- `companyId`.
- `name`.
- `description`.
- `status`.
- `scope` (`COMPANY`, `BRANCH` o `CROSS_BRANCH_READ`).
- Conjunto de permisos.
- Fecha de creación y actualización.
- Usuario que lo creó o modificó.

Ejemplos:

- `Encargado de inventario`
- `Supervisor regional`
- `Recepción clínica`
- `Cajero de sucursal`
- `Consulta de existencias`

Los roles personalizados solo tienen efecto dentro de la empresa que los creó. No pueden utilizarse para otorgar acceso a otra empresa.

El administrador de la empresa podrá:

- Crear roles.
- Editar roles.
- Duplicar roles.
- Activar o desactivar roles.
- Asignar roles a usuarios.
- Definir sucursales alcanzables.
- Gestionar permisos de módulos.

No podrá otorgar mediante un rol personalizado capacidades reservadas al propietario de la cuenta, como transferir la propiedad global de la cuenta o administrar empresas que no le pertenecen.

## 5. Permisos

Los permisos deben ser acciones explícitas y no solo nombres de pantallas.

Ejemplos:

- `customers.read`
- `customers.create`
- `customers.update`
- `inventory.read`
- `inventory.adjust`
- `inventory.transfer.create`
- `inventory.transfer.receive`
- `cash.open`
- `cash.close`
- `reports.consolidated.read`
- `branches.manage`
- `subscriptions.manage`

El permiso debe combinarse con el alcance del rol. Por ejemplo, `inventory.read` en una sucursal no equivale a `inventory.read` en todas las sucursales.

## 6. Asignación de empleados

Una membresía en la empresa no es suficiente para trabajar en una sucursal.

```text
User
  └── CompanyMembership
        └── BranchAssignment
              ├── branchId
              ├── status
              ├── assignedAt
              └── permissions
```

Un empleado puede estar asignado a varias sucursales, pero cada asignación debe ser explícita y auditable.

## 7. Reglas por tipo de operación

### Consulta local

Permitida cuando el usuario tiene asignación activa a la sucursal y permiso de lectura del módulo.

### Modificación local

Permitida cuando el usuario tiene asignación activa, permiso operativo, la suscripción de la empresa está activa y la sucursal está dentro de la capacidad contratada.

### Consulta cruzada

Requiere un permiso como `CROSS_BRANCH_READ` y debe limitarse al recurso solicitado.

Ejemplo permitido:

```text
Consultar existencia disponible del producto X en sucursal B.
```

### Modificación cruzada

No debe permitirse como operación directa desde otra sucursal.

Ejemplo no permitido:

```text
Registrar una venta en sucursal A descontando directamente stock de sucursal B.
```

La forma válida de realizarlo es:

1. Solicitar transferencia de B hacia A.
2. Autorizar la transferencia según los permisos configurados.
3. Registrar salida en B.
4. Registrar recepción en A.
5. Habilitar el producto en el inventario de A.
6. Registrar la venta desde A.

## 8. Reglas de backend

Cada caso de uso debe recibir o resolver un `AccessContext` con:

- `userId`.
- `companyId`.
- `branchId` activo.
- Sucursales asignadas.
- Permisos efectivos.
- Estado de suscripción y capacidad de sucursales contratada.

Los repositorios y consultas deben filtrar por el alcance autorizado. No es suficiente enviar `branchId` desde el frontend ni ocultar opciones visuales.

Los identificadores de otra sucursal deben responder como inexistentes cuando el usuario no tenga autorización, evitando revelar información por diferencias de respuesta.

## 9. Auditoría

Se deben registrar como mínimo:

- Usuario que ejecutó la acción.
- Empresa y sucursal de contexto.
- Sucursal consultada, cuando sea distinta.
- Permiso utilizado.
- Tipo de operación.
- Fecha y hora.
- Resultado.

Las consultas cruzadas de inventario y los movimientos entre sucursales requieren auditoría obligatoria.

## 10. Reglas de suscripción

- La empresa paga y administra una suscripción empresarial.
- Cada plan incluye un número máximo de sucursales.
- Una empresa puede agregar sucursales por encima del límite mediante complementos recurrentes.
- La suscripción debe impedir crear o activar sucursales que excedan el límite contratado.
- Todas las sucursales de la empresa dependen del estado de la suscripción empresarial.
- Una suscripción suspendida no debe permitir nuevas operaciones, aunque los datos históricos deben permanecer disponibles para usuarios autorizados.
- La consulta administrativa de suscripciones no otorga acceso a los datos operativos de las sucursales.

## 11. Cómo se aplica hoy

Estado de la implementación del MVP. Lo que el modelo anterior pide y aún no existe se lista al final.

- **Permisos con comodín.** Un permiso es un texto `módulo.acción`. `*` concede todo y `familia.*` concede todo lo que cuelga de esa familia (`inventory.*` cubre `inventory.stock.write`). El comodín solo cuenta al final. La regla vive en `Permissions`, en `jcomercy-core`, y la usan ambos módulos.
- **El dueño no es un rol asignado.** Quien es dueña de la cuenta tiene `*` en esa cuenta y en todas sus empresas.
- **Los miembros reciben los permisos de su rol en esa empresa.** Cuenta la membresía activa, y valen los roles del sistema y los roles propios de esa empresa, nunca los de otra. Los permisos de inventario de los roles del sistema se sembraron en la migración V022.
- **Company-management decide en cada petición, desde la base de datos.** Primero comprueba a quién pertenece lo que se pide y luego el permiso. Cada ruta declara el suyo con `@RequiresPermission`, que debe coincidir con el `x-required-permission` del contrato (lo verifica `RequiredPermissionsContractTest`). Una ruta sin declarar se rechaza. Una membresía suspendida o un rol cambiado surten efecto en la siguiente petición.
- **Hay dos clases de token (ERR-011).** El inicio de sesión entrega un **token de cuenta**: dice quién eres y no nombra empresas, sucursales ni permisos. Para trabajar dentro de una empresa la persona **entra a ella** (`POST /accounts/{id}/company-accesses/{empresa}/session`) y recibe un **token de empresa**: el claim `ctx` nombra esa empresa, y `companyIds`, `branchIds` y `permissions` traen solo lo que la persona tiene **en ella**. Dura una hora y renovar la sesión no lo renueva. Un token nunca junta las empresas de la persona, así que los permisos de una no valen en otra. Quien es dueña de una empresa y personal de otra recibe `*` en la primera y solo los permisos de su rol en la segunda.
- **Cada ruta exige su clase de token.** Las rutas bajo `/accounts/{id}` piden el token de cuenta y rechazan el de una empresa; las rutas bajo `/companies/{id}` piden el token de **esa** empresa y rechazan el de cuenta y el de otra empresa. GraphQL aplica lo mismo. Lo hacen `RestOwnershipInterceptor` y `EffectivePermissions`. Así, quien robe un token de empresa no administra la cuenta ni lista las demás empresas. El tipo de token se revisa antes de buscar la empresa, para que la respuesta no revele cuáles existen. Un token que no corresponde responde `403` con un mensaje que dice qué hacer.
- **El token no decide si la persona sigue perteneciendo.** Company-management lo comprueba en cada petición contra la base de datos, así que una membresía suspendida deja de valer en la siguiente petición. Los demás módulos, como inventario, no tienen acceso a esos datos y solo leen los claims `companyIds`, `branchIds` y `permissions` del token de empresa.
- **Contraseña opcional por empresa.** Cada persona puede ponerle una contraseña propia a su acceso a una empresa; entonces entrar la pide. Cinco intentos fallidos bloquean ese acceso quince minutos, y el contador vive en la base de datos. Cambiarla exige la actual; quitarla exige la contraseña de la **cuenta**, que es la salida si se olvidó la de la empresa. Equivocarse al entrar, al cambiarla o al quitarla gasta el mismo contador. Solo se guarda la huella cifrada. La contraseña solo protege la puerta: no sustituye los permisos.
- **Límite conocido tras ERR-011.** Un token de empresa sigue vigente hasta que expire (una hora) aunque la persona deje de pertenecer a la empresa. Company-management lo rechaza en la siguiente petición, pero inventario solo lee el token y lo aceptaría hasta entonces. Esto se infiere de leer el código; no hay una prueba que lo ejerza.
- **Todavía sin implementar:** los roles `OPERATOR`, `ACCOUNTANT`, `AUDITOR` y `CROSS_BRANCH_VIEWER` (el seed tiene `ACCOUNT_OWNER`, `COMPANY_ADMIN`, `BRANCH_MANAGER` y `STAFF`), el alcance por sucursal asignada en company-management, las rutas para crear y administrar membresías (hoy solo se crean por SQL), y un rol de administración de la plataforma (suspender una cuenta ajena).
