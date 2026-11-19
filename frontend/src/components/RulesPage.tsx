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
        <p>Creează-ți un cont, intră în cont și alege o competiție. Selectează etapa, completează scorurile pe care le anticipezi și salvează pronosticurile.</p>
        <p>Pentru fiecare echipă poți introduce un număr întreg între <strong>0 și 99</strong>, inclusiv. Completează ambele scoruri. Pronosticul contează doar după ce a fost salvat cu succes; simpla completare a câmpurilor nu îl salvează.</p>
      </section>

      <section className="rules-section" aria-labelledby="termen-title">
        <h2 id="termen-title">Până când poți modifica pronosticul</h2>
        <p>Poți salva, modifica sau șterge un pronostic până la ora de începere programată pentru meci în aplicație. Din acel moment, pronosticul este blocat.</p>
        <p>Termenul este verificat de server la fiecare salvare, modificare sau ștergere, după ceasul serverului. Orele meciurilor sunt afișate în fusul tău orar local.</p>
      </section>

      <section className="rules-section" aria-labelledby="vizibilitate-title">
        <h2 id="vizibilitate-title">Când vezi pronosticurile celorlalți</h2>
        <p>Pentru a consulta clasamentul și pronosticurile altor utilizatori, trebuie să fii conectat la cont.</p>
        <p>Fiecare utilizator alege dacă își ascunde pronosticurile înainte de start. Opțiunea „Ascunde pronosticurile mele până la start” se găsește în „Setările contului”, din meniul contului, și este <strong>dezactivată implicit pentru conturile noi</strong>. Conturile existente își păstrează opțiunea salvată.</p>
        <p>Când opțiunea este activată, ceilalți văd scorul pronosticat din momentul în care se atinge ora de începere programată a fiecărui meci. Când este dezactivată, scorul devine vizibil imediat după salvare. Schimbarea opțiunii se aplică atât pronosticurilor deja salvate, cât și celor viitoare.</p>
        <p>După termenul-limită al meciului, pronosticurile sunt vizibile indiferent de opțiunea aleasă. Preferința de vizibilitate nu schimbă termenul până la care poți modifica un pronostic.</p>
        <p>Vizibilitatea se stabilește separat pentru fiecare meci. Îți poți vedea propriile pronosticuri salvate oricând ești conectat.</p>
      </section>

      <section id="punctaj" className="rules-section rules-scoring" tabIndex={-1} aria-labelledby="punctaj-title">
        <h2 id="punctaj-title">Cum primești puncte</h2>
        <p>Pentru fiecare pronostic salvat poți primi între <strong>0 și 10 puncte</strong>, în două părți:</p>
        <ul>
          <li><strong>5 puncte pentru rezultat:</strong> ai anticipat corect victoria gazdelor, egalul sau victoria oaspeților. Dacă ai greșit rezultatul, primești 0 puncte pentru această parte.</li>
          <li><strong>Până la 5 puncte bonus pentru precizia scorului</strong>, chiar dacă ai greșit rezultatul.</li>
        </ul>
        <p>Pornești de la 5 puncte bonus și pierzi câte un punct pentru fiecare gol cu care ai greșit, calculat separat pentru fiecare echipă. Bonusul scade până la minimum 0.</p>
        <div className="rules-formula">
          <p><strong>Eroare totală</strong> = |goluri pronosticate gazde − goluri finale gazde| + |goluri pronosticate oaspeți − goluri finale oaspeți|</p>
          <p><strong>Bonus de precizie</strong> = max(0, 5 − eroarea totală)</p>
          <p><strong>Total puncte</strong> = puncte pentru rezultat + bonus de precizie</p>
        </div>
        <p>Un <strong>scor exact aduce 10 puncte</strong>: 5 pentru rezultat și 5 bonus. Punctele se acordă doar când meciul este marcat ca încheiat și are scor final. Scorul unui meci în desfășurare nu aduce încă puncte.</p>

        <div className="rules-table-wrap" role="region" aria-label="Exemple de punctaj" tabIndex={0}>
          <table className="rules-table">
            <caption>Exemple de calcul · scorurile sunt în ordinea gazde–oaspeți</caption>
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
        <p>Punctele se adună pentru sezonul curent al competiției, separat de alte sezoane sau competiții. În prezent, pagina „Clasament” afișează doar <strong>SuperLiga, sezonul curent</strong>.</p>
        <p>În clasamentul sezonului curent apar toți utilizatorii, începând cu <strong>0 puncte</strong>, chiar dacă nu au salvat încă pronosticuri sau meciurile lor nu s-au încheiat. La egalitate de puncte, utilizatorii împart același loc, iar următoarea poziție ține cont de numărul lor: de exemplu, <strong>1, 2, 2, 4</strong>.</p>
      </section>

      <section className="rules-section" aria-labelledby="rezultate-title">
        <h2 id="rezultate-title">Rezultate și situații speciale</h2>
        <p>Dacă un scor final este corectat în aplicație, punctele pronosticurilor și clasamentul sunt recalculate pe baza noului scor. Vezi valorile actualizate la reîncărcarea datelor.</p>
        <p>Meciurile marcate ca amânate sau anulate nu aduc puncte cât timp au acest statut. Pronosticurile salvate se păstrează. Dacă meciul este ulterior marcat ca încheiat și primește un scor final, punctajul se calculează după formula de mai sus.</p>
        <p>Schimbarea statutului unui meci nu modifică automat termenul de salvare sau vizibilitatea pronosticurilor: acestea depind în continuare de ora de începere programată în aplicație.</p>
      </section>
    </main>
  );
}
