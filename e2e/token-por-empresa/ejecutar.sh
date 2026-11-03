#!/usr/bin/env bash
# Prueba de punta a punta de ERR-011 (token por empresa): construye las dos aplicaciones, las arranca como procesos
# reales, corre verificar.py contra ellas y las detiene al terminar, haya pasado o no.
#
# Uso: e2e/token-por-empresa/ejecutar.sh [--sin-construir]
#   --sin-construir  reutiliza los .jar de target/ en vez de volver a construirlos
# Variables: CM_PORT (18080) e INV_PORT (18081) para los puertos; SECURITY_JWT_SECRET si no se usa el secreto local.
# Necesita Java 21, Maven y python3. No usa RabbitMQ: los avisos de conexión rehusada en los registros son ruido.
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$AQUI/../.." && pwd)"
CM_PORT="${CM_PORT:-18080}"
INV_PORT="${INV_PORT:-18081}"
TMP="$(mktemp -d)"
PIDS=()

limpiar() {
    for pid in "${PIDS[@]:-}"; do
        [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
    done
    wait 2>/dev/null || true
    rm -rf "$TMP"
}
trap limpiar EXIT

for puerto in "$CM_PORT" "$INV_PORT"; do
    if ss -ltn 2>/dev/null | grep -q ":$puerto\b"; then
        echo "El puerto $puerto ya está en uso. Libéralo o elige otro con CM_PORT / INV_PORT." >&2
        exit 2
    fi
done

if [ "${1:-}" != "--sin-construir" ]; then
    echo "== Construyendo las dos aplicaciones =="
    (cd "$RAIZ" && mvn -B -ntp -q package -DskipTests)
fi

CM_JAR="$(ls "$RAIZ"/company-management/company-management-infra/target/*-SNAPSHOT.jar | head -1)"
INV_JAR="$(ls "$RAIZ"/inventory/inventory-infra/target/*-SNAPSHOT.jar | head -1)"

# El cliente de H2 que crea la membresía debe ser la misma versión que usa la aplicación: se saca de su propio .jar.
python3 -I - "$CM_JAR" "$TMP/h2.jar" <<'PY'
import re, sys, zipfile
jar, destino = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(jar) as z:
    nombre = next(n for n in z.namelist() if re.fullmatch(r"BOOT-INF/lib/h2-[\d.]+\.jar", n))
    open(destino, "wb").write(z.read(nombre))
PY

DB_URL="jdbc:h2:file:$TMP/cm-db;MODE=PostgreSQL;AUTO_SERVER=TRUE"

echo "== Arrancando company-management ($CM_PORT) e inventario ($INV_PORT) =="
SERVER_PORT="$CM_PORT" SPRING_DATASOURCE_URL="$DB_URL" java -jar "$CM_JAR" > "$TMP/cm.log" 2>&1 &
PIDS+=("$!")
SERVER_PORT="$INV_PORT" java -jar "$INV_JAR" > "$TMP/inv.log" 2>&1 &
PIDS+=("$!")

for _ in $(seq 1 60); do
    if grep -q 'Started CompanyManagementApplication' "$TMP/cm.log" && grep -q 'Started InventoryApplication' "$TMP/inv.log"; then
        break
    fi
    for pid in "${PIDS[@]}"; do
        if ! kill -0 "$pid" 2>/dev/null; then
            echo "Una aplicación se detuvo al arrancar." >&2
            tail -n 30 "$TMP/cm.log" "$TMP/inv.log" >&2
            exit 3
        fi
    done
    sleep 3
done
if ! grep -q 'Started CompanyManagementApplication' "$TMP/cm.log" || ! grep -q 'Started InventoryApplication' "$TMP/inv.log"; then
    echo "Las aplicaciones no arrancaron a tiempo." >&2
    tail -n 30 "$TMP/cm.log" "$TMP/inv.log" >&2
    exit 3
fi

echo "== Verificando =="
if python3 -I "$AQUI/verificar.py" "$TMP/h2.jar" "$DB_URL" "http://localhost:$CM_PORT" "http://localhost:$INV_PORT"; then
    exit 0
fi
echo "La verificación falló. Últimas líneas de los registros:" >&2
tail -n 20 "$TMP/cm.log" "$TMP/inv.log" >&2
exit 1
