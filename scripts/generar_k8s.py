#!/usr/bin/env python3
"""
Genera los manifiestos de Kubernetes desde los descriptores de servicio.

    python3 scripts/generar_k8s.py

Lee
    despliegue/infra.yml              niveles, conexiones, entrada, entornos
    servicios/*/descriptor.yml        el nivel y el tope de cada servicio
Escribe
    despliegue/k8s/generado/<entorno>/   Namespace, Deployment, Service, PDB, HPA,
                                        NetworkPolicy, Job de migración y el gateway

La salida NO se versiona (.gitignore): el gate es que este script corra sin
errores, no un diff. Lo que se revisa son los descriptores y `infra.yml`.

Los manifiestos son DERIVADOS: no se editan a mano. Catorce copias divergen y la
divergencia se descubre en producción (ADR-025).

Antes de escribir una línea, VALIDA las reglas duras de ADR-037. Si alguna no
cierra, no genera nada y explica cuál:

  1 · ningún servicio con menos de 2 réplicas
  2 · las réplicas y el máximo son los de su nivel
  3 · Σ (replicas_max × pool) cabe en PgBouncer
  4 · el pool de PgBouncer cabe en max_connections menos el margen
  5 · un HPA sin tope no existe
  6 · la antiafinidad `required` necesita al menos 2 zonas declaradas
"""

import math
import pathlib
import sys

# Estos informes se imprimen con acentos y flechas. En Windows la consola entrega
# stdout en cp1252 y el generador muere con UnicodeEncodeError despues de haber
# escrito los archivos — en tres de las cinco maquinas del parque.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


try:
    import yaml
except ImportError:  # pragma: no cover
    sys.exit("Falta PyYAML:  python3 -m pip install pyyaml")

RAIZ = pathlib.Path(__file__).resolve().parent.parent
INFRA = RAIZ / "despliegue" / "infra.yml"
SERVICIOS = RAIZ / "servicios"
DESTINO = RAIZ / "despliegue" / "k8s" / "generado"

CABECERA = ("# GENERADO por scripts/generar_k8s.py desde despliegue/infra.yml y\n"
            "# servicios/*/descriptor.yml — no editar a mano (ADR-025, ADR-037).\n")


# ── lectura ────────────────────────────────────────────────────────────────

def cargar():
    infra = yaml.safe_load(INFRA.read_text(encoding="utf-8"))
    servicios = []
    for ruta in sorted(SERVICIOS.glob("*/descriptor.yml")):
        d = yaml.safe_load(ruta.read_text(encoding="utf-8"))
        d["_ruta"] = ruta.relative_to(RAIZ)
        servicios.append(d)
    return infra, servicios


# ── validación — lo que hace que este script sirva de algo ─────────────────

