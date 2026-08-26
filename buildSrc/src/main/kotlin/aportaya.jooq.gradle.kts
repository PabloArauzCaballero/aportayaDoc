import bo.aportaya.gradle.AportayaExtension

// Las clases de jOOQ se generan por INTROSPECCION de la base viva, y solo del
// esquema propio: es el invariante 11 hecho build. Si el esquema cambio y nadie
// regenero, el codigo no compila — que es exactamente lo que tiene que pasar.
plugins {
    id("aportaya.base")
}

val aportaya = extensions.findByType<AportayaExtension>()
    ?: extensions.create<AportayaExtension>("aportaya")

val generado = layout.buildDirectory.dir("generated/jooq")

sourceSets["main"].java.srcDir(generado)

val generateJooq = tasks.register("generateJooq") {
    group = "build"
    description = "Genera las clases de jOOQ del esquema propio desde la base viva"
    val esquema = aportaya.esquema
    val salida = generado
    val url = providers.environmentVariable("BD_URL_ADMIN").orElse("jdbc:postgresql://127.0.0.1:5433/pasanaku")
    val usuario = providers.environmentVariable("BD_USUARIO_ADMIN").orElse("pasanaku")
    val clave = providers.environmentVariable("BD_CLAVE_ADMIN").orElse("pasanaku")
    outputs.dir(salida)
    doLast {
        val nombre = esquema.get()
        val configuracion = org.jooq.meta.jaxb.Configuration()
            .withJdbc(
                org.jooq.meta.jaxb.Jdbc()
                    .withDriver("org.postgresql.Driver")
                    .withUrl(url.get())
                    .withUser(usuario.get())
                    .withPassword(clave.get()),
            )
            .withGenerator(
                org.jooq.meta.jaxb.Generator()
                    .withDatabase(
                        org.jooq.meta.jaxb.Database()
                            .withName("org.jooq.meta.postgres.PostgresDatabase")
                            .withIncludes(".*")
                            .withInputSchema(nombre),
                    )
                    .withGenerate(
                        org.jooq.meta.jaxb.Generate()
                            .withDeprecated(false)
                            // Una columna de tipo que jOOQ no conoce (inet, por
                            // ejemplo) sale marcada como obsoleta, y con -Werror eso
                            // rompe la compilacion de codigo que nadie escribio. No
                            // es una desactivacion del gate: el gate sigue siendo
                            // -Werror sobre el codigo que si se escribe.
                            .withDeprecationOnUnknownTypes(false)
                            .withJavaTimeTypes(true)
                            .withPojos(false)
                            .withDaos(false),
                    )
                    .withTarget(
                        org.jooq.meta.jaxb.Target()
                            .withPackageName("bo.aportaya.$nombre.generado")
                            .withDirectory(salida.get().asFile.absolutePath),
                    ),
            )
        org.jooq.codegen.GenerationTool.generate(configuracion)
    }
}

tasks.named<JavaCompile>("compileJava") { mustRunAfter(generateJooq) }
