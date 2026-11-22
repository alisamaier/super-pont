import { useEffect } from "react";
import { BookOpen } from "lucide-react";
import "./Rules.css";

const examples = [
  ["5–0", "5–0", 5, 5, 10],
  ["5–0", "4–0", 5, 4, 9],
  ["5–0", "3–0", 5, 3, 8],
  ["5–0", "6–1", 5, 3, 8],
  ["1–1", "0–0", 5, 3, 8],
  ["1–0", "0–0", 0, 4, 4],
  ["1–0", "0–1", 0, 3, 3],
];

export function RulesPage({ hash }: { hash: string }) {
  useEffect(() => {
    const target = document.getElementById(hash.slice(1) || "regulament");
    target?.scrollIntoView?.({ block: "start" });
    target?.focus({ preventScroll: true });
  }, [hash]);

  return (
    <main id="regulament" className="main-container rules-page" tabIndex={-1} aria-labelledby="rules-title">
      <header className="rules-heading">
        <span className="rules-icon"><BookOpen size={26} aria-hidden="true" /></span>
        <p className="section-eyebrow">GHIDUL SUPER PONT</p>
        <h1 id="rules-title">Regulament</h1>
        <p>Tu alegi scorul. Fiecare gol contează.</p>
      </header>

      <section className="rules-section" aria-labelledby="participare-title">
        <h2 id="participare-title">Cum participi</h2>
        <p>Creează-ți un cont, confirmă adresa de email și autentifică-te. Alege competiția și etapa, completează ambele scoruri și salvează pronosticul.</p>
        <p>Introdu pentru fiecare echipă un număr întreg între <strong>0 și 99</strong>, inclusiv. Pronosticul contează doar după salvarea cu succes.</p>
      </section>

      <section className="rules-section" aria-labelledby="termen-title">
        <h2 id="termen-title">Până când poți modifica pronosticul</h2>
        <p>Poți salva, modifica sau șterge un pronostic până la ora de start afișată în aplicație. Din acel moment, pronosticul este blocat. Orele sunt afișate în fusul tău orar local.</p>
      </section>

      <section className="rules-section" aria-labelledby="vizibilitate-title">
        <h2 id="vizibilitate-title">Cine îți poate vedea pronosticurile</h2>
        <p>Pronosticurile tale sunt vizibile celorlalți utilizatori autentificați imediat după salvare.</p>
        <p>Dacă preferi să le păstrezi ascunse până la începerea meciurilor, activează <strong>„Ascunde pronosticurile mele până la start”</strong> din <strong>Setările contului</strong>. Fiecare pronostic devine vizibil la ora de start a meciului respectiv.</p>
        <p>Setarea se aplică și pronosticurilor deja salvate. Tu le poți vedea oricând.</p>
      </section>

      <section id="punctaj" className="rules-section rules-scoring" tabIndex={-1} aria-labelledby="punctaj-title">
        <h2 id="punctaj-title">Cum primești puncte</h2>
        <p>Pentru fiecare pronostic salvat poți primi între <strong>0 și 10 puncte</strong>, în două părți:</p>
        <ul>
          <li><strong>5 puncte pentru rezultat:</strong> ai anticipat corect victoria gazdelor, egalul sau victoria oaspeților; altfel, 0 puncte.</li>
          <li><strong>Până la 5 puncte bonus pentru precizia scorului</strong>, chiar dacă ai greșit rezultatul.</li>
        </ul>
        <p>Pornești de la 5 puncte bonus și pierzi câte un punct pentru fiecare gol cu care ai greșit, calculat separat pentru fiecare echipă. Bonusul scade până la minimum 0.</p>
        <p>Un <strong>scor exact aduce 10 puncte</strong>. Punctele se acordă doar pentru meciurile încheiate, cu scor final.</p>

        <h3 id="exemple-title">Exemple de calcul</h3>
        <p id="exemple-ordine" className="rules-table-note">Scorurile sunt în ordinea gazde–oaspeți</p>
        <div className="rules-table-wrap" role="region" aria-labelledby="exemple-title" tabIndex={0}>
          <table className="rules-table" aria-labelledby="exemple-title" aria-describedby="exemple-ordine">
            <thead>
              <tr>
                <th scope="col">Scor final</th>
                <th scope="col">Pronostic</th>
                <th scope="col">Rezultat</th>
                <th scope="col">Bonus</th>
                <th scope="col">Total</th>
              </tr>
            </thead>
            <tbody>
              {examples.map(([final, prediction, outcome, bonus, total], index) => (
                <tr key={index}>
                  <td>{final}</td><td>{prediction}</td><td>{outcome}</td><td>{bonus}</td><td><strong>{total}</strong></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="rules-table-note">„Rezultat” înseamnă punctele pentru victoria sau egalul anticipat, iar „Bonus” înseamnă punctele pentru precizia scorului.</p>
      </section>

      <section className="rules-section" aria-labelledby="clasament-title">
        <h2 id="clasament-title">Clasamentul</h2>
        <p>Clasamentul adună punctele din sezonul curent al competiției selectate. Apar toți utilizatorii, inclusiv cei fără pronosticuri sau cu <strong>0 puncte</strong>.</p>
        <p>Utilizatorii cu același număr de puncte ocupă același loc. De exemplu, dacă doi utilizatori împart locul 2, următorul ocupă locul 4, deoarece trei utilizatori sunt înaintea lui.</p>
      </section>

      <section className="rules-section" aria-labelledby="rezultate-title">
        <h2 id="rezultate-title">Rezultate și situații speciale</h2>
        <p>Dacă rezultatul final este corectat, punctele pronosticurilor și clasamentul se actualizează.</p>
        <p>Meciurile amânate sau anulate nu aduc puncte cât timp au acest statut. Pronosticurile salvate se păstrează.</p>
        <p>Termenul de modificare și vizibilitatea pronosticurilor urmează ora de start afișată, inclusiv pentru meciurile amânate.</p>
      </section>
    </main>
  );
}
