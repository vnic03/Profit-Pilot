import axios from "axios";
import { API_BASE_URL } from "./apiConfig";

const baseUrl = `${API_BASE_URL}/api/news`;

export async function getNews(symbol, signal) {
    const response = await axios.get(baseUrl, {
        params: {symbol},
        signal,
        timeout: 25000
    });

    return response.data;
}

export async function getNewsBudget(signal) {
    const response = await axios.get(`${baseUrl}/budget`, {
        signal,
        timeout: 15000
    });

    return response.data;
}
