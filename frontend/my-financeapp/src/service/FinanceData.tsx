import React, {useEffect, useState} from "react";
import { getClosePrices, getMacdSeries, getHigh, getOpen,
    getLow, getVolume } from "./financeService";
import StockChart from "../components/charts/prototype/prototype";
import ExportButton from "../components/ExportButton";


export interface FinancialSymbolData {
    closePrices: Record<string, number>;
    high: Record<string, number>;
    low: Record<string, number>;
    open: Record<string, number>;
    volume: Record<string, number>;
    macd: Record<string, number>;
    macdSignal: Record<string, number>;
    macdHistogram: Record<string, number>;
    sma: Record<string, number>;
    ema: Record<string, number>;
    rsi: Record<string, number>;
}

export interface FinanceDataStructure {
    [key: string]: FinancialSymbolData;
}

interface FinanceDataProps {
    refreshVersion: number;
}

interface LoadResult {
    key: string;
    data: FinancialSymbolData | null;
    error: string | null;
}

export const symbols = ["IBM", "AAPL", "GOOGL", "MSFT", "AMZN"];

const FinanceData: React.FC<FinanceDataProps> = ({refreshVersion}) => {
    const [currentSymbol, setCurrentSymbol] = useState(symbols[0]);
    const [retryVersion, setRetryVersion] = useState(0);
    const [result, setResult] = useState<LoadResult | null>(null);

    const requestKey =
        `${currentSymbol}:${refreshVersion}:${retryVersion}`;

    useEffect(() => {
        let cancelled = false;

        const loadData = async () => {
            try {
                const [closePrices, high, low, open, volume, macdSeries] =
                    await Promise.all([
                        getClosePrices(currentSymbol),
                        getHigh(currentSymbol),
                        getLow(currentSymbol),
                        getOpen(currentSymbol),
                        getVolume(currentSymbol),
                        getMacdSeries(currentSymbol)
                    ]);

                if (cancelled) {
                    return;
                }

                setResult({
                    key: requestKey,
                    error: null,
                    data: {
                        closePrices,
                        high,
                        low,
                        open,
                        volume,
                        macd: macdSeries.macd,
                        macdSignal: macdSeries.signal,
                        macdHistogram: macdSeries.histogram,
                        sma: {},
                        ema: {},
                        rsi: {}
                    }
                });
            } catch {
                if (!cancelled) {
                    setResult({
                        key: requestKey,
                        data: null,
                        error: "Could not load stored prices. Check the backend and try again."
                    });
                }
            }
        };

        void loadData();

        return () => {
            cancelled = true;
        };
    }, [currentSymbol, requestKey]);

    const current = result?.key === requestKey ? result : null;

    return (
        <section aria-label="Stock prices">
            <label htmlFor="symbol-dropdown">Symbol: </label>
            <select
                id="symbol-dropdown"
                value={currentSymbol}
                onChange={event => setCurrentSymbol(event.target.value)}
            >
                {symbols.map(symbol => (
                    <option key={symbol} value={symbol}>
                        {symbol}
                    </option>
                ))}
            </select>

            <ExportButton key={currentSymbol} symbol={currentSymbol} />

            {!current && (
                <p role="status">Loading stored prices for {currentSymbol}...</p>
            )}

            {current?.error && (
                <div role="alert">
                    <p>{current.error}</p>
                    <button onClick={() => setRetryVersion(value => value + 1)}>
                        Retry prices
                    </button>
                </div>
            )}

            {current?.data && (
                Object.keys(current.data.closePrices).length === 0 ? (
                    <p>No stored prices available for {currentSymbol}.</p>
                ) : (
                    <StockChart
                        key={requestKey}
                        symbol={currentSymbol}
                        symbolData={current.data}
                    />
                )
            )}
        </section>
    );
};

export default FinanceData;
