# Contrato de integración entre JComercy Core y Clinerya

## 1. Objetivo

Definir la frontera entre la plataforma empresarial JComercy y el producto vertical clínico Clinerya.

JComercy Core debe poder operar sin depender de Clinerya. Clinerya utilizará las capacidades de JComercy mediante identificadores y contratos explícitos.

## 2. Propiedad de datos

### JComercy Core es propietario de

- Usuario e identidad.
- Empresa.
- Sucursal.
- Membresías y asignaciones de empleados.
- Roles y permisos.
- Cliente comercial.
- Suscripción, plan y módulos contratados.
- Estado de activación de la solución Clinerya.

### Clinerya es propietario de

- Clínica.
- Perfil clínico del paciente.
- Expediente e historia clínica.
- Tratamientos clínicos.
- Recetas.
- Notas médicas.
- Estudios y adjuntos clínicos.
- Consultorios, salas y agenda clínica especializada.
- Reglas y datos clínicos regulados.

## 3. Relaciones entre contextos

La relación mínima propuesta es:

```text
JComercy Company
  └── JComercy Branch
        └── Clinerya Clinic
              └── Clinerya Patient -> JComercy Customer
```

Clinerya debe conservar referencias externas:

- `companyId`: empresa propietaria.
- `branchId`: sucursal donde opera la clínica.
- `customerId`: cliente comercial relacionado con el paciente.

Clinerya puede agregar sus propios identificadores, por ejemplo `clinicId` y `patientId`, pero no debe duplicar como entidades propietarias los datos generales de empresa, sucursal o cliente.

## 4. Activación de Clinerya

El flujo esperado es:

1. El usuario crea o selecciona una empresa en JComercy.
2. El usuario crea o activa una sucursal.
3. La empresa cuenta con un plan que incluye el módulo clínico.
4. La sucursal está dentro del límite de sucursales contratado.
5. JComercy habilita Clinerya para la sucursal.
6. Clinerya crea o vincula la clínica asociada.
7. El administrador configura consultorios, salas y reglas clínicas dentro de Clinerya.

Si la suscripción empresarial está suspendida o el módulo no está habilitado, no se deben permitir nuevas operaciones clínicas. Los datos históricos deben permanecer disponibles según los permisos y las políticas de conservación aplicables.

## 5. Clientes y pacientes

Un cliente de JComercy puede tener opcionalmente un perfil clínico en Clinerya. La relación no es automática: se crea únicamente cuando una clínica registra o vincula a esa persona como paciente.

```text
Customer
  └── ClinicalPatientProfile
        ├── patientId
        ├── clinicId
        └── clinical data owned by Clinerya
```

### 5.1 Flujo de creación

1. El paciente llega a una clínica que utiliza Clinerya.
2. El personal busca si ya existe un `Customer` dentro de la empresa.
3. Si no existe, Clinerya solicita los datos comerciales mínimos y JComercy crea el cliente.
4. Clinerya crea su entidad `Patient` con los datos clínicos necesarios.
5. Clinerya guarda la relación mediante `customerId`.
6. El paciente queda disponible como cliente de la empresa y como paciente de la clínica correspondiente.

### 5.2 Vinculación de un cliente existente

Si el cliente ya existe en JComercy, Clinerya no debe duplicarlo. Debe:

1. Localizarlo mediante una búsqueda autorizada.
2. Solicitar confirmación al usuario autorizado.
3. Crear el perfil `Patient` asociado al `customerId`.
4. Guardar únicamente los datos clínicos dentro de Clinerya.

### 5.3 Reglas de la relación

