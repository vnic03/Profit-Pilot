import React from 'react';
import './styles.scss';

interface MarketMoversProps {
    movers: {
        winners: StockChange[],
        losers: StockChange[]
    },
    handleLoadMore: () => void;
}

export interface StockChange {
    name: string,
    percentChange?: number,
    date: string,
    industry?: string
}

export interface WinnersAndLosers {
    winners: StockChange[];
    losers: StockChange[];
}

const MarketMovers: React.FC<MarketMoversProps> = ({movers, handleLoadMore}) => {
    return (
        <div className="market-movers">
            <h2>Market Movers</h2>
            <div className="winners">
                <h3>Winners</h3>
                <ul>
                    {movers.winners.map((winner, index) => (
                        <li key={index}>
                            <span>{winner.name}</span> - <span
                            className="positive">{(winner.percentChange || 0).toFixed(2)}%</span>
                        </li>
                    ))}
                </ul>
            </div>
            <div className="losers">
                <h3>Losers</h3>
                <ul>
                    {movers.losers.map((loser, index) => (
                        <li key={index}>
                            <span>{loser.name}</span> - <span
                            className="negative">{(loser.percentChange || 0).toFixed(2)}%</span>
                        </li>
                    ))}
                </ul>
            </div>
            <button onClick={handleLoadMore}>Load More</button>
        </div>
    );
};

export default MarketMovers;
