package gloom.host;

import gloom.Defs;
import gloom.Events;
import gloom.Files;
import gloom.Map;
import gloom.Mem;
import gloom.Objects;
import gloom.Palette;
import gloom.Render;
import gloom.Vars;
import gloom.data.ObjInfo;

/**
 * Orchestration de niveau (sans LWJGL) : charge une vraie map Gloom + ses textures + ses
 * objets, construit la palette, et rend une frame complète.
 *
 * Réplique la séquence de chargement (loadtile → initmap → loadtxts → calcpalettes → ...),
 * spawne les objets de l'événement de départ (execevent), et utilise l'objet joueur spawné
 * comme caméra (son eyey=-110 donne la bonne hauteur d'œil). renderFrame = drawscene.
 */
public final class LevelScene {

    public int player;          // objet joueur spawné (caméra)
    public int width, height;

    /** Setup permanent fait une seule fois (map_rgbs persistant + objets permanents remappés). */
    private static boolean permInit = false;

    /** Variante de moteur : false=gloom.s (chunky, focshft 6), true=gloom2.s (planaire/AGA, focshft 7).
     *  Sélectionnée via -Dengine=gloom2. Par défaut gloom.s (tests + run classique). */
    public static boolean gloom2 = "gloom2".equals(System.getProperty("engine"));

    public void init(int w, int h, String mapName, String tile) {
        width = w; height = h;
        Render.setupFramebuffer(w, h);
        Render.configure(gloom2);            // focshft + table castrots selon la variante
        Mem.ww(Vars.mode, 1);                // mode gore « les restes restent » (décals au sol) par défaut
        gloom.Maths.seedrnd(12345);          // RNG (rnddelay, dispersion de tir…)
        gloom.Maths.seedrnd2(54321);

        // ----- setup PERMANENT (une fois) : palette globale + objets toujours présents -----
        // Réplique gloom.s:9377-9476 : map_rgbs est alloué UNE fois ; les armes (balles/sparks),
        // le joueur et les tokens (santé/powerups) sont remappés UNE fois et occupent des index
        // FIXES [map_rgbs+2 .. map_rgbsfrom). Les niveaux suivants n'y touchent plus → pas de saut.
        if (!permInit) {
            Mem.wl(Vars.map_rgbs, Mem.alloc(8192 * 2));
            Mem.wl(Vars.map_rgbsw, Mem.alloc(8192 * 2));
            Mem.wl(Vars.map_rgbsr, Mem.alloc(8192 * 2));
            int a0 = Mem.l(Vars.map_rgbs);
            Mem.ww(a0, -1);                                       // move #-1,(a0)+ (couleur drapeau)
            Mem.wl(Vars.map_rgbsat, a0 + 2);
            remapWeapons();                                       // armes : index permanents
            Objects.loadanobj(0);                                 // player1 (sprite joueur)
            Objects.loadanobj(2);                                 // tokens (santé + powerups partagent ce sheet)
            Mem.wl(Vars.map_rgbsfrom, Mem.l(Vars.map_rgbsat));    // fin des permanents
            permInit = true;
        }

        // ----- setup PAR NIVEAU : la palette repart APRÈS les permanents -----
        // (loadtile fait map_rgbsat = map_rgbsfrom puis pose map_rgbsfrom2 après la tuile, cf. 10163)
        loadtile(tile);                                           // tuile sol/plafond → map_rgbsfrom2

        int map = Files.loadfile(nameAddr("maps/" + mapName), 1);
        if (map == 0) throw new IllegalStateException("map introuvable: maps/" + mapName);
        Mem.wl(Vars.map_map, map);
        Map.initmap();
        Map.loadtxts();

        // arène + listes (avant le spawn des objets)
        Mem.wl(Vars.memory, Mem.alloc(512 * 1024));
        Vars.initLists();

        // recharge les sprites des MONSTRES (descripteurs vidés → données fraîches remappées contre
        // la palette de CE niveau), comme freeobjlist+loadobjs. AVANT calcpalettes (couleurs → rampes).
        ObjInfo.clearMonsterShapes();
        preloadObjects(1);

        Palette.calcpalettes();
        Palette.makepalettes();
        Palette.initdarktable();
        Mem.wl(Vars.palette, Vars.palettes);

        // spawn des objets de l'événement de départ (joueur, monstres, items, portes, rotpolys)
        // Mono-joueur (scriptplay) : clr player1 ; not.l player2 → le slot joueur 2 est « occupé »
        // (-1) donc exec_addobj saute le spawn du 2e joueur (sinon il apparaît dans le niveau).
        Mem.wl(ObjInfo.player1, 0);
        Mem.wl(ObjInfo.player2, -1);
        Mem.wl(Vars.memat, Mem.l(Vars.memory));
        Events.execevent(1);

        player = Mem.l(ObjInfo.player1);
        if (player == 0) player = makeFallbackPlayer();   // map sans joueur : caméra de secours
    }

