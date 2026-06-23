# Gloom (portage Java) — compilation & build

Projet Gradle du portage Java de *Gloom*. Ce fichier explique **comment compiler les sources**
et **comment produire un build complet en s'appuyant sur le dépôt original `GloomAmiga`** (qui
fournit les assets : maps, textures, sprites, sons, palettes, tables…).

> Vue d'ensemble du portage (architecture, avancement) : voir `../README.md`.

---

## 1. Prérequis

- **JDK 21** (testé avec Eclipse Adoptium 21.0.3). La *toolchain* Gradle est figée sur la
  version 21 (`build.gradle`), et `jpackage` (inclus dans le JDK 21) sert au packaging.
- **Gradle 9.x** (testé 9.5.1). Pas de wrapper fourni : utilisez un Gradle système, ou ajoutez
  un wrapper avec `gradle wrapper` puis lancez `./gradlew …`.
- **OS** : Windows x64 par défaut (natives LWJGL `natives-windows`). Pour Linux/macOS, voir §6.
- Les dépendances **LWJGL 3.3.4** (GLFW/OpenGL/OpenAL) sont récupérées depuis Maven Central
  au premier build (connexion requise une fois).

---

## 2. Disposition des dossiers (IMPORTANT)

Le moteur lit ses assets **directement dans le dépôt `GloomAmiga`** — le dépôt **original** de
Gloom : <https://github.com/earok/GloomAmiga> (sources `.s`/`.bb2` + tous les assets). Par
défaut, ce dépôt doit être un **frère** de `gloom-java` :

```
Gloom/                     ← dossier parent
├── GloomAmiga/            ← dépôt ORIGINAL — git clone https://github.com/earok/GloomAmiga
│   ├── maps/  objs/  ggfx/  txts/  pics/  sfxs/  misc/  fonts/  …
│   └── title  title.pal  *.bin  medplay  …
└── gloom-java/
    └── java/              ← CE projet Gradle (build.gradle, src/)
```

Pour le récupérer :

```bash
cd Gloom/                                       # le parent de gloom-java
git clone https://github.com/earok/GloomAmiga
```

Au lancement, les tâches Gradle s'exécutent avec `workingDir = gloom-java/` et le code résout
les assets en `../GloomAmiga` (→ `Gloom/GloomAmiga`). **Sans ce dépôt voisin, le jeu démarre
mais ne trouve aucun asset.**

Pour pointer ailleurs (dépôt à un autre emplacement), passez la propriété :

```bash
gradle run -Dgloom.assets=/chemin/vers/GloomAmiga
```

---

## 3. Compiler les sources

Toutes les commandes se lancent depuis `gloom-java/java/` :

```bash
gradle compileJava        # compile uniquement (sortie dans build/classes)
gradle build              # compile + jar (build/libs/gloom.jar) + vérifications
gradle clean              # nettoie build/
```

La disposition source est l'historique NetBeans (`src/`, et non `src/main/java`) — c'est
déclaré dans `build.gradle` (`sourceSets.main.java.srcDirs = ['src']`).

---

## 4. Lancer le jeu (depuis les sources)

```bash
gradle run                       # PARTIE COMPLÈTE — variante gloom.s (chunky, focshft 6)
gradle run2                      # variante gloom2.s (planaire/AGA : focshft 7 + castrots128)
gradle run -Dscale=4             # fenêtre plus grande (résolution interne 320×240 × 4)
gradle run -Dmap=map1_3 -Dtile=1 # charge un seul niveau (test/visite)
gradle run -Dmedspeed=0.9        # ajuste le tempo de la musique MED
```

Options reconnues : `-Dw=` `-Dh=` `-Dscale=` `-Dmap=` `-Dtile=` `-Dengine=gloom2` `-Dmedspeed=`
`-Dgloom.assets=`.

**Tests / harnais** (chacun doit afficher `TOUT OK` ou écrire un PNG/WAV de démonstration) :

```bash
gradle checkLayout       # garde-fou de disposition mémoire
gradle monsterTest       # IA monstres + boss
gradle medTest           # lecteur de musique MED → med1.wav
gradle levelRenderTest -Dmap=map1_3   # rend une frame → level.png
# … (voir `gradle tasks --group verification` pour la liste complète)
```

---

## 5. Produire un build distribuable (jpackage)

`jpackage` crée une **application native autonome** : exécutable + **JRE embarqué** + jars LWJGL
(et natives). Aucun Java n'est requis sur la machine cible.

```bash
gradle jpackage           # → build/jpackage/Gloom/  (Windows : Gloom.exe)
gradle jpackageInstaller  # installeur natif : .msi (Windows, nécessite WiX), .dmg, .deb
```

> ⚠️ **Licence** : seuls les sources `.s`/`.bb2` de Gloom sont dans le domaine public. **Les
> assets (graphismes, sons, maps…) ne sont PAS redistribuables.** Le paquet **n'embarque donc
> PAS les assets** : il inclut un script qui les **télécharge** depuis le dépôt original.

Contenu du paquet `build/jpackage/Gloom/` :

```
Gloom/
├── Gloom.exe          ← lanceur natif (JRE embarqué)
├── fetch-assets.bat   ← télécharge les assets (Windows)
├── fetch-assets.sh    ← idem (Linux/macOS)
├── app/               ← gloom.jar + jars LWJGL + Gloom.cfg
└── runtime/           ← JRE
```

**Côté utilisateur final** (deux étapes) :

1. Lancer **`fetch-assets.bat`** (ou `.sh`) **une fois** : il récupère les assets dans
   `app/GloomAmiga`.
   - **Avec git** : `git clone git@github.com:earok/GloomAmiga.git` (repli HTTPS automatique).
   - **Sans git** : repli automatique sur le **téléchargement du ZIP** via `curl` + `tar`
     (tous deux inclus dans Windows 10/11 ; `curl`/`wget` + `unzip`/`tar` sous Linux/macOS).
   Aucun outil à installer dans le cas standard.
2. Lancer **`Gloom.exe`**.

Comment ça marche : l'exécutable est configuré avec `-Dgloom.assets=$APPDIR/GloomAmiga`
(le lanceur jpackage résout `$APPDIR` vers le dossier `app/` au runtime), et le script clone
précisément dans `app/GloomAmiga`. Le jeu y trouve donc ses assets.

Le build, lui (cf. `build.gradle`) : `jpackageStage` rassemble le jar + les dépendances LWJGL
(sans assets), `jpackage` empaquette en app-image, puis copie `fetch-assets.bat`/`.sh`
(dossier `dist/`) à la racine du paquet.

---

## 6. Construire pour une autre plateforme (Linux / macOS)

Les natives LWJGL sont figées sur Windows. Pour cibler une autre plateforme, changez le
classifier dans `build.gradle` :

```groovy
ext {
    lwjglVersion = '3.3.4'
    lwjglNatives = 'natives-linux'      // ou 'natives-macos' / 'natives-macos-arm64'
}
```

puis relancez `gradle run` / `gradle jpackage` **sur la plateforme cible** (jpackage produit un
paquet pour l'OS sur lequel il s'exécute). `jpackageInstaller` choisit automatiquement le type
(`msi`/`dmg`/`deb`) selon l'OS.
