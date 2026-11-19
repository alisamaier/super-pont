import { RotateCw, TriangleAlert } from "lucide-react";

export function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="load-error" role="alert">
      <TriangleAlert size={18} aria-hidden="true" />
      <p>{message}</p>
      <button className="text-button" onClick={onRetry}>
        <RotateCw size={14} aria-hidden="true" /> Încearcă din nou
      </button>
    </div>
  );
}