    /**
     * Remappe les sprites d'armes (balles/sparks) vers la palette globale (gloom.s:9421-9440).
     * Appelé UNE seule fois (setup permanent) : les armes occupent des index de palette FIXES,
     * comme dans l'original. reloadWeapons recharge des données fraîches (remap est destructif).
     */
    private void remapWeapons() {
        gloom.data.Tables.reloadWeapons();
        int[] w = {gloom.data.Tables.bullet1, gloom.data.Tables.bullet2, gloom.data.Tables.bullet3,
                gloom.data.Tables.bullet4, gloom.data.Tables.bullet5, gloom.data.Tables.sparks1,
                gloom.data.Tables.sparks2, gloom.data.Tables.sparks3, gloom.data.Tables.sparks4,
                gloom.data.Tables.sparks5};
        for (int a : w) Objects.remapanim(a);                     // remap vers la palette du niveau
    }

    private void loadtile(String tile) {
        int fl = Files.loadfile(nameAddr("txts/floor" + tile), 1);
        int rf = Files.loadfile(nameAddr("txts/roof" + tile), 1);
        Mem.wl(Vars.floor, fl);
        Mem.wl(Vars.roof, rf);
        Mem.wl(Vars.map_rgbsat, Mem.l(Vars.map_rgbsfrom));
        if (fl != 0) { int pal = fl + 128 * 128; Map.addpal(pal); Map.remap(fl, pal); }
        if (rf != 0) { int pal = rf + 128 * 128; Map.addpal(pal); Map.remap(rf, pal); }
        Mem.wl(Vars.map_rgbsfrom2, Mem.l(Vars.map_rgbsat));
        Mem.ww(Vars.floorflag, fl != 0 ? 1 : -1);
        Mem.ww(Vars.roofflag, rf != 0 ? 1 : -1);
    }

    /** Charge les sprites des objets référencés par les loadobjs de l'événement `ev`. */
    private void preloadObjects(int ev) {
        int events = Mem.l(Vars.map_events);
        int a6 = Mem.l(Vars.map_map) + Mem.l(events + ev * 4);
        for (int guard = 0; guard < 100000; guard++) {
            int op = Mem.uw(a6); a6 += 2;
            if (op == 0) break;
            switch (op) {
                case 1 -> a6 += 10;                       // addobj
                case 2 -> a6 += 2;                        // opendoor
                case 3 -> a6 += 8;                        // teleport
                case 5 -> a6 += 4;                        // changetxt
                case 6 -> a6 += 8;                        // rotpolys
                case 4 -> {                               // loadobjs : charge chaque type
                    while ((short) Mem.uw(a6) >= 0) { Objects.loadanobj(Mem.uw(a6)); a6 += 2; }
                    a6 += 2;
                }
                default -> { return; }
            }
        }
    }

    /** Rend une frame (drawscene, gloom.s:2709). */
    public void renderFrame() {
        Mem.wl(Vars.memat, Mem.l(Vars.memory));           // reset arène frame
        Objects.calcscene(player);                        // palette/caméra/murs + sprites objets→shapelist
        Render.clearFramebuffer(0);
        Render.castwalls();
        Render.makeqstrip();
        Render.renderwalls();
        int camy = (short) Mem.w(Vars.camy);
        if ((short) Mem.w(Vars.floorflag) > 0) Render.flat(-camy, -1, Mem.w(Vars.maxy) - 1, Mem.l(Vars.floor));
        if ((short) Mem.w(Vars.roofflag) > 0) Render.flat(-255 - camy, 1, Mem.w(Vars.miny), Mem.l(Vars.roof));
        Render.drawshapes();                              // sprites objets + strips see-through
        Render.drawblood();
    }

    /**
     * Input clavier → buffer de contrôle joyx0[0] (lu par getcntrl→playerlogic).
     * joyx = tourner (±1), joyy = avant(-1)/arrière(+1), joyb = feu, joys = mode strafe (joyx devient strafe).
     */
    public void setInput(int joyx, int joyy, int joyb, int joys) {
        Mem.ww(Vars.joyx0 + 0, joyx);
        Mem.ww(Vars.joyx0 + 2, joyy);
        Mem.ww(Vars.joyx0 + 4, joyb);
        Mem.ww(Vars.joyx0 + 6, joys);
    }

    /** Une étape de jeu : logique (joueur + monstres + collision) une frame sur deux, comme l'original. */
    public void tick() {
        int fc = (Mem.uw(Vars.framecnt) + 1) & 0xffff;
        Mem.ww(Vars.framecnt, fc);
        if ((fc & 1) != 0) {
            // géométrie dynamique chaque frame (gloom.s:2932-2935, avant obj_loop)
            gloom.Dynamic.doanims();                      // animations de textures
            gloom.Dynamic.dorots();                       // polygones en rotation / morph
            gloom.Dynamic.dodoors();                      // ouverture/fermeture des portes
            Objects.moveblood();                          // physique du sang
            Objects.obj_loop();
            int p = Mem.l(ObjInfo.player1);
            if (p != 0) player = p;
        }
    }

    private int makeFallbackPlayer() {
        int p = Mem.alloc(Defs.ob_size);
        Mem.ww(p + Defs.ob_eyey, -110);
        int events = Mem.l(Vars.map_events);
        int a6 = Mem.l(Vars.map_map) + Mem.l(events + 4);
        // (caméra de secours : origine grille)
        Mem.wl(p + Defs.ob_x, (16 * 256 + 128) << 16);
        Mem.wl(p + Defs.ob_z, (16 * 256 + 128) << 16);
        return p;
    }

    private static int nameAddr(String name) {
        int a = Mem.alloc(name.length() + 1);
        Mem.load(a, name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        Mem.wb(a + name.length(), 0);
        return a;
    }
}