def validar(infra, servicios):
    errores = []
    niveles = infra["niveles"]

    for d in servicios:
        nombre, ruta = d.get("servicio", "?"), d["_ruta"]
        nivel = d.get("nivel")
        if nivel not in niveles:
            errores.append(f"{ruta}: nivel '{nivel}' no existe (son {', '.join(niveles)})")
            continue
        n = niveles[nivel]
        rmin = d.get("replicas", {}).get("min")
        rmax = d.get("replicas", {}).get("max")

        if not isinstance(rmin, int) or rmin < 2:
            errores.append(
                f"{ruta}: replicas.min = {rmin}. Ningún servicio con menos de 2:"
                f" una réplica es una ventana de caída garantizada en cada despliegue")
        elif rmin < n["replicas_min"]:
            errores.append(f"{ruta}: {nivel} exige al menos {n['replicas_min']} réplicas, declara {rmin}")

        if not isinstance(rmax, int):
            errores.append(f"{ruta}: falta replicas.max. Un HPA sin tope agota PostgreSQL")
        else:
            if rmax < rmin:
                errores.append(f"{ruta}: replicas.max ({rmax}) menor que replicas.min ({rmin})")
            if rmax > n["replicas_max"]:
                errores.append(
                    f"{ruta}: replicas.max = {rmax} supera el tope de {nivel}"
                    f" ({n['replicas_max']}). Subirlo exige recalcular el pool")

        if not d.get("pool", {}).get("hikari_por_replica"):
            errores.append(f"{ruta}: falta pool.hikari_por_replica")

        if not d.get("nivel_porque"):
            errores.append(f"{ruta}: falta nivel_porque. El nivel se justifica, no se elige")

    if errores:
        return errores

    # Regla 3 — el techo real: conexiones de cliente contra el pooler.
    pgb = infra["base_de_datos"]["pgbouncer"]
    demanda = sum(d["replicas"]["max"] * d["pool"]["hikari_por_replica"] for d in servicios)
    if demanda > pgb["max_client_conn"]:
        errores.append(
            f"conexiones: los servicios pueden pedir {demanda} conexiones al escalar al"
            f" máximo y PgBouncer acepta {pgb['max_client_conn']}. O baja replicas.max,"
            f" o baja el pool, o sube max_client_conn con evidencia de que la máquina lo aguanta")

    # Regla 4 — y el pooler contra la base.
    pg = infra["base_de_datos"]["postgres"]
    servidor = pgb["default_pool_size"] * pgb["pools_esperados"]
    disponible = pg["max_connections"] - pg["margen_reservado"]
    if servidor > disponible:
        errores.append(
            f"conexiones: PgBouncer abriría {servidor} conexiones de servidor y la base"
            f" deja {disponible} (max_connections {pg['max_connections']} menos el margen"
            f" {pg['margen_reservado']})")

    # Regla 6 — antiafinidad estricta sin zonas es una promesa que no se cumple.
    for entorno, cfg in infra["entornos"].items():
        estrictos = [d["servicio"] for d in servicios
                     if niveles[d["nivel"]]["antiafinidad"] == "required"]
        if estrictos and cfg.get("zonas", 1) < 2 and entorno == "prod":
            errores.append(
                f"entorno {entorno}: {len(estrictos)} servicios exigen antiafinidad"
                f" estricta y solo hay {cfg.get('zonas')} zona. Con una zona la"
                f" redundancia es de proceso, no de nodo: declaralo en el ADR o sumá zonas")
    return errores


# ── generación ─────────────────────────────────────────────────────────────

def replicas_de(d, infra, entorno):
    factor = infra["entornos"][entorno]["factor_replicas"]
    return max(2, math.ceil(d["replicas"]["min"] * factor))


def deployment(d, infra, entorno, ns):
    n = infra["niveles"][d["nivel"]]
    s = d["servicio"]
    reps = replicas_de(d, infra, entorno)
    if n["antiafinidad"] == "required":
        afinidad = f"""      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchLabels: {{app: {s}}}
              topologyKey: kubernetes.io/hostname"""
    else:
        afinidad = f"""      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 100
              podAffinityTerm:
                labelSelector:
                  matchLabels: {{app: {s}}}
                topologyKey: kubernetes.io/hostname"""
    reparto = ""
    if infra["entornos"][entorno].get("zonas", 1) >= 2:
        reparto = f"""      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: topology.kubernetes.io/zone
          whenUnsatisfiable: {'DoNotSchedule' if n['antiafinidad'] == 'required' else 'ScheduleAnyway'}
          labelSelector:
            matchLabels: {{app: {s}}}
"""
    return f"""{CABECERA}apiVersion: apps/v1
kind: Deployment
metadata:
  name: {s}
  namespace: {ns}
  labels: {{app: {s}, nivel: {d['nivel']}}}
  annotations:
    aportaya.bo/nivel-porque: "{d['nivel_porque']}"
    aportaya.bo/objetivo-disponibilidad: "{d['disponibilidad']['objetivo_mensual']}"
spec:
  replicas: {reps}
  strategy:
    type: {d['despliegue']['estrategia']}
    rollingUpdate: {{maxUnavailable: {d['despliegue']['maxUnavailable']}, maxSurge: 1}}
  selector:
    matchLabels: {{app: {s}}}
  template:
    metadata:
      labels: {{app: {s}, nivel: {d['nivel']}}}
    spec:
{afinidad}
{reparto}      securityContext: {{runAsNonRoot: true, runAsUser: 1000, fsGroup: 1000}}
      # El apagado controlado importa tanto como el arranque: sin la espera, el
      # balanceador sigue mandando tráfico a un pod que ya está terminando.
      terminationGracePeriodSeconds: 45
      containers:
        - name: {s}
          image: aportaya/{s}:${{VERSION}}
          imagePullPolicy: IfNotPresent
          ports: [{{containerPort: 8080}}]
          lifecycle:
            preStop: {{exec: {{command: ["sh", "-c", "sleep 10"]}}}}
          env:
            - name: APORTAYA_POOL_MAXIMO
              value: "{d['pool']['hikari_por_replica']}"
          envFrom:
            - configMapRef: {{name: {s}-config}}
            - secretRef: {{name: {s}-secretos}}
          resources:
            requests: {{memory: {d['recursos']['memoria']}, cpu: {d['recursos']['cpu']}}}
            limits: {{memory: {d['recursos']['memoria']}, cpu: {d['recursos']['cpu']}}}
          readinessProbe:
            httpGet: {{path: {d['sondas']['readiness']}, port: 8080}}
            initialDelaySeconds: 10
            periodSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet: {{path: {d['sondas']['liveness']}, port: 8080}}
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          startupProbe:
            httpGet: {{path: {d['sondas']['liveness']}, port: 8080}}
            periodSeconds: 5
            failureThreshold: 6      # 30 s: el presupuesto de arranque es 20 s (ADR-025)
"""


