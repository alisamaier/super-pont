import { useEffect, useRef, type MouseEvent } from "react";
import { ChevronDown, Settings } from "lucide-react";

export function AccountMenu({ displayName, onNavigate, onSignOut, disabled }: {
  displayName: string;
  onNavigate: (event: MouseEvent<HTMLAnchorElement>, destination: string) => void;
  onSignOut: () => void;
  disabled: boolean;
}) {
  const menu = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    const closeOutside = (event: PointerEvent) => {
      if (event.target instanceof Node && !menu.current?.contains(event.target)) menu.current?.removeAttribute("open");
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape" && menu.current?.open) {
        menu.current.removeAttribute("open");
        menu.current.querySelector("summary")?.focus();
      }
    };
    document.addEventListener("pointerdown", closeOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, []);

  return (
    <details className="account-menu" ref={menu}>
      <summary title={displayName}>
        <span className="account-name">{displayName}</span><ChevronDown size={14} aria-hidden="true" />
      </summary>
      <div className="account-menu-panel">
        <a href="/setari-cont" onClick={(event) => {
          onNavigate(event, "/setari-cont");
          menu.current?.removeAttribute("open");
        }}><Settings size={15} aria-hidden="true" /> Setările contului</a>
        <button type="button" disabled={disabled} onClick={onSignOut}>Deconectare</button>
      </div>
    </details>
  );
}
