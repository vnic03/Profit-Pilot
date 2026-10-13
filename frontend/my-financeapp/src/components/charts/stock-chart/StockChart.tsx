import React, {useCallback, useMemo, useState} from "react";

import {
    Bar,
    Brush,
    CartesianGrid,
    ComposedChart,
    Legend,
    Line,
    LineChart,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis
} from "recharts";

import "./StockChart.scss";

import ChartControls from "./ChartControls";
import type {SeriesKey} from "./ChartControls";
import type {FinancialSymbolData} from "../../../service/FinanceData";

import {
    getEMA,
    getRSI,
    getSMA
} from "../../../service/financeService";

import {useIndicator} from "../../../service/useIndicator";

interface StockChartProps {
    symbol: string;
    symbolData: FinancialSymbolData;
}

interface ChartData {
    date: string;
    close: number;
    open?: number;
    low?: number;
    high?: number;
    volume?: number;
    SMA?: number;
    EMA?: number;
    RSI?: number;
    MACD?: number;
    signal?: number;
    histogram?: number;
}

type HistoryRange = "20" | "60" | "all";

const StockChart: React.FC<StockChartProps> = React.memo(({
    symbol,
    symbolData
}) => {
    const [smaPeriod, setSmaPeriod] = useState(14);
    const [emaPeriod, setEmaPeriod] = useState(14);
    const [rsiPeriod, setRsiPeriod] = useState(14);

    const sma = useIndicator(symbol, smaPeriod, getSMA);
    const ema = useIndicator(symbol, emaPeriod, getEMA);
    const rsi = useIndicator(symbol, rsiPeriod, getRSI);

    const [range, setRange] = useState<HistoryRange>("all");
    const [chartVersion, setChartVersion] = useState(0);

    const [visibleData, setVisibleData] = useState<
        Record<SeriesKey, boolean>
    >({
        close: true,
        high: false,
        low: false,
        open: false,
        volume: false,
        SMA: false,
        EMA: false,
        RSI: false,
        MACD: false
    });

    const data: ChartData[] = useMemo(() => {
        return Object.keys(symbolData.closePrices)
            .sort()
            .map(date => ({
                date,
                close: symbolData.closePrices[date],
                high: symbolData.high?.[date],
                low: symbolData.low?.[date],
                open: symbolData.open?.[date],
                volume: symbolData.volume?.[date],
                SMA: sma.data[date],
                EMA: ema.data[date],
                RSI: rsi.data[date],
                MACD: symbolData.macd?.[date],
                signal: symbolData.macdSignal?.[date],
                histogram: symbolData.macdHistogram?.[date]
            }));
    }, [symbolData, sma.data, ema.data, rsi.data]);

    const chartData = range === "all"
        ? data
        : data.slice(-Number(range));

    const anySeriesVisible = Object.values(visibleData).some(Boolean);

    const firstDate = data[0]?.date;
    const latestDate = data[data.length - 1]?.date;

    const chartKey = `${symbol}:${range}:${chartVersion}`;
    const syncId = `stock-${symbol}`;

    const toggleDataSeries = useCallback((name: SeriesKey) => {
        setVisibleData(current => ({
            ...current,
            [name]: !current[name]
        }));
    }, []);

    return (
        <div className="chart-container">
            <div className="data-status">
                <h2>{symbol}</h2>

                {latestDate ? (
                    <>
                        <p>
                            Latest stored close:{" "}
                            <time dateTime={latestDate}>
                                {latestDate}
                            </time>
                        </p>

                        <p>
                            {data.length} stored trading days
                            {" · "}
                            {firstDate} to {latestDate}
                        </p>

                        <p>Daily prices. Not live quotes.</p>
                    </>
                ) : (
                    <p>No stored prices available for this symbol.</p>
                )}
            </div>

            <ChartControls
                visible={visibleData}
                onToggle={toggleDataSeries}
                smaPeriod={smaPeriod}
                emaPeriod={emaPeriod}
                rsiPeriod={rsiPeriod}
                onSmaChange={setSmaPeriod}
                onEmaChange={setEmaPeriod}
                onRsiChange={setRsiPeriod}
            />

            <div className="chart-range-controls">
                <label htmlFor={`chart-range-${symbol}`}>
                    Show history
                </label>

                <select
                    id={`chart-range-${symbol}`}
                    value={range}
                    onChange={event => {
                        setRange(event.target.value as HistoryRange);
                        setChartVersion(value => value + 1);
                    }}
                >
                    <option value="20">
                        Latest 20 stored sessions
                    </option>
                    <option value="60">
                        Latest 60 stored sessions
                    </option>
                    <option value="all">
                        All stored sessions
                    </option>
                </select>

                <button
                    type="button"
                    onClick={() => {
                        setRange("all");
                        setChartVersion(value => value + 1);
                    }}
                >
                    Reset view
                </button>
            </div>

            <p className="chart-range-description">
                Selected history: {chartData.length} stored sessions
                {chartData.length > 0 && (
                    <>
                        {" · "}
                        {chartData[0].date} to{" "}
                        {chartData[chartData.length - 1].date}
                    </>
                )}
                . Drag the handles below the chart to narrow the view.
            </p>

            {!anySeriesVisible && (
                <p role="status">
                    Choose a price series or indicator to display.
                </p>
            )}

            {[
                {name: "SMA", visible: visibleData.SMA, state: sma},
                {name: "EMA", visible: visibleData.EMA, state: ema},
                {name: "RSI", visible: visibleData.RSI, state: rsi}
            ].map(({name, visible, state}) => (
                visible && (
                    <div key={name}>
                        {state.loading && (
                            <p role="status">Loading {name}...</p>
                        )}

                        {state.error && (
                            <div role="alert">
                                <p>{name}: {state.error}</p>

                                {state.canRetry && (
                                    <button
                                        type="button"
                                        onClick={state.retry}
                                    >
                                        Retry {name}
                                    </button>
                                )}
                            </div>
                        )}

                        {state.empty && (
                            <p role="status">
                                Not enough stored prices to calculate {name}
                                {" "}with this period.
                            </p>
                        )}
                    </div>
                )
            ))}

            <ResponsiveContainer width="100%" height={360}>
                <LineChart
                    key={chartKey}
                    data={chartData}
                    syncId={syncId}
                    margin={{top: 12, right: 8, left: 0, bottom: 5}}
                    accessibilityLayer
                >
                    <CartesianGrid strokeDasharray="3 3"/>

                    <XAxis
                        dataKey="date"
                        minTickGap={28}
                        tick={{fontSize: 11}}
                        tickFormatter={(value: string) => value.slice(5)}
                    />

                    <YAxis
                        yAxisId="price"
                        domain={["auto", "auto"]}
                        tickFormatter={(value: number) => value.toFixed(2)}
                    />

                    <YAxis
                        yAxisId="volume"
                        orientation="right"
                        hide={!visibleData.volume}
                        tickFormatter={(value: number) =>
                            `${(value / 1e6).toFixed(2)}M`
                        }
                    />

                    <Tooltip/>
                    <Legend/>

                    <Line
                        yAxisId="price"
                        type="linear"
                        dataKey="close"
                        stroke="#8884d8"
                        name="Close"
                        dot={false}
                        hide={!visibleData.close}
                        isAnimationActive={false}
                    />

                    <Line
                        yAxisId="price"
                        type="linear"
                        dataKey="high"
                        stroke="#82ca9d"
                        name="High"
                        dot={false}
                        hide={!visibleData.high}
                        isAnimationActive={false}
                    />

                    <Line
                        yAxisId="price"
                        type="linear"
                        dataKey="low"
                        stroke="#ffc658"
                        name="Low"
                        dot={false}
                        hide={!visibleData.low}
                        isAnimationActive={false}
                    />

                    <Line
                        yAxisId="price"
                        type="linear"
                        dataKey="open"
                        stroke="#ff7300"
                        name="Open"
                        dot={false}
                        hide={!visibleData.open}
                        isAnimationActive={false}
                    />

                    <Line
                        yAxisId="volume"
                        type="linear"
                        dataKey="volume"
                        stroke="#555555"
                        name="Volume"
                        dot={false}
                        hide={!visibleData.volume}
                        isAnimationActive={false}
                    />

                    <Line
                        yAxisId="price"
                        type="linear"
                        dataKey="SMA"
                        stroke="#9467bd"
                        name={`SMA (${smaPeriod})`}
                        dot={false}
                        connectNulls={false}
                        hide={!visibleData.SMA}
                        isAnimationActive={false}
                    />

                    <Line
                        yAxisId="price"
                        type="linear"
                        dataKey="EMA"
                        stroke="#8c564b"
                        name={`EMA (${emaPeriod})`}
                        dot={false}
                        connectNulls={false}
                        hide={!visibleData.EMA}
                        isAnimationActive={false}
                    />

                    <Brush
                        dataKey="date"
                        height={30}
                        stroke="#8884d8"
                    />
                </LineChart>
            </ResponsiveContainer>

            {visibleData.RSI && (
                <section aria-label="RSI chart">
                    <h3>RSI ({rsiPeriod})</h3>

                    <ResponsiveContainer width="100%" height={220}>
                        <LineChart
                            key={chartKey}
                            data={chartData}
                            syncId={syncId}
                            margin={{top: 12, right: 8, left: 0, bottom: 5}}
                            accessibilityLayer
                        >
                            <CartesianGrid strokeDasharray="3 3"/>

                            <XAxis
                                dataKey="date"
                                minTickGap={28}
                                tick={{fontSize: 11}}
                                tickFormatter={(value: string) =>
                                    value.slice(5)
                                }
                            />

                            <YAxis
                                domain={[0, 100]}
                                ticks={[0, 30, 50, 70, 100]}
                            />

                            <Tooltip/>

                            <ReferenceLine
                                y={30}
                                stroke="#888888"
                                strokeDasharray="4 4"
                            />

                            <ReferenceLine
                                y={70}
                                stroke="#888888"
                                strokeDasharray="4 4"
                            />

                            <Line
                                type="linear"
                                dataKey="RSI"
                                stroke="#d62728"
                                name={`RSI (${rsiPeriod})`}
                                dot={false}
                                connectNulls={false}
                                isAnimationActive={false}
                            />
                        </LineChart>
                    </ResponsiveContainer>
                </section>
            )}

            {visibleData.MACD && (
                <section aria-label="MACD chart">
                    <h3>MACD (12, 26, 9)</h3>

                    <ResponsiveContainer width="100%" height={250}>
                        <ComposedChart
                            key={chartKey}
                            data={chartData}
                            syncId={syncId}
                            margin={{top: 12, right: 8, left: 0, bottom: 5}}
                            accessibilityLayer
                        >
                            <CartesianGrid strokeDasharray="3 3"/>

                            <XAxis
                                dataKey="date"
                                minTickGap={28}
                                tick={{fontSize: 11}}
                                tickFormatter={(value: string) =>
                                    value.slice(5)
                                }
                            />

                            <YAxis
                                domain={["auto", "auto"]}
                                tickFormatter={(value: number) =>
                                    value.toFixed(2)
                                }
                            />

                            <Tooltip/>
                            <Legend/>
                            <ReferenceLine y={0} stroke="#888888"/>

                            <Bar
                                dataKey="histogram"
                                name="Histogram"
                                fill="#94a3b8"
                                isAnimationActive={false}
                            />

                            <Line
                                type="linear"
                                dataKey="MACD"
                                stroke="#e377c2"
                                name="MACD"
                                dot={false}
                                connectNulls={false}
                                isAnimationActive={false}
                            />

                            <Line
                                type="linear"
                                dataKey="signal"
                                stroke="#2563eb"
                                name="Signal (9)"
                                dot={false}
                                connectNulls={false}
                                isAnimationActive={false}
                            />
                        </ComposedChart>
                    </ResponsiveContainer>
                </section>
            )}
        </div>
    );
});

export default StockChart;
