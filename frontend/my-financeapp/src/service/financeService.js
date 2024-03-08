import axios from "axios";

const baseUrl = 'http://localhost:8080/api/finance';

async function fetchFromApi(endpoint, params = {}) {
    try {
        const response = await axios.get(`${baseUrl}/${endpoint}`, { params });
        return response.data;
    } catch (error) {
        console.error("Error fetching data from API:", error);
        throw error;
    }
}

// Global-Error-Handler
axios.interceptors.response.use(
    response => response,
    error => {
        console.error("API Error:", error.response || error.message);
        return Promise.reject(error);
    }
);

export const getClosePrices = (symbol) => fetchFromApi(`prices/${symbol}`);

export const getHigh = (symbol) => fetchFromApi(`high/${symbol}`);

export const getLow = (symbol) => fetchFromApi(`low/${symbol}`);

export const getOpen = (symbol) => fetchFromApi(`open/${symbol}`);

export const getVolume = (symbol) => fetchFromApi(`volume/${symbol}`);

export const getMACD = (symbol) => fetchFromApi(`macd/${symbol}`);

export const getSMA = (symbol, period) => fetchFromApi(`dynamicSMA/${symbol}/${period}`, { period });

export const getEMA = (symbol, period) => fetchFromApi(`dynamicEMA/${symbol}/${period}`, { period });

export const getRSI = (symbol, period) => fetchFromApi(`dynamicRSI/${symbol}/${period}`, { period });

export const getWinnersAndLosers = (symbol, amount) => fetchFromApi(`winnersAndLosers/${symbol}/${amount}`);
