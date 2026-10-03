"""Generates src/main/resources/assets/descentmtb/textures/item/trail_tool.png (16x16 little spade)."""
from PIL import Image
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "descentmtb", "textures", "item", "trail_tool.png")

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()

WOOD = [(122, 84, 44, 255), (150, 105, 56, 255)]
WOOD_D = (84, 56, 28, 255)
IRON = [(214, 218, 222, 255), (170, 176, 184, 255)]
IRON_D = (96, 102, 112, 255)

# handle: diagonal from bottom-left up to the blade (top-right)
for i in range(8):
    x, y = 1 + i, 14 - i
    px[x, y] = WOOD[i % 2]
    if x + 1 < 16:
        px[x + 1, y] = WOOD_D
# grip crossbar at the bottom of the handle
for dx, dy in ((0, 14), (1, 15), (0, 15)):
    px[dx, dy] = WOOD_D

# spade blade (rounded, top-right corner)
blade = [(10, 5), (11, 4), (12, 3), (13, 2), (14, 1),
         (10, 4), (11, 3), (12, 2), (13, 1),
         (11, 5), (12, 4), (13, 3), (14, 2),
         (12, 5), (13, 4), (14, 3),
         (13, 5), (14, 4), (14, 5), (11, 6), (12, 6), (13, 6), (10, 6)]
for x, y in blade:
    px[x, y] = IRON[(x + y) % 2]
# outline / shading
for x, y in ((9, 5), (9, 6), (10, 7), (11, 7), (12, 7), (13, 7), (14, 6), (15, 5), (15, 4), (15, 3), (14, 0), (13, 0), (12, 1), (11, 2), (10, 3), (9, 4)):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = IRON_D
# socket connecting handle to blade
px[8, 7] = IRON_D
px[9, 7] = IRON[1]
px[9, 8] = IRON_D

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("wrote", os.path.normpath(OUT))
