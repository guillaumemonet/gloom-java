package gloom;

/**
 * Sous-système 04c — Exécution des événements de niveau.
 *
 * Traduction littérale de gloom.s :
 *  - execevent (gloom.s:2189) : dispatcher d'opcodes d'un programme d'événement ;
 *  - exec_opendoor (gloom.s:2237) : crée une porte ;
 *  - exec_changetxt (gloom.s:2226) : change la texture d'une zone ;
 *  - exec_rotpolys (gloom.s:2332) : démarre une rotation/morph de polygones.
 *
 * exec_addobj/loadobjs (08a), exec_teleport (08e : événements & portes) sont portés.
 *
 * Opcodes : 1=addobj, 2=opendoor, 3=teleport, 4=loadobjs, 5=changetxt, 6=rotpolys, 0=fin.
 */
public final class Events {

    private Events() {
    }

    /** execevent (gloom.s:2189) : d0 = numéro d'événement à exécuter. */
    public static void execevent(int d0) {
        Mem.wb(Vars.doorsfxflag, 0);                  // sf doorsfxflag
        int a6 = Mem.l(Vars.map_map);                 // move.l map_map,a6
        int a0 = Mem.l(Vars.map_events);              // move.l map_events,a0
        a6 += Mem.l(a0 + d0 * 4);                     // add.l 0(a0,d0*4),a6

        while (true) {                                // exec_loop
            int op = Mem.uw(a6); a6 += 2;             // move (a6)+,d0
            if (op == 0) break;                       // beq .rts
            switch (op) {
                case 1 -> a6 = exec_addobj(a6);       // add an object
                case 2 -> a6 = exec_opendoor(a6);     // open a door
                case 3 -> a6 = exec_teleport(a6);     // teleport
                case 4 -> a6 = exec_loadobjs(a6);     // load objects
                case 5 -> a6 = exec_changetxt(a6);    // change texture
                case 6 -> a6 = exec_rotpolys(a6);     // start polygons rotating
                default -> System.err.println("[execevent] opcode inconnu " + op + " (warn #$f0f)");
            }
        }
        // .rts
        if (Mem.uw(Vars.doorsfxflag) != 0) {          // tst doorsfxflag ; beq .nodoor
            Mem.ww(Vars.doorsfxflag, 0);              // clr doorsfxflag
            Sfx.playsfx(Mem.l(Vars.doorsfx), 64, 2);  // doorsfx, vol 64, pri 2
        }
    }

    /** exec_opendoor (gloom.s:2237). */
    public static int exec_opendoor(int a6) {
        Mem.wb(Vars.doorsfxflag, 0xff);               // st doorsfxflag
        int a0 = Lists.addlast(Vars.doors);           // addlast doors  ; a0 = nouvelle porte
        int d0 = Mem.uw(a6); a6 += 2;                 // move (a6)+,d0  ; door #
        int a1 = Mem.l(Vars.map_poly) + (d0 << 5);    // lsl #5 ; polygon to open
        Mem.wl(a0 + Defs.do_poly, a1);                // move.l a1,do_poly(a0)
        Mem.wl(a0 + Defs.do_lx, Mem.l(a1 + Defs.zo_lx)); // move.l zo_lx(a1),do_lx(a0)
        Mem.wl(a0 + Defs.do_rx, Mem.l(a1 + Defs.zo_rx)); // move.l zo_rx(a1),do_rx(a0)
        Mem.ww(a0 + Defs.do_frac, 0);                 // clr do_frac
        Mem.ww(a0 + Defs.do_fracadd, 0x100);          // move #$100,do_fracadd
        return a6;
    }

    /** exec_changetxt (gloom.s:2226). */
    static int exec_changetxt(int a6) {
        int d0 = Mem.uw(a6); a6 += 2;                 // move (a6)+,d0  ; zone#
        int a1 = Mem.l(Vars.map_poly) + (d0 << 5);    // lsl #5 ; polygon
        d0 = Mem.uw(a6); a6 += 2;                      // move (a6)+,d0  ; new texture
        Mem.ww(Vars.changedtxt, d0);                  // move d0,changedtxt
        Mem.wb(a1 + Defs.zo_t, d0);                   // move.b d0,zo_t(a1)
        return a6;
    }

