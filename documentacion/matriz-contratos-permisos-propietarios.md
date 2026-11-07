# Matriz de Gobernanza de Contratos, Permisos y Propietarios de Módulos

Este documento define la asignación de propiedad exclusiva de datos y contratos, la matriz de permisos RBAC y el alcance operativo para el módulo `company_management` de JComercy y sus integraciones con `Identity`, `Billing` y `Personal/Nómina`.

---

## 1. Principio de Propiedad Exclusiva por Módulo

> [!IMPORTANT]
> - **`Company Management`:** Propietario exclusivo de cuentas administrativas, empresas, sucursales, membresías empresariales, asignaciones de sucursal, roles/permisos del sistema y personalizados, operaciones asíncronas de configuración y suscripciones webhook.
> - **`Identity`:** Propietario exclusivo de identidades de usuario, credenciales, autenticación JWT, recuperación de acceso y verificación de identidad.
> - **`Billing`:** Propietario exclusivo de planes comerciales, facturación, cobros, estados de suscripción pagada, límites contratados y complementos (`add-ons`).
> - **`Personal y Nómina`:** Propietario exclusivo de datos laborales, contratos de trabajo, puestos, expedientes y nóminas. **Permanecerá completamente fuera de `Company Management`** y no administrará membresías ni permisos de acceso a la plataforma.

---

## 2. Matriz de Contratos REST y Permisos (`x-required-permission`)

