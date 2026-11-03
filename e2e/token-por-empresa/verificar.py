#!/usr/bin/env python3
"""Prueba de punta a punta de ERR-011: el token que entrega company-management y lo que hace inventario con él.

Necesita las dos aplicaciones corriendo como procesos reales y compartiendo el secreto del JWT, y la base H2 de
company-management abierta con AUTO_SERVER=TRUE (para crear la membresía, que todavía no tiene ruta). Lo más cómodo es
lanzarla con ejecutar.sh, que construye, arranca, verifica y detiene todo.

Uso: python3 -I verificar.py <jar de h2> <url jdbc de la base de company-management> [url cm] [url inventario]
El secreto del JWT sale de SECURITY_JWT_SECRET, con el mismo valor por defecto que las aplicaciones.
"""
import base64
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

H2_JAR = sys.argv[1]
DB_URL = sys.argv[2]
CM = sys.argv[3] if len(sys.argv) > 3 else "http://localhost:18080"
INV = sys.argv[4] if len(sys.argv) > 4 else "http://localhost:18081"
SECRET = os.environ.get("SECURITY_JWT_SECRET", "local-only-change-me-012345678901234567890123456789")
PASSWORD = "ClaveSegura123!"
RUN = int(time.time()) % 100_000_000

resultados = []


def call(method, url, token=None, body=None, idempotent=False):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = token
    if idempotent:
        headers["Idempotency-Key"] = str(uuid.uuid4())
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read().decode()
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        try:
            return error.code, json.loads(raw) if raw else None
        except ValueError:
            return error.code, raw


