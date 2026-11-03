# Arquitectura inicial de JComercy Core

## 1. Propósito

JComercy Core será la plataforma empresarial multiempresa y multisucursal de JCode. Proporcionará la identidad, organización, suscripciones y capacidades comunes sobre las que se construirán productos verticales como Clinerya, restaurantes, retail y otros nichos.

Los módulos clínicos no forman parte del núcleo genérico. Clinerya conservará sus conceptos especializados: clínica, paciente, expediente, tratamiento, historia clínica, receta y reglas clínicas.

## 2. Jerarquía de negocio

```text
Usuario global
  └── Membresía en empresa
        └── Empresa (tenant)
              ├── Sucursales
              │     └── Configuración operativa básica
              ├── Clientes
              └── Suscripción y módulos habilitados
```

### 2.1 Usuario

Representa la identidad global de una persona. Un usuario puede pertenecer a varias empresas y tener roles diferentes en cada una.

El usuario no es propietario de los datos operativos de una empresa por el solo hecho de autenticarse. El acceso siempre debe resolverse mediante una membresía activa.

### 2.2 Empresa

Es el tenant principal y el límite de aislamiento de datos y configuración. Dentro de una empresa, cada sucursal también debe operar con aislamiento de datos y operaciones por defecto. La empresa contrata y paga la suscripción; el plan define las sucursales incluidas y los cargos adicionales aplicables.

Campos iniciales sugeridos:

- `id`
- `legalName`
- `commercialName`
- `taxId` (RFC en México)
- `taxRegime`
- `email`
- `phone`
- `address`
- `timezone`
- `currency`
- `status`
- `createdAt`
- `updatedAt`

La configuración fiscal de la empresa debe permanecer separada del módulo tributario por país.

### 2.3 Sucursal

Es una unidad operativa de una empresa. Una empresa puede operar inicialmente con una o varias sucursales.

Campos iniciales sugeridos:

- `id`
- `companyId`
- `code`
- `name`
- `address`
- `phone`
- `timezone`
- `status`
- `createdAt`
- `updatedAt`

Las ventas, cajas y existencias pueden asociarse a una sucursal. Los reportes podrán consolidarse a nivel de empresa.

### 2.4 Recursos y espacios por producto vertical

Los recursos o espacios no formarán parte de JComercy Core. Cada producto vertical debe definirlos desde cero, porque su significado, atributos, estados y reglas operativas pueden ser diferentes entre industrias.

Ejemplos de implementaciones verticales:

- Clinerya: consultorios, salas y espacios clínicos.
- Restaurantes: mesas, áreas de comedor y estaciones de cocina.
- Retail: cajas, áreas de venta y almacenes.
- Talleres: bahías de servicio y áreas de trabajo.

JComercy Core solo proporcionará la sucursal como límite organizacional. El producto vertical será responsable de:

- Definir la entidad y sus atributos.
- Definir sus estados y transiciones.
- Administrar su disponibilidad y programación.
- Determinar qué usuarios pueden operarlo.
- Relacionarlo con clientes, ventas, citas u órdenes.
- Crear sus propias tablas, endpoints y componentes de frontend.

### 2.5 Cliente

Representa a la persona física o moral que recibe servicios, compra productos o mantiene una relación comercial con la empresa.

Campos iniciales sugeridos:

- `id`
- `companyId`
- `type` (`INDIVIDUAL` o `BUSINESS`)
- `firstName`
- `lastName`
- `businessName`
- `taxId`
- `email`
- `phone`
- `address`
- `status`
- `createdAt`
- `updatedAt`

Un producto vertical puede mantener un perfil especializado relacionado con el cliente. Por ejemplo, Clinerya puede asociar un `customerId` con un paciente clínico.

## 3. Membresías y permisos

### 3.1 Membresía de empresa

`CompanyMembership` relaciona un usuario con una empresa.

Campos iniciales sugeridos:

- `id`
- `userId`
- `companyId`
- `role`
- `status`
- `joinedAt`

### 3.2 Acceso a empresas y sucursales

El propietario de la cuenta administradora es quien puede ver y administrar todas las empresas y sucursales asociadas a su cuenta. Esta capacidad también puede otorgarse a otro usuario mediante un permiso administrativo explícito.

