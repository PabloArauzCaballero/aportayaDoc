---
tags:
  - arquitectura
  - adr
titulo: "ADR-034 — Almacenamiento de archivos: adaptador local por defecto"
estado: aceptada
fecha: 2026-08-19
---

# ADR-034 — Almacenamiento de archivos

> Aplica [[ADR-033 Puertos y adaptadores]] al primer puerto que hace falta el día
> uno: subir el anverso de una cédula.

## Contexto

Trece tablas del modelo guardan un archivo que el sistema recibe o produce:

| Módulo | Tabla | Columnas | Qué guarda |
| --- | --- | --- | --- |
| M01 | `documento_identidad` | `url_anverso`, `url_reverso`, `hash_archivo` | Cédula del cliente |
| M01 | `verificacion_kyc` | `url_selfie` | Selfie de la prueba de vida |
| M01 | `usuario` | `url_avatar` | Foto de perfil |
| M03 | `comprobante_manual` | `archivo_url`, `hash_archivo` | Comprobante que carga el cliente |
| M03 | `constancia_pago` | `url_pdf` | Constancia emitida |
| M08 | `evidencia_incumplimiento` | `url_archivo`, `hash_archivo` | Evidencia del descargo |
| M09 | `exportacion_reporte` | `url_archivo`, `hash_archivo` | Reporte exportado |
| M10 | `estado_cuenta_billetera` | `url_archivo`, `hash_archivo` | Extracto del cliente |
| M10 | `certificado_saldo` | `url_documento` | Certificado de saldo |
| M11 | `factura_electronica` | `url_pdf`, `url_xml` | Factura y su XML |
| M12 | `reporte_regulatorio` | `url_archivo`, `hash_archivo` | Reporte presentado al regulador |
| M12 | `contrato_adhesion`, `acta_comite`, `politica_interna` | `url_documento` | Documentos de gobierno |
| M14 | `pieza_creativa` | `url_recurso` | Creatividad publicitaria |

**Seis columnas parecidas que NO son esto**, y no pasan por el puerto:
`constancia_pago.url_publica`, `certificado_reputacion.url_publica` y
`documento_publicado.url_publica` son enlaces **deliberadamente públicos**, para que
un tercero verifique sin sesión; `qr_cobro.url_imagen`, `enlace_pago_rapido.url_corta`
y `version_plantilla.url_encabezado_media` son enlaces, no archivos guardados.

Son datos personales y evidencia legal: la cédula tiene retención de diez años, el
descargo es parte de un debido proceso y el extracto sostiene el cuadre de custodia.

En el stack anterior esto lo resolvía `multer` sobre Express: middleware, carpeta de
subida y listo. En Spring Boot el equivalente es `MultipartFile` más
`spring.servlet.multipart.*`, pero *nadie decidió todavía* qué se guarda en esas
columnas ni quién sirve el binario. Sin esa decisión, cada servicio la va a inventar
distinta y dos de ellos van a poner una URL pública en la columna.

## Decisión

**Un puerto `AlmacenDeArchivos`, con `AlmacenLocalAdaptador` como implementación por
defecto (disco, sin dependencias, el equivalente a multer) y `AlmacenS3Adaptador`
como opcional. En la columna nunca va una URL pública: va una clave de objeto.**

```java
public interface AlmacenDeArchivos {
    ClaveObjeto guardar(ContenidoEntrante contenido, AmbitoArchivo ambito);
    ContenidoAlmacenado leer(ClaveObjeto clave);
    UrlTemporal urlTemporal(ClaveObjeto clave, Duration vigencia);
    void marcarDeBaja(ClaveObjeto clave, String motivo);
}
```

**La clave de objeto, y por qué no es una URL**

```
local://identidad/2026/08/9f2c1e4a-….jpg
  s3://identidad/2026/08/9f2c1e4a-….jpg
```

