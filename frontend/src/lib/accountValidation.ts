export function passwordError(value: string, requireMinimum = true): string {
  if (!value.trim()) return "Introdu parola.";
  if (requireMinimum && value.length < 8) return "Parola trebuie să aibă cel puțin 8 caractere.";
  return new TextEncoder().encode(value).length > 72 ? "Parola poate avea cel mult 72 de octeți." : "";
}

export function displayNameError(value: string): string {
  if (!value.trim()) return "Introdu numele.";
  return value.trim().length > 64 ? "Numele poate avea cel mult 64 de caractere." : "";
}

export const normalizeEmail = (email: string) => email.trim().toLowerCase();
