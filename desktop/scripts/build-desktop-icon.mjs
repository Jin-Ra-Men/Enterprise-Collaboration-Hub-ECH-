/**
 * Builds `assets/icon.png` from `assets/icon-source-cs.png` (or argv[1]):
 * - Removes near-white background (transparent alpha)
 * - Trims, then fits into a 512×512 transparent square for Electron / ICO pipeline
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import sharp from "sharp";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, "..");

const INPUT = process.argv[2] || path.join(root, "assets", "icon-source-cs.png");
const OUT_PNG = path.join(root, "assets", "icon.png");
const CANVAS = 512;

async function main() {
  if (!fs.existsSync(INPUT)) {
    console.error("Missing:", INPUT);
    process.exit(1);
  }

  const { data, info } = await sharp(INPUT)
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });

  const { width, height, channels } = info;
  if (channels !== 4) {
    console.error("Expected RGBA");
    process.exit(1);
  }

  const pixels = new Uint8ClampedArray(data);
  for (let i = 0; i < pixels.length; i += 4) {
    const r = pixels[i];
    const g = pixels[i + 1];
    const b = pixels[i + 2];
    if (r >= 235 && g >= 235 && b >= 235) {
      pixels[i + 3] = 0;
    }
  }

  await sharp(Buffer.from(pixels), {
    raw: { width, height, channels: 4 },
  })
    .trim()
    .resize(CANVAS, CANVAS, {
      fit: "contain",
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    })
    .png()
    .toFile(OUT_PNG);

  console.log("[ECH] wrote", OUT_PNG);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
