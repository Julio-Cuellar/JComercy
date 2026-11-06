# Roles, membresías y alta de empleados por RH

> Plan del frente de roles y membresías (M0 a M8) y los diagramas del flujo de alta y activación de empleados.
> Los diagramas están en Mermaid, igual que `diagramas-modulo-inventory.md`.

## Qué se construye

- RH registra al empleado: nombre completo, rol, RFC, CURP, correo y sucursales. El empleado no llena nada de eso.
- El sistema envía un enlace por correo. El empleado solo elige su contraseña y queda activo.
- Socios y contadores serán otros tipos de perfil. Hoy solo empleados; el tipo de perfil se guarda como dato.

## Decisiones tomadas

| Tema | Decisión |
|---|---|
| Correo | Lo escribe RH. El enlace va a ese buzón. El empleado no lo cambia y solo elige su contraseña. |
| RFC | Único dentro de cada empresa, no en todo el sistema. |
| Cuenta de acceso | Al activar se crea una cuenta con los datos que RH registró, sin empresa propia, reutilizando el inicio de sesión actual. Si el correo ya tiene cuenta, se pide iniciar sesión en vez de crear otra. |
| Conflictos | Si el RFC o el correo ya pertenecen a otra cuenta, se avisa a RH al registrar (409), no al activar. |
| Código del enlace | Aleatorio de 32 bytes. Solo se guarda su huella. Viaja únicamente en el correo. Vigencia de 7 días. Uso único. |
| Envío | Después de confirmar la transacción. Si falla, la invitación queda «correo no enviado» y RH usa Reenviar, que crea un código nuevo. |
| Enlace inválido | Vencido, usado o inexistente responden igual (404), para no revelar qué códigos existen. |
| Datos personales | RFC y CURP no viajan en los eventos ni en los registros. Solo los ve quien tiene `employees.read`. |
| Permisos | Se añade la familia `employees.*` a `COMPANY_ADMIN`. Nadie otorga más permisos de los que tiene. |

## Etapas

| Etapa | Qué entrega |
|---|---|
| M0 | Estos diagramas y el registro del error de la recuperación de contraseña (ERR-029). |
| M1 | Ver membresías (con filtros y paginación), roles y mis permisos efectivos. |
| M2 | Roles propios de la empresa: crear, editar, desactivar. Los del sistema no se tocan. |
| M3 | Perfil de empleado por RH. Permiso `employees.*`. |
| M4 | Puerto de correo con adaptador SMTP y adaptador de prueba. La recuperación de contraseña pasa a usarlo. |
| M5 | Invitación y activación: consultar, activar, reenviar, cancelar. |
| M6 | Asignaciones por sucursal: listar, crear, cambiar, retirar. |
| M7 | Suspender, reactivar y revocar. |
| M8 | Cierre: suites completas, AsyncAPI al día, bitácora y PR. |

## 1. Alta por RH y envío del enlace

```mermaid
sequenceDiagram
  autonumber
  actor RH as Persona de RH
  participant API as company-management
  participant BD as Base de datos
  participant Correo as Servicio de correo
  actor Emp as Empleado
  RH->>API: Registrar empleado (datos, rol, sucursales, correo)
  API->>API: Revisar permiso, empresa, rol y sucursales
  API->>BD: Buscar RFC y correo ya usados
  alt RFC o correo en conflicto
    API-->>RH: 409 con el motivo y qué hacer
  else sin conflicto
    API->>BD: Guardar perfil Pendiente e invitación con huella del código
    API->>BD: Guardar evento de invitación sin RFC ni CURP
    API-->>RH: 201 perfil en estado Pendiente
    API->>Correo: Enviar enlace con el código, tras confirmar la transacción
    alt el correo sale
      Correo-->>Emp: Correo con el enlace de activación
    else falla el envío
      API->>BD: Marcar invitación como correo no enviado
      Note over RH,API: RH ve Reenviar, que crea un código nuevo
    end
  end
```

## 2. Activación por el empleado

```mermaid
sequenceDiagram
  autonumber
  actor Emp as Empleado
  participant Web as Pantalla de activación
  participant API as company-management
  participant BD as Base de datos
  Emp->>Web: Abre el enlace del correo
  Web->>API: Consultar invitación (código)
  API->>BD: Buscar por la huella del código
  alt vencido, usado o inexistente
    API-->>Web: 404 con el mismo mensaje en los tres casos
    Web-->>Emp: Este enlace ya no sirve, pide uno nuevo a RH
  else vigente
    API-->>Web: Nombre, empresa, rol y correo
    Emp->>Web: Elige su contraseña
    Web->>API: Activar acceso (código, contraseña)
    API->>BD: Crear cuenta de acceso si el correo no tiene una
    API->>BD: Membresía activa, asignaciones de sucursal e invitación aceptada, en una transacción
    API->>BD: Eventos de membresía creada e invitación aceptada
    API-->>Web: 204 sin contenido
    Web-->>Emp: Listo, ya puedes entrar
    Emp->>API: Iniciar sesión y entrar a la empresa
    API-->>Emp: Token de la empresa con sus permisos y sucursales
  end
```

## 3. Decisiones al abrir el enlace

```mermaid
flowchart TD
  A["Abre el enlace"] --> B{"Código vigente y sin usar?"}
  B -- No --> X["Mensaje único: el enlace ya no sirve"]
  B -- Sí --> C{"La empresa sigue activa?"}
  C -- No --> Y["Aviso: la empresa no está activa, habla con RH"]
  C -- Sí --> D{"El correo ya tiene cuenta?"}
  D -- Sí --> E["Pide iniciar sesión con esa cuenta, sin crear otra"]
  D -- No --> F["Pide contraseña de 8 caracteres o más"]
  F --> G{"Contraseña válida?"}
  G -- No --> F2["Explica qué falta"]
  F2 --> F
  G -- Sí --> H["Crea la cuenta de acceso"]
  E --> I["Crea membresía activa y asignaciones"]
  H --> I
  I --> J["Invitación aceptada, el código ya no sirve"]
  J --> K["Entra a la empresa con sus permisos"]
```

## 4. Estados de la invitación y de la membresía

```mermaid
stateDiagram-v2
  direction LR
  [*] --> Pendiente: RH registra al empleado
  Pendiente --> Pendiente: Reenviar con código nuevo
  Pendiente --> Cancelada: RH cancela la invitación
  Pendiente --> Vencida: Pasan 7 días
  Vencida --> Pendiente: Reenviar con código nuevo
  Pendiente --> Activo: El empleado activa su acceso
  Activo --> Suspendido: RH suspende
  Suspendido --> Activo: RH reactiva
  Activo --> Revocado: RH revoca
  Suspendido --> Revocado: RH revoca
  Cancelada --> [*]
  Revocado --> [*]
```

`Pendiente`, `Cancelada` y `Vencida` son estados de la invitación. `Activo`, `Suspendido` y `Revocado` son estados de la membresía.