    /** exec_rotpolys (gloom.s:2332) : rotation (bit0=0) ou morph (bit0=1). */
    static int exec_rotpolys(int a6) {
        int a0 = Lists.addlast(Vars.rotpolys);        // addlast rotpolys
        if (a0 == 0) {                                // bne .ok
            a6 += 8;                                  // addq #8,a6
            return a6;
        }
        // .ok
        Mem.wb(Vars.doorsfxflag, 0xff);               // st doorsfxflag
        int d0 = Mem.uw(a6);                          // movem (a6)+,d0-d3 : polynum,count,speed,flags
        int d1 = Mem.uw(a6 + 2);
        int d2 = Mem.uw(a6 + 4);
        int d3 = Mem.uw(a6 + 6);
        a6 += 8;
        Mem.ww(a0 + Defs.rp_rot, 0);                  // clr rp_rot
        Mem.ww(a0 + Defs.rp_num, d1);                 // move d1,rp_num
        Mem.ww(a0 + Defs.rp_speed, d2);               // move d2,rp_speed
        Mem.ww(a0 + Defs.rp_flags, d3);               // move d3,rp_flags
        int d5 = d1 - 1;                              // move d1,d5 ; subq #1,d5
        int a2 = Mem.l(Vars.map_poly) + (d0 << 5);    // lsl #5 ; add → polygons
        Mem.wl(a0 + Defs.rp_first, a2);               // move.l a2,rp_first(a0)

        if ((d3 & 1) != 0) {                          // btst #0,d3 ; beq .rot
            // morph : stocke (dx,dz,basex,basez) par vertex
            int a3 = a2 + ((d1 << 5) & 0xffff);       // lsl #5,d1 ; lea 0(a2,d1),a3
            int a1 = a0 + Defs.rp_vx;                 // lea rp_vx(a0),a1
            int cnt = d5;
            do {                                      // .loop
                int x3 = Mem.w(a3 + Defs.zo_lx);      // movem zo_lx(a3),d0-d1
                int z3 = Mem.w(a3 + Defs.zo_lz);
                int x2 = Mem.w(a2 + Defs.zo_lx);      // movem zo_lx(a2),d2-d3
                int z2 = Mem.w(a2 + Defs.zo_lz);
                Mem.ww(a1, x3 - x2);                  // sub d2,d0 ; movem d0-d3,(a1)
                Mem.ww(a1 + 2, z3 - z2);              // sub d3,d1
                Mem.ww(a1 + 4, x2);
                Mem.ww(a1 + 6, z2);
                a1 += 8;                              // addq #8,a1
                a2 += 32;                             // lea 32(a2),a2
                a3 += 32;                             // lea 32(a3),a3
            } while (cnt-- != 0);                     // dbf d5,.loop
        } else {
            // .rot : calcule le centre puis stocke les vertices relatifs
            int d6 = 0, d7 = 0;                       // moveq #0,d6/d7
            int a1 = a2;                              // move.l a2,a1
            int cnt = d5;
            do {                                      // .loop0
                d6 += Mem.w(a1 + Defs.zo_lx);         // movem zo_lx(a1),d0/d2 ; add.l d0,d6
                d7 += Mem.w(a1 + Defs.zo_lz);         //                        add.l d2,d7
                a1 += 32;
            } while (cnt-- != 0);                     // dbf d5,.loop0
            d6 = M68k.divu(d6, d1);                   // divu d1,d6
            d7 = M68k.divu(d7, d1);                   // divu d1,d7
            Mem.ww(a0 + Defs.rp_cx, d6); Mem.ww(a0 + Defs.rp_cz, d7); // movem d6-d7,rp_cx
            a1 = a0 + Defs.rp_lx;                     // lea rp_lx(a0),a1
            int cnt2 = d1 - 1;                        // subq #1,d1
            do {                                      // .loop2
                int vx = Mem.w(a2 + Defs.zo_lx);      // movem zo_lx(a2),d0/d2
                int vz = Mem.w(a2 + Defs.zo_lz);
                Mem.ww(a1, vx - d6); a1 += 2;         // sub d6,d0 ; move d0,(a1)+
                Mem.ww(a1, vz - d7); a1 += 2;         // sub d7,d2 ; move d2,(a1)+
                Mem.wl(a1, Mem.l(a2 + Defs.zo_na)); a1 += 4; // move.l zo_na(a2),(a1)+
                a2 += 32;                             // lea 32(a2),a2
            } while (cnt2-- != 0);                    // dbf d1,.loop2
        }
        return a6;
    }

    // --- objets (sous-système 08a) ---

    static int exec_addobj(int a6) {
        return Objects.exec_addobj(a6);
    }

    static int exec_loadobjs(int a6) {
        return Objects.exec_loadobjs(a6);
    }

    /**
     * exec_teleport (gloom.s:2254) : arme la téléportation de l'objet déclencheur (eventobj).
     * Données : telex(2), drapeau lock(2), telez(2), telerot(2). Drapeau lock≠0 = machine
     * Defender (locklogic) — reporté (sous-système 12). Sinon → animation de téléport (pixsizeadd=2).
     */
    static int exec_teleport(int a6) {
        int a0 = Mem.l(Vars.eventobj);
        Mem.ww(a0 + Defs.ob_telex, Mem.w(a6)); a6 += 2;
        int lock = Mem.w(a6); a6 += 2;                 // addq #2,a6 (drapeau lock/téléport)
        Mem.ww(a0 + Defs.ob_telez, Mem.w(a6)); a6 += 2;
        Mem.ww(a0 + Defs.ob_telerot, Mem.w(a6)); a6 += 2;
        if ((Mem.w(Vars.finished) | Mem.w(Vars.finished2)) != 0) return a6; // déjà fini/téléport
        if (lock != 0) return a6;                      // LOCK (Defender) — reporté
        Mem.ww(a0 + Defs.ob_pixsizeadd, 2);            // .tele : déclenche l'anim de téléport
        Sfx.playsfx(Mem.l(Vars.telesfx), 64, 10);      // dotelesfx (stub)
        return a6;
    }
}