| Dominio | Método | Ruta HTTP | Permiso Explícito (`x-required-permission`) | Alcance Mínimo | Módulo Propietario |
|---|:---:|---|---|:---:|:---:|
| **Registro** | `POST` | `/api/v1/accounts/registration` | `accounts.register` | `ACCOUNT` | Company Management |
| **Cuenta** | `GET` | `/api/v1/accounts/{publicAccountId}` | `accounts.read` | `ACCOUNT` | Company Management |
| **Cuenta** | `PATCH` | `/api/v1/accounts/{publicAccountId}` | `accounts.update` | `ACCOUNT` | Company Management |
| **Cuenta** | `GET` | `/api/v1/accounts/{publicAccountId}/companies` | `accounts.companies.read` | `ACCOUNT` | Company Management |
| **Cuenta** | `POST` | `/api/v1/accounts/{publicAccountId}/companies` | `companies.create` | `ACCOUNT` | Company Management |
| **Cuenta** | `POST` | `/api/v1/accounts/{publicAccountId}/deactivation-requests` | `accounts.deactivate` | `ACCOUNT` | Company Management |
| **Cuenta** | `POST` | `/api/v1/accounts/{publicAccountId}/reactivation` | `accounts.reactivate` | `ACCOUNT` | Company Management |
| **Cuenta** | `POST` | `/api/v1/accounts/{publicAccountId}/invitations` | `accounts.invitations.create` | `ACCOUNT` | Company Management |
| **Cuenta** | `GET` | `/api/v1/accounts/{publicAccountId}/history` | `accounts.history.read` | `ACCOUNT` | Company Management |
| **Empresa** | `GET` | `/api/v1/companies/{publicCompanyId}` | `companies.read` | `COMPANY` | Company Management |
| **Empresa** | `PATCH` | `/api/v1/companies/{publicCompanyId}` | `companies.update` | `COMPANY` | Company Management |
| **Empresa** | `PATCH` | `/api/v1/companies/{publicCompanyId}/profile` | `companies.profile.update` | `COMPANY` | Company Management |
| **Empresa** | `PATCH` | `/api/v1/companies/{publicCompanyId}/tax-profile` | `companies.tax_profile.update` | `COMPANY` | Company Management |
| **Empresa** | `PATCH` | `/api/v1/companies/{publicCompanyId}/contact` | `companies.contact.update` | `COMPANY` | Company Management |
| **Empresa** | `PATCH` | `/api/v1/companies/{publicCompanyId}/operational-settings` | `companies.operational_settings.update` | `COMPANY` | Company Management |
| **Empresa** | `PATCH` | `/api/v1/companies/{publicCompanyId}/localization` | `companies.localization.update` | `COMPANY` | Company Management |
| **Empresa** | `PATCH` | `/api/v1/companies/{publicCompanyId}/preferences` | `companies.preferences.update` | `COMPANY` | Company Management |
| **Empresa** | `POST` | `/api/v1/companies/{publicCompanyId}/configuration-completion` | `companies.configure` | `COMPANY` | Company Management |
| **Empresa** | `POST` | `/api/v1/companies/{publicCompanyId}/activation` | `companies.activate` | `COMPANY` | Company Management |
| **Empresa** | `POST` | `/api/v1/companies/{publicCompanyId}/suspension` | `companies.suspend` | `COMPANY` | Company Management |
| **Empresa** | `POST` | `/api/v1/companies/{publicCompanyId}/reactivation` | `companies.reactivate` | `COMPANY` | Company Management |
| **Empresa** | `POST` | `/api/v1/companies/{publicCompanyId}/deletion-requests` | `companies.delete` | `COMPANY` | Company Management |
| **Empresa** | `GET` | `/api/v1/companies/{publicCompanyId}/history` | `companies.history.read` | `COMPANY` | Company Management |
| **Empresa** | `GET` | `/api/v1/companies/{publicCompanyId}/capabilities` | `companies.capabilities.read` | `COMPANY` | Company Management |
| **Sucursal** | `GET` | `/api/v1/companies/{publicCompanyId}/branches` | `branches.read` | `COMPANY` | Company Management |
| **Sucursal** | `POST` | `/api/v1/companies/{publicCompanyId}/branches` | `branches.create` | `COMPANY` | Company Management |
| **Sucursal** | `GET` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}` | `branches.read` | `BRANCH` | Company Management |
| **Sucursal** | `PATCH` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}` | `branches.update` | `BRANCH` | Company Management |
| **Sucursal** | `PATCH` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/profile` | `branches.profile.update` | `BRANCH` | Company Management |
| **Sucursal** | `PATCH` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/localization` | `branches.localization.update` | `BRANCH` | Company Management |
| **Sucursal** | `POST` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/configuration-completion` | `branches.configure` | `BRANCH` | Company Management |
| **Sucursal** | `POST` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/suspension` | `branches.suspend` | `BRANCH` | Company Management |
| **Sucursal** | `POST` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/reactivation` | `branches.reactivate` | `BRANCH` | Company Management |
| **Sucursal** | `POST` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/activation` | `branches.activate` | `BRANCH` | Company Management |
| **Sucursal** | `POST` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/head-office-designation` | `branches.head_office.designate` | `COMPANY` | Company Management |
| **Sucursal** | `POST` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/deletion-requests` | `branches.delete` | `BRANCH` | Company Management |
| **Sucursal** | `GET` | `/api/v1/companies/{publicCompanyId}/branches/{publicBranchId}/history` | `branches.history.read` | `BRANCH` | Company Management |
| **Consultas Cruzadas** | `GET` | `/api/v1/companies/{publicCompanyId}/cross-branch-queries` | `cross_branch.read` | `BRANCH` (autorizada) | Company Management |
| **Consultas Cruzadas** | `POST` | `/api/v1/companies/{publicCompanyId}/cross-branch-query-authorizations` | `cross_branch.authorizations.create` | `COMPANY` | Company Management |
| **Consultas Cruzadas** | `DELETE` | `/api/v1/companies/{publicCompanyId}/cross-branch-query-authorizations/{authorizationId}` | `cross_branch.authorizations.delete` | `COMPANY` | Company Management |
| **Membresías** | `GET` | `/api/v1/companies/{publicCompanyId}/memberships` | `memberships.read` | `COMPANY` | Company Management |
| **Membresías** | `POST` | `/api/v1/companies/{publicCompanyId}/memberships/invitations` | `memberships.invite` | `COMPANY` | Company Management |
| **Membresías** | `POST` | `/api/v1/membership-invitations/{invitationToken}/acceptance` | `memberships.accept` | N/A (Token) | Company Management |
| **Membresías** | `GET` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}` | `memberships.read` | `COMPANY` | Company Management |
| **Membresías** | `PATCH` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}` | `memberships.update` | `COMPANY` | Company Management |
| **Membresías** | `POST` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/resend` | `memberships.resend` | `COMPANY` | Company Management |
| **Membresías** | `POST` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/suspension` | `memberships.suspend` | `COMPANY` | Company Management |
| **Membresías** | `POST` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/reactivation` | `memberships.reactivate` | `COMPANY` | Company Management |
| **Membresías** | `POST` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/revocation` | `memberships.revoke` | `COMPANY` | Company Management |
| **Membresías** | `GET` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/history` | `memberships.history.read` | `COMPANY` | Company Management |
| **Asignaciones** | `GET` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments` | `assignments.read` | `COMPANY` | Company Management |
| **Asignaciones** | `POST` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments` | `assignments.create` | `COMPANY` | Company Management |
| **Asignaciones** | `PATCH` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments/{assignmentId}` | `assignments.update` | `COMPANY` | Company Management |
| **Asignaciones** | `DELETE` | `/api/v1/companies/{publicCompanyId}/memberships/{membershipId}/assignments/{assignmentId}` | `assignments.delete` | `COMPANY` | Company Management |
| **Contexto Operativo** | `GET` | `/api/v1/companies/{publicCompanyId}/memberships/me/operational-contexts` | `operational_context.read` | `USER` | Company Management |
| **Contexto Operativo** | `GET` | `/api/v1/companies/{publicCompanyId}/memberships/me/operational-context` | `operational_context.read_active` | `USER` | Company Management |
| **Contexto Operativo** | `POST` | `/api/v1/companies/{publicCompanyId}/memberships/me/operational-context` | `operational_context.select` | `USER` | Company Management |
| **Roles & Permisos** | `GET` | `/api/v1/companies/{publicCompanyId}/permissions` | `permissions.read` | `COMPANY` | Company Management |
| **Roles & Permisos** | `GET` | `/api/v1/companies/{publicCompanyId}/roles` | `roles.read` | `COMPANY` | Company Management |
| **Roles & Permisos** | `POST` | `/api/v1/companies/{publicCompanyId}/roles` | `roles.create` | `COMPANY` | Company Management |
| **Roles & Permisos** | `GET` | `/api/v1/companies/{publicCompanyId}/roles/{roleId}` | `roles.read` | `COMPANY` | Company Management |
| **Roles & Permisos** | `PATCH` | `/api/v1/companies/{publicCompanyId}/roles/{roleId}` | `roles.update` | `COMPANY` | Company Management |
| **Roles & Permisos** | `DELETE` | `/api/v1/companies/{publicCompanyId}/roles/{roleId}` | `roles.delete` | `COMPANY` | Company Management |
| **Roles & Permisos** | `POST` | `/api/v1/companies/{publicCompanyId}/roles/{roleId}/deactivation` | `roles.deactivate` | `COMPANY` | Company Management |
| **Roles & Permisos** | `GET` | `/api/v1/companies/{publicCompanyId}/effective-permissions/{userId}` | `effective_permissions.read` | `COMPANY` | Company Management |
| **Roles & Permisos** | `GET` | `/api/v1/companies/{publicCompanyId}/memberships/me/effective-permissions` | `effective_permissions.read_own` | `USER` | Company Management |
| **Operaciones** | `GET` | `/api/v1/operations/{operationId}` | `operations.read` | `USER` / `BRANCH` | Company Management |
| **Operaciones** | `GET` | `/api/v1/operations/{operationId}/history` | `operations.history.read` | `USER` / `BRANCH` | Company Management |
| **Operaciones** | `GET` | `/api/v1/operations/{operationId}/errors` | `operations.errors.read` | `USER` / `BRANCH` | Company Management |
| **Operaciones** | `POST` | `/api/v1/operations/{operationId}/cancellation` | `operations.cancel` | `USER` / `COMPANY` | Company Management |
| **Operaciones** | `POST` | `/api/v1/operations/{operationId}/retry` | `operations.retry` | `USER` / `COMPANY` | Company Management |
| **Operaciones** | `POST` | `/api/v1/operations/{operationId}/recovery` | `operations.recover` | `USER` / `COMPANY` | Company Management |
| **Operaciones** | `GET` | `/api/v1/operations/{operationId}/result` | `operations.result.read` | `USER` / `BRANCH` | Company Management |
| **Operaciones** | `GET` | `/api/v1/operations/{operationId}/result/download` | `operations.download` | `USER` / `BRANCH` | Company Management |
| **Import / Export / Files** | `POST` | `/api/v1/companies/{publicCompanyId}/imports/validation` | `imports.validate` | `COMPANY` | Company Management |
| **Import / Export / Files** | `POST` | `/api/v1/companies/{publicCompanyId}/imports` | `imports.execute` | `COMPANY` | Company Management |
| **Import / Export / Files** | `POST` | `/api/v1/companies/{publicCompanyId}/exports` | `exports.create` | `COMPANY` | Company Management |
| **Import / Export / Files** | `GET` | `/api/v1/companies/{publicCompanyId}/files/{fileId}` | `files.read` | `COMPANY` | Company Management |
| **Import / Export / Files** | `DELETE` | `/api/v1/companies/{publicCompanyId}/files/{fileId}` | `files.delete` | `COMPANY` | Company Management |
| **Import / Export / Files** | `GET` | `/api/v1/companies/{publicCompanyId}/files/{fileId}/download` | `files.download` | `COMPANY` | Company Management |
| **Auditoría** | `GET` | `/api/v1/companies/{publicCompanyId}/audit-records` | `audit.read` | `COMPANY` | Company Management |
| **Auditoría** | `GET` | `/api/v1/companies/{publicCompanyId}/audit-records/{auditRecordId}` | `audit.read` | `COMPANY` | Company Management |
| **Auditoría** | `POST` | `/api/v1/companies/{publicCompanyId}/audit-exports` | `audit.export` | `COMPANY` | Company Management |
| **Webhooks** | `GET` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions` | `webhooks.read` | `COMPANY` | Company Management |
| **Webhooks** | `POST` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions` | `webhooks.create` | `COMPANY` | Company Management |
| **Webhooks** | `GET` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}` | `webhooks.read` | `COMPANY` | Company Management |
| **Webhooks** | `PATCH` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}` | `webhooks.update` | `COMPANY` | Company Management |
| **Webhooks** | `DELETE` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}` | `webhooks.delete` | `COMPANY` | Company Management |
| **Webhooks** | `POST` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/test` | `webhooks.test` | `COMPANY` | Company Management |
| **Webhooks** | `POST` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/secret-rotation` | `webhooks.secret_rotate` | `COMPANY` | Company Management |
| **Webhooks** | `GET` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/deliveries` | `webhooks.deliveries.read` | `COMPANY` | Company Management |
| **Webhooks** | `POST` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/pause` | `webhooks.pause` | `COMPANY` | Company Management |
| **Webhooks** | `POST` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/resume` | `webhooks.resume` | `COMPANY` | Company Management |
| **Webhooks** | `POST` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/real-event-test` | `webhooks.test` | `COMPANY` | Company Management |
| **Webhooks** | `POST` | `/api/v1/companies/{publicCompanyId}/webhook-subscriptions/{subscriptionId}/deliveries/{deliveryId}/redelivery` | `webhooks.redeliver` | `COMPANY` | Company Management |
| **Billing Interno** | `GET` | `/internal/v1/companies/{publicCompanyId}/billing-capabilities` | `internal.billing.read` | `COMPANY` | Company Management |
| **Billing Interno** | `POST` | `/internal/v1/companies/{publicCompanyId}/billing-change-requests` | `internal.billing.manage` | `COMPANY` | Company Management |

---

## 3. Rutas y permisos de Sales

Contrato: [openapi-sales-v1.yaml](openapi-sales-v1.yaml); diseño: [diseno-modulo-ventas.md](diseno-modulo-ventas.md). Sales es propietario exclusivo de precios de venta, ventas, líneas, pagos y folios (esquema `sales`). Las rutas pueden cambiar de detalle hasta que cada etapa de BIT-059 en adelante las implemente.

| Dominio | Método | Ruta HTTP | Permiso Explícito (`x-required-permission`) | Alcance Mínimo | Módulo Propietario |
|---|:---:|---|---|:---:|:---:|
| **Precios** | `PUT` | `/api/v1/companies/{companyId}/products/{productId}/price` | `sales.prices.write` | `COMPANY` | Sales |
| **Precios** | `GET` | `/api/v1/companies/{companyId}/prices` | `sales.read` | `COMPANY` | Sales |
| **Ventas** | `POST` | `/api/v1/companies/{companyId}/branches/{branchId}/sales` | `sales.create` | `BRANCH` | Sales |
| **Ventas** | `GET` | `/api/v1/companies/{companyId}/branches/{branchId}/sales` | `sales.read` | `BRANCH` | Sales |
| **Ventas** | `GET` | `/api/v1/companies/{companyId}/branches/{branchId}/sales/{saleId}` | `sales.read` | `BRANCH` | Sales |
| **Ventas** | `POST` | `/api/v1/companies/{companyId}/branches/{branchId}/sales/{saleId}/cancellation` | `sales.cancel` | `BRANCH` | Sales |

Permisos por rol del sistema (los siembra la migración V031, etapa S1): `COMPANY_ADMIN` recibe `sales.*`; `BRANCH_MANAGER`, `sales.create`, `sales.read`, `sales.cancel` y `sales.prices.write`; `STAFF`, `sales.create` y `sales.read`. Para vender, Sales reenvía el token de quien vende a Inventory, cuyas rutas de reserva aceptan `inventory.reservations.write` (permiso nuevo, en S1) además de `inventory.stock.write`; `STAFF` y `BRANCH_MANAGER` lo reciben.
