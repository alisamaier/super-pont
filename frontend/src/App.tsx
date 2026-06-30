import "./App.css";

const apiUrl = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

function App() {
  return (
    <main className="app-shell">
      <section className="intro" aria-labelledby="page-title">
        <p className="eyebrow">Pronosticuri fotbal Romania</p>
        <h1 id="page-title">Super Pont</h1>
        <p className="lede">
          Monorepo initializat pentru viitoarea platforma de predictii fotbalistice.
        </p>
        <div className="status-row" aria-label="Service status">
          <span className="status-pill">Frontend ready</span>
          <span className="status-pill secondary">API: {apiUrl}</span>
        </div>
      </section>
    </main>
  );
}

export default App;