- No todos los clientes son pacientes.
- Un cliente puede tener perfiles clínicos en una o más clínicas si el modelo de negocio lo permite.
- Cada perfil clínico debe pertenecer a una empresa y sucursal autorizadas.
- Eliminar o desactivar un paciente no debe eliminar automáticamente al cliente comercial.
- Eliminar o desactivar un cliente debe verificar si existen perfiles clínicos, ventas, cobros o documentos relacionados.
- Los datos generales compartidos deben limitarse a lo necesario: nombre, contacto y datos administrativos autorizados.
- La información clínica solo puede ser consultada por Clinerya y por usuarios con permisos clínicos específicos.
- JComercy no debe mostrar notas médicas, diagnósticos, recetas ni expediente en sus módulos empresariales generales.

## 6. Reglas de aislamiento

- Clinerya no puede consultar datos de otra empresa solo porque exista un `companyId` válido.
- Toda consulta debe validarse contra el contexto de usuario, membresía, sucursal y permisos.
- La clínica solo puede operar dentro de la sucursal vinculada.
- La consulta consolidada de JComercy no debe exponer datos clínicos sensibles.
- Los datos clínicos no deben copiarse al núcleo empresarial salvo los mínimos necesarios para operación comercial.

## 7. Integración técnica inicial

Para la primera versión se recomienda mantener un monolito modular con contratos internos:

- Puertos de consulta de empresa, sucursal y cliente.
- Puertos de validación de membresía y permisos.
- Eventos de activación, suspensión y cambio de suscripción.
- Identificadores UUID compartidos como referencias, no como entidades JPA compartidas.

Clinerya podrá consumir estos contratos mediante adaptadores internos. Si posteriormente se separa como servicio independiente, los mismos contratos podrán exponerse mediante API o mensajería.

## 8. Chatbot y comunicaciones

JComercy podrá administrar los canales de comunicación comerciales, como correo electrónico y WhatsApp, mediante el módulo de chatbot e integraciones.

El chatbot podrá:

- Capturar datos básicos de una persona interesada.
- Crear o actualizar un `Customer` cuando la persona otorgue los datos necesarios.
- Solicitar consentimiento para recibir comunicaciones.
- Enviar recordatorios de citas.
- Confirmar, cancelar o solicitar reprogramación de citas.
- Consultar horarios disponibles mediante un contrato con Clinerya.
- Crear una solicitud de cita en Clinerya después de validar empresa, sucursal y permisos.
- Enviar confirmaciones y avisos operativos.

El chatbot no debe acceder por defecto a:

- Expedientes.
- Diagnósticos.
- Notas médicas.
- Recetas.
- Estudios clínicos.
- Información clínica no necesaria para la operación solicitada.

El flujo de una cita puede ser:

```text
WhatsApp o correo
  └── Chatbot JComercy
        ├── Identifica o crea Customer
        ├── Valida consentimiento y canal
        ├── Consulta disponibilidad autorizada
        └── Solicita a Clinerya crear o actualizar la cita
```

Clinerya seguirá siendo propietario de la cita clínica y de sus reglas de agenda. JComercy será responsable del canal de comunicación, el consentimiento comercial y el registro de la interacción.

Los recordatorios deben utilizar únicamente los datos mínimos necesarios, por ejemplo nombre, fecha, hora, sucursal y medio de contacto. No deben incluir diagnósticos, tratamientos ni otra información clínica sensible.

## 9. Eventos relevantes

Eventos iniciales sugeridos:

- `CompanyCreated`
- `BranchCreated`
- `BranchActivated`
- `BranchSuspended`
- `SubscriptionActivated`
- `SubscriptionSuspended`
- `ModuleEnabled`
- `ModuleDisabled`
- `CustomerCreated`
- `CustomerDeactivated`

Clinerya debe reaccionar a los eventos necesarios sin modificar directamente las tablas de JComercy Core.

## 10. Decisiones de frontera

- JComercy Core no conoce entidades clínicas.
- Clinerya no administra suscripciones.
- Clinerya no administra membresías empresariales globales.
- La empresa y la sucursal son referencias externas para Clinerya.
- El paciente es una especialización clínica de un cliente comercial.
- Los consultorios y salas pertenecen exclusivamente al contexto Clinerya.
