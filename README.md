# JComercy

JComercy es un ecosistema modular de productos de software para PyMEs mexicanas y latinoamericanas.

## Estado actual

El repositorio se encuentra en la fase de diseño y definición arquitectónica. La implementación comenzará con el núcleo empresarial y sus módulos base.

## Documentación

La documentación aprobada se encuentra en [`documentacion/`](documentacion/), incluyendo:

- Arquitectura del núcleo.
- SDD de JComercy Core y Company Management.
- Modelo relacional de `company_management`.
- Contratos síncronos y asíncronos.
- Plan de migraciones Flyway.
- Estrategia de separación entre JComercy y Clinerya.
- [Bitácora de desarrollo](documentacion/bitacora.md): qué se hizo, cómo, qué afectó y el catálogo de errores con su solución.

## Regla de separación

Clinerya conserva su repositorio y operación independiente. Sus módulos se incorporarán posteriormente de manera individual, mediante contratos de JComercy y sin compartir tablas directamente.
