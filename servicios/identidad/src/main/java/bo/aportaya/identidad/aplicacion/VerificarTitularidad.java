package bo.aportaya.identidad.aplicacion;

import bo.aportaya.identidad.dominio.DocumentoDeIdentidad;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Si el nombre y el documento que alguien declara son los del titular de la cuenta.
 *
 * <p><b>Contesta si coinciden; no dice cuales son.</b> Es la diferencia entre una
 * comprobacion y una fuga: devolver el nombre y el documento guardados convertiria esta
 * ruta en un directorio de clientes para cualquier servicio comprometido. El documento
 * ademas nunca sale, ni siquiera aca dentro — se guarda como hash con pimienta, asi que
 * lo que se compara son dos hashes.
 *
 * <p>La usa {@code entregas} antes de registrar una cuenta bancaria: R-SEG-02 exige que
 * la cuenta sea de quien la registra, y ese dato vive en este servicio.
 */
@Service
public class VerificarTitularidad {

    private final Datos datos;
    private final String pimienta;

    public VerificarTitularidad(Datos datos, @Value("${aportaya.seguridad.pimienta}") String pimienta) {
        this.datos = datos;
        this.pimienta = pimienta;
    }

    @Transactional(readOnly = true)
    public boolean coincide(UUID usuarioId, String nombreDeclarado, String documentoDeclarado, ContextoSesion ctx) {
        String hashDeclarado = DocumentoDeIdentidad.de(DocumentoDeIdentidad.Tipo.CI, documentoDeclarado, pimienta, "BO")
                .hashNumero();

        return datos.conContexto(ctx, dsl -> {
            var fila = dsl.select(
                            DSL.field("u.nombres", String.class),
                            DSL.field("u.apellidos", String.class),
                            DSL.field("d.hash_numero", String.class))
                    .from(DSL.table(DSL.name("identidad", "usuario")).as("u"))
                    .leftJoin(DSL.table(DSL.name("identidad", "documento_identidad"))
                            .as("d"))
                    .on(DSL.field("d.usuario_id", UUID.class).eq(DSL.field("u.id", UUID.class)))
                    .where(DSL.field("u.id", UUID.class).eq(usuarioId))
                    .fetchOne();

            if (fila == null || fila.get("d.hash_numero", String.class) == null) {
                // Sin documento verificado no se puede afirmar que coincide. Denegar por
                // omision (invariante 9): quien no completo su KYC no registra cuentas.
                return false;
            }
            if (!fila.get("d.hash_numero", String.class).equals(hashDeclarado)) {
                return false;
            }
            String delTitular = fila.get("u.nombres", String.class) + " " + fila.get("u.apellidos", String.class);
            return normalizar(delTitular).equals(normalizar(nombreDeclarado));
        });
    }

    /** Sin tildes, sin dobles espacios y en minusculas: «José Pérez» es «Jose Perez». */
    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
