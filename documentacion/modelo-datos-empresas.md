# Modelo conceptual de datos: cuentas, empresas y sucursales

## 1. Objetivo

Definir las entidades y relaciones iniciales de JComercy Core sin entrar todavía en código, tablas SQL ni migraciones Flyway.

Este modelo cubre:

- Usuario propietario.
- Cuenta administrativa.
- Empresas.
- Sucursales.
- Suscripciones y planes.
- Usuarios delegados.
- Roles y permisos.
- Asignación de empleados a sucursales.

## 2. Jerarquía principal

```text
Usuario propietario
  └── Cuenta administrativa
        ├── Empresas
        │     ├── Suscripción empresarial
        │     ├── Membresías
        │     └── Sucursales
        │           └── Asignaciones de usuarios
        └── Administradores delegados
```

## 3. Entidades principales

### 3.1 User

Representa la identidad global de una persona dentro de JComercy.

Responsabilidades:

- Autenticación.
- Perfil básico.
- Verificación de contacto.
- Preferencias personales.

Un usuario puede ser propietario, administrador delegado, empleado o miembro de varias empresas.

### 3.2 Account

Representa la cuenta administrativa creada durante el registro inicial de JComercy.

No representa una empresa ni una sucursal. Es el contenedor que permite que un mismo propietario gestione varias empresas.

Responsabilidades:

- Identificar al propietario principal.
- Agrupar empresas relacionadas.
- Administrar usuarios con alcance global delegado.
- Administrar la relación comercial general con JComercy.

Relaciones:

- Una `Account` tiene un propietario principal.
- Una `Account` puede contener varias `Company`.
- Una `Account` puede tener administradores delegados.

### 3.3 AccountMembership

Relaciona usuarios adicionales con la cuenta administrativa.

Ejemplos de miembros:

- Administrador general.
- Socio.
- Gestor administrativo.
- Responsable de suscripciones.

Una membresía de cuenta no otorga automáticamente acceso operativo a las empresas. Ese acceso debe otorgarse mediante `CompanyMembership`.

### 3.4 Company

Representa una empresa o negocio perteneciente a una cuenta administrativa.

Responsabilidades:

- Datos legales y comerciales.
- Configuración fiscal.
- Clientes y operaciones comerciales.
- Sucursales.
- Suscripción.
- Módulos contratados.

Relaciones:

- Una empresa pertenece a una sola `Account`.
- Una empresa tiene una o varias `Branch`.
- Una empresa tiene una suscripción empresarial.
- Una empresa tiene membresías de usuarios.

### 3.5 CompanyMembership

Relaciona un usuario con una empresa y define su rol principal dentro de ella.

Ejemplos:

- Administrador de empresa.
- Contador.
- Recursos humanos.
- Gerente.
- Auditor.
- Operador.

Una membresía debe indicar si está activa, pendiente, suspendida o revocada.

### 3.6 Branch

Representa una sucursal perteneciente a una empresa.

Responsabilidades:

- Datos de ubicación y contacto.
- Estado operativo.
- Configuración local.
- Identificación para ventas, inventario, caja y reportes.

La sucursal no tiene una suscripción independiente. Su disponibilidad depende de la suscripción de la empresa y del número de sucursales contratado.

### 3.7 BranchAssignment

Relaciona una membresía de empresa con una o más sucursales.

Permite que un empleado trabaje en sucursales específicas.

Reglas:

- Un usuario puede estar asignado a varias sucursales.
- La asignación debe poder activarse o revocarse.
- La asignación no puede existir sin una membresía activa en la empresa.
- El dueño de la cuenta puede tener alcance global de propietario.
- Los demás usuarios requieren asignación explícita, salvo que tengan un permiso global delegado.

### 3.8 Plan

Representa un paquete comercial ofrecido por JComercy.

Un plan define:

- Precio base.
- Periodicidad de cobro.
- Sucursales incluidas.
- Módulos incluidos.
- Límites de usuarios.
- Límites de almacenamiento.
- Límites de operaciones o integraciones.

