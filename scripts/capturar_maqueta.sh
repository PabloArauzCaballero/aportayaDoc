#!/usr/bin/env bash
# ============================================================================
# Captura pantallas de la maqueta con Chrome en modo headless.
# ----------------------------------------------------------------------------
# Existe porque revisar la maqueta "a ojo" en el navegador no deja rastro y se
# escapan cosas: un saldo que da negativo, una barra de avance que se convierte
# en una mancha, una frase partida en pedazos por un flex. Esto la abre en una
# pantalla de celular, salta a la pantalla que se le pida y deja un PNG.
#
#   scripts/capturar_maqueta.sh inicio salida.png [optimista|adverso]
#   scripts/capturar_maqueta.sh extracto movs.png adverso
# ============================================================================
set -euo pipefail

PANTALLA="${1:?falta la pantalla: inicio, extracto, aportes, grupo, perfil, sorteo…}"
SALIDA="${2:?falta el archivo de salida}"
CLIMA="${3:-optimista}"

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FUENTE="$RAIZ/docs/Views/AportaYa-Maqueta.html"
TMP="$(mktemp -t maqueta).html"
trap 'rm -f "$TMP"' EXIT

CHROME=""
for c in "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
         "/Applications/Chromium.app/Contents/MacOS/Chromium" \
         "$(command -v google-chrome || true)" "$(command -v chromium || true)"; do
  [ -n "$c" ] && [ -x "$c" ] && CHROME="$c" && break
done
[ -n "$CHROME" ] || { echo "No encontré Chrome ni Chromium" >&2; exit 1; }

python3 - "$FUENTE" "$TMP" "$PANTALLA" "$CLIMA" <<'PY'
import sys, io
fuente, tmp, pantalla, clima = sys.argv[1:5]
html = io.open(fuente, encoding="utf-8").read()
# Se oculta el banco de aparatos y se estira el teléfono para que entre toda la
# pantalla en la captura, sin scroll.
extra = """
<style>
  body{background:#EDEBE1!important;background-image:none!important}
  .barra,.consola,.preguntas,.pie,.banco>section:nth-child(2){display:none!important}
  .banco{grid-template-columns:1fr!important;padding:0!important;max-width:none!important}
  .banco>section:first-child .pieza__cab{display:none!important}
  .tel{max-width:none!important;width:430px!important;margin:0!important;border-radius:0!important;
       padding:0!important;box-shadow:none!important;aspect-ratio:auto!important;height:auto!important}
  .tel__pant{border-radius:0!important;height:auto!important}
  .vista{overflow:visible!important;flex:none!important}
</style>
<script>
  document.addEventListener('DOMContentLoaded', () => setTimeout(() => {
    try { aplicarClima('%s'); sesionMovil = true; irM('%s'); }
    catch (e) { document.title = 'ERROR ' + e.message; }
  }, 30));
</script>
""" % (clima, pantalla)
io.open(tmp, "w", encoding="utf-8").write(html + extra)
PY

"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
          --virtual-time-budget=6000 --window-size=430,2400 \
          --screenshot="$SALIDA" "file://$TMP" 2>/dev/null

echo "$SALIDA · pantalla $PANTALLA · escenario $CLIMA"
