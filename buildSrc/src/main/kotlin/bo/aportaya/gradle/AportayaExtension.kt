package bo.aportaya.gradle

import org.gradle.api.provider.Property

/**
 * Lo unico que un servicio declara sobre si mismo. El resto —toolchain, formato,
 * corredores de prueba, empaquetado— lo pone la convencion, para que catorce
 * servicios sean iguales y no solo parecidos.
 *
 * `esquema` y `rol` no son decoracion: de ahi salen las clases de jOOQ que se
 * generan (solo las del esquema propio, invariante 11) y el usuario con el que
 * el servicio se conecta.
 */
abstract class AportayaExtension {
    abstract val esquema: Property<String>
    abstract val rol: Property<String>
}