El esquema dice **qué adaptador** la escribió; el resto es la ruta lógica. Cambiar de
adaptador no obliga a reescribir trece tablas: obliga a copiar los objetos y a
resolver las claves viejas con el adaptador viejo, que sigue registrado.

Una URL pública en la columna sería un error de dos maneras: expone evidencia
personal a quien tenga el enlace, y ata el dato a un dominio y a un proveedor.

> **Nombre inconsistente detectado.** `comprobante_manual` llama `archivo_url` a lo
> que las otras seis tablas llaman `url_archivo`. Es del modelo y **manda el modelo**
> ([[Contrato de implementación para IA]] §1.1): el código usa `archivo_url` ahí y
> `url_archivo` en las demás. Unificarlo es un cambio de modelo con su ADR, no una
> corrección al escribir el repositorio.

**El binario no se sirve nunca directo.** Lo entrega un endpoint del **servicio
dueño** de la tabla, que valida sesión, permisos y RLS, y devuelve el contenido o una
URL temporal de vigencia corta. Ese acceso queda en la bitácora igual que cualquier
lectura de dato personal ([[ADR-031 Lecturas, réplica y rol auditor]]).

**Las siete reglas de toda subida**

1. **Lista blanca por ámbito**, no lista negra: identidad acepta `image/jpeg`,
   `image/png` y `application/pdf`, y nada más.
2. **El tipo se decide por el contenido**, leyendo los primeros bytes — no por la
   extensión ni por el `Content-Type` que mandó el cliente.
3. **Tamaño máximo por ámbito**, rechazado en el borde y también configurado en
   `spring.servlet.multipart.max-file-size`.
4. **El nombre original nunca es la ruta.** Se guarda como metadato; la ruta la
   genera el adaptador con un UUID. Así no hay `../` ni colisiones ni nombres con
   datos personales adentro.
5. **SHA-256 obligatorio** al guardar, y va a `hash_archivo` en las siete tablas
   que la tienen. En las que no —avatar, certificado, factura, pieza creativa— el
   hash igual se calcula y viaja en la respuesta, pero **no se inventa columna**.
6. **Los archivos de identidad se cifran en reposo**, con la versión de llave que ya
   registra `documento_identidad.version_llave`.
7. **Los objetos no se sobrescriben ni se borran.** Reemplazar es guardar uno nuevo y
   apuntar la fila; dar de baja es lógico, y el barrido de retención es el único que
   borra bytes, cuando vence el plazo de `politica_retencion`.

**El adaptador local, en concreto** — es el que corre en dev y en el arranque:

```yaml
aportaya:
  adaptadores:
    archivos: local
  archivos:
    local:
      raiz: ./var/archivos          # fuera del repositorio y del .jar
    ambitos:
      identidad:   { tamano_max: 8MB,  tipos: [image/jpeg, image/png, application/pdf] }
      comprobante: { tamano_max: 5MB,  tipos: [image/jpeg, image/png, application/pdf] }
      reporte:     { tamano_max: 50MB, tipos: [application/pdf, text/csv] }
```

Escribe con `Files.write` a un temporal y renombra al final, para que nunca exista
una clave apuntando a un archivo a medio subir.

## Motivo

**Se arranca hoy y sin cuenta de nadie.** El flujo de KYC —que es el primero de todos
los flujos— necesita subir una cédula. Con el adaptador local eso funciona en la
máquina de cualquiera el primer día.

**La decisión difícil no es dónde se guardan los bytes: es qué se guarda en la
columna.** Eso es lo que después cuesta caro cambiar, y por eso es lo que fija este
ADR. Que hoy sea disco y mañana S3 pasa a ser un detalle de despliegue.

