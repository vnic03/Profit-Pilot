import React, {useState, useCallback, useMemo} from "react";
import {LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, Brush} from 'recharts';
import { FaInfoCircle } from 'react-icons/fa';
import "./protoypeStyles.scss";
import descriptions from "./descriptions";
import {FinancialSymbolData} from "../../../service/FinanceData";

interface StockChartProps {
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

const StockChart: React.FC<StockChartProps> = React.memo(({ symbolData }) => {
    const [smaPeriod, setSmaPeriod] = useState(14);
    const [emaPeriod, setEmaPeriod] = useState(14);
    const [rsiPeriod, setRsiPeriod] = useState(14);

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


    const data: ChartData[] = useMemo(() =>  symbolData ? Object.keys(symbolData.closePrices).map(date => ({
        date: date,
        close: symbolData.closePrices?.[date],
        high: symbolData.high?.[date],
        low: symbolData.low?.[date],
        open: symbolData.open?.[date],
        volume: symbolData.volume?.[date],
        SMA: symbolData.sma?.[date],
        EMA: symbolData.ema?.[date],
        RSI: symbolData.rsi?.[date],
        MACD: symbolData.macd?.[date],
    })) : [], [symbolData]);


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
                               onChange={(e) => setSmaPeriod(Number(e.target.value))}/>
                        <input type="number" value={smaPeriod} onChange={(e) => setSmaPeriod(Number(e.target.value))}
                               className="manual-input"/>
                    </div>
                )}

                {visibleData.EMA && (
                    <div className="setting">
                        <label>EMA Period:</label>
                        <input type="range" min={MIN_PERIOD} max={MAX_PERIOD} value={emaPeriod}
                               onChange={(e) => setEmaPeriod(Number(e.target.value))}/>
                        <input type="number" value={emaPeriod} onChange={(e) => setEmaPeriod(Number(e.target.value))}
                               className="manual-input"/>
                    </div>
                )}

                {visibleData.RSI && (
                    <div className="setting">
                        <label>RSI Period:</label>
                        <input type="range" min={MIN_PERIOD} max={MAX_PERIOD} value={rsiPeriod}
                               onChange={(e) => setRsiPeriod(Number(e.target.value))}/>
                        <input type="number" value={rsiPeriod} onChange={(e) => setRsiPeriod(Number(e.target.value))}
                               className="manual-input"/>
                    </div>
                )}
            </div>

            <ResponsiveContainer width="100%" height={500}>
                <LineChart data={data} margin={{top: 20, right: 30, left: 20, bottom: 5,}}>

                    <CartesianGrid strokeDasharray="3 3"/>
                    <XAxis dataKey="date"/>
                    <YAxis yAxisId={"left"} domain={['dataMin - 10', 'dataMax + 10']}
                           tickFormatter={(value) => `${value.toFixed(2)}`}/>
                    <YAxis yAxisId={"right"} orientation={"right"}
                           tickFormatter={(value) => `${(value / 1e6).toFixed(2)}M`}/>
                    <Tooltip/>
                    <Legend/>

                    <Line yAxisId="left" type="monotone" dataKey="close" stroke="#8884d8" name="Close"
                          hide={!visibleData.close}/>
                    <Line yAxisId="left" type="monotone" dataKey="high" stroke="#82ca9d" name="High"
                          hide={!visibleData.high}/>
                    <Line yAxisId="left" type="monotone" dataKey="low" stroke="#ffc658" name="Low"
                          hide={!visibleData.low}/>
                    <Line yAxisId="left" type="monotone" dataKey="open" stroke="#ff7300" name="Open"
                          hide={!visibleData.open}/>
                    <Line yAxisId="right" type="monotone" dataKey="volume" stroke="#000000" name="Volume"
                          hide={!visibleData.volume}/>
                    <Line yAxisId="left" type="monotone" dataKey="SMA" stroke="#9467bd" name="SMA"
                          hide={!visibleData.SMA}/>
                    <Line yAxisId="left" type="monotone" dataKey="EMA" stroke="#8c564b" name="EMA"
                          hide={!visibleData.EMA}/>
                    <Line yAxisId="left" type="monotone" dataKey="RSI" stroke="#d62728" name="RSI"
                          hide={!visibleData.RSI}/>
                    <Line yAxisId="left" type="monotone" dataKey="MACD" stroke="#e377c2" name="MACD"
                          hide={!visibleData.MACD}/>

                    <Brush dataKey={"date"} height={30} stroke={"#8884d8"}/>

                </LineChart>
            </ResponsiveContainer>
        </div>
    );
});

export default StockChart;