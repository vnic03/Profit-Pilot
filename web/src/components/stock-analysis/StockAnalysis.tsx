import React, { useEffect, useState } from "react";
import { getChartData } from "../../service/financeService";
import StockChart from "../charts/stock-chart/StockChart";
import ExportButton from "../ExportButton";
import { useStockCatalog } from "../../service/useStockCatalog";
import SymbolBrowser from "./SymbolBrowser";
import type { FinancialSymbolData, Stock } from "../../service/apiTypes";

interface StockAnalysisProps {
  refreshVersion: number;
  onStockChange?: (stock: Stock | null) => void;
}

interface LoadResult {
  key: string;
  data: FinancialSymbolData | null;
  error: string | null;
}

const StockAnalysis: React.FC<StockAnalysisProps> = ({ refreshVersion, onStockChange }) => {
  const catalog = useStockCatalog(refreshVersion);
  const [selectedSymbol, setSelectedSymbol] = useState("");

  const selectedStock =
    catalog.stocks.find((stock) => stock.symbol === selectedSymbol) ??
    catalog.stocks.find((stock) => stock.symbol === "IBM") ??
    catalog.stocks[0];

  const currentSymbol = selectedStock?.symbol ?? "";

  useEffect(() => {
    onStockChange?.(selectedStock ?? null);
  }, [selectedStock, onStockChange]);

  const [retryVersion, setRetryVersion] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);

  const requestKey = `${currentSymbol}:${refreshVersion}:${retryVersion}`;

  useEffect(() => {
    if (!currentSymbol) return;

    const controller = new AbortController();

    const loadData = async () => {
      try {
        const data: FinancialSymbolData = await getChartData(currentSymbol, controller.signal);

        if (!controller.signal.aborted) {
          setResult({ key: requestKey, data, error: null });
        }
      } catch {
        if (!controller.signal.aborted) {
          setResult({
            key: requestKey,
            data: null,
            error: "Could not load stored prices.",
          });
        }
      }
    };

    void loadData();

    return () => controller.abort();
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
    return <p role="status">No stocks are currently configured.</p>;
  }

  const dates = Object.keys(current?.data?.closePrices ?? {}).sort();
  const latest = dates.at(-1);
  const previous = dates.at(-2);

  const close = latest ? current?.data?.closePrices[latest] : undefined;

  const previousClose = previous ? current?.data?.closePrices[previous] : undefined;

  const change =
    close !== undefined && previousClose !== undefined && previousClose !== 0
      ? ((close - previousClose) / previousClose) * 100
      : null;

  return (
    <section className="analysis-layout" aria-label="Stock prices">
      <SymbolBrowser
        stocks={catalog.stocks}
        selected={currentSymbol}
        onSelect={setSelectedSymbol}
      />

      <div className="signal-workspace">
        <header className="signal-header">
          <div className="signal-identity">
            <h1>{currentSymbol}</h1>

            <div>
              <span>
                {selectedStock.exchange}
                {" / "}
                {selectedStock.currency}
              </span>
              <p>{selectedStock.name}</p>
            </div>
          </div>

          <div className="signal-readout">
            <span>LAST CLOSE</span>
            <strong>
              {close?.toLocaleString("en-US", {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              }) ?? "—"}
            </strong>
          </div>

          <div className="signal-session">
            <span>
              DAILY / <time dateTime={latest}>{latest ?? "—"}</time>
            </span>

            {change !== null && (
              <strong className={change < 0 ? "negative" : "positive"}>
                {change >= 0 ? "+" : ""}
                {change.toFixed(2)}%<small> / PREVIOUS STORED CLOSE</small>
              </strong>
            )}
          </div>
        </header>

        {!current && (
          <p className="workspace-message" role="status">
            Loading stored prices for {currentSymbol}...
          </p>
        )}

        {current?.error && (
          <div className="workspace-message" role="alert">
            <p>{current.error}</p>
            <button onClick={() => setRetryVersion((value) => value + 1)}>Retry prices</button>
          </div>
        )}

        {current?.data &&
          (dates.length === 0 ? (
            <p className="workspace-message">No stored prices available for {currentSymbol}.</p>
          ) : (
            <StockChart key={requestKey} symbol={currentSymbol} symbolData={current.data} />
          ))}

        <footer className="signal-footer">
          <span>
            {dates.length} SESSIONS
            <small>
              {dates[0]} {latest && ` / ${latest}`}
            </small>
          </span>

          <ExportButton key={currentSymbol} symbol={currentSymbol} />
        </footer>
      </div>
    </section>
  );
};

export default StockAnalysis;
