# Primul mediu online gratuit — Super Pont

Verificat în documentația oficială la **25 noiembrie 2026**. Acest ghid descrie
acțiuni de făcut ulterior în conturile tale. Pregătirea codului nu creează resurse,
nu face push și nu publică aplicația.

## Alegerea serviciilor și limitele gratuite

| Serviciu | Limite relevante | Decizie |
| --- | --- | --- |
| Vercel Hobby | Doar uz personal, necomercial; 1 milion Edge Requests/lună; proiectele din repository-uri de organizație Git nu sunt suportate pe Hobby. | Frontend static și proxy `/api`. Pentru un proiect comercial sau repository de organizație, alternativa gratuită este Render Static Site, cu regulile de mai jos. |
| Render Free Web Service | 750 ore/workspace/lună; repaus după 15 minute fără trafic, trezire aproximativ un minut; disc efemer; fără disc persistent și shell; SMTP 25/465/587 blocat. | Backend Docker; email HTTPS; logo-uri în Neon. Importurile se execută direct la originea Render. |
| Neon Free | 100 CU-ore/proiect/lună, 1 GB PostgreSQL/proiect (20 GB total/cont), 5 GB transfer public/proiect/lună; suspendare după 5 minute de inactivitate. | Proiect nou, gol, separat de local; fără restaurarea unui dump local. |
| Brevo Free | 300 emailuri/zi, inclusiv mesaje tranzacționale; expeditor verificat și activarea accesului tranzacțional. | API HTTPS pe 443; confirmarea emailului rămâne obligatorie. |

