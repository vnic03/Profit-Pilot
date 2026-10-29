import axios from "axios";
import { API_BASE_URL } from "./apiConfig";
import type {
  Budget,
  FinancialSymbolData,
  IndicatorValues,
  Stock,
  StatusRow,
  WinnersAndLosers,
} from "./apiTypes";

const baseUrl = `${API_BASE_URL}/api/finance`;

async function fetchFromApi<T>(endpoint: string, signal?: AbortSignal): Promise<T> {
  const response = await axios.get<T>(`${baseUrl}/${endpoint}`, {
    signal,
    timeout: 15000,
  });

  return response.data;
}

export const getChartData = (symbol: string, signal?: AbortSignal) =>
  fetchFromApi<FinancialSymbolData>(`chart/${encodeURIComponent(symbol)}`, signal);

export const getWinnersAndLosers = (amount: number, signal?: AbortSignal) =>
  fetchFromApi<WinnersAndLosers>(`winnersAndLosers/${amount}`, signal);

export const getDataStatus = (signal?: AbortSignal) =>
  fetchFromApi<StatusRow[]>("data-status", signal);

export const getStocks = (signal?: AbortSignal) => fetchFromApi<Stock[]>("stocks", signal);

export const getImportBudget = (signal?: AbortSignal) =>
  fetchFromApi<Budget>("import-budget", signal);

async function fetchIndicator(
  indicator: "dynamicSMA" | "dynamicEMA" | "dynamicRSI",
  symbol: string,
  period: number,
  signal?: AbortSignal,
): Promise<IndicatorValues> {
  try {
    return await fetchFromApi<IndicatorValues>(
      `${indicator}/${encodeURIComponent(symbol)}/${period}`,
      signal,
    );
  } catch (error) {
    if (axios.isCancel(error) || !axios.isAxiosError<{ message?: string }>(error)) {
      throw error;
    }

    if (error.response?.status === 400) {
      throw new Error(error.response.data?.message || "Invalid indicator settings.");
    }

    if (error.code === "ECONNABORTED") {
      throw new Error("Loading took too long. Please try again.");
    }

    if (!error.response) {
      throw new Error("Could not reach the backend. Please try again.");
    }

    throw new Error("Could not load indicator data. Please try again.");
  }
}

export const getSMA = (symbol: string, period: number, signal?: AbortSignal) =>
  fetchIndicator("dynamicSMA", symbol, period, signal);

export const getEMA = (symbol: string, period: number, signal?: AbortSignal) =>
  fetchIndicator("dynamicEMA", symbol, period, signal);

export const getRSI = (symbol: string, period: number, signal?: AbortSignal) =>
  fetchIndicator("dynamicRSI", symbol, period, signal);

export async function getArffExport(symbol: string, signal?: AbortSignal): Promise<Blob> {
  try {
    const response = await axios.get<Blob>(`${baseUrl}/export/${encodeURIComponent(symbol)}`, {
      responseType: "blob",
      signal,
      timeout: 30000,
    });

    return response.data;
  } catch (error) {
    if (axios.isCancel(error) || !axios.isAxiosError(error)) {
      throw error;
    }

    const status = error.response?.status;

    if (status === 400) {
      throw new Error("This stock is not supported.");
    }

    if (status === 422) {
      throw new Error("Export requires at least 34 stored daily prices for this stock.");
    }

    if (error.code === "ECONNABORTED") {
      throw new Error("The export took too long. Please try again.");
    }

    if (!error.response) {
      throw new Error("Could not reach the backend. Check the connection and try again.");
    }

    throw new Error("Could not prepare the export. Please try again.");
  }
}
