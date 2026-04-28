/**
 * Builds `assets/icon.png` from `assets/icon-source-cs.png` (or argv[1]):
 * - Removes edge-connected near-white background only (keeps internal white logo)
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
const EDGE_BG_THRESHOLD = 242;

function isNearWhite(r, g, b) {
  return r >= EDGE_BG_THRESHOLD && g >= EDGE_BG_THRESHOLD && b >= EDGE_BG_THRESHOLD;
}

function makeEdgeBackgroundTransparent(pixels, width, height) {
  const visited = new Uint8Array(width * height);
  const queue = [];
  let qi = 0;

  const pushIfBg = (x, y) => {
    const idx = y * width + x;
    if (visited[idx]) return;
    visited[idx] = 1;
    const p = idx * 4;
    const r = pixels[p];
    const g = pixels[p + 1];
    const b = pixels[p + 2];
    const a = pixels[p + 3];
    if (a === 0 || !isNearWhite(r, g, b)) return;
    queue.push(idx);
  };

  for (let x = 0; x < width; x++) {
    pushIfBg(x, 0);
    pushIfBg(x, height - 1);
  }
  for (let y = 0; y < height; y++) {
    pushIfBg(0, y);
    pushIfBg(width - 1, y);
  }

  while (qi < queue.length) {
    const idx = queue[qi++];
    const p = idx * 4;
    pixels[p + 3] = 0;
    const x = idx % width;
    const y = Math.floor(idx / width);
    if (x > 0) pushIfBg(x - 1, y);
    if (x + 1 < width) pushIfBg(x + 1, y);
    if (y > 0) pushIfBg(x, y - 1);
    if (y + 1 < height) pushIfBg(x, y + 1);
  }
}

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
  makeEdgeBackgroundTransparent(pixels, width, height);

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
