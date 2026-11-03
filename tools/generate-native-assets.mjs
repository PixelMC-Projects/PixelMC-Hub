// Original application tooling, Apache-2.0. Reuses artwork under its original notices.
// Prerequisite: npm install --prefix .runtime/native-assets --no-save --package-lock=false @resvg/resvg-js@2.6.2
// Run from this repository: node tools/generate-native-assets.mjs
import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import * as Lucide from "lucide-react";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const nativeRequire = createRequire(path.join(root, ".runtime/native-assets/package.json"));
const { Resvg } = nativeRequire("@resvg/resvg-js");
const output = path.join(root, "minecraft/src/main/resources/assets/pixelmc_hub");
const read = (relative) => fs.readFile(path.join(root, relative));
const hash = (buffer) => crypto.createHash("sha256").update(buffer).digest("hex");
const write = async (relative, data) => {
  const destination = path.join(output, relative);
  await fs.mkdir(path.dirname(destination), { recursive: true });
  await fs.writeFile(destination, data);
};
const mapping = {
  product: "PixelMC-Hub",
  generatedBy: "tools/generate-native-assets.mjs",
  description: "Native preview copies of the current web reference; local-preview private skin is deliberately excluded.",
  artwork: [],
  icons: [],
};

for (const filename of ["avatar.png", "collection-chest.png", "fortune-barrel.png", "fortune-slip.png", "server-logo.png"]) {
  const source = `public/assets/${filename}`;
  const target = `textures/ui/${filename}`;
  const bytes = await read(source);
  await write(target, bytes);
  mapping.artwork.push({ source, target, sha256: hash(bytes), license: filename === "server-logo.png" ? "User-provided brand asset, see NOTICE.md" : filename === "avatar.png" ? "Original MemorialTitles demo placeholder, see NOTICE.md" : "AGPL-3.0-only" });
}

const sources = await Promise.all(["src/App.jsx", "src/title-icons.js"].map((file) => fs.readFile(path.join(root, file), "utf8")));
const iconNames = [...new Set(sources.flatMap((source) => [...source.matchAll(/import\s*\{([^}]+)\}\s*from\s*["']lucide-react["']/g)].flatMap((match) => match[1].split(",").map((name) => name.trim()).filter(Boolean))))];
const kebab = (name) => name.replace(/([a-z0-9])([A-Z])/g, "$1-$2").replace(/([a-zA-Z])([0-9])/g, "$1-$2").toLowerCase();
const iconAliases = { Grid2X2: "grid-2x2" };
async function rasterizeIcon(exportName, strokeWidth, subdirectory = "") {
  if (!Lucide[exportName]) throw new Error(`Unknown Lucide icon: ${exportName}`);
  const name = iconAliases[exportName] || kebab(exportName);
  // Some Lucide shapes (for example Tag's hole) use currentColor for fill.
  const svg = renderToStaticMarkup(React.createElement(Lucide[exportName], { size: 192, color: "#ffffff", strokeWidth })).replaceAll("currentColor", "#ffffff");
  const png = new Resvg(svg, { fitTo: { mode: "width", value: 192 } }).render().asPng();
  const target = `textures/icons/${subdirectory}${name}.png`;
  await write(target, png);
  mapping.icons.push({ exportName, target, dimensions: [192, 192], viewBox: [0,0,24,24], strokeWidth, color: "white RGBA, transparent background", license: "ISC", sha256: hash(png) });
}
for (const exportName of iconNames) await rasterizeIcon(exportName, 1.65);
// Only title preset symbols use the web's explicit emblem/preview width overrides.
const titleIconNames = [...sources[1].matchAll(/import\s*\{([^}]+)\}\s*from\s*["']lucide-react["']/g)].flatMap((match) => match[1].split(",").map((name) => name.trim()).filter(Boolean));
for (const exportName of titleIconNames) {
  await rasterizeIcon(exportName, 1.5, "stroke-1.5/");
  await rasterizeIcon(exportName, 1, "stroke-1.0/");
}

const fortunesBytes = await read("src/fortunes.json");
await write("data/fortunes.json", fortunesBytes);
const fortunes = JSON.parse(fortunesBytes.toString("utf8"));
let dataSource = await fs.readFile(path.join(root, "src/data.js"), "utf8");
dataSource = dataSource.replace(/import fortuneBook from "\.\/fortunes\.json";/, `const fortuneBook = ${JSON.stringify(fortunes)};`);
const data = await import(`data:text/javascript;base64,${Buffer.from(dataSource).toString("base64")}`);
const demo = { demonstratorOnly: true, TODAY: data.TODAY.toISOString(), todayKey: data.todayKey, player: data.player, titles: data.titles, archive: data.archive, gradeColors: data.gradeColors, stateLabels: data.stateLabels };
await write("data/demo-data.json", JSON.stringify(demo, null, 2) + "\n");
mapping.data = [{ source: "src/fortunes.json", target: "data/fortunes.json", count: fortunes.fortunes.length, sha256: hash(fortunesBytes), license: "AGPL-3.0-only" }, { source: "src/data.js", target: "data/demo-data.json", demonstratorOnly: true }];
for (const [source, target] of [["NOTICE.md", "licenses/ORIGINAL-NOTICE.md"], ["LICENSE", "licenses/APACHE-2.0.txt"], ["licenses/AGPL-3.0-assets.txt", "licenses/AGPL-3.0-assets.txt"], ["node_modules/lucide-react/LICENSE", "licenses/LUCIDE-ISC.txt"]]) {
  await write(target, await read(source));
}
await write("ASSET-MAPPING.json", JSON.stringify(mapping, null, 2) + "\n");
await write("NOTICE.md", `# PixelMC-Hub native prototype assets\n\nArtwork is copied byte-for-byte from public/assets, with the original source attribution preserved in licenses/ORIGINAL-NOTICE.md. The collection chest, fortune barrel, fortune slip and all sixty fortune texts retain AGPL-3.0-only; full text is in licenses/AGPL-3.0-assets.txt. The PixelMC brand logo is user-provided and is not relicensed. The avatar is the MemorialTitles bundled demo placeholder. No private local-preview/player-skin.png is packaged.\n\nWhite transparent PNG icons are rasterized at 192 by 192 from installed lucide-react 0.468.0 SVG components, retaining the ISC copyright and permission text in licenses/LUCIDE-ISC.txt. They retain Lucide's 24 by 24 viewBox and round caps/joins. Default icons use 1.65-unit strokes to match the final web global SVG CSS override; the nine title preset symbols also provide 1.5-unit emblem variants under textures/icons/stroke-1.5/ and 1.0-unit preview-symbol variants under textures/icons/stroke-1.0/. These widths reflect the web cascade rather than Lucide's original 2-unit presentation attribute. No image generation or external image service is used.\n\nDemo JSON retains the web prototype's fictitious player, titles, dates and archive; it is not production player data. ASSET-MAPPING.json records original paths, native paths, stroke widths and SHA-256 hashes. System fonts are referenced at runtime and are not distributed. Original tooling and HubHead code are Apache-2.0.\n`);
console.log(`Copied ${mapping.artwork.length} original images, rasterized ${mapping.icons.length} Lucide icons, copied ${fortunes.fortunes.length} fortunes.`);