**El hash no es opcional porque el archivo es evidencia.** Un descargo, un extracto o
un documento presentado al regulador tienen que poder demostrar que son el mismo
archivo que se recibió. La columna ya existe en el modelo; esta decisión la vuelve
obligatoria de llenar.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Guardar los bytes en PostgreSQL (`bytea` o *large object*)** | Infla la base y sus respaldos, y arrastra los binarios a cada restauración de punto en el tiempo ([[ADR-013 Respaldo y continuidad]]). El respaldo del archivo tiene otra frecuencia y otro costo que el de la base. |
| **S3 / MinIO desde el primer día** | Mete una dependencia de infraestructura antes de tener el primer caso de uso andando, y obliga a credenciales para programar. Queda como el adaptador de producción, detrás del mismo puerto. |
| **URL pública en la columna** | Expone datos personales a quien tenga el enlace y ata la fila a un dominio y a un proveedor. |
| **Que cada servicio resuelva sus archivos como quiera** | Ocho tablas en cinco servicios: cinco criterios distintos de validación y de retención sobre datos personales. |
| **Servir el binario desde NGINX directo** | NGINX no conoce la sesión ni RLS. El permiso sobre una cédula no se decide por ruta. |

## Consecuencias

**A favor**

- El servicio de identidad se puede implementar entero sin infraestructura externa.
- Cambiar a S3 es un adaptador y una línea de configuración; las ocho tablas no se
  tocan.
- La validación de tipo, tamaño y hash es una sola, y se prueba una sola vez.

**En contra, y hay que asumirlo**

- **El adaptador local no sirve para producción con más de una réplica**: dos
  instancias no comparten disco. Está bien: producción usa el adaptador de objetos, y
  el arranque de la aplicación **falla** si el perfil es de producción y el adaptador
  configurado es `local`.
- Guardar el objeto y confirmar la fila son **dos sistemas**: si la transacción
  revierte después de escribir el archivo, queda un objeto huérfano. Se acepta y se
  limpia con un barrido de objetos sin fila, con antigüedad mínima. Al revés —fila sin
  objeto— no puede pasar: primero se guarda el objeto, después se confirma la fila.
- El barrido de retención hay que escribirlo; sin él, "no se borra nunca" deja de ser
  una regla y pasa a ser una acumulación.

## Hueco declarado — no hay tabla de objetos

**El modelo guarda la clave y el hash dentro de cada tabla del negocio, pero no
existe una entidad de objeto almacenado.** Eso deja sin lugar cuatro datos que el
adaptador conoce y hoy se pierden: tamaño en bytes, tipo real detectado, versión de
llave de cifrado para los archivos que no son de identidad, y fecha de vencimiento de
retención por objeto.

Se declara como hueco y **no se completa por decisión de implementación**
([[Contrato de implementación para IA]] §4). Cerrarlo es agregar
`objeto_almacenado` al `.puml` de infraestructura por [[ADR-032 Aplicación del esquema]] y la skill `boveda-modelo`, con un ADR que lo justifique. Mientras tanto:
las columnas existentes alcanzan para operar, y el barrido de retención se apoya en
la política de la tabla dueña.

## Cómo se verifica

- [ ] Ningún valor guardado en `url_*` empieza con `http://` o `https://`.
- [ ] Toda fila con archivo tiene `hash_archivo` no nulo, y el hash coincide al leer.
- [ ] Prueba de rechazo: extensión `.jpg` con contenido ejecutable → rechazado por
      contenido, no por extensión.
- [ ] Prueba de rechazo: nombre con `../` → la ruta generada queda bajo la raíz.
- [ ] Prueba: pedir un archivo ajeno con otra sesión → 403 y queda en la bitácora.
- [ ] Arranque con perfil de producción y `adaptadores.archivos = local` → **falla**.
- [ ] La suite de contrato de puerto pasa igual con el adaptador local y con MinIO.

## Ver también

[[ADR-033 Puertos y adaptadores]] · [[ADR-013 Respaldo y continuidad]] ·
[[ADR-031 Lecturas, réplica y rol auditor]] · [[ADR-032 Aplicación del esquema]] ·
[[Contrato de implementación para IA]] · `kyc-onboarding` · `extraccion-de-datos`
