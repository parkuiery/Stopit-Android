import path from "node:path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The dev overlay badge would otherwise be baked into headless artboard captures.
  devIndicators: false,
  turbopack: {
    root: path.resolve(__dirname),
  },
};

export default nextConfig;
