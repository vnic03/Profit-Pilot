import axios from "axios";

const baseUrl = 'http://localhost:8080/api/finance';

// service to fetch data from the API
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

export const getMacdSeries = (symbol) => fetchFromApi(`macd-series/${symbol}`);

export const getWinnersAndLosers = (amount) => fetchFromApi(`winnersAndLosers/${amount}`);

export const getDataStatus = () => fetchFromApi("data-status");

async function fetchIndicator(indicator, symbol, period, signal) {
    try {
        const response = await axios.get(
            `${baseUrl}/${indicator}/${encodeURIComponent(symbol)}/${period}`,
            {
                signal,
                timeout: 15000
            }
        );

        return response.data;
    } catch (error) {
        if (axios.isCancel(error)) {
            throw error;
        }

        if (error.response?.status === 400) {
            throw new Error(
                error.response.data?.message
                    || "Invalid indicator settings."
            );
        }

        if (error.code === "ECONNABORTED") {
            throw new Error(
                "Loading took too long. Please try again."
            );
        }

        if (!error.response) {
            throw new Error(
                "Could not reach the backend. Please try again."
            );
        }

        throw new Error(
            "Could not load indicator data. Please try again."
        );
    }
}

export const getSMA = (symbol, period, signal) =>
    fetchIndicator("dynamicSMA", symbol, period, signal);

export const getEMA = (symbol, period, signal) =>
    fetchIndicator("dynamicEMA", symbol, period, signal);

export const getRSI = (symbol, period, signal) =>
    fetchIndicator("dynamicRSI", symbol, period, signal);

export async function getArffExport(symbol, signal) {
    try {
        const response = await axios.get(
            `${baseUrl}/export/${encodeURIComponent(symbol)}`,
            {
                responseType: "blob",
                signal,
                timeout: 30000
            }
        );

        return response.data;
    } catch (error) {
        if (axios.isCancel(error)) {
            throw error;
        }

        const status = error.response?.status;

        if (status === 400) {
            throw new Error("This stock is not supported.");
        }

        if (status === 422) {
            throw new Error(
                "Export requires at least 34 stored daily prices for this stock."
            );
        }

        if (error.code === "ECONNABORTED") {
            throw new Error(
                "The export took too long. Please try again."
            );
        }

        if (!error.response) {
            throw new Error(
                "Could not reach the backend. Check the connection and try again."
            );
        }

        throw new Error(
            "Could not prepare the export. Please try again."
        );
    }
}

export async function getStocks(signal) {
    const response = await axios.get(`${baseUrl}/stocks`, {
        signal,
        timeout: 15000
    });

    return response.data;
}

export async function getImportBudget(signal) {
    const response = await axios.get(`${baseUrl}/import-budget`, {
        signal,
        timeout: 15000
    });

    return response.data;
}
