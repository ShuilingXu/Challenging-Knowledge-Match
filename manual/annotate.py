"""Annotate staff-console screenshots with numbered callouts and produce crops."""
import json
import os
from PIL import Image, ImageDraw, ImageFont

RAW = "manual/raw"
OUT = "manual/final"
os.makedirs(OUT, exist_ok=True)

S = 2  # screenshots were captured at deviceScaleFactor=2 (CSS px -> image px)
TEAL = (15, 118, 110)
WHITE = (255, 255, 255)
FONT_PATH = r"C:\Windows\Fonts\msyhbd.ttc"


def font(size):
    return ImageFont.truetype(FONT_PATH, size)


def badge(draw, cx, cy, n, r=26):
    """White-ringed teal circle with a white number (coordinates in image px)."""
    draw.ellipse([cx - r - 7, cy - r - 7, cx + r + 7, cy + r + 7], fill=WHITE)
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=TEAL)
    f = font(int(r * 1.25))
    text = str(n)
    box = draw.textbbox((0, 0), text, font=f)
    draw.text((cx - (box[2] - box[0]) / 2 - box[0], cy - (box[3] - box[1]) / 2 - box[1]), text, fill=WHITE, font=f)


def outline(draw, box, width=4, radius=10):
    x, y, w, h = [v * S for v in box]
    draw.rounded_rectangle([x, y, x + w, y + h], radius=radius * S, outline=TEAL, width=width)


def line(draw, a, b, width=4):
    draw.line([a[0] * S, a[1] * S, b[0] * S, b[1] * S], fill=TEAL, width=width)


def open_base(name):
    return Image.open(os.path.join(RAW, name)).convert("RGB")


def crop(base, box):
    x, y, w, h = [v * S for v in box]
    return base.crop((x, y, x + w, y + h)), (x, y)


def shift(pt, origin):
    return ((pt[0] * S - origin[0]), (pt[1] * S - origin[1]))


def annotate(base, box, items, out, origin=(0, 0), radius=26):
    img = base
    d = ImageDraw.Draw(img)
    for it in items:
        if it.get("box"):
            outline(d, it["box"])
        if it.get("line"):
            line(d, it["line"][0], it["line"][1])
        cx, cy = shift(it["badge"], origin)
        badge(d, cx, cy, it["n"], radius)
    img.save(out, optimize=True)
    print(out, img.size)


# ---------------- quiz ----------------
quiz = open_base("quiz-long.png")
annotate(
    quiz.crop((0, 0, 1440 * S, 1500 * S)),
    None,
    [
        dict(n=1, box=(377, 332, 574, 40), badge=(640, 306), line=((640, 320), (640, 332))),
        dict(n=3, box=(315, 990, 768, 70), badge=(270, 1025), line=((292, 1025), (315, 1025))),
        dict(n=2, box=(316, 1074, 121, 42), badge=(376, 1150), line=((376, 1136), (376, 1116))),
        dict(n=5, box=(575, 1074, 133, 42), badge=(641, 1150), line=((641, 1136), (641, 1116))),
        dict(n=4, box=(716, 1074, 109, 42), badge=(770, 1150), line=((770, 1136), (770, 1116))),
    ],
    os.path.join(OUT, "quiz-overview.png"),
)

buttons, origin = crop(quiz, (300, 1050, 808, 130))
annotate(
    buttons,
    None,
    [
        dict(n=2, box=(316 - 300, 1074 - 1050, 121, 42), badge=(376 - 300, 1150 - 1050), line=((76, 100), (76, 66))),
        dict(n=5, box=(575 - 300, 1074 - 1050, 133, 42), badge=(641 - 300, 1150 - 1050), line=((341, 100), (341, 66))),
        dict(n=4, box=(716 - 300, 1074 - 1050, 109, 42), badge=(770 - 300, 1150 - 1050), line=((470, 100), (470, 66))),
    ],
    os.path.join(OUT, "quiz-buttons.png"),
)

# ---------------- soup ----------------
soup = open_base("soup-long.png")
annotate(
    soup,
    None,
    [
        dict(n=1, box=(1163, 585, 85, 42), badge=(1114, 606), line=((1136, 606), (1163, 606))),
        dict(n=2, box=(311, 939, 551, 588), badge=(826, 888)),
        dict(n=3, box=(311, 660, 1070, 57), badge=(270, 688), line=((292, 688), (311, 688))),
        dict(n=4, box=(290, 1626, 593, 299), badge=(270, 1652), line=((292, 1652), (311, 1652))),
        dict(n=5, box=(907, 762, 495, 871), badge=(884, 786)),
    ],
    os.path.join(OUT, "soup-overview.png"),
)

raw_soup = open_base("soup-long.png")
control, _ = crop(raw_soup, (296, 543, 1100, 197))
annotate(
    control,
    None,
    [
        dict(n=1, box=(1163 - 296, 585 - 543, 85, 42), badge=(790, 63), line=((816, 63), (867, 63))),
    ],
    os.path.join(OUT, "soup-control.png"),
)

board, _ = crop(raw_soup, (305, 938, 585, 458))
annotate(board, None, [], os.path.join(OUT, "soup-board.png"))
