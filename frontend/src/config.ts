const configuredUrl = import.meta.env.VITE_API_URL;
if (!configuredUrl) {
  throw new Error("VITE_API_URL is required");
}
const parsedUrl = new URL(configuredUrl);
if (!["http:", "https:"].includes(parsedUrl.protocol)) {
  throw new Error("VITE_API_URL must use HTTP or HTTPS");
}
export const apiUrl = configuredUrl.replace(/\/+$/, "");