def service(s, ns):
    return f"""{CABECERA}apiVersion: v1
kind: Service
metadata: {{name: {s}, namespace: {ns}}}
spec:
  type: ClusterIP
  selector: {{app: {s}}}
  ports: [{{port: 80, targetPort: 8080}}]
"""


def pdb(d, infra, entorno, ns):
    n = infra["niveles"][d["nivel"]]
    reps = replicas_de(d, infra, entorno)
    # Un PDB que exige más de lo que hay bloquea el drenaje del nodo para siempre.
    minimo = min(n["pdb_min_disponibles"], reps - 1) or 1
    return f"""{CABECERA}apiVersion: policy/v1
kind: PodDisruptionBudget
metadata: {{name: {d['servicio']}, namespace: {ns}}}
spec:
  minAvailable: {minimo}
  selector:
    matchLabels: {{app: {d['servicio']}}}
"""


def hpa(d, infra, entorno, ns):
    n = infra["niveles"][d["nivel"]]
    if not n["hpa"]:
        return None
    return f"""{CABECERA}apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata: {{name: {d['servicio']}, namespace: {ns}}}
spec:
  scaleTargetRef: {{apiVersion: apps/v1, kind: Deployment, name: {d['servicio']}}}
  minReplicas: {replicas_de(d, infra, entorno)}
  # El tope NO es negociable en el panel del clúster: sale del descriptor, y el
  # generador comprobó que replicas_max x pool cabe en PgBouncer (ADR-037 §3).
  maxReplicas: {d['replicas']['max']}
  metrics:
    - type: Resource
      resource: {{name: cpu, target: {{type: Utilization, averageUtilization: 70}}}}
  behavior:
    scaleDown: {{stabilizationWindowSeconds: 300}}
"""


def networkpolicy(s, ns):
    return f"""{CABECERA}# Denegación por omisión: solo el gateway entra, y solo la base y Kafka salen.
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: {{name: {s}, namespace: {ns}}}
spec:
  podSelector: {{matchLabels: {{app: {s}}}}}
  policyTypes: [Ingress, Egress]
  ingress:
    - from:
        - podSelector: {{matchLabels: {{app: gateway}}}}
      ports: [{{port: 8080}}]
  egress:
    - to: [{{podSelector: {{matchLabels: {{app: pgbouncer}}}}}}]
    - to: [{{podSelector: {{matchLabels: {{app: kafka}}}}}}]
    - ports: [{{port: 53, protocol: UDP}}]
"""


def gateway(infra, entorno, ns):
    g = infra["entrada"]["gateway"]
    n = infra["niveles"][g["nivel"]]
    reps = max(2, math.ceil(n["replicas_min"] * infra["entornos"][entorno]["factor_replicas"]))
    return f"""{CABECERA}apiVersion: apps/v1
kind: Deployment
metadata: {{name: gateway, namespace: {ns}, labels: {{app: gateway, nivel: {g['nivel']}}}}}
spec:
  replicas: {reps}
  strategy:
    type: RollingUpdate
    rollingUpdate: {{maxUnavailable: 0, maxSurge: 1}}
  selector: {{matchLabels: {{app: gateway}}}}
  template:
    metadata: {{labels: {{app: gateway, nivel: {g['nivel']}}}}}
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector: {{matchLabels: {{app: gateway}}}}
              topologyKey: kubernetes.io/hostname
      securityContext: {{runAsNonRoot: true, runAsUser: 1000}}
      terminationGracePeriodSeconds: 45
      containers:
        - name: gateway
          image: {g['imagen']}:${{VERSION}}
          ports: [{{containerPort: {g['puerto']}}}]
          resources:
            requests: {{memory: {g['recursos']['memoria']}, cpu: {g['recursos']['cpu']}}}
            limits: {{memory: {g['recursos']['memoria']}, cpu: {g['recursos']['cpu']}}}
          readinessProbe:
            httpGet: {{path: /actuator/health/readiness, port: {g['puerto']}}}
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata: {{name: gateway, namespace: {ns}}}
spec:
  type: ClusterIP
  selector: {{app: gateway}}
  ports: [{{port: 80, targetPort: {g['puerto']}}}]
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata: {{name: gateway, namespace: {ns}}}
spec:
  minAvailable: {min(n['pdb_min_disponibles'], reps - 1) or 1}
  selector: {{matchLabels: {{app: gateway}}}}
---
# La única puerta pública. Ningún servicio tiene Service de tipo LoadBalancer.
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: aportaya
  namespace: {ns}
  annotations:
    nginx.ingress.kubernetes.io/limit-rps: "50"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
spec:
  ingressClassName: {infra['entrada']['ingress']['clase']}
  rules:
    - http:
        paths:
          - path: /
            pathType: Prefix
            backend: {{service: {{name: gateway, port: {{number: 80}}}}}}
"""


