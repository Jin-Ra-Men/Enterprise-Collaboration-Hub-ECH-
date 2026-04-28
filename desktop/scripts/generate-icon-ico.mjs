/**
 * Windows용 assets/icon.ico 생성 (assets/icon.png, 정사각형 필요).
 * electron-builder는 EXE/바로가기 아이콘에 .ico 임베드를 사용하는 것이 안정적이다.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import pngToIco from "png-to-ico";
import sharp from "sharp";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, "..");
const input = path.join(root, "assets", "icon.png");
const output = path.join(root, "assets", "icon.ico");

if (!fs.existsSync(input)) {
  console.error("Missing:", input);
  process.exit(1);
}

const sizes = [16, 24, 32, 40, 48, 64, 128, 256];
const pngBuffers = await Promise.all(
  sizes.map((size) =>
    sharp(input)
      .resize(size, size, {
        fit: "contain",
        background: { r: 0, g: 0, b: 0, alpha: 0 },
      })
      .png()
      .toBuffer()
  )
);

const buf = await pngToIco(pngBuffers);
fs.writeFileSync(output, buf);
console.log("[CSTalk] wrote", output, `(${buf.length} bytes, sizes=${sizes.join(",")})`);