Surse: [Vercel Hobby](https://vercel.com/docs/plans/hobby),
[Vercel limits](https://vercel.com/docs/limits),
[Render Free](https://render.com/docs/free),
[Render pricing](https://render.com/pricing),
[Neon plans](https://neon.com/docs/introduction/plans),
[Brevo Free](https://help.brevo.com/hc/en-us/articles/208580669-FAQs-What-are-the-limits-of-the-Free-plan).
Render listează 512 MB RAM pentru instanța Free; workspace Hobby include 5 GB
bandwidth și [500 minute pipeline/lună](https://render.com/docs/build-pipeline). Verifică în dashboard cotele înainte de
creare; ele se pot schimba. Fără metodă de plată, depășirea cotelor Render poate
suspenda serviciile sau opri buildurile. Nu activa upgrade automat sau servicii plătite.

SMTP-ul obișnuit nu funcționează pe Render Free. Alternativa implementată este
**Brevo prin HTTPS**, configurabilă prin mediu.

Nu este necesar să cumperi un domeniu pentru frontend și API. Pentru Brevo poți
verifica o adresă pe care o controlezi prin codul trimis de furnizor; un domeniu
propriu cu DKIM/DMARC este recomandat pentru livrare. Adresele Gmail/Yahoo nu pot
fi autentificate ca domenii proprii și pot avea probleme de livrabilitate.
[Instrucțiunile oficiale pentru expeditor](https://help.brevo.com/hc/en-us/articles/208836149-Create-a-new-sender-From-name-and-From-email).
Activarea contului și livrarea efectivă nu pot fi garantate înainte de testarea contului tău.

## Arhitectura autentificării

Browserul apelează `https://FRONTEND.vercel.app/api/...`; Vercel redirecționează
intern cererea spre `https://BACKEND.onrender.com/api/...`, păstrând calea `/api`.
Frontendul folosește aceeași origine pentru pagini și API. `VITE_API_URL` trebuie
să fie **originea frontendului** în acest mod, fără `/api`, cale sau secrete.
Local rămâne `http://localhost:8080`.

Cookie-urile `JSESSIONID` și `SP_REGISTRATION` sunt HttpOnly, Secure și SameSite=Lax,
fără atribut Domain. Browserul le primește de la domeniul frontendului prin proxy.
Clientul păstrează `credentials: include` și cere token CSRF din sesiune înaintea
scrierilor, inclusiv login/import. Nu se dezactivează CSRF și nu se folosește JWT
în localStorage. Răspunsurile autentificate au `Cache-Control: no-store`;
logo-urile publice își păstrează ETag și cache-ul de 24 ore.

Apelurile directe din browser între domeniile gratuite ar necesita SameSite=None
și acceptarea cookie-urilor third-party; ele pot eșua în browsere care le blochează.
Proxy-ul evită această dependență.
[Politica browserelor pentru cookie-uri cross-origin](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS#third-party_cookies). CORS rămâne limitat la un singur `FRONTEND_URL`
exact; preview-urile Vercel nu sunt autorizate automat. Folosește URL-ul stabil,
nu URL-uri de deploy temporare. [Proxy oficial Vercel](https://vercel.com/docs/routing/rewrites).

## 1. Neon: baza separată

1. Creează un proiect **Free**, de exemplu `super-pont-online-test`, într-o regiune
   apropiată de Render (de exemplu Frankfurt). Alege PostgreSQL **16**.
2. Creează baza `superpont_test` și rolul aplicației. Păstrează parola doar în
   managerul tău de parole și ulterior în Render. Nu reutiliza parola locală.
3. În **Connect**, folosește hostname-ul **direct**, fără `-pooler`, pentru această
   instanță unică și Flyway. Poolul Hikari este limitat la 5 conexiuni.
4. Construiește URL-ul JDBC fără username/parolă în el:

   ```text
   jdbc:postgresql://<hostname-neon>:5432/superpont_test?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&channelBinding=require
   ```

   Nu copia un URI PostgreSQL care include credențialele în `DATABASE_URL`. `verify-full`
   verifică certificatul și hostname-ul; fabrica Java folosește CA-urile din JRE.
   În JDBC parametrul este `channelBinding`, nu `channel_binding`.
   Nu înlocui cu `sslmode=require` sau un factory care ignoră certificatele.
   [Neon TLS](https://neon.com/docs/connect/connect-securely),
   [pgJDBC SSL](https://jdbc.postgresql.org/documentation/ssl/).
5. Nu importa dumpuri, conturi, pronosticuri sau volume locale. Flyway creează
   schema la prima pornire; profilul `prod` nu încarcă `db/devdata`.

## 2. Brevo: emailuri reale

1. Creează un cont Free și finalizează verificările/activarea pentru
   **Transactional Email** cerute în cont. Nu crea liste marketing pentru aplicație.
2. În **Settings → Senders, Domains, IPs → Senders**, adaugă expeditorul `Super Pont`
   cu o adresă pe care o controlezi. Confirm-o cu codul primit. Dacă ai deja un
   domeniu propriu, adaugă în DNS înregistrările furnizate de Brevo și verifică-l.
3. În **SMTP & API → API Keys**, creează o **cheie API** (nu cheie SMTP), păstrată
   doar în Render ca `BREVO_API_KEY`. Verifică eventualele restricții IP din cont;
   dacă le activezi, permite numai intervalele outbound ale serviciului Render.
4. Setează `MAIL_FROM=Super Pont <adresa-expeditorului-verificat>` în Render.
   Nu folosi `.local`, `.example`, `localhost` sau Mailpit în acest mediu.
5. Aplicația trimite text și HTML prin `POST https://api.brevo.com/v3/smtp/email`,
   cu timeout limitat, fără redirecturi sau retry automat. Un `201` indică acceptarea
   mesajului, nu confirmarea livrării. Verifică **Transactional → Logs**, spam și
   eventualele blocări de cont/cotă. Erorile nu confirmă automat contul.
   [Contractul API](https://developers.brevo.com/reference/send-transac-email).

Emailul de confirmare conține un cod și link spre `/confirma-email`; codul se
introduce în același browser în care s-a început înregistrarea. Resetarea parolei
folosește `/reseteaza-parola#token=...`, la originea frontendului configurat.
Nu publica tokenuri sau coduri în loguri, capturi ori probleme Git.

## 3. Stabilește URL-urile frontendului și backendului

În contul Vercel alege un nume de proiect personal și domeniul stabil dorit.
În Render alege numele Web Service. Notează URL-urile efectiv atribuite; dacă un
nume nu este disponibil, folosește numele atribuit și actualizează toate setările.
Poți crea întâi proiectul Vercel cu un deploy inițial, apoi actualiza API-ul și
redeploya după ce Render este disponibil. Nu invita testeri înainte de verificări.

Pentru configurarea locală a proxy-ului, rulează din rădăcina repository-ului,
**după ce cunoști originea Render**:

```powershell
.\backend\scripts\Configure-Vercel.ps1 -BackendUrl 'https://<serviciul-tau>.onrender.com'
git diff -- frontend/vercel.json
```

Scriptul modifică doar `frontend/vercel.json`. Înlocuiește destinația placeholder
`configure-render-backend.invalid`, apoi include schimbarea în commitul
pe care alegi ulterior să îl publici. Nu include secrete. Ruta API este înaintea
fallbackului SPA; `/regulament`, `/clasament`, `/confirma-email` și resetarea parolei
ajung la `index.html` la reîncărcare. Fișierele statice existente sunt servite normal.

## 4. Render: backend Docker

În **New → Web Service** conectează repository-ul și configurează:

| Setare | Valoare |
| --- | --- |
| Runtime | Docker |
| Root Directory | `backend` |
| Dockerfile Path | `./Dockerfile` (relativ la root) |
| Docker Build Context | `.` (relativ la root) |
| Region | Aproape de Neon, de exemplu Frankfurt |
| Instance Type | Free |
| Health Check Path | `/actuator/health` |
| Docker Command / Start Command | Necompletat: se folosește ENTRYPOINT |
| Auto Deploy | Off / manual pentru primul mediu de test |
| Persistent Disk | Niciunul |

Dockerfile-ul construiește cu wrapper Gradle 9.1 și JDK 25, rulează testele și
pornește JRE 25 cu utilizator fără root. Serverul ascultă pe `0.0.0.0:$PORT`.
Nu seta comenzi `bootRun` și nu adăuga profilul `dev`.

### Variabile Render obligatorii

| Nume | Valoare / locul de unde o iei |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | URL-ul JDBC Neon cu TLS de la pasul 1 |
| `DATABASE_USERNAME` | Rolul Neon |
| `DATABASE_PASSWORD` | Parola rolului, secret doar în Render |
| `FRONTEND_URL` | Originea stabilă HTTPS a frontendului, fără slash final |
| `MAIL_PROVIDER` | `brevo` |
| `BREVO_API_KEY` | Cheia API secretă din Brevo |
| `MAIL_FROM` | Expeditorul real verificat în Brevo |
| `SPORTSTATS_SYNC_ENABLED` | `false` |
| `TEAM_LOGO_AUTOMATIC_ENABLED` | `false` |

Ultimele două rămân false și în profilul `prod`, indiferent de variabilele cu
aceste nume. Profilul refuză configurări care reactivează programarea prin alte
override-uri. Nu combina `prod` cu `dev`. Nu seta `LEADERBOARD_DEMO`.

### Variabile opționale și valori implicite

| Nume | Utilizare |
| --- | --- |
| `SPORTSTATS_API_KEY` | Secret obligatoriu înaintea importurilor reale, inutil pentru pornirea aplicației |
| `PASSWORD_RESET_FRONTEND_URL` | Implicit `FRONTEND_URL`; păstrează aceeași origine HTTPS |
| `PORT` | Furnizat de Render; fallback local 8080 |
| `JAVA_TOOL_OPTIONS` | Implicit `-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError`; monitorizează memoria în Free |
| `SPORTSTATS_CONNECT_TIMEOUT` / `SPORTSTATS_REQUEST_TIMEOUT` | `PT5S` / `PT10S` |
| `SPORTSTATS_DAYS_BACK` / `SPORTSTATS_DAYS_AHEAD` | 7 / 14; relevante pentru scheduler, oprit aici |
| `SPORTSTATS_PHASE_DIVISIONS` | Implicit `dx2=REGULAR`; schimbă doar cu coduri verificate ale furnizorului |
| `SPORTSTATS_SEASON_START_MONTH` / `SPORTSTATS_SEASON_START_DAY` | 7 / 1, conform sezonului importat |

Nu configura `TEAM_LOGO_CACHE_DIR` pentru Render: `prod` folosește stocare în
baza de date și nu permite `filesystem`. Cookie-urile sunt Secure și SameSite=Lax
în acest profil. Nu seta Domain, SameSite=None sau `SESSION_COOKIE_SECURE=false`.
Nu sunt necesare variabilele SMTP în Render. Transportul `smtp` rămâne disponibil
pentru local sau alt host, prin `SMTP_*` documentate în README; pe Render Free
alege `brevo`. Nu activa debug HTTP/mail și nu publica variabile în loguri.

`AUTH_TRUSTED_PROXY_CIDRS` rămâne implicit neconfigurat: forwarding headers nu
sunt de încredere. Dacă îl setezi, folosește numai proxy-uri verificate ale
platformei, fără `0.0.0.0/0`. Limitele IP pot fi partajate de utilizatorii aflați
în spatele proxy-ului până verifici lanțul Render/Vercel. Rate limitingul per
adresă email rămâne activ. Nu lărgi încrederea doar pentru a evita un `429`.

Pornește deployul manual când alegi tu. Verifică Events/Logs și
`https://<backend>.onrender.com/actuator/health`: trebuie să răspundă 200, doar
`{"status":"UP"}`. `/actuator/env` și `/actuator/configprops` nu sunt expuse.

## 5. Vercel: frontend

| Setare | Valoare |
| --- | --- |
| Plan | Hobby, proiect personal necomercial |
| Root Directory | `frontend` |
| Framework | Vite |
| Node.js | 22.x |
| Install Command | `npm ci` |
| Build Command | `npm run build` |
| Output Directory | `dist` |
| Production environment: `VITE_API_URL` | `https://<frontend-stabil>.vercel.app` — aceeași origine cu paginile |

Asigură-te că `vercel.json` are originea Render reală. Nu există substituție de
variabile de mediu în acest JSON; folosește scriptul de la pasul 3. Redeployează
după orice schimbare de URL. Nu pune chei API, parole sau tokenuri în `VITE_*`.
Nu autoriza automat preview-urile și verifică setările Deployment Protection dacă
mediul trebuie să poată fi accesat de testeri fără un cont Vercel.

Alternativă gratuită pentru restricțiile Hobby: **Render Static Site**, root
`frontend`, build `npm ci && npm run build`, publish `dist`, `VITE_API_URL` egal cu
originea Static Site. În **Redirects/Rewrites**, adaugă întâi `/api/*` →
`https://<backend>.onrender.com/api/*` cu acțiunea Rewrite, apoi `/*` → `/index.html`
tot Rewrite. Actualizează `FRONTEND_URL` în backend la noua origine stabilă.
[Render rewrites](https://render.com/docs/redirects-rewrites).

## 6. Primul administrator, fără cont sau parolă implicită

1. Înregistrează-te în frontend cu propria parolă unică. Primește emailul real și
   confirmă codul. Verifică faptul că loginul funcționează ca utilizator normal.
2. În **SQL Editor** al proiectului Neon corect, verifică adresa, fără să extragi
   hashuri de parole:

   ```sql
   SELECT id, email, email_verified, role FROM users
   WHERE email = lower('INLOCUIESTE_CU_ADRESA_TA_CONFIRMATA');
   ```

3. Înlocuiește aceeași adresă mai jos și rulează blocul atomic o singură dată:

   ```sql
   DO $bootstrap$
   DECLARE promoted integer;
   BEGIN
     IF EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN') THEN
       RAISE EXCEPTION 'Exista deja un administrator; verifica manual conturile.';
     END IF;
     UPDATE users SET role = 'ADMIN', password_version = password_version + 1
     WHERE email = lower('INLOCUIESTE_CU_ADRESA_TA_CONFIRMATA')
       AND email_verified = true AND role = 'USER';
     GET DIAGNOSTICS promoted = ROW_COUNT;
     IF promoted <> 1 THEN
       RAISE EXCEPTION 'Este necesar exact un cont confirmat; nicio promovare efectuata.';
     END IF;
   END;
   $bootstrap$;
   ```

4. Fă logout/login pentru o sesiune nouă. Verifică `GET /api/users/me` → `role: ADMIN`.
   Nu adăuga endpoint public de promovare, bootstrap password sau admin în seed.
   Nu seta manual `email_verified=true`; parcurge confirmarea reală.

## 7. Primul import și actualizările manuale

Setează cheia SportStats în Render și aplică mediul prin redeploy. API-ul
furnizorului trebuie să permită competițiile cerute de importator; un `402` sau
`403` nu este rezolvat prin schimbarea hostingului. Scripturile nu inițiază plăți.
Importurile nu copiază conturi sau pronosticuri locale.

Din PowerShell, la rădăcina proiectului:

```powershell
$env:SUPERPONT_API_URL = 'https://<backend>.onrender.com'
.\backend\scripts\Sync-SportStats.ps1 -Target Hosted -DateFrom '2026-11-15' -DateTo '2026-12-06' -Email 'adresa-ta-confirmata'
.\backend\scripts\Sync-TeamLogos.ps1 -Target Hosted -Email 'adresa-ta-confirmata'
```

Alege datele reale dorite; exemplul nu se execută automat. Poți transmite direct
`-ApiUrl 'https://<backend>.onrender.com'`, inclusiv fără `-Target Hosted`.
Implicit, fără opțiuni de țintă, rămâne backendul local. Parola este cerută prin
`Read-Host -AsSecureString`, nu prin argument sau variabilă salvată în Git.
Scripturile așteaptă health până la 240 secunde (configurabil prin
`-HealthTimeoutSeconds`), folosesc sesiune ADMIN și token CSRF nou după login și
încearcă logout în `finally`. Nu rula importuri prin proxy-ul frontendului:
Vercel limitează cererile proxiate la 120 secunde.

Pentru actualizări, repetă importul cu intervalul de date dorit. Identitățile și
UUID-urile sunt păstrate, iar rezultatele actualizate recalculează punctele prin
mecanismul existent. Pentru logo-uri lipsă repetă comanda fără `-Force`; folosește
`-Force` doar când dorești refresh explicit. Respectă `Retry-After`; un timeout
poate apărea după ce serverul a importat deja, deci verifică rezultatul înainte
de repetare. [Timeout proxy Vercel](https://vercel.com/docs/limits).

În `prod`, migrarea **V14** stochează PNG-urile validate, adresate după SHA-256,
în `team_logo_images` din Neon. Nu depind de filesystem, restart sau redeploy și
nu necesită serviciu de object storage plătit. Metadatele și creditele rămân în
`teams`. Cache-ul HTTP, limitele de imagine și fallbackul badge sunt păstrate;
afișarea nu descarcă imagini de la furnizor. Local rămâne cache-ul pe volumul
Podman. Nu se migrează automat fișiere locale în Neon: rulează importul de logo-uri.
PNG-urile și versiunile vechi ocupă spațiu în cota PostgreSQL; urmărește Storage
și fă backup cu tot cu tabelul de imagini. Nu există curățare automată a versiunilor.

## 8. Verificări după deploy

- Health direct Render: 200, doar status; la erori nu apar stack trace, SQL,
  configurații sau valori ale variabilelor. Verifică CORS: originea configurată
  este acceptată, o origine străină nu este acceptată.
- Deschide `/regulament` și `/clasament` direct și cu refresh; verifică și un
  link de resetare primit prin email. Cererile API nu trebuie să primească HTML-ul SPA.
- Într-un browser cu third-party cookies blocate: înregistrare, cod primit real,
  confirmare, login, refresh și logout. Un cont neconfirmat trebuie să rămână
  blocat pentru autentificarea normală. Verifică cookie-urile pe originea frontendului.
- Resetare parolă: mesaj primit real, link către frontendul stabil, token utilizabil
  o singură dată; parola nouă funcționează și sesiunile vechi sunt invalidate.
- După import: program real, selecții competiție/sezon/fază/etapă, logo-uri și
  credite; pronostic 0–99 înainte de kickoff salvat și regăsit după refresh/login.
  Inputul invalid și scrierile fără CSRF sunt respinse; contul normal nu poate importa.
- Restart/redeploy backend: conturile, pronosticurile și PNG-urile rămân în Neon.
  Sesiunile HTTP sunt în memorie și se pierd: este necesar login și un token CSRF nou.
  Confirmarea începută poate fi reluată prin login dacă sesiunea browserului s-a pierdut.
- Lasă backendul în repaus peste 15 minute. Pagina statică se încarcă, însă API-ul
  poate întârzia sau returna temporar eroare/pagină Render la trezire. Folosește
  reîncercarea existentă după aproximativ un minut; verifică și trezirea Neon.
  Nu trimite pinguri pentru a ocoli repausul. Nu există actualizări programate.
- Controlează cotele Neon/Render/Vercel/Brevo și consumul memoriei reale. Un test
  local nu garantează pornirea în cei 512 MB sau livrarea în inboxul fiecărui destinatar.

## Ce se verifică local și ce rămâne extern

Local: build/test/lint, migrații pe baze de test izolate, lipsa seedului în configurația
fără `dev`, cache PNG în DB, securitatea endpointurilor, payloadul HTTPS email cu
server simulat, configurarea proxy/SPA și verificarea fișierelor pentru secrete.
Buildul Docker poate fi verificat cu `podman build -t super-pont-deploy-check ./backend`.

Doar după configurarea conturilor și deploy: conexiunea TLS efectivă la Neon,
resursele disponibile în Render Free, proxy-ul/cookie-urile în browsere reale,
livrarea și aprobarea Brevo, importul SportStats cu cheia ta, repausul platformelor
și persistența după un redeploy găzduit. Nu sunt necesare credențiale în repository.

## Rezultatele verificării locale — 25 noiembrie 2026

- Backend: `gradlew build` cu JDK 25 și Gradle 9.1 — 137 teste trecute,
  1 test PostgreSQL opțional omis (necesită baza sa dedicată); zero eșecuri.
- Frontend: Node 22, `npm ci`, 161 teste trecute, lint și build de producție reușite.
- Imaginea Docker completă s-a construit prin Podman, inclusiv testele din Dockerfile.
  Pornirea pe portul 10000, fără root, a funcționat cu limită de 512 MiB.
- PostgreSQL 16 temporar, separat de datele locale: migrații până la V14, zero
  conturi și meciuri inițiale; PNG servit din bytea, păstrat după restart și
  înlocuirea imaginii cu versiunea finală. Containerele și baza temporară au fost eliminate.
- Verificări HTTP: CORS exact și refuz pentru origine străină, health doar cu
  status, eroare 400 sanitizată, configurație Actuator inaccesibilă, ETag/304 și
  cache public pentru PNG. Fallbackul vizual este acoperit de testele frontendului.
- JAR-ul și imaginea finală resping `prod,dev` înainte de datasource/Flyway.
  Transportul Brevo este verificat cu server HTTP local, inclusiv eșec și
  suprimarea conținutului sensibil; niciun email real nu a fost trimis.
- Scripturi PowerShell: parsare, refuzul originii invalide și generarea proxy-ului
  într-o copie temporară; fișierele reale de deploy nu au fost configurate cu URL-uri externe.
- Scanarea fișierelor Git și a celor noi nu a detectat chei/parole locale sau
  chei private active. Fișierele `.env` și `.tools` sunt ignorate; credențialele
  demo/test documentate rămân exclusiv pentru dezvoltare.
- `npm audit --omit=dev`: zero vulnerabilități. Auditul complet raportează
  9 constatări moderate în instrumentele Vitest/TypeScript ESLint și dependențele
  lor; acestea nu sunt livrate ca servicii în frontendul static. Nu s-au actualizat
  automat dependențele în afara acestei pregătiri.

Testul Docker folosește conexiune PostgreSQL locală izolată, fără profilul `dev`;
TLS Neon și profilul complet cu servicii reale trebuie verificate după deploy.
Testul de memorie validează pornirea, fără a garanta consumul sub încărcare.
