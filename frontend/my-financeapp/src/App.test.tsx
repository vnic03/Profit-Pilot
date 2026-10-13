import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";
import App from "./App";
import {getWinnersAndLosers} from "./service/financeService";

jest.mock("./service/financeService", () => ({
    getWinnersAndLosers: jest.fn()
}));

jest.mock("./service/FinanceData", () => ({
    __esModule: true,
    default: ({refreshVersion}: {refreshVersion: number}) => (
        <div data-testid="price-refresh">{refreshVersion}</div>
    )
}));

jest.mock("./components/DataStatus", () => ({
    __esModule: true,
    default: ({refreshVersion}: {refreshVersion: number}) => (
        <div data-testid="status-refresh">{refreshVersion}</div>
    )
}));

jest.mock("./components/news/News", () => ({
    __esModule: true,
    default: () => <div>News placeholder</div>
}));

const loadMovers = getWinnersAndLosers as jest.Mock;

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
