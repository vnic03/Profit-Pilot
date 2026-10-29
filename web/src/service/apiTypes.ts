export type IndicatorValues = Record<string, number>;

export interface FinancialSymbolData {
  closePrices: Record<string, number>;
  high: Record<string, number>;
  low: Record<string, number>;
  open: Record<string, number>;
  volume: Record<string, number>;
  macd: Record<string, number>;
  macdSignal: Record<string, number>;
  macdHistogram: Record<string, number>;
}

export interface StockChange {
  symbol: string;
  name: string;
  percentageRate: number;
  date: string;
  industry?: string;
}

export interface WinnersAndLosers {
  winners: StockChange[];
  losers: StockChange[];
}

export interface Stock {
  symbol: string;
  name: string;
  exchange: string;
  currency: string;
}

export interface StatusRow {
  symbol: string;
  priceCount: number;
  latestPriceDate: string | null;
  lastAttemptAt: string | null;
  lastSuccessAt: string | null;
  freshness: "CURRENT" | "BEHIND_REFERENCE" | "MISSING" | "FUTURE_DATE" | "CALENDAR_UNAVAILABLE";
  referenceDate: string | null;
}

export interface Budget {
  provider: string;
  requestLimit: number;
  reservedRequests: number;
  remainingRequests: number;
  window: string;
}

export interface NewsArticle {
  title: string;
  publicationDate: string;
  description: string;
  url: string;
  imageUrl: string | null;
  source: string;
}

export interface NewsResponse {
  status: "disabled" | "ready";
  articles: NewsArticle[];
  fetchedAt: string | null;
}
