package bo.aportaya.plataforma.pruebas.barrido;

import java.nio.file.Path;

/**
 * Una violacion de una regla propia, con el archivo y la linea donde esta.
 *
 * <p>Un hallazgo sin ubicacion obliga a buscar; uno con ubicacion se arregla.
 */
public record Hallazgo(Path archivo, int linea, String detalle) {

    public String describir(Path raiz) {
        Path relativo = archivo.startsWith(raiz) ? raiz.relativize(archivo) : archivo;
        return linea > 0 ? "%s:%d  %s".formatted(relativo, linea, detalle) : "%s  %s".formatted(relativo, detalle);
    }
}
