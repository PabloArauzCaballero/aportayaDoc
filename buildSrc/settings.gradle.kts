dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        // El mismo catalogo que el build principal: los plugins de convencion no
        // pueden quedar en otra version que los servicios que configuran.
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
