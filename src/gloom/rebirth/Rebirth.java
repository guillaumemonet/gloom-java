package gloom.rebirth;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.TechniqueDef;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.SceneProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FogFilter;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.control.BillboardControl;
import com.jme3.system.AppSettings;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.jme3.util.Screenshots;

import gloom.Assets;
import gloom.Defs;
import gloom.Mem;
import gloom.Objects;
import gloom.Sfx;
import gloom.Vars;
import gloom.data.ObjInfo;
import gloom.data.Tables;
import gloom.host.Audio;
import gloom.host.LevelScene;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * « Gloom Rebirth » — vue 3D (remaster) basée jMonkeyEngine, branche `rebirth`.
 *
 * Étape 4 : JOUABLE. La SIMULATION 2D portée ({@link LevelScene}) reste l'état autoritaire — on lui
 * envoie les contrôles, on la fait tick(), et on lit en retour le joueur (→ caméra) et les objets
 * (→ billboards face caméra, sprites Gloom). Étapes 1-3 : fondation JME, carte→géométrie, textures.
 */
public final class Rebirth extends SimpleApplication {

    private static final float S = 1f / 64f;          // échelle gloom → JME
    private static final float WALL_H = 256f * S;
    private static final boolean HEADLESS = System.getProperty("shot") != null;
    private static final String MAP = System.getProperty("map", "map1_3");
    private static final String TILE = System.getProperty("tile", "1");

    private LevelScene scene;
    private final Node enemies = new Node("enemies");
    private final Node goreNode = new Node("gore");                  // décals de gore au sol (liste Vars.gore)
    private final Node walls = new Node("walls");                    // murs reconstruits chaque frame (portes animées)
    private final Map<Integer, Material> wallMatCache = new HashMap<>();
    private final Map<Integer, Texture2D> wallTexCache = new HashMap<>();
    private final Map<Integer, Texture2D> spriteCache = new HashMap<>();
    private final Map<Integer, float[]> texLight = new HashMap<>();  // texNum → {r,g,b} si texture émissive
    private final Set<Integer> seeThroughTex = new HashSet<>();      // textures à colonnes ajourées (grilles/portes)
    private static final float SPEED = Float.parseFloat(System.getProperty("speed", "1.5"));  // boost de vitesse (-Dspeed)
    private static final float TICK_DT = 1f / (60f * SPEED);       // cadence FIXE : logique 30*SPEED Hz (indépendante du fps)
    private float acc;                                             // accumulateur de temps pour le pas fixe
    private final Map<Integer, Integer> wallTexBase = new HashMap<>();   // texNum → pointeur pixels au build (détection anim)
    private final Map<Integer, Integer> slotOfBase = new HashMap<>();    // pointeur pixels initial → slot (= frame d'anim) pour le HD
    private PointLight torch;                                        // lumière portée par le joueur
    private PointLight muzzle;                                       // flash de bouche (tir)
    private static final int BULLET_LIGHTS = 6;                     // pool de lumières dynamiques des balles
    private PointLight[] bulletLights;                              // une lumière par balle proche
    private float flash;                                             // intensité du flash (décroît)
    private boolean prevFire;                                        // front montant du tir
    private final List<Geometry> spritePool = new ArrayList<>();     // billboards réutilisés (pas de GC/frame)
    private Mesh unitQuad;                                           // quad unité partagé (mis à l'échelle)
    private int frame;
    // souris (mouselook) : delta X accumulé en PIXELS, sensibilité en unités-rot/pixel (-Dmousesens)
    private float mouseDX;
    private long glfwWindow;                                        // handle GLFW (pour verrouiller le curseur)
    private static final float MOUSE_SENS = Float.parseFloat(System.getProperty("mousesens", "0.18"));
    // état des contrôles (held)
    private boolean kFwd, kBack, kLeft, kRight, kStrafeL, kStrafeR, kFire;
    // HUD
    private Geometry hpBar;
    private BitmapText hudText;
    private Geometry redOverlay;                                    // voile rouge plein écran (coups + mort)
    private float redFlash;                                        // intensité du flash de coup (décroît)
    // audio (réutilise la pile host.Audio + MedPlayer + Sfx du port 2D ; JME audio désactivé)
    private Audio audio;

    public static void main(String[] args) {
        Rebirth app = new Rebirth();
        AppSettings s = new AppSettings(true);
        s.setResolution(960, 720);
        s.setTitle("Gloom Rebirth (3D)");
        s.setVSync(true);
        s.setAudioRenderer(null);          // pas d'audio JME : on garde la pile OpenAL de host.Audio
        app.setSettings(s);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);                      // caméra pilotée par la simulation
        setDisplayStatView(false);                     // pas de stats de debug JME
        setDisplayFps(true);                           // compteur FPS (diagnostic de fluidité)
        inputManager.setCursorVisible(false);
        grabCursor();                                  // VERROUILLE la souris dans la fenêtre (mouselook FPS)
        // textures HD optionnelles : PNG dans <cwd>/hd/ (cwd = dépôt assets). Repli sur le procédural.
        assetManager.registerLocator(".", FileLocator.class);

        scene = new LevelScene();
        scene.init(320, 240, MAP, TILE);
        Mem.ww(scene.player + Defs.ob_lives, 5);        // vies de départ (init standalone)
        buildLevel();
        rootNode.attachChild(enemies);
        rootNode.attachChild(goreNode);
        enemies.setShadowMode(ShadowMode.Off);         // pas d'ombre carrée depuis les quads sprites
        goreNode.setShadowMode(ShadowMode.Off);

