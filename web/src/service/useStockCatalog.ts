import { useCallback, useEffect, useState } from "react";
import { getStocks } from "./financeService";
import type { Stock } from "./apiTypes";

interface CatalogResult {
  key: string;
  stocks: Stock[];
  error: string | null;
}

const EMPTY_STOCKS: Stock[] = [];

export function useStockCatalog(refreshVersion: number) {
  const [result, setResult] = useState<CatalogResult | null>(null);
  const [retryVersion, setRetryVersion] = useState(0);

  const key = `${refreshVersion}:${retryVersion}`;

  const retry = useCallback(() => {
    setRetryVersion((value) => value + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    const load = async () => {
      try {
        const stocks: Stock[] = await getStocks(controller.signal);

        if (!controller.signal.aborted) {
          setResult({ key, stocks, error: null });
        }
      } catch {
        if (!controller.signal.aborted) {
          setResult({
            key,
            stocks: EMPTY_STOCKS,
            error: "Could not load the stock list. Please try again.",
          });
        }
      }
    };

    void load();

    return () => controller.abort();
  }, [key]);

  const current = result?.key === key ? result : null;

  return {
    stocks: current?.stocks ?? EMPTY_STOCKS,
    loading: current === null,
    error: current?.error ?? null,
    retry,
  };
}
