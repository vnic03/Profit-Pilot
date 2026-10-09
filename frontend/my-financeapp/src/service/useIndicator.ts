import {useEffect, useState} from "react";

type Values = Record<string, number>;

type FetchIndicator = (
    symbol: string,
    period: number
) => Promise<Values>;

interface IndicatorResult {
    key: string;
    data: Values;
    error: string | null;
}

const EMPTY_VALUES: Values = {};

export function useIndicator(
    symbol: string,
    period: number,
    fetchIndicator: FetchIndicator
) {
    const [result, setResult] = useState<IndicatorResult | null>(null);
    const key = `${symbol}:${period}`;

    const validPeriod =
        Number.isInteger(period) && period >= 1 && period <= 50;

    useEffect(() => {
        if (!validPeriod) {
            return;
        }

        let cancelled = false;

        const timer = window.setTimeout(async () => {
            try {
                const data = await fetchIndicator(symbol, period);

                if (!cancelled) {
                    setResult({key, data, error: null});
                }
            } catch {
                if (!cancelled) {
                    setResult({
                        key,
                        data: EMPTY_VALUES,
                        error: "Could not load indicator data."
                    });
                }
            }
        }, 250);

        return () => {
            cancelled = true;
            window.clearTimeout(timer);
        };
    }, [symbol, period, key, validPeriod, fetchIndicator]);

    const currentResult = result?.key === key ? result : null;

    return {
        data: validPeriod
            ? currentResult?.data ?? EMPTY_VALUES
            : EMPTY_VALUES,
        loading: validPeriod && currentResult === null,
        error: validPeriod
            ? currentResult?.error ?? null
            : "Period must be a whole number between 1 and 50."
    };
}
