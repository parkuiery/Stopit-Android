// Headless capture of the /ads artboards into out/ads/.
//
// Not wired into the `ASO screenshots build` CI gate: it needs a browser driver
// that is deliberately kept out of package.json so `bun install --frozen-lockfile`
// stays untouched. Install it ad hoc before running:
//
//   bun dev --port 3100                       # in one shell
//   npm i --no-save playwright-core           # in another
//   node scripts/capture-ads.mjs
//
// Chrome is driven through the `chrome` channel, so no browser download is needed
// on a machine that already has Google Chrome installed.
import { chromium } from "playwright-core";
import path from "node:path";
import fs from "node:fs";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const BASE = process.env.BASE ?? "http://localhost:3100";
const OUT = process.env.OUT ?? path.join(ROOT, "out", "ads");

// Must mirror SIZES / CONCEPTS in src/app/ads/page.tsx.
const SIZES = {
  landscape: { w: 1200, h: 628 },
  square: { w: 1200, h: 1200 },
  portrait: { w: 1200, h: 1500 },
};
const CONCEPTS = ["focus", "block", "record"];

fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ channel: "chrome" });
try {
  for (const concept of CONCEPTS) {
    for (const [sizeId, { w, h }] of Object.entries(SIZES)) {
      const page = await browser.newPage({
        viewport: { width: w, height: h },
        deviceScaleFactor: 1,
      });
      await page.goto(`${BASE}/ads?solo=${concept}-${sizeId}`, { waitUntil: "networkidle" });
      // Fonts and the scene bitmap must both be decoded before the shot, or the
      // capture lands on fallback metrics and a blank background.
      await page.evaluate(() => document.fonts.ready);
      await page.evaluate(() =>
        Promise.all(
          Array.from(document.images)
            .filter((img) => !img.complete)
            .map((img) => new Promise((res) => { img.onload = img.onerror = res; })),
        ),
      );
      await page.waitForTimeout(400);
      const file = path.join(OUT, `ads-${concept}-${w}x${h}.png`);
      await page.screenshot({ path: file, clip: { x: 0, y: 0, width: w, height: h } });
      console.log(path.basename(file));
      await page.close();
    }
  }
} finally {
  await browser.close();
}
