import { useEffect, useState } from "react";
import "./App.css";
import Navbar, { type View } from "./components/navbar/Navbar";
import StockAnalysis from "./components/stock-analysis/StockAnalysis";
import MarketMovers from "./components/market-movers/MarketMovers";
import DataStatus from "./components/DataStatus";
import News from "./components/news/News";
import { getWinnersAndLosers } from "./service/financeService";
import type { Stock, WinnersAndLosers } from "./service/apiTypes";

function Movers({ refreshVersion }: { refreshVersion: number }) {
  const [amount, setAmount] = useState(5);
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState<{
    key: string;
    data?: WinnersAndLosers;
    error?: string;
  } | null>(null);

  const key = `${amount}:${refreshVersion}:${retry}`;

  useEffect(() => {
    let cancelled = false;

    getWinnersAndLosers(amount + 1).then(
      (data: WinnersAndLosers) => {
        if (!cancelled) setResult({ key, data });
      },
      () => {
        if (!cancelled) {
          setResult({
            key,
            error: "Could not load market movers.",
          });
        }
      },
    );

    return () => {
      cancelled = true;
    };
  }, [amount, key]);

  const current = result?.key === key ? result : null;

  return (
    <section aria-labelledby="movers-title">
      <h1 id="movers-title">Market movements</h1>

      {!current && <p role="status">Loading market movers...</p>}

      {current?.error && (
        <div role="alert">
          <p>{current.error}</p>
          <button onClick={() => setRetry((value) => value + 1)}>Retry market movers</button>
        </div>
      )}

      {current?.data &&
        (current.data.winners.length + current.data.losers.length === 0 ? (
          <p>No market movers available.</p>
        ) : (
          <MarketMovers
            movers={{
              winners: current.data.winners.slice(0, amount),
              losers: current.data.losers.slice(0, amount),
            }}
            hasMore={current.data.winners.length > amount || current.data.losers.length > amount}
            handleLoadMore={() => setAmount((value) => value + 5)}
          />
        ))}
    </section>
  );
}

export default function App() {
  const [view, setView] = useState<View>("analysis");
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [stock, setStock] = useState<Stock | null>(null);

  return (
    <div className="instrument">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>

      <Navbar view={view} onNavigate={setView} />

      <main id="main-content" tabIndex={-1}>
        {/* Preserve chart settings when switching views. */}
        <div hidden={view !== "analysis"}>
          <StockAnalysis refreshVersion={refreshVersion} onStockChange={setStock} />
        </div>

        {view === "movers" && (
          <div className="utility-view">
            <Movers refreshVersion={refreshVersion} />
          </div>
        )}

        {view === "data" && (
          <div className="utility-view">
            <DataStatus refreshVersion={refreshVersion} />
          </div>
        )}

        {view === "news" && (
          <div className="utility-view">
            <News key={stock?.symbol ?? "none"} stock={stock} />
          </div>
        )}
      </main>

      <footer className="instrument-footer">
        <span>STORED DAILY DATA</span>

        <button
          aria-label="Reload stored data"
          title="Reload saved data; does not import new prices"
          onClick={() => setRefreshVersion((value) => value + 1)}
        >
          RELOAD
        </button>

        <a href="https://github.com/vnic03" target="_blank" rel="noopener noreferrer">
          BUILT BY NICO
        </a>
      </footer>
    </div>
  );
}