        // éclairage de base volontairement bas → les point lights créent de vrais halos (ambiance)
        DirectionalLight sun = new DirectionalLight(new Vector3f(-0.4f, -0.9f, -0.3f).normalizeLocal(),
                ColorRGBA.White.mult(0.35f));
        rootNode.addLight(sun);
        rootNode.addLight(new AmbientLight(ColorRGBA.White.mult(0.22f)));
        viewPort.setBackgroundColor(new ColorRGBA(0.02f, 0.02f, 0.04f, 1f));
        // FOV LARGE (~70° vertical) : Gloom a un champ de vision large (fish-eye) ; un FOV étroit
        // fait paraître le déplacement lent. Plan near PROCHE (0.05) : sinon en s'approchant d'un mur
        // le polygone croise le plan near et se fait clipper (le mur « disparaît » de près).
        cam.setFrustumPerspective(70f, (float) cam.getWidth() / cam.getHeight(), 0.05f, 400f);

        // single-pass : permet plusieurs point lights en une passe (perf)
        renderManager.setPreferredLightMode(TechniqueDef.LightMode.SinglePass);
        renderManager.setSinglePassLightBatchSize(16);

        // torche du joueur (suit la caméra) — éclaire la zone proche dans le noir/brouillard
        torch = new PointLight(new Vector3f(), ColorRGBA.White.mult(1.6f), 1100f * S);
        rootNode.addLight(torch);
        // flash de bouche (éteint au repos ; pulse à chaque tir)
        muzzle = new PointLight(new Vector3f(), ColorRGBA.Black, 900f * S);
        rootNode.addLight(muzzle);
        // pool de lumières dynamiques des balles (couleur noire = éteinte quand inutilisée)
        bulletLights = new PointLight[BULLET_LIGHTS];
        for (int i = 0; i < bulletLights.length; i++) {
            bulletLights[i] = new PointLight(new Vector3f(), ColorRGBA.Black, 460f * S);
            rootNode.addLight(bulletLights[i]);
        }

