import type { PageView } from "./types";

export const invalidResponse = "Răspunsul API nu are formatul așteptat.";

export function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function readPage<T>(response: unknown, page: number, valid: (item: unknown) => item is T): PageView<T> {
  if (
    !isRecord(response) ||
    !Array.isArray(response.items) ||
    !response.items.every(valid) ||
    response.page !== page ||
    typeof response.totalPages !== "number" ||
    !Number.isSafeInteger(response.totalPages) ||
    response.totalPages < 0 ||
    !Number.isInteger(response.size) ||
    !Number.isInteger(response.totalElements)
  ) {
    throw new Error(invalidResponse);
  }
  return response as unknown as PageView<T>;
}

export async function readPages<T>(
  filters: Record<string, string>,
  load: (query: URLSearchParams) => Promise<unknown>,
  valid: (item: unknown) => item is T,
): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0, totalPages = 1; page < totalPages; page++) {
    const response = await load(
      new URLSearchParams({ ...filters, page: String(page), size: "100" }),
    );
    const data = readPage(response, page, valid);
    items.push(...data.items);
    totalPages = data.totalPages;
  }
  return items;
}
