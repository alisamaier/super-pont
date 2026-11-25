import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { URL } from "node:url";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ".", "VITE_");
  if (!env.VITE_API_URL) {
    throw new Error("VITE_API_URL is required; set it in frontend/.env or the hosting environment");
  }
  const url = new URL(env.VITE_API_URL);
  if (!["http:", "https:"].includes(url.protocol)) {
    throw new Error("VITE_API_URL must use HTTP or HTTPS");
  }
  if (url.username || url.password || url.pathname !== "/" || url.search || url.hash) {
    throw new Error("VITE_API_URL must be an origin without credentials, path, query or fragment");
  }
  return { plugins: [react()] };
});

