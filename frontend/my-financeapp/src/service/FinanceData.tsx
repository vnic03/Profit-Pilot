import React, {useState, useEffect} from "react";
import {getClosePrices, getMACD,getMacdSeries, getHigh, getOpen, getLow, getVolume} from "./financeService";
import StockChart from "../components/charts/prototype/prototype";

interface SymbolSelectorProps {
    onChange: (symbol: string) => void;
    current: string;
}

export interface FinancialSymbolData {
    closePrices: { [date: string]: number };
    high: { [date: string]: number };
    low: { [date: string]: number };
    open: { [date: string]: number };
    volume: { [date: string]: number };
    macd: { [date: string]: number };
    macdSignal: { [date: string]: number };
    macdHistogram: { [date: string]: number };
    sma: { [date: string]: number };
    ema: { [date: string]: number };
    rsi: { [date: string]: number };
}

export interface FinanceDataStructure {
    [key: string]: FinancialSymbolData;
}

interface FinanceDataProps {
    onFinanceDataUpdate: (data: FinanceDataStructure) => void;
}

const SymbolSelector: React.FC<SymbolSelectorProps> = ({ onChange, current }) => (
    <div className="symbol-selector">
        <label htmlFor="symbol-dropdown">Symbol: </label>
        <select id="symbol-dropdown" value={current}
                onChange={(e) => onChange(e.target.value)}
        >
            {symbols.map((symbol) => (
                <option key={symbol} value={symbol}>{symbol}</option>
            ))}
        </select>
    </div>
);

export const symbols = ["IBM", "AAPL", "GOOGL", "MSFT", "AMZN"];

const FinanceData: React.FC<FinanceDataProps> = ({ onFinanceDataUpdate }) => {
    const [currentSymbol, setCurrentSymbol] = useState<string>(symbols[0]);
    const [financeData, setFinanceData] = useState<FinanceDataStructure>({});

    useEffect(() => {
        const loadData = async () => {
            if (financeData[currentSymbol]) {
                onFinanceDataUpdate(financeData);
                return;
            }

            const macdSeries = await getMacdSeries(currentSymbol);

            const data: FinancialSymbolData = {
                closePrices: await getClosePrices(currentSymbol),
                high: await getHigh(currentSymbol),
                low: await getLow(currentSymbol),
                open: await getOpen(currentSymbol),
                volume: await getVolume(currentSymbol),
                macd: macdSeries.macd,
                macdSignal: macdSeries.signal,
                macdHistogram: macdSeries.histogram,
                sma: {},
                ema: {},
                rsi: {},
            };

            const newData = { ...financeData, [currentSymbol]: data };

            setFinanceData(newData);
            onFinanceDataUpdate(newData);
        };

        loadData();

    }, [currentSymbol, financeData, onFinanceDataUpdate]);

    const symbolChange =(symbol: string) => {
        setCurrentSymbol(symbol);
    }

    return(
        <div>
            <SymbolSelector onChange={symbolChange} current={currentSymbol} />
        <div>
            {financeData[currentSymbol] ? (
                <StockChart
                    key={currentSymbol}
                    symbol={currentSymbol}
                    symbolData={financeData[currentSymbol]}
                />
            ) : (
                <p>Loading data...</p>
            )}
        </div>
    </div>
    );
}

export default FinanceData;
