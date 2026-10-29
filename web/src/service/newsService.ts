import axios from "axios";
import { API_BASE_URL } from "./apiConfig";
import type { Budget, NewsResponse } from "./apiTypes";

const baseUrl = `${API_BASE_URL}/api/news`;

export async function getNews(symbol: string, signal?: AbortSignal): Promise<NewsResponse> {
  const response = await axios.get<NewsResponse>(baseUrl, {
    params: { symbol },
    signal,
    timeout: 25000,
  });

  return response.data;
}

export async function getNewsBudget(signal?: AbortSignal): Promise<Budget> {
  const response = await axios.get<Budget>(`${baseUrl}/budget`, {
    signal,
    timeout: 15000,
  });

  return response.data;
}
