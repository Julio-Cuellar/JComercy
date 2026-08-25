# Separación de la historia Flyway: Clinerya y JComercy

## 1. Situación detectada

El repositorio copiado conserva la configuración histórica de Clinerya:

- La aplicación utiliza `classpath:db/migration`.
- Flyway tiene `baseline-on-migrate: true`.
- Existen migraciones históricas de Clinerya con versiones `V1` a `V33`.
- La configuración todavía utiliza nombres y variables de entorno de MediCloud/Clinerya.

Esta configuración no debe utilizarse como base de la nueva línea de JComercy porque podría ejecutar migraciones clínicas, crear tablas fuera del alcance aprobado y mezclar historiales incompatibles.

## 2. Recomendación

Crear una línea Flyway limpia para JComercy y dejar la historia de Clinerya fuera de sus ubicaciones de migración activas.

```text
JComercy
└── migraciones nuevas y propias
    ├── V001__company_management_create_schema.sql
    ├── V002__company_management_create_account.sql
    └── ...

Clinerya
└── migraciones históricas de su aplicación activa
    ├── V1__init_schema.sql
    ├── ...
    └── V33__appointment_quotation_items.sql
```

La nueva aplicación JComercy no debe cargar el directorio histórico de Clinerya. Los archivos heredados pueden conservarse en el repositorio como referencia o en una ubicación archivada que no esté configurada en Flyway.

## 3. Configuración conceptual futura

La configuración de JComercy deberá definir explícitamente:

```text
Aplicación: jcomercy-application
Ubicación: classpath:db/migration/jcomercy
Historial: flyway_schema_history_jcomercy
Baseline automático: desactivado
Esquemas: company_management y los esquemas de módulos aprobados
```

La configuración de Clinerya deberá permanecer aislada en su aplicación y conservar su historial actual.

## 4. Regla sobre `baseline-on-migrate`

Para JComercy se recomienda desactivar `baseline-on-migrate` en la operación normal. El baseline automático puede ocultar una historia no deseada y hacer que una base con migraciones de Clinerya parezca compatible con JComercy.

Si se necesitara adoptar una base existente, primero se deberá realizar una evaluación explícita y crear un baseline documentado. No se utilizará el baseline automático como mecanismo para saltar las migraciones heredadas.

## 5. Historia y base de datos

### Opción recomendada: base nueva de JComercy

- Crear una base de datos nueva para el ecosistema JComercy.
- Ejecutar únicamente sus migraciones.
- Mantener Clinerya en su base activa o en una base controlada por su propia aplicación.
- Integrar ambos sistemas mediante contratos, no mediante tablas compartidas.

Ventaja: aislamiento máximo y menor riesgo para los clientes activos de Clinerya.

### Opción alternativa: misma base con historiales separados

Solo si existe una razón operativa para compartir PostgreSQL:

- Usar ubicaciones distintas.
- Usar tablas de historial Flyway distintas.
- Usar esquemas propietarios separados.
- Usar credenciales o roles de base de datos separados.
- Prohibir FK y consultas directas entre los módulos.

Esta opción es más compleja y no se recomienda para la primera implementación de JComercy.

## 6. Secuencia segura de transición

1. Crear la estructura base de la nueva aplicación JComercy.
2. Separar físicamente la ubicación de migraciones nuevas.
3. Desactivar la carga de migraciones históricas de Clinerya.
4. Definir el historial Flyway propio de JComercy.
5. Crear una base de datos de desarrollo limpia.
6. Ejecutar `validate` e `info` sobre la nueva línea.
7. Aplicar las primeras migraciones de `company_management`.
8. Verificar que ningún esquema clínico o tabla heredada sea creado.

## 7. Criterios de seguridad

- JComercy no debe conectarse con el usuario propietario utilizado por Clinerya.
- Cada aplicación debe tener credenciales de base de datos independientes.
- El runtime de JComercy no debe tener permisos sobre los esquemas clínicos.
- Los módulos deben comunicarse mediante APIs o eventos.
- Las pruebas deben verificar que las migraciones de JComercy no ejecutan scripts heredados.

## 8. Estado

**Estado: APROBADO por el propietario del proyecto.**

Queda aprobada la estrategia de una base nueva para JComercy, una línea Flyway limpia y la separación completa de la historia heredada de Clinerya.

La aprobación permite preparar la estructura del nuevo proyecto y sus ubicaciones de migración. No autoriza modificar el repositorio activo de Clinerya ni ejecutar migraciones sobre su base de datos.
