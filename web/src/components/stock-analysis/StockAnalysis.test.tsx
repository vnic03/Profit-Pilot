import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";
import {beforeEach, expect, test, vi, type Mock} from "vitest";
import StockAnalysis from "./StockAnalysis";
import {
    getStocks,
    getClosePrices,
    getHigh,
    getLow,
    getOpen,
    getVolume,
    getMacdSeries
} from "../../service/financeService";

vi.mock("../../service/financeService", () => ({
    getStocks: vi.fn(),
    getClosePrices: vi.fn(),
    getHigh: vi.fn(),
    getLow: vi.fn(),
    getOpen: vi.fn(),
    getVolume: vi.fn(),
    getMacdSeries: vi.fn()
}));

vi.mock("../charts/stock-chart/StockChart", () => ({
    default: ({symbol}: {symbol: string}) => (
        <div>Chart for {symbol}</div>
    )
}));

vi.mock("../ExportButton", () => ({
    default: () => null
}));

const loadStocks = getStocks as Mock;
const loadPrices = getClosePrices as Mock;

const testStock = {
    symbol: "DEMO",
    name: "Demo Company",
    exchange: "Test Exchange",
    currency: "USD"
};

beforeEach(() => {
    vi.resetAllMocks();

    for (const loader of [
        getClosePrices,
        getHigh,
        getLow,
        getOpen,
        getVolume
    ]) {
        (loader as Mock).mockResolvedValue({});
    }

    (getMacdSeries as Mock).mockResolvedValue({
        macd: {},
        signal: {},
        histogram: {}
    });
});

test("does not request prices while the catalog is loading", () => {
    loadStocks.mockImplementation(() => new Promise(() => {}));

    render(<StockAnalysis refreshVersion={0}/>);

    expect(
        screen.getByText("Loading available stocks...")
    ).toBeInTheDocument();

    expect(loadPrices).not.toHaveBeenCalled();
});

test("uses the symbol and company name returned by the backend", async () => {
    loadStocks.mockResolvedValue([testStock]);

    render(<StockAnalysis refreshVersion={0}/>);

    expect(
        await screen.findByRole("option", {
            name: "DEMO — Demo Company"
        })
    ).toBeInTheDocument();

    await screen.findByText("No stored prices available for DEMO.");

    expect(loadPrices).toHaveBeenCalledWith("DEMO");
    expect(screen.getByRole("combobox")).toHaveValue("DEMO");
});

test("retries catalog loading after a failure", async () => {
    loadStocks
        .mockRejectedValueOnce(new Error("Offline"))
        .mockResolvedValueOnce([testStock]);

    render(<StockAnalysis refreshVersion={0}/>);

    const retry = await screen.findByRole("button", {
        name: "Retry stock list"
    });

    expect(loadPrices).not.toHaveBeenCalled();

    fireEvent.click(retry);

    await screen.findByText("No stored prices available for DEMO.");

    expect(loadStocks).toHaveBeenCalledTimes(2);
    expect(loadPrices).toHaveBeenCalledWith("DEMO");
});

test("an empty catalog does not trigger price requests", async () => {
    loadStocks.mockResolvedValue([]);

    render(<StockAnalysis refreshVersion={0}/>);

    expect(
        await screen.findByText("No stocks are currently configured.")
    ).toBeInTheDocument();

    expect(loadPrices).not.toHaveBeenCalled();
});
