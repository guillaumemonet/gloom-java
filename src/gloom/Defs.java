package gloom;

/**
 * Sous-système 04a — Offsets canoniques des structures de gloom.s.
 *
 * Traduction littérale des blocs `rsreset` / `rs.b|w|l` (gloom.s:47-355).
 * Les offsets sont calculés par le MÊME mécanisme que l'assembleur (un compteur RS
 * remis à zéro par rsreset, avancé par rs.b/w/l ; `rs.x 0` = alias sans avancer),
 * de sorte qu'une erreur de transcription est impossible à masquer — gloom.tools.CheckLayout
 * vérifie ensuite les tailles totales contre les valeurs canoniques.
 *
 * ⚠️ Ces offsets DOIVENT rester identiques au format on-disk (maps) et inter-structures.
 * Ne jamais réordonner. Voir docs/ARCHITECTURE.md §3.
 *
 * Le `de` (mini-jeu Defender, gloom.s:8156) est reporté au sous-système 12.
 */
public final class Defs {

    // --- mécanisme RS (compteur d'offset de structure) ---
    private static int rs;
    private static void rsreset() { rs = 0; }
    private static int rsb(int n) { int o = rs; rs += n; return o; }
    private static int rsw(int n) { int o = rs; rs += 2 * n; return o; }
    private static int rsl(int n) { int o = rs; rs += 4 * n; return o; }

    // ================= rotpoly (rp) — gloom.s:47 =================
    public static final int rp_next, rp_prev, rp_speed, rp_rot, rp_flags,
            rp_cx, rp_cz, rp_first, rp_num,
            rp_vx, rp_lx, rp_vz, rp_lz, rp_ox, rp_na, rp_oz, rp_nb, rp_more, rp_size;
    static {
        rsreset();
        rp_next = rsl(1);
        rp_prev = rsl(1);
        rp_speed = rsw(1);
        rp_rot = rsw(1);
        rp_flags = rsw(1);
        rp_cx = rsw(1);
        rp_cz = rsw(1);
        rp_first = rsl(1);
        rp_num = rsw(1);
        rp_vx = rsw(0); rp_lx = rsw(1);
        rp_vz = rsw(0); rp_lz = rsw(1);
        rp_ox = rsw(0); rp_na = rsw(1);
        rp_oz = rsw(0); rp_nb = rsw(1);
        rp_more = rsb(8 * 31);            // =32 max verts
        rp_size = rsb(0);
    }

    // ================= fx (canal SFX) — gloom.s:76 =================
    public static final int fx_status, fx_priority, fx_sfx, fx_vol, fx_offset, fx_dma, fx_int, fx_size;
    static {
        rsreset();
        fx_status = rsw(1);
        fx_priority = rsw(1);
        fx_sfx = rsl(1);
        fx_vol = rsw(1);
        fx_offset = rsw(1);
        fx_dma = rsw(1);
        fx_int = rsw(1);
        fx_size = rsb(0);
    }

    // ================= blood (bl) — gloom.s:90 =================
    public static final int bl_next, bl_prev, bl_x, bl_y, bl_z, bl_xvec, bl_dest,
            bl_yvec, bl_zvec, bl_color, bl_size;
    static {
        rsreset();
        bl_next = rsl(1);
        bl_prev = rsl(1);
        bl_x = rsl(1);
        bl_y = rsl(1);
        bl_z = rsl(1);
        bl_xvec = rsl(1);
        bl_dest = rsl(0);
        bl_yvec = rsl(1);
        bl_zvec = rsl(1);
        bl_color = rsl(1);
        bl_size = rsb(0);
    }

    // ================= texture (te) — gloom.s:107 =================
    public static final int te_pal, te_size;
    static {
        rsreset();
        te_pal = rsl(1);
        te_size = rsb(0);
    }

    // ================= door (do) — gloom.s:115 =================
    public static final int do_next, do_prev, do_poly, do_lx, do_lz, do_rx, do_rz,
            do_frac, do_fracadd, do_size;
    static {
        rsreset();
        do_next = rsl(1);
        do_prev = rsl(1);
        do_poly = rsl(1);
        do_lx = rsw(1);
        do_lz = rsw(1);
        do_rx = rsw(1);
        do_rz = rsw(1);
        do_frac = rsw(1);
        do_fracadd = rsw(1);
        do_size = rsb(0);
    }

