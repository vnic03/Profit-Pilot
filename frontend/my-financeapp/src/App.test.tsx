import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import {beforeEach, expect, test, vi, type Mock} from "vitest";
import App from "./App";
import {getWinnersAndLosers} from "./service/financeService";

vi.mock("./service/financeService", () => ({
    getWinnersAndLosers: vi.fn()
}));

vi.mock("./service/FinanceData", () => ({
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

test("reload refreshes prices, data status and market movers", async () => {
    render(<App/>);

    await screen.findByText(/No market movers available/);

    expect(screen.getByTestId("price-refresh")).toHaveTextContent("0");
    expect(screen.getByTestId("status-refresh")).toHaveTextContent("0");
    expect(loadMovers).toHaveBeenCalledTimes(1);

    fireEvent.click(
        screen.getByRole("button", {name: "Reload stored data"})
    );

    await waitFor(() => {
        expect(loadMovers).toHaveBeenCalledTimes(2);
    });

    await screen.findByText(/No market movers available/);

    expect(screen.getByTestId("price-refresh")).toHaveTextContent("1");
    expect(screen.getByTestId("status-refresh")).toHaveTextContent("1");
    expect(loadMovers).toHaveBeenLastCalledWith(6);
});