def job_migracion(ns):
    return f"""{CABECERA}# La migración es un paso del despliegue, no del arranque: con catorce procesos
# levantando a la vez, catorce aplicando DDL es una carrera garantizada
# (ADR-025). El mecanismo es psql sobre sql/aplicar.sql; Flyway está descartado
# (ADR-032).
apiVersion: batch/v1
kind: Job
metadata:
  name: "migracion-${{VERSION}}"
  namespace: {ns}
spec:
  backoffLimit: 0        # una migración que falla se mira, no se reintenta sola
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: migrador
          image: postgres:16
          command: ["psql", "-v", "ON_ERROR_STOP=1", "-f", "/sql/aplicar.sql"]
          # El search_path completo: el SQL escrito a mano nombra las tablas sin
          # calificar (docs/Arquitectura/Entornos y despliegue).
          env:
            - name: PGUSER
              value: rol_migracion
          envFrom: [{{secretRef: {{name: migrador-secretos}}}}]
          volumeMounts: [{{name: sql, mountPath: /sql}}]
      volumes:
        - name: sql
          configMap: {{name: sql-esquema}}
"""


def main():
    infra, servicios = cargar()

    problemas = validar(infra, servicios)
    if problemas:
        print(f"DESPLIEGUE INVÁLIDO ({len(problemas)}):")
        for p in problemas:
            print(f"  - {p}")
        return 1

    if DESTINO.exists():
        for viejo in DESTINO.rglob("*.yaml"):
            viejo.unlink()

    total = 0
    for entorno, cfg in infra["entornos"].items():
        ns = cfg["namespace"]
        carpeta = DESTINO / entorno
        carpeta.mkdir(parents=True, exist_ok=True)

        (carpeta / "00-namespace.yaml").write_text(
            f"{CABECERA}apiVersion: v1\nkind: Namespace\nmetadata: {{name: {ns}}}\n",
            encoding="utf-8")
        (carpeta / "01-gateway.yaml").write_text(gateway(infra, entorno, ns), encoding="utf-8")
        (carpeta / "02-migracion.yaml").write_text(job_migracion(ns), encoding="utf-8")
        total += 3

        for d in servicios:
            s = d["servicio"]
            piezas = [deployment(d, infra, entorno, ns), service(s, ns),
                      pdb(d, infra, entorno, ns), networkpolicy(s, ns)]
            escalador = hpa(d, infra, entorno, ns)
            if escalador:
                piezas.append(escalador)
            (carpeta / f"10-{s}.yaml").write_text("---\n".join(piezas), encoding="utf-8")
            total += 1

        procesos = sum(replicas_de(d, infra, entorno) for d in servicios)
        maximos = sum(d["replicas"]["max"] for d in servicios)
        print(f"{entorno:5} → {carpeta.relative_to(RAIZ)}: {len(servicios)} servicios ·"
              f" {procesos} procesos en reposo · {maximos} en el pico")

    demanda = sum(d["replicas"]["max"] * d["pool"]["hikari_por_replica"] for d in servicios)
    pgb = infra["base_de_datos"]["pgbouncer"]
    print(f"conexiones en el pico: {demanda} de {pgb['max_client_conn']} que acepta PgBouncer")
    print(f"total: {total} archivos generados · reglas de ADR-037 verificadas")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
