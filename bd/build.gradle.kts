// Las tareas de base de datos. Sin codigo: existe para que `./gradlew bd:reset` sea
// el mismo comando en las cinco maquinas del parque, tengan o no `psql` instalado —
// el cliente que se usa es el del contenedor, montado sobre el repositorio en /repo.
//
// El esquema NO se escribe aca: sale de scripts/generar_ddl.py y vive en sql/.

val raiz = rootProject.layout.projectDirectory.asFile
val compose = File(raiz, "despliegue/compose/base.yml").absolutePath
val contenedor = "aportaya-postgres"
val base = "pasanaku"
val admin = "pasanaku"

fun psql(nombre: String, descripcion: String, archivo: String) =
    tasks.register<Exec>(nombre) {
        group = "base de datos"
        this.description = descripcion
        commandLine(
            "docker", "exec", "-i", contenedor,
            "psql", "-v", "ON_ERROR_STOP=1", "-U", admin, "-d", base, "-q", "-f", "/repo/$archivo",
        )
    }

val bajar = tasks.register<Exec>("bajar") {
    group = "base de datos"
    description = "Apaga la infraestructura y BORRA el volumen"
    commandLine("docker", "compose", "-f", compose, "--profile", "base", "down", "-v")
}

val levantar = tasks.register<Exec>("levantar") {
    group = "base de datos"
    description = "docker compose --profile base up -d --wait"
    commandLine("docker", "compose", "-f", compose, "--profile", "base", "up", "-d", "--wait")
    mustRunAfter(bajar)
}

val aplicar = psql("aplicar", "16 esquemas, 14 roles, las tablas, las reglas y los permisos", "sql/aplicar.sql")

val generarSemillas = tasks.register<Exec>("generarSemillas") {
    group = "base de datos"
    description = "Genera el SQL de semillas desde seeders/ (valida la frontera dev / minimos)"
    workingDir = raiz
    commandLine("python3", "scripts/generar_semillas.py")
}

val semillas = psql("semillas", "Los 20 catalogos minimos — los mismos que van a produccion", "sql/60_semillas/sembrar.sql")
val dev = psql("dev", "Datos de desarrollo — NUNCA en produccion (seeders/dev)", "sql/61_dev/sembrar_dev.sql")
val verificaciones = psql("verificaciones", "sql/50_verificacion/verificaciones.sql", "sql/50_verificacion/verificaciones.sql")
val humo = psql("humo", "prueba_humo.sql — todo OK, cero FALLA", "sql/50_verificacion/prueba_humo.sql")

// El orden es el de aplicar.sql y no se negocia: sin esquemas no hay tablas, sin
// tablas no hay semillas, y sin semillas la prueba de humo no prueba nada.
aplicar { dependsOn(levantar) }
semillas { dependsOn(aplicar, generarSemillas) }
dev { dependsOn(semillas) }
verificaciones { dependsOn(dev) }
humo { dependsOn(verificaciones) }

tasks.register("reset") {
    group = "base de datos"
    description = "Volumen limpio -> esquemas y roles -> aplicar -> semillas -> prueba de humo"
    dependsOn(bajar, levantar, aplicar, generarSemillas, semillas, dev, verificaciones, humo)
}
