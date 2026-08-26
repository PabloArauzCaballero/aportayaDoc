package bo.aportaya.identidad.infraestructura;

import static bo.aportaya.identidad.generado.Tables.CONSENTIMIENTO;
import static bo.aportaya.identidad.generado.Tables.DOCUMENTO_IDENTIDAD;
import static bo.aportaya.identidad.generado.Tables.USUARIO;
import static bo.aportaya.identidad.generado.Tables.VERIFICACION_KYC;

import bo.aportaya.identidad.dominio.DocumentoDeIdentidad;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** El alta del usuario y lo que la acompaña dentro de `identidad`. */
@Component
public class RegistroRepositorio {

    public boolean telefonoYaRegistrado(DSLContext dsl, String telefono) {
        return dsl.fetchExists(dsl.selectFrom(USUARIO)
                .where(USUARIO.TELEFONO_E164.eq(telefono))
                .and(USUARIO.ELIMINADO_EN.isNull()));
    }

    public boolean documentoYaRegistrado(DSLContext dsl, String hashNumero) {
        return dsl.fetchExists(
                dsl.selectFrom(DOCUMENTO_IDENTIDAD).where(DOCUMENTO_IDENTIDAD.HASH_NUMERO.eq(hashNumero)));
    }

    public UUID crearUsuario(
            DSLContext dsl,
            String codigoPublico,
            String nombres,
            String apellidos,
            String telefono,
            LocalDate fechaNacimiento,
            String estado,
            OffsetDateTime ahora) {
        return dsl.insertInto(USUARIO)
                .set(USUARIO.CODIGO_PUBLICO, codigoPublico)
                .set(USUARIO.NOMBRES, nombres)
                .set(USUARIO.APELLIDOS, apellidos)
                .set(USUARIO.TELEFONO_E164, telefono)
                .set(USUARIO.FECHA_NACIMIENTO, fechaNacimiento)
                .set(USUARIO.ESTADO, estado)
                .set(USUARIO.NIVEL_KYC, "NINGUNO")
                .set(USUARIO.IDIOMA, "es")
                .set(USUARIO.ZONA_HORARIA, "America/La_Paz")
                .set(USUARIO.FECHA_REGISTRO, ahora)
                .returning(USUARIO.ID)
                .fetchOne(USUARIO.ID);
    }

    /** El numero va cifrado; lo que se indexa es su hash. Nunca el numero en claro. */
    public UUID guardarDocumento(
            DSLContext dsl, UUID usuarioId, DocumentoDeIdentidad documento, String numeroCifrado, String hashArchivo) {
        return dsl.insertInto(DOCUMENTO_IDENTIDAD)
                .set(DOCUMENTO_IDENTIDAD.USUARIO_ID, usuarioId)
                .set(DOCUMENTO_IDENTIDAD.TIPO, documento.tipo().name())
                .set(DOCUMENTO_IDENTIDAD.NUMERO_CIFRADO, numeroCifrado)
                .set(DOCUMENTO_IDENTIDAD.VERSION_LLAVE, (short) 1)
                .set(DOCUMENTO_IDENTIDAD.HASH_NUMERO, documento.hashNumero())
                .set(DOCUMENTO_IDENTIDAD.PAIS_EMISION, documento.paisEmision())
                // Clave de objeto local, nunca una URL publica (ADR-034).
                .set(DOCUMENTO_IDENTIDAD.URL_ANVERSO, "local://documentos/" + usuarioId + "/anverso")
                .set(DOCUMENTO_IDENTIDAD.HASH_ARCHIVO, hashArchivo)
                .set(DOCUMENTO_IDENTIDAD.ESTADO, "EN_REVISION")
                .returning(DOCUMENTO_IDENTIDAD.ID)
                .fetchOne(DOCUMENTO_IDENTIDAD.ID);
    }

    public UUID iniciarVerificacion(
            DSLContext dsl, UUID usuarioId, UUID documentoId, String nivel, OffsetDateTime ahora) {
        return dsl.insertInto(VERIFICACION_KYC)
                .set(VERIFICACION_KYC.USUARIO_ID, usuarioId)
                .set(VERIFICACION_KYC.DOCUMENTO_ID, documentoId)
                .set(VERIFICACION_KYC.NIVEL_SOLICITADO, nivel)
                .set(VERIFICACION_KYC.ESTADO, "EN_REVISION")
                .set(VERIFICACION_KYC.INICIADA_EN, ahora)
                .returning(VERIFICACION_KYC.ID)
                .fetchOne(VERIFICACION_KYC.ID);
    }

    /** Cada finalidad por separado: aceptar el contrato no es aceptar publicidad. */
    public void registrarConsentimientos(
            DSLContext dsl, UUID usuarioId, List<String> finalidades, String ip, String agente, OffsetDateTime ahora) {
        for (String finalidad : finalidades) {
            dsl.insertInto(
                            CONSENTIMIENTO,
                            CONSENTIMIENTO.USUARIO_ID,
                            CONSENTIMIENTO.TIPO,
                            CONSENTIMIENTO.VERSION_DOCUMENTO,
                            CONSENTIMIENTO.HASH_DOCUMENTO,
                            CONSENTIMIENTO.OTORGADO,
                            CONSENTIMIENTO.FECHA_HORA,
                            CONSENTIMIENTO.IP_ORIGEN,
                            CONSENTIMIENTO.AGENTE_USUARIO)
                    .values(
                            DSL.val(usuarioId),
                            DSL.val(finalidad),
                            DSL.val("1"),
                            DSL.val("0".repeat(64)),
                            DSL.val(true),
                            DSL.val(ahora),
                            comoInet(ip),
                            DSL.val(agente))
                    .execute();
        }
    }

    private static Field<Object> comoInet(String ip) {
        return DSL.field("cast({0} as inet)", Object.class, DSL.val(ip));
    }
}
