#!/usr/bin/env python3
"""
Genera el perfil `todo` del compose: los catorce servicios y sus dependencias.

    python3 scripts/generar_compose.py

Lee
    servicios/*/descriptor.yml               que servicios hay
    servicios/*/src/main/resources/*.yml     que variables exige cada uno
Escribe
    despliegue/compose/servicios.yml         un servicio por carpeta, perfil `todo`

La salida es DERIVADA y no se edita a mano. Catorce bloques escritos a mano
divergen, y la divergencia aparece cuando alguien levanta el stack completo y un
servicio no arranca porque le falta una variable que los otros trece si tienen.

Las variables que cada servicio exige NO se inventan: salen de leer su
`application.yml` y buscar los `${...}`. Si alguien agrega una clave nueva y no la
declara aca, este script la incluye sola en la proxima corrida.

Ningun servicio publica puerto: la unica entrada publica sigue siendo NGINX
(ADR-025). Y ninguno arranca antes de que la base este lista y migrada.
"""

import pathlib
import re
import sys

RAIZ = pathlib.Path(__file__).resolve().parent.parent
SERVICIOS = RAIZ / "servicios"
SALIDA = RAIZ / "despliegue/compose/servicios.yml"

# Lo que vale igual para los catorce. Un valor por variable, y aca se ve entero.
COMUNES = {
    "BD_URL": "jdbc:postgresql://pgbouncer:6432/pasanaku",
    "BD_CLAVE": "pasanaku",
    "KAFKA_URL": "kafka:9092",
    "JWKS_URI": "http://identidad:8080/.well-known/jwks.json",
}

# Secretos y datos del entorno. En el compose local son valores de desarrollo y
# estan a la vista a proposito: lo que NO puede pasar es que en produccion salgan
# de aca. Ahi los pone el almacen de secretos, y el servicio no levanta sin ellos.
DE_DESARROLLO = {
    "SEGURIDAD_PIMIENTA": "pimienta-local-no-es-la-de-produccion",
    "WEBHOOK_SECRETO": "secreto-local-no-es-el-de-produccion",
    "CERTIFICADOS_CLAVE_FIRMA": "clave-local-no-es-la-de-produccion",
    "CUENTA_PUENTE_CUSTODIA": "00000000-0000-0000-0000-0000000000c0",
    "BASE_URL_PUBLICA": "http://localhost",
    "SIN_NIT_EMISOR": "1234567890",
}

CABECERA = """# Perfil `todo`: los catorce servicios, GENERADO por scripts/generar_compose.py.
#
#   docker compose -f despliegue/compose/base.yml -f despliegue/compose/servicios.yml \\
#     --profile todo up -d --wait
#
# NO se edita a mano. Un bloque distinto de los otros trece es una divergencia que
# aparece recien cuando alguien levanta el stack entero (ADR-025).
#
# Ninguno publica puerto: la unica entrada publica es NGINX. Y ninguno arranca antes
# de que la base este lista — el orden del despliegue no es negociable.
name: aportaya

# La red la declara base.yml, que es el archivo que siempre se combina con este.
# Repetirla aca con `external: true` haria que un `up` de este solo no la creara.
networks:
  interna:
    name: aportaya-interna

services:
"""

BLOQUE = """  {nombre}:
    build:
      context: ../..
      dockerfile: despliegue/Dockerfile
      args:
        SERVICIO: {nombre}
    image: aportaya/{nombre}:local
    container_name: aportaya-{nombre}
    profiles: [todo]
    networks: [interna]
    environment:
{ambiente}
    healthcheck:
      test: ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1"]
      interval: 10s
      timeout: 3s
      retries: 18
      start_period: 60s
"""


def variables_de(servicio):
    """Las variables que este servicio exige, leidas de su propia configuracion."""
    config = SERVICIOS / servicio / "src/main/resources/application.yml"
    if not config.is_file():
        return []
    texto = config.read_text(encoding="utf-8")
    return sorted(set(re.findall(r"\$\{([A-Z_]+)[:}]", texto)))


def main():
    servicios = sorted(
        d.name for d in SERVICIOS.iterdir() if (d / "descriptor.yml").is_file()
    )
    if not servicios:
        print("no hay servicios con descriptor: nada que generar")
        return 1

    sin_valor = []
    bloques = []
    for servicio in servicios:
        lineas = []
        for variable in variables_de(servicio):
            valor = COMUNES.get(variable) or DE_DESARROLLO.get(variable)
            if valor is None:
                sin_valor.append(f"{servicio}: {variable}")
                continue
            lineas.append(f"      {variable}: {valor}")
        # El perfil `local` enciende el simulador de pagos y la mensajeria simulada,
        # que son los defaults ya elegidos por el contrato de implementacion.
        lineas.append("      SPRING_PROFILES_ACTIVE: local")
        bloques.append(BLOQUE.format(nombre=servicio, ambiente="\n".join(lineas)))

    if sin_valor:
        print("Variables que ningun valor cubre; agregalas al script antes de generar:")
        for falta in sin_valor:
            print(f"  {falta}")
        return 1

    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    SALIDA.write_text(CABECERA + "\n".join(bloques), encoding="utf-8")
    print(f"compose del perfil `todo`: {len(servicios)} servicios -> {SALIDA.relative_to(RAIZ)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
