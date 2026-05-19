// Renders sibling *.html mockups to PNGs via Playwright at two scales:
//
//   screenshots/                — dev-preview PNGs at dpr=2 for in-tool review
//   play-assets/                — Play Store-grade PNGs at dpr=2.625 (Pixel 7
//                                 native density), suitable for direct upload
//                                 as phone screenshots. Feature graphic
//                                 (feature-graphic.html) renders to 1024x500
//                                 at dpr=1 — Play Store's exact spec.
//
// Each HTML defaults to a 412x915 logical viewport (Pixel 7 logical px).
// Override per file with: <!-- viewport: WIDTHxHEIGHT -->
//
// Playwright lookup: defaults to a sibling repo at
// ../../../riftlens/frontend/node_modules. Override with the env var
// SLEEPSOUND_PLAYWRIGHT_DIR. See README / BUILDING for details.

import { existsSync, mkdirSync, readdirSync, readFileSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import { basename, dirname, join, resolve } from "node:path";
import { createRequire } from "node:module";

const __dirname = dirname(fileURLToPath(import.meta.url));
const candidatePaths = [
  process.env.SLEEPSOUND_PLAYWRIGHT_DIR,
  resolve(__dirname, "../../../riftlens/frontend/node_modules"),
  resolve(__dirname, "../../node_modules"),
  resolve(__dirname, "node_modules"),
].filter(Boolean);

const playwrightDir = candidatePaths.find((p) =>
  existsSync(join(p, "playwright/index.js")),
);
if (!playwrightDir) {
  console.error(
    "Could not find a Playwright install. Tried:\n  " +
      candidatePaths.join("\n  ") +
      "\nSet SLEEPSOUND_PLAYWRIGHT_DIR to a node_modules path that contains playwright.",
  );
  process.exit(1);
}
const require = createRequire(import.meta.url);
const { chromium } = require(join(playwrightDir, "playwright/index.js"));

const previewDir = join(__dirname, "screenshots");
const playDir = join(__dirname, "play-assets");
mkdirSync(previewDir, { recursive: true });
mkdirSync(playDir, { recursive: true });

const htmls = readdirSync(__dirname).filter((f) => f.endsWith(".html"));
if (htmls.length === 0) {
  console.error("No .html files found in", __dirname);
  process.exit(1);
}

const VIEWPORT_RE = /<!--\s*viewport:\s*(\d+)x(\d+)\s*-->/i;

function viewportFor(htmlPath) {
  const text = readFileSync(htmlPath, "utf8");
  const m = VIEWPORT_RE.exec(text);
  if (m) return { width: Number(m[1]), height: Number(m[2]) };
  return { width: 412, height: 915 }; // Pixel 7 logical default
}

async function renderOne(browser, htmlPath, vp, dpr, outPath) {
  const ctx = await browser.newContext({
    viewport: vp,
    deviceScaleFactor: dpr,
  });
  const page = await ctx.newPage();
  await page.goto(pathToFileURL(htmlPath).toString(), { waitUntil: "networkidle" });
  await page.waitForTimeout(400); // let the Material Symbols font settle
  await page.screenshot({ path: outPath, fullPage: false });
  await ctx.close();
  console.log("wrote", outPath);
}

const browser = await chromium.launch();
try {
  for (const html of htmls) {
    const htmlPath = join(__dirname, html);
    const vp = viewportFor(htmlPath);
    const stem = basename(html, ".html");
    const isFeatureGraphic = stem === "feature-graphic";

    // Always produce a dev-preview at dpr 2.
    await renderOne(browser, htmlPath, vp, 2, join(previewDir, stem + ".png"));

    // Play-asset render: feature graphic at native size + dpr 1 (Play wants
    // exactly 1024x500). Everything else gets Pixel 7 density (dpr 2.625)
    // so screenshots land at 1081x2402 — within Play's 1080-min recommendation.
    const playDpr = isFeatureGraphic ? 1 : 2.625;
    await renderOne(browser, htmlPath, vp, playDpr, join(playDir, stem + ".png"));
  }
} finally {
  await browser.close();
}