Los empleados no deben obtener acceso automático a todas las sucursales de una empresa. Para trabajar en JComercy, cada empleado debe ser asignado a una o más sucursales de trabajo.

Se recomienda soportar:

- Acceso administrativo a todas las empresas y sucursales, solo para el propietario o usuarios autorizados.
- Acceso a empresas específicas.
- Asignación del empleado a una o varias sucursales de trabajo.
- Acceso a sucursales específicas.
- Permisos por módulo.
- Permisos de lectura, operación, administración y auditoría.

La asignación de sucursal debe formar parte de la autorización efectiva. Un usuario podrá tener una membresía válida en la empresa, pero no podrá operar en una sucursal hasta que tenga una asignación activa para ella, salvo que posea el permiso administrativo de alcance global.

### 3.3 Aislamiento entre sucursales

La información y las operaciones de una sucursal no deben quedar disponibles automáticamente para otra sucursal de la misma empresa. El aislamiento se aplica también entre sucursales que pertenecen al mismo tenant.

Reglas iniciales:

- Una sucursal solo puede consultar y modificar sus propios datos operativos por defecto.
- Un usuario con acceso a la sucursal A no puede consultar datos de la sucursal B, salvo que tenga un permiso explícito de consulta cruzada.
- La consulta cruzada debe ser específica, auditable y preferentemente de solo lectura.
- Un usuario autorizado podrá consultar, por ejemplo, la existencia disponible de un producto en otra sucursal.
- Consultar la existencia de la sucursal B no otorga permiso para vender desde la sucursal A el inventario perteneciente a B.
- Las ventas, cobros, ajustes, reservas y salidas de inventario deben ejecutarse en la sucursal propietaria de la operación.
- Para utilizar inventario de otra sucursal debe existir un proceso formal de transferencia, recepción y actualización de existencias.
- Los reportes consolidados requieren permisos administrativos o de consulta corporativa.

Ejemplo:

```text
Sucursal A puede consultar stock de Sucursal B
        └── solo lectura y con autorización

Sucursal A no puede vender stock de Sucursal B
        └── primero debe solicitar y completar una transferencia
```

El backend debe aplicar el alcance de sucursal en cada caso de uso, consulta, comando y repositorio. No debe depender únicamente de filtros enviados por el frontend.

## 4. Suscripciones y capacidades

La suscripción pertenece a la empresa, nunca al usuario individual ni a una sucursal individual. El plan contratado define cuántas sucursales puede administrar la empresa. Algunos planes incluyen una cantidad fija de sucursales y otros permiten agregar sucursales adicionales mediante un cargo por cada sucursal nueva.

```text
Company
  ├── Branches
  └── Subscription
        ├── Plan
        ├── Status
        ├── BillingCycle
        ├── BranchLimit
        ├── AdditionalBranchItems
        └── ModuleEntitlements
```

Entidades iniciales:

- `Plan`: catálogo comercial de planes.
- `Subscription`: contrato activo de una empresa.
- `SubscriptionItem`: módulos o complementos contratados.
- `Module`: catálogo de capacidades de JComercy.
- `ModuleEntitlement`: módulo habilitado y sus límites para la empresa y sus sucursales.
- `AdditionalBranchItem`: cargo recurrente por cada sucursal que exceda el límite incluido en el plan.

Ejemplo de estructura comercial, sin fijar todavía precios:

| Plan | Sucursales incluidas | Expansión |
|---|---:|---|
| Básico | 1 | Puede cambiar de plan para administrar más sucursales |
| Intermedio | Hasta 3 | Incluidas dentro de la suscripción |
| Pro | Hasta 4 | Puede agregar sucursales adicionales con cargo recurrente por sucursal |

Los límites, módulos incluidos y precios deben permanecer configurables en el catálogo de planes, no codificados directamente en los módulos operativos.

La autorización debe validar estas condiciones:

1. El usuario tiene una membresía activa y el permiso requerido dentro de la empresa.
2. El usuario está asignado a la sucursal o tiene alcance administrativo global.
3. La empresa tiene una suscripción activa.
4. La sucursal está dentro del límite incluido en el plan o cuenta con un complemento de sucursal adicional.
5. El módulo solicitado está habilitado para la empresa y disponible para la sucursal.

