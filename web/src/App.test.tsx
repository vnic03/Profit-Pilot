import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {beforeEach, expect, test, vi, type Mock} from "vitest";
import App from "./App";
import {getWinnersAndLosers} from "./service/financeService";

vi.mock("./service/financeService", () => ({
    getWinnersAndLosers: vi.fn()
}));

vi.mock("./components/stock-analysis/StockAnalysis", () => ({
    default: ({refreshVersion}: {refreshVersion: number}) => (
        <div data-testid="price-refresh">{refreshVersion}</div>
    )
}));

vi.mock("./components/DataStatus", () => ({
    default: ({refreshVersion}: {refreshVersion: number}) => (
        <div data-testid="status-refresh">{refreshVersion}</div>
    )
}));

vi.mock("./components/news/News", () => ({
    default: () => <div>News placeholder</div>
}));

const loadMovers = getWinnersAndLosers as Mock;

beforeEach(() => {
    loadMovers.mockReset();
    loadMovers.mockResolvedValue({winners: [], losers: []});
});

test("navigation preserves analysis and reload reaches each view", async () => {
    render(<App/>);

    expect(screen.getByTestId("price-refresh")).toHaveTextContent("0");
    expect(loadMovers).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", {name: "movers"}));

    await screen.findByText(/No market movers available/);

    fireEvent.click(
        screen.getByRole("button", {name: "Reload stored data"})
    );

    await waitFor(() => {
        expect(loadMovers).toHaveBeenCalledTimes(2);
    });

    expect(loadMovers).toHaveBeenLastCalledWith(6);
    expect(screen.getByTestId("price-refresh")).toHaveTextContent("1");

    fireEvent.click(screen.getByRole("button", {name: "data"}));

    expect(screen.getByTestId("status-refresh")).toHaveTextContent("1");

    fireEvent.click(screen.getByRole("button", {name: "analysis"}));

    expect(screen.getByTestId("price-refresh")).toBeVisible();
});
