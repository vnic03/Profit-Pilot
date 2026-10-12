import React, {useState, useCallback, useMemo} from "react";
import {LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer, Brush,
  ReferenceLine, ComposedChart, Bar} from 'recharts';
import { FaInfoCircle } from 'react-icons/fa';
import "./protoypeStyles.scss";
import descriptions from "./descriptions";
import {FinancialSymbolData} from "../../../service/FinanceData";
import {getSMA, getEMA, getRSI} from "../../../service/financeService";
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

interface Description {
    close: string[];
    high: string[];
    low: string[];
    open: string[];
    volume: string[];
    SMA: string[];
    EMA: string[];
    RSI: string[];
    MACD: string[];
}

const MIN_PERIOD: number = 1;
const MAX_PERIOD: number = 50;

const StockChart: React.FC<StockChartProps> = React.memo(({ symbol, symbolData }) => {
    const [smaPeriod, setSmaPeriod] = useState(14);
    const [emaPeriod, setEmaPeriod] = useState(14);
    const [rsiPeriod, setRsiPeriod] = useState(14);

    const sma = useIndicator(symbol, smaPeriod, getSMA);
    const ema = useIndicator(symbol, emaPeriod, getEMA);
    const rsi = useIndicator(symbol, rsiPeriod, getRSI);

    const [visibleData, setVisibleData] = useState({
        close: true,
        high: true,
        low: true,
        open: true,
        volume: false,
        SMA: false,
        EMA: false,
        RSI: false,
        MACD: false,
    })

    const [showModal, setShowModal] = useState(false);
    const [modalContent, setModalContent] = useState("");

    const data: ChartData[] = useMemo(() => {
        if (!symbolData) {
            return [];
        }

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
                histogram: symbolData.macdHistogram?.[date],
            }));
    }, [symbolData, sma.data, ema.data, rsi.data]);


    const firstDate = data[0]?.date;
    const latestDate = data[data.length - 1]?.date;

    const toggleDataSeries = useCallback((name: keyof typeof visibleData) => {
        setVisibleData(state => ({
            ...state,
            [name]: !state[name]
        }));
    }, []);

    const handleOpenModal = useCallback((key: keyof Description) => {
        setModalContent(descriptions[key].join('\n'));
        setShowModal(true);
    }, []);

    const renderModal = showModal && (
        <div className="modal-backdrop">
            <div className="modal-content">
                <p>{modalContent}</p>
                <button onClick={() => setShowModal(false)}>Close</button>
            </div>
        </div>
    );

    return (
        <div className={"chart-container"}>
            {renderModal}

            <div className="data-status">
                <h2>{symbol}</h2>

                {latestDate ? (
                    <>
                        <p>
                            Latest stored close:{" "}
                            <time dateTime={latestDate}>{latestDate}</time>
                        </p>
                        <p>
                            {data.length} stored trading days
                            {" · "}
                            {firstDate} to {latestDate}
                        </p>
                        <p>Daily closing prices. Not live quotes.</p>
                    </>
                ) : (
                    <p>No stored prices available for this symbol.</p>
                )}
            </div>

            <button onClick={() => toggleDataSeries('volume')}>Volume</button>
            <button onClick={() => handleOpenModal('volume')}>
                <FaInfoCircle className="info-icon"/>
            </button>

            <button onClick={() => toggleDataSeries('MACD')}>MACD</button>
            <button onClick={() => handleOpenModal('MACD')}>
                <FaInfoCircle className="info-icon"/>
            </button>

            <button onClick={() => toggleDataSeries('SMA')}>SMA</button>
            <button onClick={() => handleOpenModal('SMA')}>
                <FaInfoCircle className="info-icon"/>
            </button>

            <button onClick={() => toggleDataSeries('RSI')}>RSI</button>
            <button onClick={() => handleOpenModal('RSI')}>
                <FaInfoCircle className="info-icon"/>
            </button>

            <button onClick={() => toggleDataSeries('EMA')}>EMA</button>
            <button onClick={() => handleOpenModal('EMA')}>
                <FaInfoCircle className="info-icon"/>
            </button>

            <div className="indicator-settings">
                {visibleData.SMA && (
                    <div className={"setting"}>
                        <label>SMA Period:</label>
                        <input type="range" min={MIN_PERIOD} max={MAX_PERIOD} value={smaPeriod}
                               onChange={(e) => setSmaPeriod(Number(e.target.value))}
                        />
                        <input type="number" min={MIN_PERIOD} max={MAX_PERIOD} step={1}
                                value={smaPeriod} onChange={(e) => setSmaPeriod(Number(e.target.value))}
                                className="manual-input"
                        />
                    </div>
                )}

                {visibleData.EMA && (
                    <div className="setting">
                        <label>EMA Period:</label>
                        <input type="range" min={MIN_PERIOD} max={MAX_PERIOD} value={emaPeriod}
                               onChange={(e) => setEmaPeriod(Number(e.target.value))}
                        />
                        <input type="number" min={MIN_PERIOD} max={MAX_PERIOD} step={1}
                                value={emaPeriod} onChange={(e) => setEmaPeriod(Number(e.target.value))}
                                className="manual-input"
                        />
                    </div>
                )}

                {visibleData.RSI && (
                    <div className="setting">
                        <label>RSI Period:</label>
                        <input type="range" min={MIN_PERIOD} max={MAX_PERIOD} value={rsiPeriod}
                               onChange={(e) => setRsiPeriod(Number(e.target.value))}
                        />
                        <input type="number" min={MIN_PERIOD} max={MAX_PERIOD} step={1}
                                value={rsiPeriod} onChange={(e) => setRsiPeriod(Number(e.target.value))}
                                className="manual-input"
                        />
                    </div>
                )}
            </div>

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
                                    <button type="button" onClick={state.retry}>
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

            <ResponsiveContainer width="100%" height={500}>
                <LineChart
                    data={data}
                    syncId={`stock-${symbol}`}
                    margin={{top: 20, right: 30, left: 20, bottom: 5}}
                >
                    <CartesianGrid strokeDasharray="3 3"/>
                    <XAxis dataKey="date"/>

                    <YAxis
                        yAxisId="price"
                        domain={["auto", "auto"]}
                        tickFormatter={(value: number) => value.toFixed(2)}
                    />

                    <YAxis
                        yAxisId="volume"
                        orientation="right"
                        hide={!visibleData.volume}
                        tickFormatter={(value: number) => `${(value / 1e6).toFixed(2)}M`}
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
                    />
                    <Line
                        yAxisId="price"
                        type="linear"
                        dataKey="high"
                        stroke="#82ca9d"
                        name="High"
                        dot={false}
                        hide={!visibleData.high}
                    />
                    <Line
                        yAxisId="price"
                        type="linear"
                        dataKey="low"
                        stroke="#ffc658"
                        name="Low"
                        dot={false}
                        hide={!visibleData.low}
                    />
                    <Line
                        yAxisId="price"
                        type="linear"
                        dataKey="open"
                        stroke="#ff7300"
                        name="Open"
                        dot={false}
                        hide={!visibleData.open}
                    />
                    <Line
                        yAxisId="volume"
                        type="linear"
                        dataKey="volume"
                        stroke="#555555"
                        name="Volume"
                        dot={false}
                        hide={!visibleData.volume}
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
                    />

                    <Brush dataKey="date" height={30} stroke="#8884d8"/>
                </LineChart>
            </ResponsiveContainer>

            {visibleData.RSI && (
                <section aria-label="RSI chart">
                    <h3>RSI ({rsiPeriod})</h3>

                    <ResponsiveContainer width="100%" height={220}>
                        <LineChart
                            data={data}
                            syncId={`stock-${symbol}`}
                            margin={{top: 10, right: 30, left: 20, bottom: 5}}
                        >
                            <CartesianGrid strokeDasharray="3 3"/>
                            <XAxis dataKey="date"/>
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
                            data={data}
                            syncId={`stock-${symbol}`}
                            margin={{top: 10, right: 30, left: 20, bottom: 5}}
                        >
                            <CartesianGrid strokeDasharray="3 3"/>
                            <XAxis dataKey="date"/>

                            <YAxis
                                domain={["auto", "auto"]}
                                tickFormatter={(value: number) => value.toFixed(2)}
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
