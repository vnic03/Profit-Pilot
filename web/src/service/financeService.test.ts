import axios from "axios";
import { beforeEach, expect, test, vi } from "vitest";
import { getChartData, getDataStatus, getWinnersAndLosers } from "./financeService";

vi.mock("axios", () => ({ default: { get: vi.fn() } }));

beforeEach(() => vi.resetAllMocks());

test("chart requests forward the abort signal and return the response", async () => {
  const controller = new AbortController();
  const data = { closePrices: { "2026-10-05": 42 } };
  vi.mocked(axios.get).mockResolvedValue({ data });

  await expect(getChartData("IBM", controller.signal)).resolves.toEqual(data);
  expect(axios.get).toHaveBeenCalledWith(expect.stringMatching(/\/api\/finance\/chart\/IBM$/), {
    signal: controller.signal,
    timeout: 15000,
  });
});

test("status and movers work without an abort signal", async () => {
  vi.mocked(axios.get).mockResolvedValue({ data: [] });
  await expect(getDataStatus()).resolves.toEqual([]);
  await expect(getWinnersAndLosers(6)).resolves.toEqual([]);
  expect(axios.get).toHaveBeenCalledTimes(2);
});
