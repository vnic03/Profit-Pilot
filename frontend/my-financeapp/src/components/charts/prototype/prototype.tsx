import React, {useEffect, useState} from "react";
import {
    LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, Brush
        } from 'recharts';
import {
    getClosePrices, getEMA, getMACD, getSMA, getHigh, getOpen, getLow, getVolume
} from "../../../service/financeService";
import { FaInfoCircle } from 'react-icons/fa';
import "./protoypeStyles.scss";
import "./descriptions";
import descriptions from "./descriptions";

interface StockChartProps {
    symbol: string;
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
    MACD: string[];
}

const StockChart: React.FC<StockChartProps> = ({ symbol }) => {
    const [data, setData] = useState<ChartData[]>([]);

    useEffect(() => {
        const fetchData = async () => {
            try {

                const responses = await Promise.all([
                    getClosePrices(symbol),
                    getHigh(symbol),
                    getLow(symbol),
                    getOpen(symbol),
                    getVolume(symbol),
                    getSMA(symbol),
                    getEMA(symbol),
                    getMACD(symbol),
                ]);

                const formatData: ChartData[] = Object.keys(responses[0]).map(date => ({
                    date: date,
                    close: responses[0][date],
                    high: responses[1][date],
                    low: responses[2][date],
                    open: responses[3][date],
                    volume: responses[4][date],
                    SMA: responses[5][date],
                    EMA: responses[6][date],
                    MACD: responses[7][date],
                }));

                setData(formatData);

            } catch (error) {
                console.error("An error occurred while fetching chart data:", error);
            }
        };

        fetchData();

    }, [symbol]);

    const [visibleData, setVisibleData] = useState({
        close: true,
        high: true,
        low: true,
        open: true,
        volume: false,
        SMA: false,
        EMA: false,
        MACD: false,
    })

    const toggleDataSeries = (name: keyof typeof visibleData) => {
        setVisibleData(state => ({
            ...state,
            [name]: !state[name]
        }));
    }

    const [showModal, setShowModal] = useState(false);
    const [modalContent, setModalContent] = useState("");

    const handleOpenModal = (key: keyof Description) => {
        setModalContent(descriptions[key].join('\n'));
        setShowModal(true);
    }

    const handleCloseModal = () => {
        setShowModal(false);
    };

    const Modal: React.FC<{
        showModal: boolean, content: string, onClose: () => void}> =
        ({showModal, content, onClose}) => {
        if (!showModal) return null;

        return (
            <div className="modal-backdrop">
                <div className="modal-content">
                    <p>{content}</p>
                    <button onClick={onClose}>Close</button>
                </div>
            </div>
        );
    }

    return (
        <div className={"chart-container"}>

            <Modal showModal={showModal} content={modalContent} onClose={handleCloseModal} />

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

            <button onClick={() => toggleDataSeries('EMA')}>EMA</button>
            <button onClick={() => handleOpenModal('EMA')}>
                <FaInfoCircle className="info-icon"/>
            </button>

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
                    <Line yAxisId="left" type="monotone" dataKey="MACD" stroke="#e377c2" name="MACD"
                          hide={!visibleData.MACD}/>

                    <Brush dataKey={"date"} height={30} stroke={"#8884d8"}/>

                </LineChart>
            </ResponsiveContainer>
        </div>
    );
};

export default StockChart;