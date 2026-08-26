#!/usr/bin/env python3
"""
Crea un servicio completo. Nadie escribe la estructura a mano.

    python3 scripts/nuevo_servicio.py tarifas
    python3 scripts/nuevo_servicio.py --todos       (los catorce)

Es la diferencia entre catorce servicios iguales y catorce servicios parecidos.
Con desplegables separados la variacion no aparece en ningun diff: se descubre en
produccion.

Genera, para <servicio>:
    build.gradle.kts                    dependencias, del catalogo de versiones
    descriptor.yml                      replicas, recursos, sondas -> genera el k8s
    README.md                           que resuelve, sus CU, eventos, trabajos
    src/main/resources/application.yml  su configuracion; no hay archivo compartido
    src/main/resources/openapi/<s>.yaml su contrato, esqueleto
    src/main/java/<pkg>/{dominio,infraestructura,aplicacion,web,trabajos}/
    src/test/java/<pkg>/ArquitecturaTest.java   ArchUnit: la direccion de dependencia
"""
import argparse
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from modelo import (ESQUEMA, ESQUEMA_CATALOGO, ESQUEMA_COMUN, MODULOS,  # noqa: E402
                    PREFIJOS, RUTAS_PUBLICAS, paquete_de, rol_de, servicio_de)

R = pathlib.Path(__file__).resolve().parent.parent
DEST = R / "servicios"

# Pool por servicio: los de dinero aguantan mas carga. La SUMA no puede superar
# max_connections, y el arranque lo advierte (ADR-021).
POOL = {"nucleo-financiero": 20, "aportes": 20, "identidad": 20}
REPLICAS = {"nucleo-financiero": 3, "aportes": 3, "identidad": 3}


def modulo_de(esquema):
    for k, v in ESQUEMA.items():
        if v == esquema:
            return k, MODULOS[k][0]
    return "--", "sin modulo"


def build_gradle(servicio, esquema):
    return f"""// {servicio} — generado por scripts/nuevo_servicio.py
// Las versiones salen del catalogo (gradle/libs.versions.toml): una dependencia
// nueva es un micro-PR al troncal, nunca un cambio en una rama de carril.
plugins {{
    id("aportaya.servicio")          // convencion: toolchain 21, spotless, test, docker
    id("aportaya.jooq")              // genera SOLO el esquema de este servicio
    id("aportaya.openapi")           // interfaz de servidor + clientes
}}

aportaya {{
    esquema.set("{esquema}")
    rol.set("{rol_de(esquema)}")
}}

dependencies {{
    implementation(project(":plataforma:comun-dominio"))
    implementation(project(":plataforma:comun-datos"))
    implementation(project(":plataforma:comun-web"))
    implementation(project(":plataforma:comun-mensajeria"))

    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.validation)
    implementation(libs.jooq)
    implementation(libs.kafka)
    implementation(libs.shedlock)
    implementation(libs.resilience4j)
    implementation(libs.micrometer)

    testImplementation(project(":plataforma:comun-pruebas"))
    testImplementation(libs.bundles.pruebas)   // JUnit 5, AssertJ, Testcontainers, ArchUnit
}}

// JPA esta PROHIBIDO (ADR-016): compite con sql/ por la propiedad del esquema y
// su dirty checking es incompatible con append-only. La convencion falla el build
// si alguien lo agrega, pero dejarlo escrito acá ahorra la discusion.
"""


def application_yml(servicio, esquema):
    pool = POOL.get(servicio, 5)
    return f"""# {servicio} — generado por scripts/nuevo_servicio.py
# Si falta una clave, el proceso NO levanta y dice cual.
spring:
  application:
    name: {servicio}
  threads:
    virtual:
      enabled: true                 # ADR-015: la carga es de E/S, no reactiva
  datasource:
    url: ${{BD_URL}}                  # por PgBouncer, modo transaccion
    username: {rol_de(esquema)}
    password: ${{BD_CLAVE}}
    hikari:
      maximum-pool-size: {pool}     # la SUMA de los catorce no supera max_connections
      data-source-properties:
        prepareThreshold: 0         # PgBouncer en modo transaccion
  kafka:
    bootstrap-servers: ${{KAFKA_URL}}

aportaya:
  esquema: {esquema}
  outbox:
    intervalo: PT1S                 # relevo: lee el outbox y publica DESPUES del commit
  jwt:
    jwks-uri: ${{JWKS_URI}}           # ADR-024: cada servicio valida la firma el mismo
  zona-horaria: America/La_Paz      # plazos habiles

management:
  endpoint:
    health:
      probes:
        enabled: true               # readiness mira base y Kafka; liveness solo el proceso
"""