    // ================= wall list (wl) — gloom.s:131 =================
    public static final int wl_next, wl_lsx, wl_rsx, wl_nz, wl_fz, wl_lx, wl_lz, wl_rx, wl_rz,
            wl_a, wl_b, wl_c, wl_sc, wl_open, wl_t, wl_size;
    static {
        rsreset();
        wl_next = rsl(1);
        wl_lsx = rsw(1);
        wl_rsx = rsw(1);
        wl_nz = rsw(1);
        wl_fz = rsw(1);
        wl_lx = rsw(1);
        wl_lz = rsw(1);
        wl_rx = rsw(1);
        wl_rz = rsw(1);
        wl_a = rsw(1);
        wl_b = rsw(1);
        wl_c = rsl(1);
        wl_sc = rsw(1);
        wl_open = rsw(1);
        wl_t = rsb(8);
        wl_size = rsb(0);
    }

    // ================= zone (zo) — gloom.s:153 =================
    public static final int zo_done, zo_lx, zo_lz, zo_rx, zo_rz, zo_a, zo_b, zo_na, zo_nb, zo_ln,
            zo_t, zo_sc, zo_open, zo_ev, zo_size;
    static {
        rsreset();
        zo_done = rsw(1);
        zo_lx = rsw(1);
        zo_lz = rsw(1);
        zo_rx = rsw(1);
        zo_rz = rsw(1);
        zo_a = rsw(1);
        zo_b = rsw(1);
        zo_na = rsw(1);
        zo_nb = rsw(1);
        zo_ln = rsw(1);
        zo_t = rsb(8);
        zo_sc = rsw(1);
        zo_open = rsw(0); zo_ev = rsw(1);
        zo_size = rsb(0);                 // 32
    }

    // ================= shape (sh) — gloom.s:177 =================
    public static final int sh_next, sh_prev, sh_x, sh_y, sh_z, sh_shape, sh_scale, sh_strip,
            sh_render, sh_size;
    static {
        rsreset();
        sh_next = rsl(1);
        sh_prev = rsl(1);
        sh_x = rsw(1);
        sh_y = rsw(1);
        sh_z = rsw(1);
        sh_shape = rsl(1);
        sh_scale = rsw(0); sh_strip = rsl(1);
        sh_render = rsl(1);
        sh_size = rsb(0);
    }

    // ================= gore (go) — gloom.s:193 =================
    public static final int go_next, go_prev, go_x, go_z, go_shape, go_size;
    static {
        rsreset();
        go_next = rsl(1);
        go_prev = rsl(1);
        go_x = rsw(1);
        go_z = rsw(1);
        go_shape = rsl(1);
        go_size = rsb(0);
    }

    // ================= object (ob) — gloom.s:205 =================
    public static final int ob_next, ob_prev, ob_x, ob_y, ob_z, ob_rot, ob_info,
            ob_rotspeed, ob_movspeed, ob_shape, ob_logic, ob_render, ob_hit, ob_die,
            ob_eyey, ob_firey, ob_gutsy, ob_mega, ob_othery, ob_colltype, ob_collwith,
            ob_cntrl, ob_damage, ob_hitpoints, ob_think, ob_frame, ob_framespeed,
            ob_base, ob_range, ob_weapon, ob_reload, ob_reloadcnt, ob_hurtpause,
            ob_firerate, ob_punchrate, ob_bouncecnt, ob_firecnt, ob_something, ob_scale,
            ob_lastbut, ob_blood, ob_ypad,
            ob_oldlogic, ob_oldlogic2, ob_oldhit, ob_olddie, ob_oldrot, ob_newrot,
            ob_yvec, ob_xvec, ob_zvec, ob_radsq, ob_rad, ob_delay, ob_delay2, ob_bounce, ob_hurtwait,
            ob_washit, ob_window, ob_nxvec, ob_lives, ob_nzvec, ob_infra, ob_thermo,
            ob_invisible, ob_hyper, ob_update, ob_mess, ob_messlen, ob_messtimer,
            ob_palette, ob_paltimer, ob_pixsize, ob_pixsizeadd, ob_telex, ob_telez, ob_telerot,
            ob_chunks, ob_size;
    static {
        rsreset();
        ob_next = rsl(1);
        ob_prev = rsl(1);
        ob_x = rsl(1);
        ob_y = rsl(1);
        ob_z = rsl(1);
        ob_rot = rsl(1);
        ob_info = rsb(0);                 // début du bloc chargé depuis objinfo
        ob_rotspeed = rsl(1);
        ob_movspeed = rsl(1);
        ob_shape = rsl(1);
        ob_logic = rsl(1);
        ob_render = rsl(1);
        ob_hit = rsl(1);
        ob_die = rsl(1);
        ob_eyey = rsw(1);
        ob_firey = rsw(1);
        ob_gutsy = rsw(1);
        ob_mega = rsw(0); ob_othery = rsw(1);
        ob_colltype = rsw(1);
        ob_collwith = rsw(1);
        ob_cntrl = rsw(1);
        ob_damage = rsw(1);
        ob_hitpoints = rsw(1);
        ob_think = rsw(1);
        ob_frame = rsl(1);
        ob_framespeed = rsl(1);
        ob_base = rsw(1);
        ob_range = rsw(1);
        ob_weapon = rsw(1);
        ob_reload = rsb(1);
        ob_reloadcnt = rsb(1);
        ob_hurtpause = rsw(1);
        ob_firerate = rsw(0); ob_punchrate = rsw(1);
        ob_bouncecnt = rsw(1);
        ob_firecnt = rsw(0); ob_something = rsw(1);
        ob_scale = rsw(1);
        ob_lastbut = rsw(1);
        ob_blood = rsw(1);
        ob_ypad = rsw(1);
        ob_oldlogic = rsl(1);
        ob_oldlogic2 = rsl(1);
        ob_oldhit = rsl(1);
        ob_olddie = rsl(1);
        ob_oldrot = rsw(1);
        ob_newrot = rsw(1);
        ob_yvec = rsl(1);
        ob_xvec = rsl(1);
        ob_zvec = rsl(1);
        ob_radsq = rsl(1);
        ob_rad = rsw(1);
        ob_delay = rsw(1);
        ob_delay2 = rsw(0); ob_bounce = rsw(1);
        ob_hurtwait = rsw(1);
        ob_washit = rsl(1);
        ob_window = rsl(1);
        ob_nxvec = rsw(0); ob_lives = rsw(1);
        ob_nzvec = rsw(0); ob_infra = rsw(1);
        ob_thermo = rsw(1);
        ob_invisible = rsw(1);
        ob_hyper = rsw(1);
        ob_update = rsw(1);
        ob_mess = rsl(1);
        ob_messlen = rsw(1);
        ob_messtimer = rsw(1);
        ob_palette = rsl(1);
        ob_paltimer = rsw(1);
        ob_pixsize = rsw(1);
        ob_pixsizeadd = rsw(1);
        ob_telex = rsw(1);
        ob_telez = rsw(1);
        ob_telerot = rsw(1);
        ob_chunks = rsl(1);
        ob_size = rsb(0);
    }

