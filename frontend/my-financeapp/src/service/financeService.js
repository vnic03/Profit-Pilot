import axios from "axios";

const baseUrl = 'http://localhost:8080/api/finance';


export const getClosePrices = async (symbol) => {
    const response = await axios.get(`${baseUrl}/prices/${symbol}`);
    return response.data;
}

export const getHigh = async (symbol) => {
    const response = await axios.get(`${baseUrl}/high/${symbol}`);
    return response.data;
}

export const getLow = async (symbol) => {
    const response = await axios.get(`${baseUrl}/low/${symbol}`);
    return response.data;
}

export const getOpen = async (symbol) => {
    const response = await axios.get(`${baseUrl}/open/${symbol}`);
    return response.data;
}

export const getVolume = async (symbol) => {
    const response = await axios.get(`${baseUrl}/volume/${symbol}`);
    return response.data;
}

export const getEMA = async (symbol) => {
    const response = await axios.get(`${baseUrl}/ema/${symbol}`);
    return response.data;
}

export const getSMA = async (symbol) => {
    const response = await axios.get(`${baseUrl}/sma/${symbol}`);
    return response.data;
}

export const getMACD = async (symbol) => {
    const response = await axios.get(`${baseUrl}/macd/${symbol}`);
    return response.data;
}