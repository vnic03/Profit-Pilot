package org.example.profitpilot.api.alpha_vantage;

import org.example.profitpilot.AppStartUpRunner;
import org.example.profitpilot.api.CacheBase;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;

@Component
public class AlphaVantageCache extends CacheBase<ApiResponse> {

    public AlphaVantageCache() {
        super();
        this.apiKey = dotenv.get("ALPHA_VANTAGE_API_KEY");
    }

    @Override
    protected ApiResponse fetchData(String symbol) {
        final String uri = String.format(
                "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                symbol, apiKey
        );
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("API Response: {}", response.body());

            if (response.statusCode() != 200) {
                logger.error("Request error: HTTP status code {}", response.statusCode());
                return null;
            }

            String responseBody = response.body();
            JSONObject jsonObject = new JSONObject(responseBody);

            if (!jsonObject.has("Time Series (Daily)")) {
                logger.error("Response does not have 'Time Series (Daily)' field.");
                return null;
            }

            JSONObject timeSeries = jsonObject.getJSONObject("Time Series (Daily)");
            Map<String, ApiResponse.DailyData> timeSeriesMap = new HashMap<>();

            for (String date : timeSeries.keySet()) {
                JSONObject dailyDataJson = timeSeries.getJSONObject(date);

                double open = dailyDataJson.getDouble("1. open");
                double high = dailyDataJson.getDouble("2. high");
                double low = dailyDataJson.getDouble("3. low");
                double close = dailyDataJson.getDouble("4. close");
                long volume = dailyDataJson.getLong("5. volume");

                ApiResponse.DailyData dailyData = new ApiResponse.DailyData(open, high, low, close, volume);
                timeSeriesMap.put(date, dailyData);
            }
            return new ApiResponse(timeSeriesMap);

        } catch (IOException e) {
            logger.error("IOException occurred while fetching daily time series", e);
        } catch (InterruptedException e) {
            logger.error("Interrupted Exception occurred while fetching daily time series", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("An unexpected error occurred while fetching daily time series", e);
        }
        return null;
    }

    private Map<String, String> fetchCompanyInfo(String symbol) {
        final String uri = String.format(
                "https://www.alphavantage.co/query?function=OVERVIEW&symbol=%s&apikey=%s", symbol, apiKey
        );
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject jsonObject = new JSONObject(response.body());
            Map<String, String> companyInfo = new HashMap<>();
            companyInfo.put("fullName", jsonObject.optString("Name", "Unknown Company"));
            companyInfo.put("industry", jsonObject.optString("Industry", "Unknown Industry"));

            return companyInfo;
        } catch (IOException e) {
            logger.error("IOException occurred while fetching OVERVIEW data", e);
        } catch (InterruptedException e) {
            logger.error("Interrupted Exception occurred while OVERVIEW data", e);
            Thread.currentThread().interrupt();
        }
        return new HashMap<>();
    }

    public Map<String, List<StockChange>> winnersAndLooser(int amount) {
        List<StockChange> winners = new ArrayList<>();
        List<StockChange> losers = new ArrayList<>();

        for (String symbol : AppStartUpRunner.SYMBOLS) {
            Map<String, List<StockChange>> symbolResults = calculateWinnersAndLosers(symbol, amount);

            winners.addAll(symbolResults.get("winners"));
            losers.addAll(symbolResults.get("losers"));
        }

        Map<String, List<StockChange>> results = new HashMap<>();

        results.put("winners", sortAndLimit(winners, amount, false));
        results.put("losers", sortAndLimit(losers, amount, true));

        return results;
    }

    private Map<String,List<StockChange>> calculateWinnersAndLosers(String symbol, int amount)
    {
        ApiResponse response = getData(symbol);
        if (response == null) {
            return Collections.emptyMap();
        }

        Map<String, String> companyInfo = fetchCompanyInfo(symbol);

        List<StockChange> winners = new ArrayList<>();
        List<StockChange> losers = new ArrayList<>();

        Map<String, ApiResponse.DailyData> timeSeries = response.timeSeries();

        String[] dates = timeSeries.keySet().toArray(new String[0]);
        Arrays.sort(dates);

        for (int i = 1; i < dates.length; i++) {
            double previousClose = timeSeries.get(dates[i - 1]).close();
            double currentClose = timeSeries.get(dates[i]).close();
            double percentChange = (currentClose - previousClose) / previousClose * 100;

            StockChange change =
                    createStockChangeWithInfo(new StockChange(symbol, percentChange, LocalDate.parse(dates[i])), companyInfo);

            if (percentChange > 0) {
                winners.add(change);
            } else {
                losers.add(change);
            }
        }
        Map<String, List<StockChange>> result = new HashMap<>();

        result.put("winners", sortAndLimit(winners, amount, false));
        result.put("losers", sortAndLimit(losers, amount, true));

        return result;
    }

    private StockChange createStockChangeWithInfo(StockChange stockChange, Map<String, String> companyInfo) {
        String name = companyInfo != null ? companyInfo.getOrDefault("fullName", "Unknown Company") : "Unknown Company";
        String industry = companyInfo != null ? companyInfo.getOrDefault("industry", "Unknown Industry") : "Unknown Industry";

        return new StockChange(stockChange.getSymbol(), stockChange.getPercentageRate(), stockChange.getDate(), name, industry);
    }

    private List<StockChange> sortAndLimit(List<StockChange> changes, int amount, boolean ascending) {
        for (StockChange change : changes) {
            System.out.println("Symbol: " + change.getSymbol() + ", Change: " + change.getPercentageRate() + ", Date: " + change.getDate());
        }
        if (ascending) {
            changes.sort(Comparator.comparingDouble(StockChange::getPercentageRate));
        } else {
            changes.sort((o1, o2) -> Double.compare(o2.getPercentageRate(), o1.getPercentageRate()));
        }
        return changes.size() > amount ? changes.subList(0, amount) : changes;
    }
}
