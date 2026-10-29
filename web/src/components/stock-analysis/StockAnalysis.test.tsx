import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, test, vi, type Mock } from "vitest";
import StockAnalysis from "./StockAnalysis";
import { getStocks, getChartData } from "../../service/financeService";

vi.mock("../../service/financeService", () => ({
  getStocks: vi.fn(),
  getChartData: vi.fn(),
}));

vi.mock("../charts/stock-chart/StockChart", () => ({
  default: ({ symbol }: { symbol: string }) => <div>Chart for {symbol}</div>,
}));

vi.mock("../ExportButton", () => ({
  default: () => null,
}));

const loadStocks = getStocks as Mock;
const loadPrices = getChartData as Mock;

const testStock = {
  symbol: "DEMO",
  name: "Demo Company",
  exchange: "Test Exchange",
  currency: "USD",
};

beforeEach(() => {
  vi.resetAllMocks();

  loadPrices.mockResolvedValue({
    closePrices: {},
    high: {},
    low: {},
    open: {},
    volume: {},
    macd: {},
    macdSignal: {},
    macdHistogram: {},
  });
});

test("does not request prices while the catalog is loading", () => {
  loadStocks.mockImplementation(() => new Promise(() => {}));

  render(<StockAnalysis refreshVersion={0} />);

  expect(screen.getByText("Loading available stocks...")).toBeInTheDocument();

  expect(loadPrices).not.toHaveBeenCalled();
});

test("uses the symbol and company name returned by the backend", async () => {
  loadStocks.mockResolvedValue([testStock]);

  render(<StockAnalysis refreshVersion={0} />);

  expect(
    await screen.findByRole("button", {
      name: "DEMO — Demo Company",
    }),
  ).toBeInTheDocument();

  await screen.findByText("No stored prices available for DEMO.");

  expect(loadPrices).toHaveBeenCalledWith("DEMO", expect.any(AbortSignal));

  expect(
    screen.getByRole("button", {
      name: "DEMO — Demo Company",
    }),
  ).toHaveAttribute("aria-pressed", "true");
});

test("retries catalog loading after a failure", async () => {
  loadStocks.mockRejectedValueOnce(new Error("Offline")).mockResolvedValueOnce([testStock]);

  render(<StockAnalysis refreshVersion={0} />);

  const retry = await screen.findByRole("button", {
    name: "Retry stock list",
  });

  expect(loadPrices).not.toHaveBeenCalled();

  fireEvent.click(retry);

  await screen.findByText("No stored prices available for DEMO.");

  expect(loadStocks).toHaveBeenCalledTimes(2);
  expect(loadPrices).toHaveBeenCalledWith("DEMO", expect.any(AbortSignal));
});

test("an empty catalog does not trigger price requests", async () => {
  loadStocks.mockResolvedValue([]);

  render(<StockAnalysis refreshVersion={0} />);

  expect(await screen.findByText("No stocks are currently configured.")).toBeInTheDocument();

  expect(loadPrices).not.toHaveBeenCalled();
});

test("searches by company and selects a different symbol", async () => {
  loadStocks.mockResolvedValue([
    testStock,
    { ...testStock, symbol: "NEXT", name: "Second Company" },
  ]);

  render(<StockAnalysis refreshVersion={0} />);

  await screen.findByText("No stored prices available for DEMO.");

  fireEvent.change(screen.getByRole("searchbox"), {
    target: { value: "Second" },
  });

  expect(
    screen.queryByRole("button", {
      name: "DEMO — Demo Company",
    }),
  ).not.toBeInTheDocument();

  fireEvent.click(
    screen.getByRole("button", {
      name: "NEXT — Second Company",
    }),
  );

  await screen.findByText("No stored prices available for NEXT.");

  expect(loadPrices).toHaveBeenLastCalledWith("NEXT", expect.any(AbortSignal));
});

test("favorite filtering retains the active selection", async () => {
  localStorage.clear();
  loadStocks.mockResolvedValue([testStock]);

  render(<StockAnalysis refreshVersion={0} />);

  await screen.findByText("No stored prices available for DEMO.");

  fireEvent.click(screen.getByRole("button", { name: "Favorite DEMO" }));

  fireEvent.click(screen.getByRole("button", { name: "FAVORITES" }));

  expect(
    screen.getByRole("button", {
      name: "DEMO — Demo Company",
    }),
  ).toHaveAttribute("aria-pressed", "true");

  expect(JSON.parse(localStorage.getItem("profit-pilot:favorites:v1") ?? "[]")).toEqual(["DEMO"]);

  fireEvent.click(screen.getByRole("button", { name: "Favorite DEMO" }));

  expect(screen.getByText("No matching symbols.")).toBeInTheDocument();

  expect(screen.getByRole("heading", { name: "DEMO" })).toBeInTheDocument();
});

test("aborts a pending chart request when unmounted", async () => {
  loadStocks.mockResolvedValue([testStock]);
  loadPrices.mockImplementation(() => new Promise(() => {}));
  const { unmount } = render(<StockAnalysis refreshVersion={0} />);
  await screen.findByText("Loading stored prices for DEMO...");
  const signal = loadPrices.mock.calls[0][1] as AbortSignal;
  expect(signal.aborted).toBe(false);
  unmount();
  expect(signal.aborted).toBe(true);
});
