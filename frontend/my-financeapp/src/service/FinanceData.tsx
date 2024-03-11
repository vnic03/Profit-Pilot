import React, {useState, useEffect} from "react";
import {getClosePrices, getMACD, getHigh, getOpen, getLow, getRSI, getEMA, getSMA, getVolume} from "./financeService";
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

            const data: FinancialSymbolData = {
                closePrices: await getClosePrices(currentSymbol),
                high: await getHigh(currentSymbol),
                low: await getLow(currentSymbol),
                open: await getOpen(currentSymbol),
                volume: await getVolume(currentSymbol),
                macd: await getMACD(currentSymbol),
                sma: await getSMA(currentSymbol, 14),
                ema: await getEMA(currentSymbol, 14),
                rsi: await getRSI(currentSymbol, 14),
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
                <StockChart symbolData={financeData[currentSymbol]} />
            ) : (
                <p>Loading data...</p>
            )}
        </div>
    </div>
    );
}

export default FinanceData;