        setupPostFx(sun);
        setupHud();
        bindKeys();
        if (!HEADLESS) initAudio();                    // SFX (Paula→OpenAL) + musique MED
        if (HEADLESS) { spawnDemoEnemy(); attachScreenshot(); }
        updateCamera();
    }

    /**
     * Verrouille le curseur dans la fenêtre (GLFW_CURSOR_DISABLED = caché + mode relatif illimité).
     * setCursorVisible(false) ne fait que masquer, et JME ré-applique SON mode après l'init → on force
     * DISABLED à chaque frame (idempotent : seulement si l'état diffère). Re-grab aussi après alt-tab.
     * glfwGetCurrentContext() = la fenêtre JME (son contexte GL est courant sur ce thread de rendu).
     */
    private void grabCursor() {
        if (glfwWindow == 0L) glfwWindow = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (glfwWindow != 0L
                && org.lwjgl.glfw.GLFW.glfwGetInputMode(glfwWindow, org.lwjgl.glfw.GLFW.GLFW_CURSOR)
                   != org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED) {
            org.lwjgl.glfw.GLFW.glfwSetInputMode(glfwWindow,
                    org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED);
        }
    }

    private void initAudio() {
        try {
            Sfx.loadSamples();
            audio = new Audio();
            Sfx.setSink(audio);
            audio.playMusic(loadModule("sfxs/med1"));
        } catch (Throwable t) {
            System.err.println("[Rebirth] audio indisponible : " + t);
            audio = null;
        }
    }

    private static byte[] loadModule(String name) {
        try { return Assets.read(name); } catch (Exception e) { return null; }
    }

    @Override
    public void destroy() {
        if (audio != null) audio.shutdown();
        super.destroy();
    }

    /** Brouillard d'ambiance (« Gloom » !) + ombres directionnelles. */
    private void setupPostFx(DirectionalLight sun) {
        rootNode.setShadowMode(ShadowMode.CastAndReceive);
        DirectionalLightShadowRenderer dlsr = new DirectionalLightShadowRenderer(assetManager, 512, 1);
        dlsr.setLight(sun);
        dlsr.setShadowIntensity(0.4f);
        viewPort.addProcessor(dlsr);

        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        // brouillard SUBTIL (profondeur d'ambiance) : densité 1.2 faisait un « filtre » devant le joueur.
        FogFilter fog = new FogFilter(new ColorRGBA(0.03f, 0.03f, 0.06f, 1f), 0.5f, 120f);
        fpp.addFilter(fog);
        // bloom : les zones brillantes (balles, panneaux émissifs, halos) « débordent » → look moderne.
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
        bloom.setBloomIntensity(1.3f);
        bloom.setExposurePower(2.2f);
        fpp.addFilter(bloom);
        viewPort.addProcessor(fpp);
    }

    /** HUD overlay (guiNode) : barre de vie + texte PV/vies/arme. */
    private void setupHud() {
        hpBar = new Geometry("hp", new Quad(1, 1));
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", ColorRGBA.Green);
        hpBar.setMaterial(m);
        guiNode.attachChild(hpBar);

        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        hudText = new BitmapText(font);
        hudText.setSize(font.getCharSet().getRenderedSize());
        hudText.setColor(new ColorRGBA(0.85f, 0.55f, 1f, 1f));   // violet, comme Gloom
        guiNode.attachChild(hudText);

        // voile rouge plein écran (équivalent de redpal : flash quand on encaisse, soutenu à la mort)
        redOverlay = new Geometry("dmg", new Quad(cam.getWidth(), cam.getHeight()));
        Material rm = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        rm.setColor("Color", new ColorRGBA(0.75f, 0f, 0f, 0f));
        rm.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        redOverlay.setMaterial(rm);
        redOverlay.setCullHint(Spatial.CullHint.Always);
        guiNode.attachChild(redOverlay);                          // au-dessus : tout l'écran rougit
        updateHud();
    }

    /**
     * Feedback de dégâts (équivalent de redpal, gloom.s:4618) : la simu pose ob_paltimer=2 quand le
     * joueur encaisse un coup → flash rouge ; à la mort (PV≤0) le rouge reste soutenu.
     */
    private void updateDamageFx() {
        int p = scene.player;
        if (Mem.w(p + Defs.ob_paltimer) > 0) redFlash = 0.45f;    // coup/tir encaissé → flash
        redFlash *= 0.86f;                                        // décroissance
        float a = (Mem.w(p + Defs.ob_hitpoints) <= 0) ? 0.55f : (redFlash < 0.02f ? 0f : redFlash);
        ((Material) redOverlay.getMaterial()).setColor("Color", new ColorRGBA(0.75f, 0f, 0f, a));
        redOverlay.setCullHint(a <= 0f ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
    }

    private void updateHud() {
        int p = scene.player;
        int hp = Math.max(0, Mem.w(p + Defs.ob_hitpoints));
        int lives = Mem.w(p + Defs.ob_lives);
        int weapon = Mem.w(p + Defs.ob_weapon) + 1;
        float h = settings.getHeight();
        hpBar.setLocalScale(Math.min(25, hp) * 8f, 14f, 1f);     // barre 0..200 px
        hpBar.setLocalTranslation(14, h - 30, 0);
        ((Material) hpBar.getMaterial()).setColor("Color",
                hp <= 5 ? ColorRGBA.Red : hp <= 12 ? ColorRGBA.Orange : ColorRGBA.Green);
        hudText.setText("HP " + hp + "    LIVES " + lives + "    WEAPON " + weapon);
        hudText.setLocalTranslation(14, h - 36, 0);
    }

    @Override
    public void simpleUpdate(float tpf) {
        frame++;
        grabCursor();                                   // maintient le verrou souris (JME peut le relâcher)
        int joyy = kFwd ? -1 : (kBack ? 1 : 0);
        int joys = (kStrafeL || kStrafeR) ? -1 : 0;
        int joyx = joys != 0 ? (kStrafeL ? -1 : 1) : (kLeft ? -1 : (kRight ? 1 : 0));
        scene.setInput(joyx, joyy, kFire ? -1 : 0, joys);
        // pas de temps FIXE : tick() à cadence constante quel que soit le framerate (logique = 30*SPEED Hz ;
        // tick() ne fait la logique qu'1 appel sur 2). SPEED>1 → jeu plus rapide que l'original.
        acc += Math.min(tpf, 0.25f);                    // cap anti-spirale de la mort
        int steps = 0;
        while (acc >= TICK_DT && steps < 6) { scene.tick(); acc -= TICK_DT; steps++; }
        // MOUSELOOK : souris droite (DX>0) → ob_rot augmente → vue tourne à droite (cf. rotplayer).
        if (mouseDX != 0f) {
            int d16 = (int) (mouseDX * MOUSE_SENS * 65536f);   // unités-rot 16.16 (256 = tour complet)
            Mem.wl(scene.player + Defs.ob_rot, Mem.l(scene.player + Defs.ob_rot) + d16);
            mouseDX = 0f;
        }
        // cam vars de la simu (pour la sélection de frame 8-directions) + caméra 3D
        Mem.wl(Vars.memat, Mem.l(Vars.memory));
        Objects.calcscene(scene.player);
        updateCamera();
        rebuildWalls();                                 // portes/rotpolys/morphs : géométrie suit la simu
        updateMuzzle();
        updateEnemies();
        updateBulletLights();                           // balles → lumières dynamiques (murs + ennemis)
        renderGore();                                   // décals de gore au sol (gibs retombés)
        updateHud();
        updateDamageFx();                               // flash rouge (coups) + rouge mort
        if (audio != null) audio.updateMusic();         // pompe le streaming MED (thread principal)
    }

    /** Flash de bouche : relance l'intensité au front montant du tir, puis décroissance rapide. */
    private void updateMuzzle() {
        if (kFire && !prevFire) flash = 1f;              // nouveau tir → flash plein
        prevFire = kFire;
        flash *= 0.55f;                                   // décroissance (~3-4 frames visibles)
        muzzle.setColor(new ColorRGBA(1f, 0.85f, 0.55f, 1f).mult(flash * 2.4f));   // orangé
        muzzle.setPosition(cam.getLocation().add(cam.getDirection().mult(40f * S)));
    }

    private void updateCamera() {
        int p = scene.player;
        // X MIROIR (-) : le repère gloom est gaucher ; sans ce miroir le sens de rotation gauche/droite
        // est inversé à l'écran. On miroir X partout (géométrie, lumières, sprites) → cohérent.
        float px = -(Mem.l(p + Defs.ob_x) >> 16) * S;
        float pz = (Mem.l(p + Defs.ob_z) >> 16) * S;
        float eye = 110f * S;
        cam.setLocation(new Vector3f(px, eye, pz));
        if (torch != null) torch.setPosition(new Vector3f(px, eye, pz));   // la torche suit le joueur
        float theta = ((Mem.l(p + Defs.ob_rot) >> 16) & 0xff) * FastMath.TWO_PI / 256f;
        cam.lookAt(new Vector3f(px - FastMath.sin(theta), eye, pz + FastMath.cos(theta)), Vector3f.UNIT_Y);
    }

    // ----------------------------------------------------------------- ennemis (billboards)

    private void updateEnemies() {
        int used = 0;
        int o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0) {                        // parcourt la liste chaînée des objets
            if (o != scene.player && placeBillboard(used, o)) used++;
            o = Mem.l(o);
        }
        for (int i = used; i < spritePool.size(); i++)   // masque les billboards en surplus (pool)
            spritePool.get(i).setCullHint(Spatial.CullHint.Always);
    }

    /**
     * Balles → lumières dynamiques. Les projectiles sont reconnus par leur ob_shape (= Tables.bulletN).
     * On affecte une PointLight du pool (couleur selon le type) à chaque balle proche ; le surplus est
     * éteint (couleur noire). La lumière éclaire automatiquement murs et ennemis (matériaux éclairés).
     */
    private void updateBulletLights() {
        int used = 0;
        int o = Mem.l(Vars.objects);
        while (Mem.l(o) != 0 && used < bulletLights.length) {
            if (o != scene.player) {
                float[] col = bulletColor(Mem.l(o + Defs.ob_shape));
                if (col != null) {
                    float bx = -(short) Mem.w(o + Defs.ob_x) * S;   // X miroir, comme la géométrie
                    float bz = (short) Mem.w(o + Defs.ob_z) * S;
                    float by = Math.max(0.15f, -(short) Mem.w(o + Defs.ob_y) * S);  // hauteur de vol
                    bulletLights[used].setPosition(new Vector3f(bx, by, bz));
                    bulletLights[used].setColor(new ColorRGBA(col[0], col[1], col[2], 1f).mult(2.2f));
                    used++;
                }
            }
            o = Mem.l(o);
        }
        for (int i = used; i < bulletLights.length; i++) bulletLights[i].setColor(ColorRGBA.Black);  // éteint
    }

    /** Couleur d'émission selon le sprite de balle (null si l'objet n'est pas un projectile). */
    private float[] bulletColor(int shape) {
        if (shape == Tables.bullet1) return new float[]{1f, 0.6f, 0.2f};    // joueur : orangé
        if (shape == Tables.bullet2) return new float[]{0.4f, 1f, 0.45f};   // ghoul : vert
        if (shape == Tables.bullet3) return new float[]{0.45f, 0.6f, 1f};   // phantom : bleu
        if (shape == Tables.bullet4) return new float[]{1f, 0.35f, 0.2f};   // terra : rouge
        if (shape == Tables.bullet5) return new float[]{1f, 0.5f, 0.1f};    // dragon : feu
        return null;
    }

    /** Configure (en le réutilisant depuis le pool) le billboard d'indice {@code slot} pour l'objet. */
    private boolean placeBillboard(int slot, int obj) {
        int shape = Mem.l(obj + Defs.ob_shape);
        if (shape == 0) return false;
        int frameOff = currentFrame(obj, shape);
        int fb = shape + frameOff;
        int yh = (short) Mem.w(fb + 2);
        int w = Mem.uw(fb + 4), h = Mem.uw(fb + 6);
        if (w <= 0 || h <= 0 || w > 256 || h > 256) return false;
        int scale = (Mem.l(obj + Defs.ob_render) == Objects.R_DRAWSHAPE_8) ? Mem.uw(obj + Defs.ob_scale) : 0x200;

        Texture2D tex = spriteCache.computeIfAbsent(fb, k -> spriteTexture(fb));
        float wW = w * scale / 256f * S, wH = h * scale / 256f * S;   // taille monde (JME)
        float ox = (short) Mem.w(obj + Defs.ob_x), oy = (short) Mem.w(obj + Defs.ob_y), oz = (short) Mem.w(obj + Defs.ob_z);
        // ancre : le pixel (xh,yh) du sprite est posé sur (ox,oy) ; billboard centré horizontalement
        float centerY = -(oy + (h * 0.5f - yh) * scale / 256f) * S;
        // en 3D le sol est un plan réel (y=0) : un sprite à ancre centrée posé au sol traverserait le
        // plancher (moitié basse sous le sol). On remonte le billboard pour qu'il repose dessus.
        if (centerY - wH / 2f < 0f) centerY = wH / 2f;

        Geometry g = spriteSlot(slot);
        g.getMaterial().setTexture("DiffuseMap", tex);
        g.setLocalScale(wW, wH, 1f);                   // quad unité mis à l'échelle (pas de Mesh recréé)
        g.setLocalTranslation(-ox * S, centerY, oz * S);   // X miroir
        g.setCullHint(Spatial.CullHint.Inherit);
        return true;
    }

    /** Renvoie le billboard du pool à cet indice, en l'allouant la première fois. */
    private Geometry spriteSlot(int slot) {
        while (slot >= spritePool.size()) {
            if (unitQuad == null) unitQuad = quadMesh(1f, 1f);
            Geometry g = new Geometry("spr", unitQuad);
            // matériau ÉCLAIRÉ (au lieu d'Unshaded) → l'ennemi reçoit la lumière des balles/torche.
            // Ambient élevé = sprite lumineux par défaut ; les point lights ajoutent par-dessus.
            Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
            mat.setBoolean("UseMaterialColors", true);
            mat.setColor("Ambient", ColorRGBA.White.mult(0.9f));
            mat.setColor("Diffuse", ColorRGBA.White);
            mat.setFloat("AlphaDiscardThreshold", 0.5f);
            mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            g.setMaterial(mat);
            g.addControl(new BillboardControl());      // face toujours la caméra
            enemies.attachChild(g);
            spritePool.add(g);
        }
        return spritePool.get(slot);
    }

    /**
     * Décals de gore au sol (liste Vars.gore) : un gib retombé (chunklogic, mode≠0) est retiré des
     * objets et déposé ici (go_x/go_z/go_shape). Le 2D les dessine via drawshape_q à plat sur le sol ;
     * en 3D on pose un quad HORIZONTAL au niveau du sol.
     */
    private void renderGore() {
        goreNode.detachAllChildren();
        int g = Vars.gore;
        while (true) {
            g = Mem.l(g);
            if (Mem.l(g) == 0) break;                  // sentinelle (.succ==0)
            int shape = Mem.l(g + Defs.go_shape);
            if (shape == 0) continue;
            int w = Mem.uw(shape + 4), h = Mem.uw(shape + 6);
            if (w <= 0 || h <= 0 || w > 256 || h > 256) continue;
            int gx = (short) Mem.w(g + Defs.go_x), gz = (short) Mem.w(g + Defs.go_z);
            Texture2D tex = spriteCache.computeIfAbsent(shape, k -> spriteTexture(shape));
            Geometry q = new Geometry("gore", floorQuad(w * 2f * S, h * 2f * S));   // échelle 0x200
            Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setTexture("ColorMap", tex);
            mat.setFloat("AlphaDiscardThreshold", 0.5f);
            mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            q.setMaterial(mat);
            q.setLocalTranslation(-gx * S, 0.02f, gz * S);   // X miroir ; juste au-dessus du sol (anti z-fight)
            goreNode.attachChild(q);
        }
    }

    /** Quad HORIZONTAL (plan XZ) centré, normale +Y — pour les décals de sol. */
    private Mesh floorQuad(float w, float d) {
        float x = w / 2, z = d / 2;
        float[] pos = { -x, 0, -z, x, 0, -z, x, 0, z, -x, 0, z };
        float[] nor = { 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0 };
        float[] uv = { 0, 0, 1, 0, 1, 1, 0, 1 };
        int[] idx = { 0, 1, 2, 0, 2, 3 };
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        m.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nor));
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uv));
        m.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(idx));
        m.updateBound();
        return m;
    }

    /** Réplique la sélection de frame (drawshape_8 : 8 directions + anim ; sinon 1 frame). */
    private int currentFrame(int obj, int shape) {
        int idx;
        if (Mem.l(obj + Defs.ob_render) == Objects.R_DRAWSHAPE_8) {
            int ang = Objects.calcangle2(obj);
            int dir = (((ang + 16) - (Mem.uw(obj + Defs.ob_rot) & 0xffff)) & 0xffff) >>> 5 & 7;
            idx = dir | ((Mem.uw(obj + Defs.ob_frame) << 3) & 0xffff);
        } else {
            idx = Mem.w(obj + Defs.ob_frame);
        }
        return Mem.l(shape + 12 + idx * 4);
    }

    /** Sprite Gloom (colonnes : pixel(col,row)=base+col*h+row) → texture RGBA (index 0 = transparent). */
    private Texture2D spriteTexture(int fb) {
        int w = Mem.uw(fb + 4), h = Mem.uw(fb + 6), base = fb + 8;
        int rgbs = Mem.l(Vars.map_rgbs);
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int pi = Mem.ub(base + col * h + row);
                int p = ((h - 1 - row) * w + col) * 4;             // flip V (row 0 = haut du sprite)
                if (pi == 0) { buf.put(p + 3, (byte) 0); continue; } // transparent
                int rgb = colorOf(rgbs, pi);
                buf.put(p, (byte) (rgb >> 16)).put(p + 1, (byte) (rgb >> 8))
                   .put(p + 2, (byte) rgb).put(p + 3, (byte) 0xff);
            }
        }
        return makeTex(buf, w, h);
    }

    /** Quad centré (XY), face +Z, pour billboard. */
    private Mesh quadMesh(float w, float h) {
        float x = w / 2, y = h / 2;
        float[] pos = { -x, -y, 0, x, -y, 0, x, y, 0, -x, y, 0 };
        float[] nor = { 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1 };
        float[] uv = { 0, 0, 1, 0, 1, 1, 0, 1 };
        int[] idx = { 0, 1, 2, 0, 2, 3 };
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        m.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nor));
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uv));
        m.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(idx));
        m.updateBound();
        return m;
    }

    // ----------------------------------------------------------------- niveau (murs/sol/plafond)

    private void buildLevel() {
        captureAnimFrames();                                       // identité des frames d'anim (pour le HD)
        Map<Integer, List<float[]>> byTex = collectWalls();        // état initial (bornes + lumières)
        float minX = 1e9f, maxX = -1e9f, minZ = 1e9f, maxZ = -1e9f;
        for (var list : byTex.values())
            for (float[] w : list) {
                minX = Math.min(minX, Math.min(w[0], w[2])); maxX = Math.max(maxX, Math.max(w[0], w[2]));
                minZ = Math.min(minZ, Math.min(w[1], w[3])); maxZ = Math.max(maxZ, Math.max(w[1], w[3]));
            }
        for (Integer t : byTex.keySet()) wallTexture(t);            // précharge (remplit seeThroughTex/texLight)

        rootNode.attachChild(walls);
        rebuildWalls();                                            // 1er build (puis chaque frame → portes animées)

        if (Mem.l(Vars.floor) != 0) rootNode.attachChild(plane("floor", minX, maxX, minZ, maxZ, 0, tileTexture(Mem.l(Vars.floor), "floor")));
        if (Mem.l(Vars.roof) != 0) rootNode.attachChild(plane("ceil", minX, maxX, minZ, maxZ, WALL_H, tileTexture(Mem.l(Vars.roof), "roof")));

        // point lights sur les murs à texture ÉMISSIVE (panneaux/écrans lumineux) — texLight rempli
        // pendant wallTexture(). Dédoublonnage par cellule de grille + plafonné (perf single-pass).
        Set<Long> usedCells = new HashSet<>();
        int added = 0;
        for (var e : byTex.entrySet()) {
            float[] col = texLight.get(e.getKey());
            if (col == null) continue;                 // texture non émissive
            for (float[] w : e.getValue()) {
                if (added >= 14) break;
                float cx = (w[0] + w[2]) / 2, cz = (w[1] + w[3]) / 2;
                long cell = ((long) Math.round(cx / 384) << 20) ^ Math.round(cz / 384);
                if (!usedCells.add(cell)) continue;    // déjà une lumière dans cette zone
                PointLight pl = new PointLight(new Vector3f(-cx * S, WALL_H * 0.45f, cz * S),   // X miroir
                        new ColorRGBA(col[0], col[1], col[2], 1f).mult(2.2f), 520f * S);
                rootNode.addLight(pl);
                added++;
            }
        }
    }

    /** Énumère les zones (segments de mur) via la grille et lit leurs coordonnées COURANTES, groupées
     *  par texture. Comme dodoors/dorots modifient zo_lx..zo_rz, relire chaque frame anime la géométrie. */
    private Map<Integer, List<float[]>> collectWalls() {
        int grid = Mem.l(Vars.map_grid), ppnt = Mem.l(Vars.map_ppnt), poly = Mem.l(Vars.map_poly);
        Set<Integer> zones = new HashSet<>();
        for (int cell = 0; cell < 32 * 32; cell++) {
            int a0 = grid + cell * 8;
            int num = Mem.w(a0);
            if (num < 0) continue;
            int pp = ppnt + Mem.uw(a0 + 2) * 2;
            for (int i = 0; i <= num; i++) { zones.add(Mem.uw(pp)); pp += 2; }
        }
        Map<Integer, List<float[]>> byTex = new HashMap<>();
        for (int z : zones) {
            int zb = poly + z * Defs.zo_size;
            float lx = (short) Mem.w(zb + Defs.zo_lx), lz = (short) Mem.w(zb + Defs.zo_lz);
            float rx = (short) Mem.w(zb + Defs.zo_rx), rz = (short) Mem.w(zb + Defs.zo_rz);
            byTex.computeIfAbsent(Mem.ub(zb + Defs.zo_t), k -> new ArrayList<>()).add(new float[]{lx, lz, rx, rz});
        }
        return byTex;
    }

    /** Reconstruit les meshes de murs depuis l'état COURANT des zones (appelé chaque frame). */
    private void rebuildWalls() {
        walls.detachAllChildren();
        Map<Integer, List<float[]>> byTex = collectWalls();
        refreshAnimatedTextures(byTex.keySet());                   // textures animées : reconstruit si le pointeur a changé
        for (var e : byTex.entrySet()) {
            Geometry wg = new Geometry("w" + e.getKey(), wallMesh(e.getValue()));
            wg.setMaterial(wallMaterial(e.getKey()));
            if (seeThroughTex.contains(e.getKey())) wg.setShadowMode(ShadowMode.Receive);   // grille ajourée
            walls.attachChild(wg);
        }
    }

    /** Matériau de mur (caché par texture) : Lighting.j3md + alpha discard si texture ajourée. */
    private Material wallMaterial(int tex) {
        return wallMatCache.computeIfAbsent(tex, k -> {
            Material m = litTextured(wallTexture(k), true);        // wallTexture() remplit seeThroughTex avant le test
            if (seeThroughTex.contains(k)) m.setFloat("AlphaDiscardThreshold", 0.5f);
            return m;
        });
    }

    /**
     * Textures animées : doanims (gloom.s:1942) fait tourner les POINTEURS de la table `textures`
     * (le numéro reste, mais textures[n] pointe vers la frame suivante). On détecte ce changement
     * de pointeur et on invalide le cache → la texture est reconstruite avec les nouveaux pixels.
     */
    private void refreshAnimatedTextures(Set<Integer> usedTex) {
        for (Integer n : usedTex) {
            Integer built = wallTexBase.get(n);
            if (built != null && built != Mem.l(Vars.textures + n * 4)) {  // pointeur changé → frame suivante
                wallTexCache.remove(n);
                wallMatCache.remove(n);
                wallTexBase.remove(n);                 // wallTexture() les repeuplera à la reconstruction
            }
        }
    }

    private Mesh wallMesh(List<float[]> walls) {
        int n = walls.size();
        float[] pos = new float[n * 12], nor = new float[n * 12], uv = new float[n * 8];
        int[] idx = new int[n * 6];
        for (int i = 0; i < n; i++) {
            float[] w = walls.get(i);
            float lx = -w[0] * S, lz = w[1] * S, rx = -w[2] * S, rz = w[3] * S;   // X miroir
            float dx = rx - lx, dz = rz - lz, len = (float) Math.sqrt(dx * dx + dz * dz) + 1e-6f;
            float nx = -dz / len, nz = dx / len, tile = len / WALL_H;
            int v = i * 4;
            putv(pos, v, lx, 0, lz); putv(pos, v + 1, rx, 0, rz); putv(pos, v + 2, rx, WALL_H, rz); putv(pos, v + 3, lx, WALL_H, lz);
            for (int k = 0; k < 4; k++) putv(nor, v + k, nx, 0, nz);
            uv[v * 2] = 0; uv[v * 2 + 1] = 0; uv[v * 2 + 2] = tile; uv[v * 2 + 3] = 0;
            uv[v * 2 + 4] = tile; uv[v * 2 + 5] = 1; uv[v * 2 + 6] = 0; uv[v * 2 + 7] = 1;
            int o = i * 6;
            idx[o] = v; idx[o + 1] = v + 1; idx[o + 2] = v + 2; idx[o + 3] = v; idx[o + 4] = v + 2; idx[o + 5] = v + 3;
        }
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        m.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nor));
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uv));
        m.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(idx));
        m.updateBound();
        return m;
    }

    private static void putv(float[] a, int v, float x, float y, float z) { a[v * 3] = x; a[v * 3 + 1] = y; a[v * 3 + 2] = z; }

    private Geometry plane(String name, float minX, float maxX, float minZ, float maxZ, float gy, Texture2D tex) {
        float x0 = -minX * S, x1 = -maxX * S, z0 = minZ * S, z1 = maxZ * S;   // X miroir
        float u0 = minX / 128f, u1 = maxX / 128f, v0 = minZ / 128f, v1 = maxZ / 128f;
        float[] pos = { x0, gy, z0, x1, gy, z0, x1, gy, z1, x0, gy, z1 };
        float[] nor = { 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0 };
        float[] uv = { u0, v0, u1, v0, u1, v1, u0, v1 };
        int[] idx = { 0, 1, 2, 0, 2, 3 };
        Mesh m = new Mesh();
        m.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(pos));
        m.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(nor));
        m.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uv));
        m.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(idx));
        m.updateBound();
        Geometry g = new Geometry(name, m);
        g.setMaterial(litTextured(tex, true));
        return g;
    }

    // ----------------------------------------------------------------- textures / matériaux

    private static int colorOf(int rgbs, int idx) {
        int c = Mem.uw(rgbs + idx * 2) & 0x0fff;
        return (((c >> 8) & 15) * 17 << 16) | (((c >> 4) & 15) * 17 << 8) | ((c & 15) * 17);
    }

    private Texture2D wallTexture(int n) {
        return wallTexCache.computeIfAbsent(n, k -> {
            int base = Mem.l(Vars.textures + k * 4), rgbs = Mem.l(Vars.map_rgbs);
            ByteBuffer buf = BufferUtils.createByteBuffer(64 * 64 * 4);
            long br = 0, bg = 0, bb = 0; int bright = 0;   // stats des pixels lumineux (→ émissivité)
            boolean holed = false;                          // au moins une colonne ajourée → texture see-through
            for (int col = 0; col < 64; col++) {
                // octet d'en-tête de colonne (cf. Render.fillColumn) : !=0 → colonne ajourée,
                // ses texels d'index 0 sont des TROUS (le moteur 2D les rend transparents).
                int hdr = base == 0 ? 0 : Mem.ub(base + col * 65);
                boolean colHoled = hdr != 0;
                if (colHoled) holed = true;
                int cb = base + col * 65 + 1;
                for (int row = 0; row < 64; row++) {
                    int ti = base == 0 ? 1 : Mem.ub(cb + row);
                    int p = ((63 - row) * 64 + col) * 4;
                    if (colHoled && ti == 0) { buf.put(p + 3, (byte) 0); continue; }   // trou → transparent
                    int rgb = base == 0 ? 0x808080 : colorOf(rgbs, ti);
                    int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                    if (0.30f * r + 0.59f * g + 0.11f * b > 165) { br += r; bg += g; bb += b; bright++; }
                    buf.put(p, (byte) r).put(p + 1, (byte) g).put(p + 2, (byte) b).put(p + 3, (byte) 0xff);
                }
            }
            if (holed) seeThroughTex.add(k);
            if (bright > 64 * 64 * 0.045f)                 // >4,5% de pixels brillants → texture émissive
                texLight.put(k, new float[]{br / (bright * 255f), bg / (bright * 255f), bb / (bright * 255f)});
            wallTexBase.put(k, base);                      // mémorise le pointeur pixels (détection d'animation)
            Texture2D proc = makeTex(buf, 64, 64);
            // HD indexé par la FRAME courante (slot d'anim), pas le numéro de zone — sinon une texture
            // animée afficherait une frame HD figée. hdNumberFor() résout le pointeur courant → slot.
            // PAS de HD pour les murs AJOURÉS : un PNG est opaque → les trous (alpha) seraient perdus.
            // (Pour du HD see-through il faudrait cuire le masque alpha dans le PNG — repoussé.)
            Texture2D hd = holed ? null : loadHd("hd/wall_" + hdNumberFor(k) + ".png");
            return hd != null ? hd : proc;
        });
    }

    /**
     * Capture l'identité des frames d'animation : doanims (gloom.s:1942) fait tourner les pointeurs de
     * textures[] sur des plages [premier..premier+N-1] (chaque entrée = une frame). On enregistre le
     * pointeur INITIAL de chaque slot → son numéro, pour pouvoir charger le bon hd/wall_&lt;slot&gt;.png.
     */
    private void captureAnimFrames() {
        int a0 = Mem.l(Vars.map_anim);
        if (a0 == 0) return;
        while (true) {
            int frames = Mem.uw(a0); a0 += 2;
            if (frames == 0) break;
            int first = Mem.uw(a0); a0 += 6;            // [premier(2)][delai(2)][compteur(2)]
            for (int k = 0; k < frames; k++) {
                int slot = first + k;
                slotOfBase.put(Mem.l(Vars.textures + slot * 4), slot);   // pointeur initial → slot
            }
        }
    }

    /** Numéro de frame HD pour la texture n : son pointeur courant → slot d'anim (sinon n lui-même). */
    private int hdNumberFor(int n) {
        Integer slot = slotOfBase.get(Mem.l(Vars.textures + n * 4));
        return slot != null ? slot : n;
    }

    /** Charge une texture HD optionnelle depuis &lt;cwd&gt;/hd/ ; null si absente (→ repli procédural). */
    private Texture2D loadHd(String path) {
        if (!new java.io.File(path).isFile()) return null;     // pas d'asset → repli (sans spam de log)
        try {
            Texture t = assetManager.loadTexture(path);
            t.setWrap(Texture.WrapMode.Repeat);
            t.setMagFilter(Texture.MagFilter.Bilinear);
            t.setMinFilter(Texture.MinFilter.Trilinear);       // mipmaps : HD net de loin
            return (Texture2D) t;
        } catch (Exception e) {
            System.err.println("[Rebirth] texture HD illisible : " + path + " (" + e + ")");
            return null;
        }
    }

    private Texture2D tileTexture(int base, String hdName) {
        Texture2D hd = loadHd("hd/" + hdName + ".png");        // hd/floor.png / hd/roof.png optionnels
        if (hd != null) return hd;
        int rgbs = Mem.l(Vars.map_rgbs);
        ByteBuffer buf = BufferUtils.createByteBuffer(128 * 128 * 4);
        for (int x = 0; x < 128; x++)
            for (int z = 0; z < 128; z++) {
                int rgb = colorOf(rgbs, Mem.ub(base + x * 128 + z));
                int p = (x * 128 + z) * 4;
                buf.put(p, (byte) (rgb >> 16)).put(p + 1, (byte) (rgb >> 8)).put(p + 2, (byte) rgb).put(p + 3, (byte) 0xff);
            }
        return makeTex(buf, 128, 128);
    }

    private Texture2D makeTex(ByteBuffer buf, int w, int h) {
        Texture2D t = new Texture2D(new Image(Image.Format.RGBA8, w, h, buf, ColorSpace.sRGB));
        t.setMagFilter(Texture.MagFilter.Nearest);
        t.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        t.setWrap(Texture.WrapMode.Repeat);
        return t;
    }

    private Material litTextured(Texture2D tex, boolean doubleSided) {
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setTexture("DiffuseMap", tex);
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", ColorRGBA.White);
        mat.setColor("Ambient", ColorRGBA.White);
        if (doubleSided) mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        return mat;
    }

    // ----------------------------------------------------------------- contrôles + outils

    private void bindKeys() {
        inputManager.addMapping("fwd", new KeyTrigger(KeyInput.KEY_W), new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("back", new KeyTrigger(KeyInput.KEY_S), new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addMapping("left", new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping("right", new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addMapping("sl", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("sr", new KeyTrigger(KeyInput.KEY_D));
        // tir : Ctrl / Espace / CLIC GAUCHE souris
        inputManager.addMapping("fire", new KeyTrigger(KeyInput.KEY_LCONTROL), new KeyTrigger(KeyInput.KEY_SPACE),
                new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        ActionListener al = (name, pressed, tpf) -> {
            switch (name) {
                case "fwd" -> kFwd = pressed;   case "back" -> kBack = pressed;
                case "left" -> kLeft = pressed; case "right" -> kRight = pressed;
                case "sl" -> kStrafeL = pressed; case "sr" -> kStrafeR = pressed;
                case "fire" -> kFire = pressed;
            }
        };
        inputManager.addListener(al, "fwd", "back", "left", "right", "sl", "sr", "fire");

        // MOUSELOOK : delta X de la souris en PIXELS → rotation du joueur (appliqué dans simpleUpdate).
        inputManager.addRawInputListener(new RawInputListener() {
            @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent e) { mouseDX += e.getDX(); }
            @Override public void beginInput() { }
            @Override public void endInput() { }
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent e) { }
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent e) { }
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent e) { }
            @Override public void onKeyEvent(com.jme3.input.event.KeyInputEvent e) { }
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent e) { }
        });
    }

    /** Spawne un ennemi (marine) devant le joueur — pour vérifier les billboards en mode -Dshot. */
    private void spawnDemoEnemy() {
        int p = scene.player;
        int px = Mem.l(p + Defs.ob_x) >> 16, pz = Mem.l(p + Defs.ob_z) >> 16;
        double theta = ((Mem.l(p + Defs.ob_rot) >> 16) & 0xff) * Math.PI * 2 / 256;
        int mx = px + (int) (Math.sin(theta) * 380), mz = pz + (int) (Math.cos(theta) * 380);
        Objects.loadanobj(10);
        Mem.wl(ObjInfo.dummy, 0);
        int ev = Mem.alloc(12);
        Mem.ww(ev, 10);
        Mem.ww(ev + 2, mx); Mem.ww(ev + 4, 0); Mem.ww(ev + 6, mz); Mem.ww(ev + 8, 0);
        Objects.exec_addobj(ev);
        for (int t = 0; t < 4; t++) scene.tick();       // quelques frames : le marine se tourne vers nous
    }

    private void attachScreenshot() {
        guiViewPort.addProcessor(new SceneProcessor() {     // après la passe GUI → capture HUD inclus
            private RenderManager rm; private boolean done;
            @Override public void initialize(RenderManager rm, ViewPort vp) { this.rm = rm; }
            @Override public void reshape(ViewPort vp, int w, int h) { }
            @Override public boolean isInitialized() { return rm != null; }
            @Override public void preFrame(float tpf) { }
            @Override public void postQueue(RenderQueue rq) { }
            @Override public void postFrame(FrameBuffer out) {
                if (done || frame < 20) return;
                done = true;
                int w = cam.getWidth(), h = cam.getHeight();
                ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
                rm.getRenderer().readFrameBuffer(out, buf);
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR);
                Screenshots.convertScreenShot(buf, img);
                for (int q = 0; q < w * h; q++) {
                    int a = img.getRGB(q % w, q / w);
                    img.setRGB(q % w, q / w, (a & 0xff00ff00) | ((a & 0xff) << 16) | ((a >> 16) & 0xff));
                }
                try { ImageIO.write(img, "png", new File("rebirth.png")); }
                catch (Exception e) { System.err.println("[Rebirth] screenshot: " + e); }
                stop();
            }
            @Override public void cleanup() { }
            @Override public void setProfiler(AppProfiler profiler) { }
        });
    }
}
