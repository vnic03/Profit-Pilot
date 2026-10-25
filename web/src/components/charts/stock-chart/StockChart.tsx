import {useMemo, useState} from "react";
import {
    Bar,
    Brush,
    CartesianGrid,
    Cell,
    ComposedChart,
    Line,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis
} from "recharts";
import ChartControls, {type SeriesKey} from "./ChartControls";
import type {FinancialSymbolData} from "../../stock-analysis/StockAnalysis";
import {getEMA, getRSI, getSMA} from "../../../service/financeService";
import {useIndicator} from "../../../service/useIndicator";
import TimeWindow, {type TimeSelection} from "./TimeWindow";

interface Props {
    symbol: string;
    symbolData: FinancialSymbolData;
}

const colors = {
    close: "#F0C75E",
    open: "#B6C4D0",
    high: "#E3E7EB",
    low: "#F08074",
    SMA: "#77B8D0",
    EMA: "#D3B5D8",
    RSI: "#F08074",
    MACD: "#77B8D0",
    signal: "#F08074",
    volume: "#77B8D0"
};

const margin = {top: 10, right: 20, left: 0, bottom: 0};
const tick = {fill: "#A5B1BD", fontSize: 11};

const numberLabel = (value: number) =>
    value.toLocaleString("en-US", {maximumFractionDigits: 2});

