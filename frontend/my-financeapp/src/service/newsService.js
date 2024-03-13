import axios from "axios";

const baseUrl = 'http://localhost:8080/api/news';

async function fetchFromApi(query) {
    try {
        const response = await axios.get(baseUrl, { params: { query } });
        return response.data;
    } catch (error) {
        console.error("Error fetching data from API:", error);
        throw error;
    }
}

export const getNews = (query) => fetchFromApi(query);