def openapi(servicio, esquema):
    pref = PREFIJOS.get(esquema, [f"/{servicio}"])
    publicas = [p for p in pref if p in RUTAS_PUBLICAS]
    nota = ("\n#   Estas rutas son las UNICAS sin sesion de todo el sistema: "
            + ", ".join(publicas) if publicas else
            "\n#   Toda ruta exige sesion: 401 sin token, 403 con rol insuficiente.")
    return f"""# Contrato de {servicio} — OpenAPI 3.1
# SE ESCRIBE PRIMERO, antes que la implementacion (ADR-020). Es lo que permite que
# otro carril genere el cliente y programe contra vos sin esperar a que termines.
#
# Prefijos reservados de este servicio: {', '.join(pref)}
#   Una ruta fuera de ellos es un rechazo automatico, no una discusion de diseno.{nota}
openapi: 3.1.0
info:
  title: {servicio}
  version: 1.0.0
  description: >-
    Servicio {servicio}. Un caso de uso = una operacion, con el codigo CU en el
    operationId. Los importes viajan como CADENA decimal, nunca como number.

servers:
  - url: /api/v1

paths: {{}}
  # Una operacion por caso de uso. Plantilla:
  #
  #   {pref[0]}/…:
  #     post:
  #       operationId: <verboObjeto>          # CU-NN
  #       parameters:
  #         - name: Idempotency-Key
  #           in: header
  #           required: true                   # toda operacion con efecto
  #           schema: {{ type: string, format: uuid }}
  #       responses:
  #         '200': …
  #         '422': {{ $ref: '#/components/responses/ReglaDeNegocio' }}

components:
  schemas:
    Dinero:
      type: object
      additionalProperties: false
      required: [monto, moneda]
      properties:
        monto:
          type: string
          pattern: '^-?\\d+\\.\\d{{2}}$'     # CADENA: un number JSON es un doble
        moneda:
          type: string
          enum: [BOB, USD]
    Error:
      type: object
      additionalProperties: false
      required: [codigo, mensaje, trazaId]
      properties:
        codigo:   {{ type: string, pattern: '^AP-CU\\d+-\\d{{2}}$' }}
        mensaje:  {{ type: string }}
        detalle:  {{ type: object }}
        trazaId:  {{ type: string }}
  responses:
    ReglaDeNegocio:
      description: Regla de negocio de la aplicacion (422, no 400)
      content:
        application/json:
          schema: {{ $ref: '#/components/schemas/Error' }}
"""


def descriptor(servicio):
    return f"""# Despliegue de {servicio} — genera el manifiesto de Kubernetes.
# Los manifiestos NO se escriben a mano: catorce copias divergen y la divergencia
# se descubre en produccion (ADR-025).
servicio: {servicio}
replicas: {REPLICAS.get(servicio, 1)}
recursos:
  memoria: 512Mi                 # presupuesto de ADR-025
  cpu: 500m
sondas:
  readiness: /actuator/health/readiness   # mira base y Kafka
  liveness:  /actuator/health/liveness    # solo el proceso
despliegue:
  estrategia: RollingUpdate
  maxUnavailable: {0 if servicio in REPLICAS else 1}
puerto_publicado: false          # solo el gateway publica puerto
"""


def arquitectura_test(servicio, pkg):
    return f"""package {pkg};

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * La direccion de dependencia no se pide: se verifica (ADR-023).
 *
 * Lo que en una configuracion de lint se eludia con un import creativo, aca es
 * una prueba que falla — y cubre las clases que se escriban dentro de seis
 * semanas, cuando ya nadie se acuerde de esta lista.
 */
@AnalyzeClasses(packages = "{pkg}")
class ArquitecturaTest {{

    @ArchTest
    static final ArchRule elDominioEsPuro =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..infraestructura..", "..web..",
                "org.springframework..", "org.jooq..");

    @ArchTest
    static final ArchRule laTransaccionVivEnElOrganismo =
        noClasses().that().resideOutsideOfPackage("..aplicacion..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");

    // Invariante 11: se depende del propio servicio y de plataforma/. De ningun
    // otro. El predicado va COMPUESTO y no en dos `should` encadenados: encadenados,
    // java.lang.Object alcanza para que cualquier clase viole la regla.
    @ArchTest
    static final ArchRule ningunImportCruzado =
        noClasses().that().resideInAPackage("{pkg}..")
            .should().dependOnClassesThat(
                resideInAPackage("bo.aportaya..")
                    .and(resideOutsideOfPackages("{pkg}..", "bo.aportaya.plataforma..")));

    @ArchTest
    static final ArchRule jpaProhibido =
        noClasses().should().dependOnClassesThat().resideInAPackage("jakarta.persistence..");

    @ArchTest
    static final ArchRule laWebNoTieneReglas =
        noClasses().that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("..infraestructura..");
}}
"""


