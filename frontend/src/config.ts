const configuredUrl = import.meta.env.VITE_API_URL;
if (!configuredUrl) {
  throw new Error("VITE_API_URL is required");
}
const parsedUrl = new URL(configuredUrl);
if (!["http:", "https:"].includes(parsedUrl.protocol)) {
  throw new Error("VITE_API_URL must use HTTP or HTTPS");
}
if (parsedUrl.username || parsedUrl.password || parsedUrl.pathname !== "/" || parsedUrl.search || parsedUrl.hash) {
  throw new Error("VITE_API_URL must be an origin without credentials, path, query or fragment");
}
export const apiUrl = configuredUrl.replace(/\/+$/, "");
