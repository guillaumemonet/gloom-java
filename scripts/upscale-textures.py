#!/usr/bin/env python3
"""
Upscale IA des textures Gloom : hd_src/*.png  ->  hd/*.png

Pipeline complet :
  1) cd <depot GloomAmiga> ; gradle -p <.../java> dumpTextures -Dmap=map1_3 -Dtile=1   (ecrit hd_src/)
  2) python upscale-textures.py --src <.../hd_src> --out <.../hd>                       (ecrit hd/)
  3) lancer rebirth : il charge automatiquement hd/wall_<n>.png, hd/floor.png, hd/roof.png

Backends (auto) :
  - 'realesrgan'  : binaire Real-ESRGAN ncnn 'realesrgan-ncnn-vulkan' present sur le PATH (VRAIE IA,
                    recommande). Modele par defaut 'realesrgan-x4plus-anime' (meilleur sur du pixel-art).
  - 'pillow'      : repli SANS IA (Pillow, agrandissement LANCZOS). Toujours disponible, qualite moindre.

Caveat textures qui se repetent (tiling) : un upscaler IA peut casser la continuite des bords.
Si une texture HD montre une couture, regenere-la en 'pillow' ou retouche-la.

Dependances : Python 3.8+. Pour le repli : `pip install pillow`. Pour l'IA : installer Real-ESRGAN ncnn
(https://github.com/xinntao/Real-ESRGAN/releases) et mettre 'realesrgan-ncnn-vulkan' sur le PATH.
"""
import argparse
import os
import shutil
import subprocess
import sys


def main():
    ap = argparse.ArgumentParser(description="Upscale des textures Gloom vers HD.")
    ap.add_argument('--src', default='hd_src', help="dossier d'entree (PNG base, defaut: hd_src)")
    ap.add_argument('--out', default='hd', help="dossier de sortie HD (defaut: hd)")
    ap.add_argument('--scale', type=int, default=4, help="facteur d'agrandissement (defaut: 4)")
    ap.add_argument('--backend', default='auto', choices=['auto', 'realesrgan', 'pillow'])
    ap.add_argument('--model', default='realesrgan-x4plus-anime', help="modele Real-ESRGAN ncnn")
    args = ap.parse_args()

    if not os.path.isdir(args.src):
        sys.exit(f"Dossier source introuvable : {args.src}  (lance d'abord 'gradle dumpTextures')")
    # parcourt RÉCURSIVEMENT (sous-dossiers par épisode : hd_src/map1/...) en conservant l'arborescence.
    pairs = []
    for root, _dirs, names in os.walk(args.src):
        for f in names:
            if f.lower().endswith('.png'):
                rel = os.path.relpath(os.path.join(root, f), args.src)
                pairs.append((os.path.join(args.src, rel), os.path.join(args.out, rel)))
    if not pairs:
        sys.exit(f"Aucun PNG sous {args.src}")

    backend = args.backend
    if backend == 'auto':
        backend = 'realesrgan' if shutil.which('realesrgan-ncnn-vulkan') else 'pillow'
    print(f"Backend : {backend}  |  {len(pairs)} textures  |  x{args.scale}  ->  {args.out}")

    pil = None
    if backend == 'pillow':
        try:
            from PIL import Image as pil
        except ImportError:
            sys.exit("Pillow manquant : `pip install pillow` (ou installe Real-ESRGAN pour la vraie IA).")

    for src, dst in pairs:
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if backend == 'realesrgan':
            subprocess.run(['realesrgan-ncnn-vulkan', '-i', src, '-o', dst,
                            '-s', str(args.scale), '-n', args.model], check=True)
        else:
            im = pil.open(src).convert('RGB')
            im = im.resize((im.width * args.scale, im.height * args.scale), pil.LANCZOS)
            im.save(dst)
        print('  HD', os.path.relpath(dst, args.out))

    print(f"Termine -> {args.out}  (rebirth les chargera automatiquement)")


if __name__ == '__main__':
    main()