def readme(servicio, esquema):
    k, nombre = modulo_de(esquema)
    pref = PREFIJOS.get(esquema, [])
    return f"""# Servicio `{servicio}`

Modulo {k} de la boveda — {nombre}.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `{esquema}` |
| **Rol de base** | `{rol_de(esquema)}` |
| **Prefijos de ruta** | {' · '.join(f'`{p}`' for p in pref) or '—'} |
| **Contrato** | [`openapi/{servicio}.yaml`](src/main/resources/openapi/{servicio}.yaml) |
| **Paquete** | `{paquete_de(servicio)}` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| | *(los llena `nuevo_cu.py`)* | |

## Eventos que emite

| Tema | Cuando |
| --- | --- |
| | |

## Eventos que consume

| Tema | De quien | Efecto |
| --- | --- | --- |
| | | |

## Trabajos programados

| Bloqueo | Cron | Que hace |
| --- | --- | --- |
| | | |

## Lo que este servicio NO puede hacer

- Leer el esquema de otro servicio. No tiene `GRANT`, y jOOQ no le genero las
  clases (invariante 11).
- Escribir el libro contable, salvo que sea `nucleo-financiero` (invariante 12).
- Usar JPA (ADR-016).
- Publicar a Kafka dentro de una transaccion: para eso esta el outbox (ADR-018).

## Como se trabaja acá

```bash
docker compose --profile base up -d --wait
./gradlew :servicios:{servicio}:generateJooq
./gradlew :servicios:{servicio}:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
"""



def aplicacion_java(servicio, pkg):
    clase = "Aplicacion"
    return f"""package {pkg};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * El punto de arranque de {servicio}. No tiene logica: si aparece un if sobre una
 * regla del pasanaku aca, esta mal ubicado — va a aplicacion/.
 *
 * La configuracion se valida al arrancar: si falta una clave, el proceso NO levanta
 * y dice cual (planes/01 §0.7).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class {clase} {{

    public static void main(String[] argumentos) {{
        SpringApplication.run({clase}.class, argumentos);
    }}
}}
"""



def barrido_test(servicio, pkg):
    return f"""package {pkg};

import bo.aportaya.plataforma.pruebas.barrido.Barrido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las reglas propias de planes/00 §6 aplicadas a las fuentes de este servicio.
 *
 * <p>La implementacion vive en plataforma/comun-pruebas: ningun servicio puede
 * desactivarlas, y el que agrega la regla numero trece la agrega una sola vez.
 */
class BarridoTest {{

    private final Barrido barrido = Barrido.delModulo();

    @Test
    @DisplayName("tamano-archivo: ningun archivo llega a 300 lineas")
    void ningunArchivoBloquea() {{
        barrido.ningunArchivoBloquea();
    }}

    @Test
    @DisplayName("sin-umbral-literal: ninguna cifra regulatoria dentro del codigo")
    void ningunUmbralEnElCodigo() {{
        barrido.ningunUmbralEnElCodigo();
    }}
}}
"""


def crear(servicio, forzar=False):
    esquema = servicio.replace("-", "_")
    if esquema not in set(ESQUEMA.values()):
        print(f"  ERROR: '{servicio}' no es uno de los catorce servicios")
        return 1
    base = DEST / servicio
    if base.exists() and not forzar:
        print(f"  omitido (ya existe): {servicio}")
        return 0

    pkg = paquete_de(servicio)
    ruta_pkg = pkg.replace(".", "/")

    for capa in ("dominio", "infraestructura", "aplicacion", "web", "trabajos"):
        d = base / "src/main/java" / ruta_pkg / capa
        d.mkdir(parents=True, exist_ok=True)
        (d / ".gitkeep").write_text("", encoding="utf-8")
    (base / "src/test/java" / ruta_pkg).mkdir(parents=True, exist_ok=True)
    (base / "src/test/resources/fixtures").mkdir(parents=True, exist_ok=True)
    (base / "src/test/resources/fixtures/.gitkeep").write_text("", encoding="utf-8")
    (base / "src/main/resources/openapi").mkdir(parents=True, exist_ok=True)

    escribir = {
        base / "build.gradle.kts": build_gradle(servicio, esquema),
        base / "descriptor.yml": descriptor(servicio),
        base / "README.md": readme(servicio, esquema),
        base / "src/main/resources/application.yml": application_yml(servicio, esquema),
        base / f"src/main/resources/openapi/{servicio}.yaml": openapi(servicio, esquema),
        base / "src/main/java" / ruta_pkg / "Aplicacion.java": aplicacion_java(servicio, pkg),
        base / "src/test/java" / ruta_pkg / "ArquitecturaTest.java": arquitectura_test(servicio, pkg),
        base / "src/test/java" / ruta_pkg / "BarridoTest.java": barrido_test(servicio, pkg),
    }
    for ruta, contenido in escribir.items():
        ruta.write_text(contenido, encoding="utf-8")
    print(f"  creado: servicios/{servicio}/  ({esquema} · {rol_de(esquema)} · {pkg})")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("servicio", nargs="?", help="nombre en kebab-case")
    ap.add_argument("--todos", action="store_true", help="crea los catorce")
    ap.add_argument("--forzar", action="store_true", help="sobrescribe si existe")
    a = ap.parse_args()

    if not a.servicio and not a.todos:
        ap.error("indica un servicio o --todos")

    objetivo = ([servicio_de(e) for e in sorted(set(ESQUEMA.values()))]
                if a.todos else [a.servicio])
    rc = 0
    for s in objetivo:
        rc |= crear(s, a.forzar)
    return rc


if __name__ == "__main__":
    raise SystemExit(main())
