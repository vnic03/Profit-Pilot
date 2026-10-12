import axios from "axios";

const baseUrl = "http://localhost:8080/api/news";

export async function getNews(symbol, signal) {
    const response = await axios.get(baseUrl, {
        params: {symbol},
        signal,
        timeout: 25000
    });

    return response.data;
}
