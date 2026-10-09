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
    handleLoadMore: () => void;
}

const MarketMovers: React.FC<MarketMoversProps> = ({
    movers,
    handleLoadMore
}) => {
    const dates = Array.from(
        new Set(
            [...movers.winners, ...movers.losers]
                .map(change => change.date)
        )
    ).sort();

    const renderChange = (change: StockChange) => (
        <li key={change.symbol}>
            <span>{change.name || change.symbol}</span>
            {" — "}
            <span
                className={
                    change.percentageRate > 0 ? "positive" : "negative"
                }
            >
                {change.percentageRate > 0 ? "+" : ""}
                {change.percentageRate.toFixed(2)}%
            </span>
            {" · "}
            <time dateTime={change.date}>{change.date}</time>
        </li>
    );

    return (
        <div className="market-movers">
            <h2>Market Movers</h2>

            <p>
                Change from each stock's previous stored close.
                Not live quotes.
            </p>

            {dates.length > 1 && (
                <p role="status">
                    These stocks have different latest price dates
                    ({dates[0]} to {dates[dates.length - 1]}).
                    This is not a same-day market ranking.
                </p>
            )}

            {dates.length === 1 && (
                <p>
                    Price date:{" "}
                    <time dateTime={dates[0]}>{dates[0]}</time>
                </p>
            )}

            <div className="winners">
                <h3>Winners</h3>
                <ul>{movers.winners.map(renderChange)}</ul>
            </div>

            <div className="losers">
                <h3>Losers</h3>
                <ul>{movers.losers.map(renderChange)}</ul>
            </div>

            <button onClick={handleLoadMore}>Load More</button>
        </div>
    );
};

export default MarketMovers;
