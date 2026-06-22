# Gloom (portage Java) — compilation & build

Projet Gradle du portage Java de *Gloom*. Ce fichier explique **comment compiler les sources en s'appuyant sur le dépôt original `GloomAmiga`** (qui
fournit les assets : maps, textures, sprites, sons, palettes, tables…).

--

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
