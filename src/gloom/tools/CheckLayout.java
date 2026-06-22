package gloom.tools;

import gloom.Defs;

/**
 * Garde-fou de disposition mémoire (sous-système 04a).
 *
 * Vérifie que les offsets/tailles calculés dans gloom.Defs (via le compteur RS)
 * correspondent aux valeurs canoniques relevées à la main dans les blocs rsreset
 * de gloom.s. Toute dérive d'un champ (réordonnancement, mauvaise taille rs.b/w/l,
 * alias oublié) est détectée ici.
 *
 * Doit afficher « TOUT OK ». Lancé par `gradle checkLayout`.
 */
public final class CheckLayout {

    private static int failures = 0;

    public static void main(String[] args) {
        // --- tailles totales des structures (valeurs canoniques) ---
        eq("rp_size", Defs.rp_size, 280);   // 32 + 8*31 (et NON 8*32)
        eq("fx_size", Defs.fx_size, 16);
        eq("bl_size", Defs.bl_size, 36);
        eq("te_size", Defs.te_size, 4);
        eq("do_size", Defs.do_size, 24);
        eq("wl_size", Defs.wl_size, 40);    // (et NON 44)
        eq("zo_size", Defs.zo_size, 32);    // commenté "32!" dans gloom.s
        eq("sh_size", Defs.sh_size, 26);    // (et NON 24)
        eq("go_size", Defs.go_size, 16);    // (et NON 18)
        eq("ob_size", Defs.ob_size, 196);
        eq("vd_size", Defs.vd_size, 16);
        eq("pa_size", Defs.pa_size, 514);
        eq("an_size", Defs.an_size, 12);
        eq("wi_size", Defs.wi_size, 62);

        // --- offsets clés (détectent un décalage de champ) ---
        // rotpoly
        eq("rp_first", Defs.rp_first, 18);
        eq("rp_num", Defs.rp_num, 22);
        eq("rp_vx==rp_lx", Defs.rp_vx, Defs.rp_lx); eq("rp_lx", Defs.rp_lx, 24);
        eq("rp_nb", Defs.rp_nb, 30);
        eq("rp_more", Defs.rp_more, 32);
        // blood — bl_dest est un alias de bl_yvec (offset 24), PAS de bl_xvec.
        eq("bl_xvec", Defs.bl_xvec, 20);
        eq("bl_dest==bl_yvec", Defs.bl_dest, Defs.bl_yvec); eq("bl_dest", Defs.bl_dest, 24);
        eq("bl_color", Defs.bl_color, 32);
        // door
        eq("do_frac", Defs.do_frac, 20);
        eq("do_fracadd", Defs.do_fracadd, 22);
        // wall
        eq("wl_c", Defs.wl_c, 24);
        eq("wl_open", Defs.wl_open, 30);
        eq("wl_t", Defs.wl_t, 32);
        // zone
        eq("zo_t", Defs.zo_t, 20);
        eq("zo_sc", Defs.zo_sc, 28);
        eq("zo_open==zo_ev", Defs.zo_open, Defs.zo_ev); eq("zo_ev", Defs.zo_ev, 30);
        // shape
        eq("sh_scale==sh_strip", Defs.sh_scale, Defs.sh_strip); eq("sh_strip", Defs.sh_strip, 18);
        eq("sh_render", Defs.sh_render, 22);
        // object
        eq("ob_x", Defs.ob_x, 8);
        eq("ob_rot", Defs.ob_rot, 20);
        eq("ob_info", Defs.ob_info, 24);
        eq("ob_logic", Defs.ob_logic, 36);
        eq("ob_mega==ob_othery", Defs.ob_mega, Defs.ob_othery); eq("ob_othery", Defs.ob_othery, 58);
        eq("ob_reloadcnt", Defs.ob_reloadcnt, 87);
        eq("ob_blood", Defs.ob_blood, 100);
        eq("ob_nxvec==ob_lives", Defs.ob_nxvec, Defs.ob_lives); eq("ob_lives", Defs.ob_lives, 156);
        eq("ob_nzvec==ob_infra", Defs.ob_nzvec, Defs.ob_infra); eq("ob_infra", Defs.ob_infra, 158);
        eq("ob_telerot", Defs.ob_telerot, 190);
        eq("ob_chunks", Defs.ob_chunks, 192);
        // vd
        eq("vd_data", Defs.vd_data, 8);
        eq("vd_ystep", Defs.vd_ystep, 12);
        // anim
        eq("an_pal", Defs.an_pal, 8);
        // window
        eq("wi_bmapmem", Defs.wi_bmapmem, 24);
        eq("wi_cop1", Defs.wi_cop1, 40);
        eq("wi_pal", Defs.wi_pal, 58);
        // palette file
        eq("pa_cols", Defs.pa_cols, 2);

        if (failures == 0) {
            System.out.println("TOUT OK");
        } else {
            System.out.println(failures + " ECHEC(S) de disposition mémoire");
            System.exit(1);
        }
    }

    private static void eq(String name, int actual, int expected) {
        if (actual != expected) {
            System.out.println("ECHEC " + name + " : attendu " + expected + ", obtenu " + actual);
            failures++;
        }
    }

    private CheckLayout() {
    }
}
