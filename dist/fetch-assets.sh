#!/bin/sh
# ===========================================================================
#  Gloom (portage Java) — récupération des assets
# ---------------------------------------------------------------------------
#  Les assets de Gloom (graphismes, sons, maps…) ne sont PAS redistribués avec
#  ce paquet. Ce script les récupère depuis le dépôt original :
#      https://github.com/earok/GloomAmiga
#
#  Méthode 1 : git (si installé).
#  Méthode 2 (sans git) : téléchargement du ZIP via curl|wget + tar|unzip.
#
#  À lancer UNE FOIS, puis lancer le jeu.
# ===========================================================================
DIR="$(cd "$(dirname "$0")" && pwd)"

# emplacement de $APPDIR selon la structure du paquet jpackage
if [ -d "$DIR/app" ]; then APPDIR="$DIR/app"            # Windows / dossier "app"
elif [ -d "$DIR/lib/app" ]; then APPDIR="$DIR/lib/app"  # Linux app-image
else APPDIR="$DIR"; fi
DEST="$APPDIR/GloomAmiga"

if [ -d "$DEST/maps" ]; then
  echo "Assets déjà présents : $DEST"
  exit 0
fi
rm -rf "$DEST"

# --- Méthode 1 : git ---
if command -v git >/dev/null 2>&1; then
  echo "[git] Récupération depuis github.com/earok/GloomAmiga ..."
  git clone --depth 1 git@github.com:earok/GloomAmiga.git "$DEST" 2>/dev/null \
    || { rm -rf "$DEST"; git clone --depth 1 https://github.com/earok/GloomAmiga.git "$DEST"; }
  [ -d "$DEST/maps" ] && { echo "OK. Lancez le jeu."; exit 0; }
  rm -rf "$DEST"
fi

# --- Méthode 2 : ZIP (sans git) ---
echo "[zip] Téléchargement du ZIP (sans git) ..."
ZIP="${TMPDIR:-/tmp}/GloomAmiga.zip"
URL_MASTER="https://github.com/earok/GloomAmiga/archive/refs/heads/master.zip"
URL_MAIN="https://github.com/earok/GloomAmiga/archive/refs/heads/main.zip"
if command -v curl >/dev/null 2>&1; then
  curl -fL -o "$ZIP" "$URL_MASTER" || curl -fL -o "$ZIP" "$URL_MAIN"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$ZIP" "$URL_MASTER" || wget -O "$ZIP" "$URL_MAIN"
else
  echo "ECHEC : ni git, ni curl, ni wget. Installez l'un d'eux puis relancez."
  exit 1
fi
[ -f "$ZIP" ] || { echo "ECHEC : téléchargement impossible."; exit 1; }

echo "Extraction ..."
if command -v unzip >/dev/null 2>&1; then unzip -q "$ZIP" -d "$APPDIR"; else tar -xf "$ZIP" -C "$APPDIR"; fi
# le ZIP s'extrait en GloomAmiga-master (ou -main) : on renomme en GloomAmiga
mv "$APPDIR"/GloomAmiga-* "$DEST" 2>/dev/null
rm -f "$ZIP"

if [ -d "$DEST/maps" ]; then echo "OK. Lancez le jeu."; else echo "ECHEC : assets introuvables."; exit 1; fi