    // ================= vd (colonne mur solide) — gloom.s:295 =================
    public static final int vd_z, vd_pal, vd_y, vd_h, vd_data, vd_ystep, vd_size;
    static {
        rsreset();
        vd_z = rsw(1);
        vd_pal = rsw(1);
        vd_y = rsw(1);
        vd_h = rsw(1);
        vd_data = rsl(1);
        vd_ystep = rsl(1);
        vd_size = rsb(0);
    }

    // ================= pa (palette file) — gloom.s:308 =================
    public static final int pa_numcols, pa_cols, pa_size;
    static {
        rsreset();
        pa_numcols = rsw(1);
        pa_cols = rsw(256);
        pa_size = rsb(0);                 // (pas de label d'origine ; 2+512=514)
    }

    // ================= an (anim file) — gloom.s:315 =================
    public static final int an_rotshft, an_frames, an_maxw, an_maxh, an_pal, an_size;
    static {
        rsreset();
        an_rotshft = rsw(1);
        an_frames = rsw(1);
        an_maxw = rsw(1);
        an_maxh = rsw(1);
        an_pal = rsl(1);
        an_size = rsb(0);
    }

    // ================= wi (window) — gloom.s:327 ===== (rs sans suffixe = .w) =====
    public static final int wi_slice, wi_nslice, wi_x, wi_y, wi_w, wi_h, wi_pw, wi_ph,
            wi_bw, wi_bh, wi_bmapmem, wi_copmem, wi_bmap, wi_cop, wi_cop1, wi_cop2,
            wi_copmod, wi_strip, wi_iff, wi_pal, wi_size;
    static {
        rsreset();
        wi_slice = rsl(1);
        wi_nslice = rsl(1);
        wi_x = rsw(1);
        wi_y = rsw(1);
        wi_w = rsw(1);
        wi_h = rsw(1);
        wi_pw = rsw(1);
        wi_ph = rsw(1);
        wi_bw = rsw(1);
        wi_bh = rsw(1);
        wi_bmapmem = rsl(1);
        wi_copmem = rsl(1);
        wi_bmap = rsl(1);
        wi_cop = rsl(1);
        wi_cop1 = rsl(1);
        wi_cop2 = rsl(1);
        wi_copmod = rsw(1);
        wi_strip = rsl(1);
        wi_iff = rsl(1);
        wi_pal = rsl(1);
        wi_size = rsb(0);
    }

    private Defs() {
    }
}