def b64(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def claims(bearer):
    payload = bearer.split(" ", 1)[1].split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


def forge_old_union_token(subject, company_ids, branch_ids):
    """El token que se emitía antes de ERR-011: las empresas y los permisos de la persona, juntos. Ya no lo emite nadie."""
    now = int(time.time())
    header = b64(json.dumps({"alg": "HS256"}).encode())
    payload = b64(json.dumps({"sub": subject, "iss": "jcomercy", "iat": now, "exp": now + 3600, "permissions": "*",
                              "companyIds": company_ids, "branchIds": branch_ids}).encode())
    signature = b64(hmac.new(SECRET.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest())
    return f"Bearer {header}.{payload}.{signature}"


def check(descripcion, esperado, real, extra=""):
    ok = esperado(real) if callable(esperado) else real == esperado
    resultados.append(ok)
    marca = "OK   " if ok else "FALLA"
    mostrado = real if not isinstance(real, dict) else "(ver arriba)"
    print(f"  [{marca}] {descripcion} -> {mostrado}{(' ' + extra) if extra else ''}")
    return ok


def h2(sql):
    subprocess.run(["java", "-cp", H2_JAR, "org.h2.tools.Shell", "-url", DB_URL, "-user", "sa", "-password", "", "-sql", sql],
                   check=True, capture_output=True, text=True)


# ---- montaje en company-management ----

def register(label, index):
    n = RUN * 10 + index
    email = f"e2e-{label}-{n}@example.com"
    status, body = call("POST", f"{CM}/api/v1/accounts/registration", idempotent=True, body={
        "name": f"Cuenta {label} {n}",
        "owner": {"firstName": "Persona", "lastName": label, "birthDate": "1985-04-12", "rfc": f"ACC{n:010d}",
                  "phoneNumber": "5512345678", "email": email, "password": PASSWORD,
                  "state": "Ciudad de México", "address": "Av. Reforma 100"}})
    assert status == 201, (status, body)
    status, login = call("POST", f"{CM}/api/v1/auth/login", body={"email": email, "password": PASSWORD})
    assert status == 200, (status, login)
    return body["publicAccountId"], "Bearer " + login["accessToken"]


def create_company(account, token, index):
    n = RUN * 10 + index
    status, body = call("POST", f"{CM}/api/v1/accounts/{account}/companies", token, idempotent=True, body={
        "legalName": f"Empresa {n} S.A.", "tradeName": f"Empresa {n}", "rfc": f"RFC{n:010d}", "taxRegime": "601",
        "incorporationDate": "2020-01-15", "phone": "5512345678", "email": f"empresa-{n}@example.com",
        "website": "https://example.com", "logoUrl": "https://example.com/logo.png",
        "fiscalAddress": {"calle": "Reforma", "numeroExterior": "100", "numeroInterior": "10", "colonia": "Juárez",
                          "codigoPostal": "06600", "municipioAlcaldia": "Cuauhtémoc", "estado": "Ciudad de México",
                          "pais": "México"}})
    assert status == 201, (status, body)
    return body["publicCompanyId"]


def enter(account, company, token):
    status, body = call("POST", f"{CM}/api/v1/accounts/{account}/company-accesses/{company}/session", token)
    assert status == 200, (status, body)
    return "Bearer " + body["accessToken"]


def create_branch(company, token, index):
    n = RUN * 10 + index
    status, body = call("POST", f"{CM}/api/v1/companies/{company}/branches", token, idempotent=True, body={
        "code": f"SUC-{n}", "name": f"Sucursal {n}", "type": "MATRIZ", "phoneNumber": "5555555555",
        "email": f"sucursal-{n}@example.com", "rfc": f"SUC{n:010d}", "fiscalName": f"Sucursal Fiscal {n}",
        "fiscalPostalCode": "06000", "centroCostoId": f"cc_{n}", "almacenId": f"alm_{n}", "listaPreciosId": "lp_retail",
        "serieFacturacion": "SERIE", "folioActual": 1, "moneda": "MXN", "responsableId": "usr_resp",
        "fechaApertura": "2026-01-01",
        "domicilio": {"calle": "Juárez", "numeroExterior": "50", "numeroInterior": "", "colonia": "Centro",
                      "codigoPostal": "06000", "municipioAlcaldia": "Cuauhtémoc", "estado": "Ciudad de México",
                      "pais": "México"}})
    assert status == 201, (status, body)
    return body["publicBranchId"]


def prepare_company(account, account_token, index):
    """Crea la empresa, su sucursal activa y devuelve el token de la dueña dentro de ella, ya con esa sucursal."""
    company = create_company(account, account_token, index)
    first_token = enter(account, company, account_token)
    branch = create_branch(company, first_token, index)
    for path in (f"/api/v1/companies/{company}/activation", f"/api/v1/companies/{company}/branches/{branch}/activation"):
        status, body = call("POST", f"{CM}{path}", first_token, idempotent=True)
        assert status == 200, (path, status, body)
    # La sucursal se activó después de entrar: se entra de nuevo para que el token la lleve.
    return company, branch, enter(account, company, account_token)


# ---- llamadas a inventario ----

def stock(company, token):
    return call("GET", f"{INV}/api/v1/companies/{company}/stock/balances", token)[0]


def create_warehouse(company, branch, token, tag, default=True):
    # Una sucursal solo admite un almacén predeterminado: quien crea uno más debe pedirlo no predeterminado, o un 409
    # de esa regla de negocio se confundiría con lo que aquí se prueba, que son los permisos.
    return call("POST", f"{INV}/api/v1/companies/{company}/branches/{branch}/warehouses", token, idempotent=True, body={
        "code": f"ALM-{RUN}-{tag}", "name": f"Almacén {tag}", "type": "GENERAL", "defaultWarehouse": default,
        "timezone": "America/Mexico_City", "currency": "MXN"})[0]


def adjust(company, branch, token):
    return call("POST", f"{INV}/api/v1/companies/{company}/branches/{branch}/stock/adjustments", token, idempotent=True, body={
        "warehouseId": str(uuid.uuid4()), "locationId": str(uuid.uuid4()), "productId": str(uuid.uuid4()),
        "quantity": 1, "unitCode": "PZA", "reason": "Prueba de punta a punta", "direction": "OUT"})[0]


print("== Montaje en company-management ==")
alice, alice_login = register("alice", 1)
bob, bob_login = register("bob", 2)
company_a, branch_a, alice_in_a = prepare_company(alice, alice_login, 3)
company_b, branch_b, bob_in_b = prepare_company(bob, bob_login, 4)
print(f"  Alice {alice} es dueña de la empresa A {company_a} (sucursal {branch_a})")
print(f"  Bob {bob} es dueño de la empresa B {company_b} (sucursal {branch_b})")

# Alice pasa a ser personal de la empresa B y se le asigna su sucursal: así, lo único que le falta es el permiso.
membership = f"mem_e2e_{RUN}"
h2(f"INSERT INTO company_management.company_membership (id, public_membership_id, public_company_id, user_id, status, "
   f"roles_json, created_at, updated_at, version) VALUES (RANDOM_UUID(), '{membership}', '{company_b}', '{alice}', "
   f"'ACTIVE', '[\"STAFF\"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)")
h2(f"INSERT INTO company_management.branch_assignment (id, public_assignment_id, public_membership_id, public_branch_id, "
   f"status, created_at, updated_at, version) VALUES (RANDOM_UUID(), 'asg_e2e_{RUN}', '{membership}', '{branch_b}', "
   f"'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)")
alice_in_b = enter(alice, company_b, alice_login)
print("  Alice es además personal (STAFF) de la empresa B, con su sucursal asignada")

print("\n== Lo que lleva cada token ==")
login_claims, a_claims, b_claims = claims(alice_login), claims(alice_in_a), claims(alice_in_b)
print(f"  token del inicio de sesión: companyIds={login_claims['companyIds']} permissions={login_claims['permissions']!r} ctx={login_claims.get('ctx')}")
print(f"  token de Alice en A: ctx={a_claims.get('ctx')} companyIds={a_claims['companyIds']} permissions={a_claims['permissions']!r}")
print(f"  token de Alice en B: ctx={b_claims.get('ctx')} companyIds={b_claims['companyIds']} permissions={b_claims['permissions']!r}")
check("el token del inicio de sesión no nombra empresas ni permisos",
      lambda c: c["companyIds"] == [] and c["permissions"] == "" and "ctx" not in c, login_claims)
check("el token de Alice en A nombra solo A y lleva *", lambda c: c["companyIds"] == [company_a] and c["permissions"] == "*", a_claims)
check("el token de Alice en B nombra solo B y NO lleva *", lambda c: c["companyIds"] == [company_b] and "*" not in c["permissions"].split(), b_claims)

print("\n== Inventario con cada token ==")
print(" El token del inicio de sesión no abre inventario")
check("consultar el stock de A con el token de cuenta", 403, stock(company_a, alice_login))
check("crear un almacén en A con el token de cuenta", 403, create_warehouse(company_a, branch_a, alice_login, "cuenta"))
print(" El token de A abre A y solo A")
check("consultar el stock de A con el token de A", 200, stock(company_a, alice_in_a))
check("crear un almacén en A con el token de A (dueña: *)", 201, create_warehouse(company_a, branch_a, alice_in_a, "a"))
check("consultar el stock de B con el token de A", 403, stock(company_b, alice_in_a))
check("ajustar stock en B con el token de A", 403, adjust(company_b, branch_b, alice_in_a))
print(" ERR-011: dueña de A y personal de B, en B solo puede lo que permite su rol")
check("consultar el stock de B con el token de B (STAFF: inventory.stock.read)", 200, stock(company_b, alice_in_b))
check("crear un almacén en B con el token de B (STAFF no administra almacenes)", 403, create_warehouse(company_b, branch_b, alice_in_b, "b-staff"))
check("ajustar stock en B con el token de B (STAFF no ajusta)", 403, adjust(company_b, branch_b, alice_in_b))
print(" Control: el dueño de B sí puede, así que los 403 anteriores son por el rol y no por el montaje")
check("crear un almacén en B con el token de Bob (dueño: *)", 201, create_warehouse(company_b, branch_b, bob_in_b, "b-dueno"))
check("ajustar stock en B con el token de Bob pasa el control de permisos (no es 403)", lambda s: s != 403,
      adjust(company_b, branch_b, bob_in_b), "(falla después por ser datos inventados)")

print("\n== Contraste: el token que se emitía antes de ERR-011 ==")
print(" Ya no lo emite company-management; se fabrica aquí solo para ver qué hacía inventario con él.")
old = forge_old_union_token(alice, [company_a, company_b], [branch_a, branch_b])
check("con el token antiguo (* y las dos empresas), Alice crea un almacén en B, donde solo es personal", 201,
      create_warehouse(company_b, branch_b, old, "b-antiguo", default=False))
check("con el token antiguo, Alice pasa el control de permisos para ajustar stock en B", lambda s: s != 403,
      adjust(company_b, branch_b, old))

print("\n== Límite conocido: una membresía suspendida ==")
h2(f"UPDATE company_management.company_membership SET status='SUSPENDED' WHERE public_membership_id='{membership}'")
status, _ = call("POST", f"{CM}/api/v1/accounts/{alice}/company-accesses/{company_b}/session", alice_login)
check("company-management ya no deja entrar a B a Alice", 403, status)
check("inventario sigue aceptando el token de B que Alice ya tenía, hasta que expire", 200, stock(company_b, alice_in_b))

print()
total, ok = len(resultados), sum(resultados)
print(f"RESULTADO: {ok} de {total} verificaciones correctas")
sys.exit(0 if ok == total else 1)
