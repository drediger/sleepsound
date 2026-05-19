// Renders any sibling *.html to PNG via Playwright at Pixel 7 dimensions
// (412 x 915, deviceScaleFactor 2). The Android project has no Node deps of
// its own — by default we reach for a Playwright install in a sibling repo
// at `../../../riftlens/frontend/node_modules`. Override with the env var
// SLEEPSOUND_PLAYWRIGHT_DIR if your Playwright lives elsewhere, or run
// `npm install playwright` and point the var at this dir's node_modules.

import { existsSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import { readdirSync, mkdirSync } from "node:fs";
import { dirname, join, basename, resolve } from "node:path";
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
const outDir = join(__dirname, "screenshots");
mkdirSync(outDir, { recursive: true });

const htmls = readdirSync(__dirname).filter((f) => f.endsWith(".html"));
if (htmls.length === 0) {
  console.error("No .html files found in", __dirname);
  process.exit(1);
}

const browser = await chromium.launch();
try {
  const ctx = await browser.newContext({
    viewport: { width: 412, height: 915 },
    deviceScaleFactor: 2,
  });
  const page = await ctx.newPage();
  for (const html of htmls) {
    const url = pathToFileURL(join(__dirname, html)).toString();
    await page.goto(url, { waitUntil: "networkidle" });
    // Give the Material Symbols font a tick to load
    await page.waitForTimeout(400);
    const outPath = join(outDir, basename(html, ".html") + ".png");
    await page.screenshot({ path: outPath, fullPage: false });
    console.log("wrote", outPath);
  }
} finally {
  await browser.close();
}
