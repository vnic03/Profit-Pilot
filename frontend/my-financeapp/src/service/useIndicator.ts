import {useCallback, useEffect, useState} from "react";

type Values = Record<string, number>;

type FetchIndicator = (
    symbol: string,
    period: number,
    signal?: AbortSignal
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
    const [retryVersion, setRetryVersion] = useState(0);

    const key = `${symbol}:${period}:${retryVersion}`;
    const validPeriod =
        Number.isInteger(period) && period >= 1 && period <= 50;

    const retry = useCallback(() => {
        setRetryVersion(value => value + 1);
    }, []);

    useEffect(() => {
        if (!validPeriod) {
            return;
        }

        const controller = new AbortController();

        const timer = window.setTimeout(async () => {
            try {
                const data = await fetchIndicator(
                    symbol,
                    period,
                    controller.signal
                );

                if (!controller.signal.aborted) {
                    setResult({key, data, error: null});
                }
            } catch (cause: unknown) {
                if (!controller.signal.aborted) {
                    setResult({
                        key,
                        data: EMPTY_VALUES,
                        error: cause instanceof Error
                            ? cause.message
                            : "Could not load indicator data."
                    });
                }
            }
        }, 250);

        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [symbol, period, key, validPeriod, fetchIndicator]);

    const current = result?.key === key ? result : null;

    return {
        data: validPeriod
            ? current?.data ?? EMPTY_VALUES
            : EMPTY_VALUES,
        loading: validPeriod && current === null,
        error: validPeriod
            ? current?.error ?? null
            : "Period must be a whole number between 1 and 50.",
        canRetry: validPeriod && current?.error != null,
        empty: validPeriod
            && current !== null
            && current.error === null
            && Object.keys(current.data).length === 0,
        retry
    };
}
