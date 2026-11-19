import { Settings } from "lucide-react";
import type { AuthUser } from "../api/auth";
import { ProfileSettings } from "./ProfileSettings";
import { PasswordSettings } from "./PasswordSettings";
import { PredictionSettings } from "./PredictionSettings";

export function AccountSettingsPage({ user, onProfileSaved, onPasswordChanged }: {
  user: AuthUser;
  onProfileSaved: (user: AuthUser) => void;
  onPasswordChanged: () => void;
}) {
  return (
    <main className="main-container account-settings-page" id="account-settings">
      <header className="settings-heading"><Settings size={28} aria-hidden="true" /><h1>Setările contului</h1></header>
      <ProfileSettings user={user} onSaved={onProfileSaved} />
      <PasswordSettings onChanged={onPasswordChanged} />
      <PredictionSettings userId={user.id} />
    </main>
  );
}
