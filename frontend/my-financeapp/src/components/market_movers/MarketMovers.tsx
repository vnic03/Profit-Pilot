import React, { useEffect, useState } from 'react';
import { getWinnersAndLosers } from "../../service/financeService";
import './styles.scss';

interface MarketMoversProps {
    symbol: string;
    amount: number;
}

interface StockChange {
    name: string,
    percentChange?: number,
    date: string,
    industry?: string
}

const MarketMovers: React.FC<MarketMoversProps> = ({symbol, amount}) => {
    const [movers, setMovers] = useState<{ winners: StockChange[], losers: StockChange[] }>({ winners: [], losers: [] });

    useEffect(() => {
        const fetchMovers = async () => {
            const data = await getWinnersAndLosers(symbol, amount);
            console.log(data)
            setMovers({
                winners: data.winners.map((winner: any) => ({
                    name: winner.name,
                    percentChange: winner.percentageRate,
                    date: winner.date,
                    industry: winner.industry
                })),
                losers: data.losers.map((loser: any) => ({
                    name: loser.name,
                    percentChange: loser.percentageRate,
                    date: loser.date,
                    industry: loser.industry
                }))
            });
        };

        fetchMovers();

    }, [symbol, amount]);

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
        </div>
    );
};

export default MarketMovers;
