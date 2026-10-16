import {act, renderHook} from "@testing-library/react";
import {
    afterEach,
    beforeEach,
    describe,
    expect,
    test,
    vi
} from "vitest";
import {useIndicator} from "./useIndicator";

type Values = Record<string, number>;

type FetchIndicator = (
    symbol: string,
    period: number,
    signal?: AbortSignal
) => Promise<Values>;

function deferred<T>() {
    let resolve!: (value: T) => void;

    const promise = new Promise<T>(done => {
        resolve = done;
    });

    return {promise, resolve};
}

async function startRequest() {
    await act(async () => {
        await vi.advanceTimersByTimeAsync(250);
    });
}

describe("useIndicator", () => {
    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.clearAllTimers();
        vi.useRealTimers();
    });

    test("invalid period does not trigger a request", async () => {
        const fetcher = vi.fn<FetchIndicator>().mockResolvedValue({});

        const {result} = renderHook(() =>
            useIndicator("IBM", 0, fetcher)
        );

        await startRequest();

        expect(fetcher).not.toHaveBeenCalled();
        expect(result.current.loading).toBe(false);
        expect(result.current.canRetry).toBe(false);
        expect(result.current.error).toBe(
            "Period must be a whole number between 1 and 50."
        );
    });

    test("only the latest slider value is requested", async () => {
        const fetcher = vi.fn<FetchIndicator>().mockResolvedValue({});

        const {rerender} = renderHook(
            ({period}) => useIndicator("IBM", period, fetcher),
            {initialProps: {period: 14}}
        );

        rerender({period: 20});
        rerender({period: 30});

        await startRequest();

        expect(fetcher).toHaveBeenCalledTimes(1);
        expect(fetcher).toHaveBeenCalledWith(
            "IBM",
            30,
            expect.any(AbortSignal)
        );
    });

    test("aborts old requests and ignores their late responses", async () => {
        const oldRequest = deferred<Values>();
        const newRequest = deferred<Values>();

        const fetcher = vi.fn<FetchIndicator>()
            .mockReturnValueOnce(oldRequest.promise)
            .mockReturnValueOnce(newRequest.promise);

        const {result, rerender} = renderHook(
            ({period}) => useIndicator("IBM", period, fetcher),
            {initialProps: {period: 14}}
        );

        await startRequest();

        const oldSignal = fetcher.mock.calls[0][2] as AbortSignal;

        rerender({period: 20});

        expect(oldSignal.aborted).toBe(true);
        expect(result.current.data).toEqual({});

        await startRequest();

        await act(async () => {
            newRequest.resolve({"2026-10-05": 200});
        });

        await act(async () => {
            oldRequest.resolve({"2026-10-05": 100});
        });

        expect(result.current.data).toEqual({"2026-10-05": 200});
        expect(result.current.loading).toBe(false);
    });

    test("retries a failed request with the same settings", async () => {
        const fetcher = vi.fn<FetchIndicator>()
            .mockRejectedValueOnce(new Error("Backend unavailable."))
            .mockResolvedValueOnce({"2026-10-05": 42});

        const {result} = renderHook(() =>
            useIndicator("IBM", 14, fetcher)
        );

        await startRequest();

        expect(result.current.error).toBe("Backend unavailable.");
        expect(result.current.canRetry).toBe(true);

        act(() => {
            result.current.retry();
        });

        expect(result.current.loading).toBe(true);
        expect(result.current.error).toBeNull();

        await startRequest();

        expect(fetcher).toHaveBeenCalledTimes(2);
        expect(result.current.data).toEqual({"2026-10-05": 42});
        expect(result.current.canRetry).toBe(false);
    });

    test("reports insufficient data after a successful empty response", async () => {
        const fetcher = vi.fn<FetchIndicator>().mockResolvedValue({});

        const {result} = renderHook(() =>
            useIndicator("IBM", 50, fetcher)
        );

        await startRequest();

        expect(result.current.empty).toBe(true);
        expect(result.current.error).toBeNull();
        expect(result.current.loading).toBe(false);
    });
});
