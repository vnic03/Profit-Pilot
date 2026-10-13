import React from "react";
import {fireEvent, render, screen} from "@testing-library/react";
import FinanceData from "./FinanceData";
import {
    getStocks,
    getClosePrices,
    getHigh,
    getLow,
    getOpen,
    getVolume,
    getMacdSeries
} from "./financeService";

jest.mock("./financeService", () => ({
    getStocks: jest.fn(),
    getClosePrices: jest.fn(),
    getHigh: jest.fn(),
    getLow: jest.fn(),
    getOpen: jest.fn(),
    getVolume: jest.fn(),
    getMacdSeries: jest.fn()
}));

jest.mock("../components/charts/stock-chart/StockChart.tsx", () => ({
    __esModule: true,
    default: ({symbol}: {symbol: string}) => (
        <div>Chart for {symbol}</div>
    )
}));

jest.mock("../components/ExportButton", () => ({
    __esModule: true,
    default: () => null
}));

const loadStocks = getStocks as jest.Mock;
const loadPrices = getClosePrices as jest.Mock;

const testStock = {
    symbol: "DEMO",
    name: "Demo Company",
    exchange: "Test Exchange",
    currency: "USD"
};

beforeEach(() => {
    jest.resetAllMocks();

    for (const loader of [
        getClosePrices,
        getHigh,
        getLow,
        getOpen,
        getVolume
    ]) {
        (loader as jest.Mock).mockResolvedValue({});
    }

    (getMacdSeries as jest.Mock).mockResolvedValue({
        macd: {},
        signal: {},
        histogram: {}
    });
});

test("does not request prices while the catalog is loading", () => {
    loadStocks.mockImplementation(() => new Promise(() => {}));

    render(<FinanceData refreshVersion={0}/>);

    expect(
        screen.getByText("Loading available stocks...")
    ).toBeInTheDocument();

    expect(loadPrices).not.toHaveBeenCalled();
});

test("uses the symbol and company name returned by the backend", async () => {
    loadStocks.mockResolvedValue([testStock]);

    render(<FinanceData refreshVersion={0}/>);

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

    render(<FinanceData refreshVersion={0}/>);

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

    render(<FinanceData refreshVersion={0}/>);

    expect(
        await screen.findByText("No stocks are currently configured.")
    ).toBeInTheDocument();

    expect(loadPrices).not.toHaveBeenCalled();
});
