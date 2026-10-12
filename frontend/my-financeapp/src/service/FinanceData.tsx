import React, {useEffect, useState} from "react";
import { getClosePrices, getMacdSeries, getHigh, getOpen,
    getLow, getVolume } from "./financeService";
import StockChart from "../components/charts/prototype/prototype";
import ExportButton from "../components/ExportButton";
import {useStockCatalog} from "./useStockCatalog";
import type {Stock} from "./useStockCatalog";


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
    onStockChange?: (stock: Stock | null) => void;
}

interface LoadResult {
    key: string;
    data: FinancialSymbolData | null;
    error: string | null;
}

const FinanceData: React.FC<FinanceDataProps> = ({
  refreshVersion,
  onStockChange
}) => {
    const catalog = useStockCatalog(refreshVersion);
    const [selectedSymbol, setSelectedSymbol] = useState("");

    const selectedStock =
        catalog.stocks.find(stock => stock.symbol === selectedSymbol)
        ?? catalog.stocks[0];

    const currentSymbol = selectedStock?.symbol ?? "";

    useEffect(() => {
        onStockChange?.(selectedStock ?? null);
    }, [selectedStock, onStockChange]);

    const [retryVersion, setRetryVersion] = useState(0);
    const [result, setResult] = useState<LoadResult | null>(null);

    const requestKey =
        `${currentSymbol}:${refreshVersion}:${retryVersion}`;

    useEffect(() => {
        if (!currentSymbol) return;

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

    if (catalog.loading) {
        return <p role="status">Loading available stocks...</p>;
    }

    if (catalog.error) {
        return (
            <div role="alert">
                <p>{catalog.error}</p>
                <button type="button" onClick={catalog.retry}>
                    Retry stock list
                </button>
            </div>
        );
    }

    if (!selectedStock) {
        return (
            <p role="status">
                No stocks are currently configured.
            </p>
        );
    }

    return (
        <section aria-label="Stock prices">
            <label htmlFor="symbol-dropdown">Stock: </label>
            <select
                id="symbol-dropdown"
                value={currentSymbol}
                onChange={event => setSelectedSymbol(event.target.value)}
            >
                {catalog.stocks.map(stock => (
                    <option key={stock.symbol} value={stock.symbol}>
                        {stock.symbol} — {stock.name}
                    </option>
                ))}
            </select>

            <p className="stock-details">
                {selectedStock.exchange}
                {" · "}
                Prices in {selectedStock.currency}
            </p>

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