export default function StockChart({symbol, symbolData}: Props) {
    const [smaPeriod, setSmaPeriod] = useState(20);
    const [emaPeriod, setEmaPeriod] = useState(20);
    const [rsiPeriod, setRsiPeriod] = useState(14);

    const sma = useIndicator(symbol, smaPeriod, getSMA);
    const ema = useIndicator(symbol, emaPeriod, getEMA);
    const rsi = useIndicator(symbol, rsiPeriod, getRSI);

    const [selection, setSelection] = useState<{
        startIndex: number;
        endIndex: number;
    } | null>(null);

    const [visible, setVisible] = useState<Record<SeriesKey, boolean>>({
        close: true,
        open: false,
        high: false,
        low: false,
        SMA: true,
        EMA: false,
        RSI: true,
        MACD: true,
        volume: false
    });

    const data = useMemo(
        () => Object.keys(symbolData.closePrices)
            .sort()
            .map(date => ({
                date,
                close: symbolData.closePrices[date],
                open: symbolData.open[date],
                high: symbolData.high[date],
                low: symbolData.low[date],
                volume: symbolData.volume[date],
                SMA: sma.data[date],
                EMA: ema.data[date],
                RSI: rsi.data[date],
                MACD: symbolData.macd[date],
                signal: symbolData.macdSignal[date],
                histogram: symbolData.macdHistogram[date]
            })),
        [symbolData, sma.data, ema.data, rsi.data]
    );

    const windowData = selection
        ? data.slice(selection.startIndex, selection.endIndex + 1)
        : data;

    const syncId = `signal-${symbol}`;

    const priceKeys = [
        "close", "open", "high", "low", "SMA", "EMA"
    ] as const;

    const axes = (kind: "price" | "rsi" | "macd" | "volume") => (
        <>
            <CartesianGrid stroke="#2B3743" vertical={false}/>

            <XAxis
                dataKey="date"
                tick={tick}
                stroke="#465361"
                minTickGap={45}
                tickFormatter={(date: string) => date.slice(5)}
                height={28}
            />

            <YAxis
                width={66}
                tick={tick}
                stroke="#465361"
                tickLine={false}
                axisLine={false}
                domain={
                    kind === "rsi"
                        ? [0, 100]
                        : kind === "volume"
                            ? [0, "auto"]
                            : ["auto", "auto"]
                }
                ticks={
                    kind === "rsi"
                        ? [0, 30, 50, 70, 100]
                        : undefined
                }
                tickFormatter={
                    kind === "volume"
                        ? value => `${numberLabel(Number(value) / 1e6)}M`
                        : numberLabel
                }
            />

            <Tooltip
                cursor={{
                    stroke: "#A5B1BD",
                    strokeDasharray: "4 4"
                }}
                contentStyle={{
                    background: "#252A30",
                    border: "1px solid #465361",
                    fontSize: 12
                }}
                labelStyle={{color: "#E3E7EB"}}
                formatter={(value: number) => numberLabel(value)}
            />
        </>
    );

    const line = (key: keyof typeof colors, label: string) => (
        <Line
            key={key}
            type="linear"
            dataKey={key}
            name={label}
            stroke={colors[key]}
            strokeWidth={1.6}
            dot={false}
            activeDot={{r: 3}}
            connectNulls={false}
            isAnimationActive={false}
        />
    );

    return (
        <div className="chart-container">
            <ChartControls
                visible={visible}
                onToggle={key => setVisible(current => ({
                    ...current,
                    [key]: !current[key]
                }))}
                smaPeriod={smaPeriod}
                emaPeriod={emaPeriod}
                rsiPeriod={rsiPeriod}
                onSmaChange={setSmaPeriod}
                onEmaChange={setEmaPeriod}
                onRsiChange={setRsiPeriod}
            />

            <TimeWindow
                data={data}
                selection={selection}
                onChange={setSelection}
            />

            {([
                {key: "SMA", state: sma},
                {key: "EMA", state: ema},
                {key: "RSI", state: rsi}
            ] as const).map(({key, state}) => (
                visible[key] && (
                    <div key={key}>
                        {state.loading && (
                            <p className="indicator-notice" role="status">
                                Loading {key}...
                            </p>
                        )}

                        {state.error && (
                            <div className="indicator-notice" role="alert">
                                {key}: {state.error}
                                {state.canRetry && (
                                    <button onClick={state.retry}>
                                        Retry {key}
                                    </button>
                                )}
                            </div>
                        )}

                        {state.empty && (
                            <p className="indicator-notice" role="status">
                                {key}: insufficient stored history for this period.
                            </p>
                        )}
                    </div>
                )
            ))}

            <section
                className="chart-lane price-lane"
                aria-label="Price chart"
            >
                <div className="lane-label">
                    PRICE <span>{symbol}</span>
                </div>

                {!priceKeys.some(key => visible[key]) && (
                    <p role="status">
                        Select a price series or moving average.
                    </p>
                )}

                <div className="price-plot">
                    <ResponsiveContainer width="100%" height="100%">
                        <ComposedChart
                            data={windowData}
                            syncId={syncId}
                            syncMethod="value"
                            margin={margin}
                            accessibilityLayer
                        >
                            {axes("price")}

                            {priceKeys
                                .filter(key => visible[key])
                                .map(key => line(key, key.toUpperCase()))}
                        </ComposedChart>
                    </ResponsiveContainer>
                </div>
            </section>

            {visible.RSI && (
                <section className="chart-lane" aria-label="RSI chart">
                    <div className="lane-label coral">
                        RSI [{rsiPeriod}] <span>30 / 70</span>
                    </div>

                    <ResponsiveContainer width="100%" height={150}>
                        <ComposedChart
                            data={windowData}
                            syncId={syncId}
                            syncMethod="value"
                            margin={margin}
                            accessibilityLayer
                        >
                            {axes("rsi")}

                            <ReferenceLine
                                y={30}
                                stroke={colors.RSI}
                                strokeDasharray="5 5"
                            />

                            <ReferenceLine
                                y={70}
                                stroke={colors.RSI}
                                strokeDasharray="5 5"
                            />

                            {line("RSI", `RSI (${rsiPeriod})`)}
                        </ComposedChart>
                    </ResponsiveContainer>
                </section>
            )}

            {visible.MACD && (
                <section className="chart-lane" aria-label="MACD chart">
                    <div className="lane-label blue">
                        MACD [12/26/9]
                        <span>BLUE / MACD · CORAL / SIGNAL</span>
                    </div>

                    <ResponsiveContainer width="100%" height={150}>
                        <ComposedChart
                            data={windowData}
                            syncId={syncId}
                            syncMethod="value"
                            margin={margin}
                            accessibilityLayer
                        >
                            {axes("macd")}
                            <ReferenceLine y={0} stroke="#465361"/>

                            <Bar
                                dataKey="histogram"
                                name="Histogram"
                                isAnimationActive={false}
                            >
                                {windowData.map(row => (
                                    <Cell
                                        key={row.date}
                                        fill={
                                            (row.histogram ?? 0) < 0
                                                ? colors.signal
                                                : colors.MACD
                                        }
                                    />
                                ))}
                            </Bar>

                            {line("MACD", "MACD")}
                            {line("signal", "Signal (9)")}
                        </ComposedChart>
                    </ResponsiveContainer>
                </section>
            )}

            {visible.volume && (
                <section className="chart-lane" aria-label="Volume chart">
                    <div className="lane-label blue">
                        VOLUME <span>SHARES / MILLIONS</span>
                    </div>

                    <ResponsiveContainer width="100%" height={140}>
                        <ComposedChart
                            data={windowData}
                            syncId={syncId}
                            syncMethod="value"
                            margin={margin}
                            accessibilityLayer
                        >
                            {axes("volume")}

                            <Bar
                                dataKey="volume"
                                name="Volume"
                                fill={colors.volume}
                                isAnimationActive={false}
                            />
                        </ComposedChart>
                    </ResponsiveContainer>
                </section>
            )}

            <details className="chart-help">
                <summary>Signal reference</summary>
                <p>
                    SMA / EMA use stored closing prices.
                    Periods: 1–50 sessions. RSI: 0–100.
                    MACD: 12/26/9.
                    Missing initial indicator values are calculation warm-up.
                    Volume has its own scale.
                    The time window applies to every lane;
                    indicators are calculated from the full stored history.
                    Daily data, not live quotes.
                </p>
            </details>
        </div>
    );
}
