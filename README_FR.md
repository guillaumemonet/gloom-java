# Gloom — portage Java

*Read this in [English](README.md).*

Portage en Java de **Gloom** (Black Magic Software, Amiga 1995), le premier clone de Doom sorti
sur Amiga. Le moteur est réécrit **ligne à ligne depuis l'assembleur 68020 d'origine**
([earok/GloomAmiga](https://github.com/earok/GloomAmiga)) — pas d'émulateur, pas de
réinterprétation : le code Java suit l'asm instruction par instruction, et les sources `.s`
restent la référence en cas de doute.

Le moteur d'origine est un rasteriseur logiciel qui « dessine » sa scène dans une **copperlist** :
un poke de registre couleur par pixel, que le copper Amiga rejoue à l'écran. Le portage jette ce
chemin d'affichage — il rend dans un framebuffer RGB classique — et garde ce qui compte : la
mémoire, l'arithmétique, les formats de données et les règles du jeu, prises dans l'assembleur
plutôt que redevinées.

![Écran titre](docs/img/ecran-titre.png)

Porté par **Guillaume Monet**.

---

## Deux moteurs, une seule simulation

Au lancement, un écran propose deux rendus. Ils partagent **exactement** le même code de jeu :
même RAM 68k émulée, mêmes listes d'objets, même IA, même script de campagne. Seul l'affichage
change.

| | **GLOOM CLASSIC** | **GLOOM REBIRTH** |
|---|---|---|
| rendu | le rasteriseur d'origine, porté | scène 3D réelle (jMonkeyEngine) |
| résolution interne | 320 × 240 | celle de l'écran |
| ce qu'on y gagne | la fidélité au pixel | éclairage dynamique, ombres, bloom, mouselook |

| CLASSIC (2D) | REBIRTH (3D) |
|---|---|
| ![Gloom Classic](docs/img/classic-2d.png) | ![Gloom Rebirth](docs/img/rebirth-3d.png) |

Le HUD, les menus et les écrans d'histoire sont ceux du jeu, décodés depuis ses propres données
(images IFF, fonte Amiga à 7 plans) :

| HUD en jeu | Écran d'histoire |
|---|---|
| ![HUD](docs/img/classic-hud.png) | ![Écran d'histoire](docs/img/ecran-histoire.png) |

Deux variantes du rasteriseur sont portées : `gloom.s` (chunky, `focshft 6`, FOV large) et
`gloom2.s` (planaire/AGA, `focshft 7` + `castrots128`, projection « Deluxe » moins déformée).
Elles partagent le même rasteriseur : seules la focale et la table de rayons changent.

---

## Les choix du portage

### La RAM 68k comme un simple tableau d'octets

Toute la mémoire du jeu est **un `byte[]` de 64 Mo, gros-boutiste et plat** (`gloom.Mem`). Les
registres `d0-d7`/`a0-a6` deviennent des `int`, et tous les accès passent par des helpers typés
(`Mem.b/w/l`, `ub/uw`, `wb/ww/wl`) qui reproduisent l'ordre des octets et l'arithmétique du 68k.
`gloom.M68k` fait le reste (`swap`, `muls/mulu`, `divs/divu`, extensions de signe, décalages `.w`).

C'est ce qui rend le portage ligne à ligne possible : une structure de l'asm reste à **la même
adresse et au même offset** qu'à l'origine, y compris ses bizarreries. Et il y en a — dans Gloom,
`ob_nxvec` et `ob_lives` sont le **même mot** (`rs.w 0`), tout comme `ob_nzvec` et `ob_infra`.
Un portage « propre » aurait séparé ces champs et cassé silencieusement le jeu.

Le garde-fou : `gradle checkLayout` vérifie que chaque offset de structure correspond au fichier
`.s`. Il doit afficher `TOUT OK`.

### Ce qui n'est PAS porté : la couche matérielle

Le copper, le blitter, la C2P, les registres audio Paula et les interruptions CIA n'ont pas
d'équivalent utile ici. À la place, une **couche hôte** mince : LWJGL 3 (GLFW/OpenGL/OpenAL) sur
PC, les API Android sur téléphone. Le moteur ne la connaît pas.

Le découplage est réel et mesurable : sur les ~13 500 lignes du portage, seuls **quatre fichiers**
dépendent de LWJGL (`host/Main`, `host/Display`, `host/Audio`, `rebirth/DesktopGlue`), et l'APK
Android compile les **mêmes sources** que le desktop, sans une ligne dupliquée. Deux interfaces
suffisent à isoler le reste :

- `HostGlue` — les trois choses de la vue 3D qui ne sont pas portables (verrou du curseur et taille
  du framebuffer via GLFW, écriture d'une capture PNG via AWT) ;
- `AudioBackend` — OpenAL sur PC, `AudioTrack` sur Android.

### L'audio refait à la manière de Paula

Le son n'est pas délégué à une bibliothèque de plus haut niveau. Les échantillons sont au format
Gloom (`[période][longueur en mots][PCM 8 bits signé]`), et la **période Paula** donne la fréquence
de lecture (`3546895 / période`). Côté Android, les 4 voies DMA sont rejouées à la main, avec un
pas en virgule fixe 16.16 et les mêmes règles d'attribution de voie (une libre, sinon la moins
prioritaire, sinon rien) — puis mixées avec la musique dans un unique flux.

La musique MED (MMD0/MMD1) est un lecteur écrit pour le portage : séquenceur + mixeur 4 voies,
le replayer d'origine étant un blob 68k non portable.

### Une seule simulation pour les deux rendus

La vue 3D n'est pas un second jeu. Elle **lit l'état du monde dans la RAM 68k** à chaque frame et
le traduit en scène jMonkeyEngine : murs extrudés depuis les zones de la map, sprites d'origine en
billboards (avec la sélection de frame à 8 directions), décals de gore au sol, gouttes de sang en
un seul mesh. La simulation tourne à pas de temps fixe (1/60 s), donc le gameplay garde sa vitesse
quel que soit le framerate.

### Android : ce que la mesure a appris

Le portage tourne sur téléphone (mesuré sur un Xiaomi : 60 images/s en 2D, ~55 en 3D). Deux
constats non évidents, obtenus en instrumentant plutôt qu'en devinant :

- **La 2D tournait à moitié vitesse** parce que `lockCanvas()` rend un canvas *logiciel* :
  l'agrandissement du framebuffer 320 × 240 vers le plein écran se faisait au processeur.
  `Surface.lockHardwareCanvas()` a suffi (35 → 60 images/s).
- **En 3D, la géométrie ne coûte rien** — 204 triangles, 27 appels de dessin : c'est bien un jeu de
  1995. Tout le coût est *par pixel* : 2,6 Mpx contre 0,077 sur Amiga, avec un éclairage à 14
  lumières. Le seul effet qui faisait déborder le budget d'image était le **bloom en pleine
  résolution** ; le calculer au quart de résolution le rend gratuit sans changer son aspect.

---

## Ce qui marche

- Les **niveaux s'enchaînent** : écran-titre, menu, écrans d'histoire, campagne complète pilotée
  par le script d'origine, points de contrôle et reprise (« CONTINUE FROM… »).
- **Déplacement et collision** suivant les règles de l'asm (glissement le long des murs, écrasement),
  tir des cinq armes avec leurs dégâts, cadences et sons.
- **IA par type** : marine, baldy, terra, ghoul, phantom, demon, lizard, troll — chacun a sa logique
  d'origine (charge, tir, esquive). Boss : le dragon (cercles + missiles à tête chercheuse) et la
  deathhead (aspiration d'âme, qui traîne le joueur vers elle).
- **Gore complet** : étincelles d'impact, 24 gouttes de sang *à chaque coup encaissé*, gibs avec
  gravité, décals persistants au sol, éclaboussure sur la vue quand une goutte frôle la caméra.
- Les monstres **bronchent** quand on les touche : sonnés et intouchables un court instant.
- **Powerups** : arme, thermo, invisibilité, invincibilité « hyper », balles rebondissantes, avec
  leurs timers, leurs messages et leurs avertissements d'expiration.
- **Géométrie dynamique** : portes, polygones en rotation, animations de textures, zones-trigger
  (embuscades, téléports, sortie).
- **Audio** : tous les effets, les voix d'ambiance des monstres, et la musique MED.
- **Vue 3D** : éclairage dynamique (torche, flash de bouche, balles), textures HD optionnelles,
  vision thermique à travers les murs, menu d'options persistant, contrôles remappables.
- **Android** : un seul APK, les deux moteurs, commandes tactiles, assets récupérés au premier
  lancement.

## Ce qui manque

- Le **mode 2 joueurs** (lien série modem) et le chat — hors périmètre, comme le reste de `ap.s`.
- Le **mini-jeu Defender** (`combatok`).
- Le fondu du volume de la musique entre les niveaux (`fadevol`).
- Les sons de coup sont fidèles, mais les **voix d'ambiance** de quelques monstres restent à vérifier
  en situation.
- `ob_infra` : vestige de l'original — le bonus « infrarouge » y donne en réalité les lunettes
  thermo, et la variable n'est lue nulle part. Reproduit tel quel.

---

## Lancer le jeu

### Depuis les sources

Il faut le dépôt d'assets **à côté** de celui-ci (les assets ne sont pas redistribuables, voir
plus bas) :

```
Gloom/
├── GloomAmiga/     ← git clone https://github.com/earok/GloomAmiga
└── gloom-java/
    └── java/       ← ce projet Gradle
```

Puis, depuis `java/` :

```bash
gradle run                        # partie complète (launcher 2D / 3D)
gradle run2                       # variante gloom2.s (focshft 7 + castrots128)
gradle rebirth                    # vue 3D directement
gradle run -Dscale=4              # fenêtre plus grande (interne 320×240)
gradle run -Dmap=map1_3 -Dtile=1  # un seul niveau (test/visite)
```

Options : `-Dw= -Dh= -Dscale= -Dmap= -Dtile= -Dengine=gloom2 -Dmedspeed= -Dgloom.assets=`.

**Prérequis** : JDK 21 (la toolchain Gradle est figée dessus, et `jpackage` en vient), Gradle 9.x.
Les natives LWJGL sont réglées sur Windows x64 ; pour Linux/macOS, changez `lwjglNatives` dans
`build.gradle` et construisez **sur la plateforme cible**.

**Contrôles** — `W`/`S` ou `↑`/`↓` avancer/reculer, `A`/`D` pas de côté, `←`/`→` tourner, clic
gauche / `Ctrl` / `Espace` tirer et valider, `Échap` quitter. En 3D, la souris vise et les touches
sont remappables dans le menu OPTIONS.

### Android

Un seul APK contient les deux moteurs. **Pouce gauche** : avancer, reculer, tourner. **Pouce
droit** : pas de côté (◀ ▶) et tir. **Bouton MENU** en haut à droite : recule d'un cran
(partie → menu → launcher → quitter), comme la touche retour.

Android 8.0 minimum. Les assets sont téléchargés au premier lancement (réseau nécessaire cette
fois-là seulement).

---

## Comment c'est vérifié

Il n'y a pas de tests unitaires au sens habituel : la référence n'est pas une spécification, c'est
un fichier assembleur. Chaque sous-système a donc son **harnais**, qui rejoue un scénario et
compare le comportement à celui décrit par l'asm. Une trentaine au total, tous dans
`src/gloom/tools/` :

```bash
gradle checkLayout        # disposition mémoire : chaque offset contre le .s
gradle mathTest           # RNG, angles, calcangle
gradle mapTest            # chargement map + textures
gradle goreTest           # sang, gibs, décals, flinch → gore.png
gradle monsterTest        # IA par type de monstre
gradle bossTest           # dragon et deathhead
gradle powerupTest        # powerups, messages, objets animés, aspiration
gradle medTest            # lecteur MED → med1.wav
gradle levelRenderTest    # rend une frame d'un vrai niveau → level.png
gradle rebirth -Dshot     # capture de la vue 3D → rebirth.png
```

Chacun affiche `TOUT OK` ou écrit un PNG/WAV de démonstration. `gradle tasks --group verification`
donne la liste complète.

---

## Structure

| dossier | contenu |
|---|---|
| `src/gloom/` | le moteur : `Mem`, `M68k`, `Render`, `Objects`, `Player`, `Map`, `Events`, `Sfx`, `MedPlayer`… |
| `src/gloom/host/` | couche hôte PC (LWJGL) : fenêtre, audio, boucle de jeu, HUD, menus, séquenceur |
| `src/gloom/rebirth/` | la vue 3D jMonkeyEngine |
| `src/gloom/data/` | sections `.data` de l'asm : tables précalculées, `objinfo` |
| `src/gloom/tools/` | les harnais de vérification |
| `android/` | module Android (compile `../src` tel quel) |
| `scripts/` | outillage annexe (upscale IA des textures pour le mode HD) |
| `dist/` | scripts de récupération des assets livrés avec le paquet |

---

## Distribution

```bash
gradle jpackage           # app native autonome → build/jpackage/Gloom/Gloom.exe (JRE embarqué)
gradle jpackageInstaller  # installeur natif : .msi (WiX requis), .dmg, .deb
cd android && gradle assembleRelease    # APK Android
```

Le paquet contient l'exécutable, le JRE, les jars, et **`fetch-assets.bat`/`.sh`** — mais **pas les
assets** (voir ci-dessous). L'utilisateur lance le script une fois, puis le jeu.

Les binaires prêts à l'emploi sont dans les
[releases](https://github.com/guillaumemonet/gloom-java/releases).

---

## Licence et crédits

*Gloom* est © **Black Magic Software**. Les **sources `.s`/`.bb2` d'origine sont dans le domaine
public** (cf. le dépôt `GloomAmiga`) et constituent la référence de ce portage.

Les **assets** — graphismes, sons, maps — **ne le sont pas** et ne sont **jamais redistribués**
ici : ni le dépôt, ni le paquet PC, ni l'APK ne les contiennent. Ils sont récupérés depuis
[earok/GloomAmiga](https://github.com/earok/GloomAmiga), dépôt de préservation, au premier
lancement.

Moteur d'origine : **Black Magic Software** (1995).
Portage Java : **Guillaume Monet**.

Le portage a été mené avec l'assistance d'une IA (Claude, Anthropic) : écriture du code de
traduction, harnais de vérification et instrumentation. Les choix d'architecture, la validation
contre l'assembleur d'origine et le résultat restent ceux de l'auteur.
