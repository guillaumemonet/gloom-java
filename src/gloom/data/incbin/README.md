# incbin de compilation

Fichiers que `gloom.s` inclut **à l'assemblage** (`incbin`) : tables précalculées (`sqr`, `arc`,
`castrots*`, `camrots*`, `gridoffs4`), sprites intégrés (`bullet1-5`, `sparks1-5`, `chatfont`) et
blobs de code 68k (`medplay`, `decrm` — non exécutés, ils tiennent leur place en mémoire).

Ils font partie des **sources** (dépôt GloomAmiga), pas des disquettes : sur Gloom Deluxe ils sont
noyés dans l'exécutable. Ils sont donc versionnés ici et lus sur le classpath par `gloom.Assets`
avant les disquettes. Copies à l'identique de `GloomAmiga/<nom>`.
