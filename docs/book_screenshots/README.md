# Guide book pictures

The guide book shows a picture of each finished multiblock. The pictures are made from in-game screenshots placed in this folder:

| File name | What to photograph |
|---|---|
| `illyrium_crucible.png` | A finished, running Illyrium Crucible |
| `illyrium_refinery.png` | A finished, running Illyrium Refinery |
| `shatter_coin_factory.png` | A finished factory slice (or a few joined), ideally with a Motivator |

How to take them: build the machine somewhere tidy, hide the HUD with F1, stand so the machine fills the middle of the screen, and press F2. Screenshots are saved in `run/screenshots/` (or `.minecraft/screenshots/`). Copy each one here with the file name above; any size works, and only the middle square is used.

Then run:

```bash
python3 tools/make_book_images.py
```

```bash
python3 tools/gen_guide_book.py
```

The first command crops and shrinks the screenshots into the book's picture format. The second rebuilds the book: a machine with a picture gets a picture page, and one without keeps the turnable 3D block layout page.
