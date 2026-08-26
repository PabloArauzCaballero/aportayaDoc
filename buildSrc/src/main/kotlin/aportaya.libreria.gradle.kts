// Un modulo de plataforma/: no arranca, no se despliega, y lo consumen los catorce.
// Comparte el BOM con los servicios para que una version no pueda divergir entre el
// piso y lo que se para encima.
plugins {
    id("aportaya.base")
    id("io.spring.dependency-management")
}

val catalogo = extensions.getByType<VersionCatalogsExtension>().named("libs")


// El generador y la libreria de jOOQ tienen que ser la MISMA version: el codigo
// generado por 3.20 llama a metodos que el runtime del BOM (3.19) no tiene, y el
// error aparece recien al compilar 300 clases generadas. Se sobrescribe la
// propiedad del BOM de Spring Boot, que es donde vive la decision.
extra["jooq.version"] = catalogo.findVersion("jooq").get().requiredVersion

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${catalogo.findVersion("spring-boot").get().requiredVersion}")
    }
}