### 3.9 CompanySubscription

Representa la suscripción contratada por una empresa.

Responsabilidades:

- Vincular empresa y plan.
- Controlar estado de pago.
- Controlar fechas del ciclo.
- Determinar la capacidad de sucursales.
- Habilitar módulos contratados.

La capacidad total de sucursales se calcula con:

```text
Sucursales permitidas = sucursales incluidas por el plan + sucursales adicionales contratadas
```

### 3.10 SubscriptionAddon

Representa un complemento de la suscripción.

El primer complemento previsto es el de sucursal adicional.

Ejemplo:

```text
Plan Pro: 4 sucursales incluidas
Complemento: 2 sucursales adicionales
Capacidad total: 6 sucursales
```

Los complementos deben tener cantidad, precio, estado y periodo de vigencia.

### 3.11 Module

Representa una capacidad funcional de JComercy.

Ejemplos:

- Inventario.
- Ventas.
- Caja.
- Contabilidad.
- Personal y nómina.
- Chatbot.
- Integraciones.
- Clinerya.

### 3.12 CompanyModuleEntitlement

Representa la habilitación de un módulo para una empresa.

Se determina a partir del plan, complementos y configuraciones comerciales.

La habilitación del módulo a nivel empresarial no elimina las restricciones de usuarios ni de sucursales.

### 3.13 Role

Representa un conjunto de permisos.

Tipos:

- Rol del sistema.
- Rol personalizado de cuenta.
- Rol personalizado de empresa.

El `ACCOUNT_OWNER` es un rol reservado para el propietario inicial de la cuenta. Los demás roles pueden asignarse o revocarse de acuerdo con las reglas correspondientes.

### 3.14 Permission

Representa una acción concreta dentro de un módulo.

Ejemplos:

- Consultar inventario.
- Ajustar inventario.
- Crear ventas.
- Abrir caja.
- Consultar reportes.
- Administrar usuarios.
- Administrar suscripciones.

Los permisos deben poder limitarse por alcance: cuenta, empresa, sucursal o consulta cruzada.

## 4. Relaciones principales

```text
User 1 ─── 1 Account              propietario principal
Account 1 ─── N Company            empresas agrupadas
Account 1 ─── N AccountMembership  administradores delegados
Company 1 ─── N CompanyMembership  usuarios de la empresa
Company 1 ─── N Branch              sucursales
CompanyMembership 1 ─── N BranchAssignment
Company 1 ─── 1 CompanySubscription
CompanySubscription N ─── 1 Plan
CompanySubscription 1 ─── N SubscriptionAddon
Plan N ─── N Module
Company N ─── N Module             mediante CompanyModuleEntitlement
Role N ─── N Permission
```

## 5. Reglas de integridad

- Una empresa no puede pertenecer a más de una cuenta administrativa.
- Una sucursal no puede pertenecer a más de una empresa.
- Una membresía de empresa debe pertenecer a la misma cuenta que la empresa.
- Una asignación de sucursal debe corresponder a una membresía de la misma empresa.
- No se puede activar una sucursal que supere la capacidad contratada.
- La suspensión de la suscripción debe afectar la operación, no eliminar datos históricos.
- El dueño puede operar en sus empresas y sucursales, respetando los módulos contratados.
- Un administrador delegado no se convierte en propietario por recibir permisos globales.
- Los roles personalizados no pueden cruzar empresas.
- Los datos operativos siempre deben incluir el contexto de empresa y, cuando corresponda, sucursal.

## 6. Decisiones pendientes antes de diseñar tablas

1. Si una cuenta administrativa puede tener más de un propietario.
2. Si el dueño puede transferir la propiedad de la cuenta.
3. Si un usuario puede tener varios roles dentro de la misma empresa.
4. Si los roles personalizados vivirán a nivel de cuenta, empresa o ambos.
5. Si los módulos se habilitan únicamente por plan o también mediante complementos.
6. Qué ocurre con las sucursales al reducir el plan contratado.
7. Si la primera empresa se crea durante el registro o después del onboarding.

