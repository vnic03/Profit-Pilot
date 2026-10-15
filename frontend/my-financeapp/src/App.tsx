import React, {useEffect, useState} from "react";
import "./App.css";
import Navbar from "./components/navbar/Navbar";
import MarketMovers, {
    WinnersAndLosers
} from "./components/market_movers/MarketMovers";
import FinanceData from "./service/FinanceData";
import {getWinnersAndLosers} from "./service/financeService";
import News from "./components/news/News";
import DataStatus from "./components/DataStatus";
import type {Stock} from "./service/useStockCatalog";


interface MoversResult {
    key: string;
    data: WinnersAndLosers | null;
    error: string | null;
}

function App() {
    const [refreshVersion, setRefreshVersion] = useState(0);
    const [retryVersion, setRetryVersion] = useState(0);
    const [amount, setAmount] = useState(5);
    const [result, setResult] = useState<MoversResult | null>(null);
    const [newsStock, setNewsStock] = useState<Stock | null>(null);

    const requestKey = `${amount}:${refreshVersion}:${retryVersion}`;

    useEffect(() => {
        let cancelled = false;

        const loadMovers = async () => {
            try {
                const data: WinnersAndLosers =
                    await getWinnersAndLosers(amount + 1);

                if (!cancelled) {
                    setResult({key: requestKey, data, error: null});
                }
            } catch {
                if (!cancelled) {
                    setResult({
                        key: requestKey,
                        data: null,
                        error: "Could not load market movers. Please try again."
                    });
                }
            }
        };

        void loadMovers();

        return () => {
            cancelled = true;
        };
    }, [amount, requestKey]);

    const current = result?.key === requestKey ? result : null;

    return (
        <div className="app-shell">
            <a className="skip-link" href="#main-content">
                Skip to content
            </a>

            <Navbar/>

            <main id="main-content" className="app-main" tabIndex={-1}>
                <header id="overview" className="page-header">
                    <div>
                        <p className="eyebrow">Research workspace</p>
                        <h1>Your market overview</h1>
                        <p className="page-intro">
                            Explore price history, compare indicators
                            and export your research.
                        </p>
                    </div>

                    <div className="refresh-controls">
                        <button
                            type="button"
                            className="primary-button"
                            onClick={() => setRefreshVersion(value => value + 1)}
                            aria-describedby="reload-description"
                        >
                            Reload stored data
                        </button>
                        <p id="reload-description">
                            Reloads saved data. Does not import new prices.
                        </p>
                    </div>
                </header>

                <section
                    id="analysis"
                    className="dashboard-panel"
                    aria-labelledby="analysis-title"
                >
                    <div className="panel-heading">
                        <div>
                            <p className="eyebrow">01 / Explore</p>
                            <h2 id="analysis-title">Stock analysis</h2>
                        </div>
                        <span className="subtle-badge">Daily prices</span>
                    </div>

                    <FinanceData
                        refreshVersion={refreshVersion}
                        onStockChange={setNewsStock}
                    />
                </section>

                <section
                    id="movers"
                    className="dashboard-panel"
                    aria-labelledby="movers-title"
                >
                    <div className="panel-heading">
                        <div>
                            <p className="eyebrow">02 / Compare</p>
                            <h2 id="movers-title">Price movements</h2>
                        </div>
                    </div>

                    {!current && (
                        <p role="status">Loading market movers...</p>
                    )}

                    {current?.error && (
                        <div role="alert">
                            <p>{current.error}</p>
                            <button
                                type="button"
                                onClick={() => setRetryVersion(value => value + 1)}
                            >
                                Retry market movers
                            </button>
                        </div>
                    )}

                    {current?.data && (
                        current.data.winners.length === 0
                        && current.data.losers.length === 0 ? (
                            <p>
                                No market movers available. Stocks may have
                                unchanged prices or insufficient stored history.
                            </p>
                        ) : (
                            <MarketMovers
                                movers={{
                                    winners: current.data.winners.slice(0, amount),
                                    losers: current.data.losers.slice(0, amount)
                                }}
                                hasMore={
                                    current.data.winners.length > amount
                                    || current.data.losers.length > amount
                                }
                                handleLoadMore={() => setAmount(value => value + 5)}
                            />
                        )
                    )}
                </section>

                <section
                    id="data-status"
                    className="dashboard-panel"
                    aria-label="Stored data coverage"
                >
                    <p className="eyebrow">03 / Verify</p>
                    <DataStatus refreshVersion={refreshVersion}/>
                </section>

                <section
                    id="news"
                    className="dashboard-panel"
                    aria-label="Market news"
                >
                    <p className="eyebrow">04 / Read</p>
                    <News
                         key={newsStock?.symbol ?? "no-stock"}
                         stock={newsStock}
                    />
                </section>

                <footer className="page-footer">
                    Profit Pilot · Historical data for market research · Running on Raspberry Pi 5.
                </footer>
            </main>
        </div>
    );
}

export default App;
