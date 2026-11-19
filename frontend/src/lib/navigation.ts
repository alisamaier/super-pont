export const userPredictionsRoute = /^\/clasament\/utilizatori\/([^/]+)$/;

export function loginReturnPath(next: string | null, origin: string): string {
  if (!next) return "/";
  try {
    const url = new URL(next, origin);
    const protectedPage = url.pathname === "/setari-cont" || url.pathname === "/clasament"
      || userPredictionsRoute.test(url.pathname);
    return url.origin === origin && protectedPage ? url.pathname + url.search + url.hash : "/";
  } catch {
    return "/";
  }
}
