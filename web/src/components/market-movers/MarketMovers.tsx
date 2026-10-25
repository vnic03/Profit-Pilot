import React from "react";
import "./styles.scss";

export interface StockChange {
  symbol: string;
  name: string;
  percentageRate: number;
  date: string;
  industry?: string;
}

export interface WinnersAndLosers {
  winners: StockChange[];
  losers: StockChange[];
}

interface MarketMoversProps {
  movers: WinnersAndLosers;
  hasMore: boolean;
  handleLoadMore: () => void;
}

const MarketMovers: React.FC<MarketMoversProps> = ({ movers, hasMore, handleLoadMore }) => {
  const dates = Array.from(
    new Set([...movers.winners, ...movers.losers].map((change) => change.date)),
  ).sort();

  const renderChange = (change: StockChange) => (
    <li key={change.symbol} className="mover-row">
      <div className="mover-company">
        <strong>{change.symbol}</strong>

        {change.name && change.name !== change.symbol && <span>{change.name}</span>}

        <time dateTime={change.date}>{change.date}</time>
      </div>

      <span
        className={change.percentageRate > 0 ? "mover-change positive" : "mover-change negative"}
      >
        {change.percentageRate > 0 ? "+" : ""}
        {change.percentageRate.toFixed(2)}%
      </span>
    </li>
  );

  return (
    <div className="market-movers">
      <p className="movers-description">
        Changes among stored stocks, compared with each stock's previous stored close. Not a
        market-wide ranking.
      </p>

      {dates.length > 1 && (
        <p className="movers-notice" role="status">
          Latest price dates differ: {dates[0]} to {dates[dates.length - 1]}. These changes are not
          from the same trading day.
        </p>
      )}

      {dates.length === 1 && (
        <p className="movers-date">
          Price date: <time dateTime={dates[0]}>{dates[0]}</time>
        </p>
      )}

      <div className="movers-grid">
        <section className="movers-group" aria-label="Rising stocks">
          <h3>Rising</h3>

          {movers.winners.length > 0 ? (
            <ul>{movers.winners.map(renderChange)}</ul>
          ) : (
            <p>No positive changes in the available data.</p>
          )}
        </section>

        <section className="movers-group" aria-label="Falling stocks">
          <h3>Falling</h3>

          {movers.losers.length > 0 ? (
            <ul>{movers.losers.map(renderChange)}</ul>
          ) : (
            <p>No negative changes in the available data.</p>
          )}
        </section>
      </div>

      <div className="movers-footer">
        {hasMore ? (
          <button type="button" onClick={handleLoadMore}>
            Show more stocks
          </button>
        ) : (
          <p>All available rising and falling stocks are shown.</p>
        )}
      </div>
    </div>
  );
};

export default MarketMovers;