El precio puede considerar que cada sucursal genera consumo adicional de recursos de nube, almacenamiento, procesamiento, tráfico e integraciones, pero comercialmente la suscripción y la relación de cobro pertenecen a la empresa.

## 5. Módulos iniciales del núcleo

- Identidad y autenticación.
- Empresas.
- Sucursales.
- Membresías y permisos.
- Clientes.
- Suscripciones, planes y sucursales adicionales.
- Catálogo de módulos.
- Auditoría.
- Notificaciones.
- Archivos y adjuntos.
- Integraciones externas.

Los módulos de inventario, ventas, caja, compras, contabilidad y personal se incorporarán sobre este núcleo, utilizando `companyId` y `branchId` según su alcance.

## Transportes estándar de JComercy

Todos los módulos actuales y futuros podrán utilizar los siguientes transportes, seleccionando el adecuado según el requisito:

- **REST:** comandos, consultas operativas, APIs de recursos e integraciones externas.
- **GraphQL:** consultas compuestas, agregación de contratos y vistas con selección flexible de campos.
- **WebSockets:** notificaciones, colaboración y actualizaciones de estado en tiempo real.

Los transportes pertenecerán a las capas de aplicación e infraestructura de cada módulo o de la aplicación de entrada. No tendrán acceso directo a tablas de otros módulos ni sustituirán las reglas de autorización, los contratos internos o los eventos del sistema.

## 6. Frontera con Clinerya

Clinerya será un producto vertical conectado a JComercy Core.

```text
JComercy Company
  └── Clinerya context
        ├── Clinic
        ├── Patient -> customerId
        ├── ClinicalRecord
        ├── Treatment
        ├── Prescription
        └── ClinicalRoom -> branchId
```

JComercy Core será propietario de:

- Usuario.
- Empresa.
- Sucursal.
- Membresía.
- Suscripción.
- Cliente.
- Activación de módulos.

Clinerya será propietario de:

- Clínica.
- Paciente.
- Expediente.
- Historia clínica.
- Tratamiento clínico.
- Receta.
- Reglas y datos clínicos regulados.

Las relaciones entre ambos contextos deben utilizar identificadores y contratos explícitos. No se deben compartir entidades JPA entre el núcleo y el producto clínico.

## 7. Decisiones iniciales

- La empresa es el tenant y límite principal de aislamiento.
- La sucursal es un segundo límite de aislamiento para datos y operaciones.
- Las consultas entre sucursales son excepcionales, explícitas, auditables y normalmente de solo lectura.
- Consultar existencias de otra sucursal no permite vender ni modificar su inventario.
- Los movimientos entre sucursales requieren una transferencia formal.
- La empresa paga una suscripción propia.
- El plan contratado define el número de sucursales incluidas.
- Las sucursales adicionales se cobran como complementos recurrentes de la empresa.
- Todas las sucursales de una empresa comparten la suscripción empresarial, sujeto a los límites y módulos contratados.
- El propietario o un usuario autorizado puede administrar todas las empresas y sucursales.
- Los empleados deben ser asignados explícitamente a sus sucursales de trabajo.
- Un usuario puede pertenecer a varias empresas.
- La sucursal es una unidad operativa de la empresa.
- Los recursos y espacios son responsabilidad de cada producto vertical.
- Cliente es un concepto comercial general; paciente es una especialización clínica.
- Clinerya conserva su modelo clínico especializado.
- JComercy Core no debe depender de Clinerya.
- Se comenzará con un monolito modular y una base de datos organizada por esquemas. Cada módulo será dueño de su esquema y de sus tablas; ningún módulo podrá acceder directamente a las tablas de otro. La colaboración se realizará mediante interfaces, APIs internas, eventos y mensajes síncronos o asíncronos.
- La fiscalidad será una capa independiente por país, comenzando con México y el SAT.

## 8. Próxima fase

Antes de implementar módulos operativos se debe definir:

1. Estados y transiciones de empresas, membresías y suscripciones.
2. Matriz de permisos y alcance por sucursal.
3. Contrato de integración entre JComercy Core y Clinerya.
4. Convención de nombres para paquetes, tablas, APIs y eventos.
5. Estructura base del repositorio y del monolito modular.
