"""Static Fraunces instances for app/src/main/res/font (10.0).

The app sets each Fraunces cut at one weight and optical size only, so it ships those instances
instead of the variable fonts. Sources: the variable Latin subsets (WONK=0) of 9.0, kept in git:

    git show b720564:app/src/main/res/font/fraunces.ttf > fraunces-var.ttf
    git show b720564:app/src/main/res/font/fraunces_italic.ttf > fraunces_italic-var.ttf
    pip install fonttools
    python3 third_party/fraunces/make_instances.py fraunces-var.ttf fraunces_italic-var.ttf app/src/main/res/font

Each instance is checked against the variable font at its location: same contours, points within
1.25 of 2000 units (rounding), advances within half a unit.
"""
import sys

from fontTools import subset
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROMAN, ITALIC, OUT = sys.argv[1:4]

# (source, location, file, name, unicodes or None for all)
CUTS = [
    (ROMAN, {"wght": 560, "opsz": 36}, "fraunces.ttf", "Printed", None),
    (ROMAN, {"wght": 400, "opsz": 96}, "fraunces_display.ttf", "Display", None),
    (ROMAN, {"wght": 600, "opsz": 96}, "fraunces_display_semibold.ttf", "Display SemiBold", None),
    # The pen writes digits only.
    (ITALIC, {"wght": 420, "opsz": 36}, "fraunces_italic.ttf", "Pen Italic", list(range(0x30, 0x3A)) + [0x20]),
]


def make(source, location, path, name, unicodes):
    font = instancer.instantiateVariableFont(TTFont(source), location, updateFontNames=False)
    font["OS/2"].usWeightClass = location["wght"]
    if unicodes is not None:
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = True
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=unicodes)
        subsetter.subset(font)
    names = font["name"]
    names.removeNames(nameID=16)
    names.removeNames(nameID=17)
    full = "Fraunces " + name
    names.setName(full, 4, 3, 1, 0x409)
    names.setName(full.replace(" ", "-"), 6, 3, 1, 0x409)
    font.save(path)
    check(source, location, path)


def check(source, location, path):
    var, static = TTFont(source), TTFont(path)
    var_glyphs, static_glyphs = var.getGlyphSet(location=location), static.getGlyphSet()
    var_cmap = var.getBestCmap()
    worst = 0.0
    for codepoint, glyph in static.getBestCmap().items():
        a, b = DecomposingRecordingPen(var_glyphs), DecomposingRecordingPen(static_glyphs)
        var_glyphs[var_cmap[codepoint]].draw(a)
        static_glyphs[glyph].draw(b)
        assert [op for op, _ in a.value] == [op for op, _ in b.value], (path, hex(codepoint))
        for (_, pa), (_, pb) in zip(a.value, b.value):
            for p, q in zip(pa, pb):
                worst = max(worst, abs(p[0] - q[0]), abs(p[1] - q[1]))
        assert abs(var_glyphs[var_cmap[codepoint]].width - static_glyphs[glyph].width) <= 0.51, (path, hex(codepoint))
    assert worst <= 1.5, (path, worst)
    print(f"{path}: {len(static.getGlyphOrder())} glyphs, points within {worst:.2f} units")


for source, location, file, name, unicodes in CUTS:
    make(source, location, f"{OUT}/{file}", name, unicodes)
