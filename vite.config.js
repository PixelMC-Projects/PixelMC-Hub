import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { readFileSync } from "node:fs";
const licenseFiles = {
  "Apache-2.0.txt": "LICENSE",
  "NOTICE.md": "NOTICE.md",
  "React-Bits.txt": "src/vendor/react-bits/LICENSE.md",
  "React-Bits-sources.json": "src/vendor/react-bits/SOURCES.json",
  "AGPL-3.0-assets.txt": "licenses/AGPL-3.0-assets.txt",
  "React.txt": "node_modules/react/LICENSE",
  "React-DOM.txt": "node_modules/react-dom/LICENSE",
  "Lucide.txt": "node_modules/lucide-react/LICENSE",
  "Motion.txt": "node_modules/motion/LICENSE.md",
  "Framer-Motion.txt": "node_modules/framer-motion/LICENSE.md",
  "Motion-DOM.txt": "node_modules/motion-dom/LICENSE.md",
  "Motion-Utils.txt": "node_modules/motion-utils/LICENSE.md",
  "tslib.txt": "node_modules/tslib/LICENSE.txt",
};
export default defineConfig({
  plugins: [react(), {
    name: "preserve-distribution-notices",
    generateBundle() {
      for (const [name, source] of Object.entries(licenseFiles)) {
        this.emitFile({ type: "asset", fileName: `licenses/${name}`,
          source: readFileSync(new URL(source, import.meta.url), "utf8") });
      }
    },
  }],
  server: { host: "127.0.0.1", port: 4176, strictPort: true },
});
