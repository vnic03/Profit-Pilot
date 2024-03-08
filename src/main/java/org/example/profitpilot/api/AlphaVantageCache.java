package org.example.profitpilot.api;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.cdimascio.dotenv.Dotenv;
import org.json.JSONObject;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AlphaVantageCache {

    private final Dotenv dotenv = Dotenv.load();
    private final String apiKey = dotenv.get("ALPHA_VANTAGE_API_KEY");
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final LoadingCache<String, ApiResponse> cache;
    private final Logger logger = LoggerFactory.getLogger(AlphaVantageCache.class);

    public AlphaVantageCache() {
        cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.DAYS)
                .build(new CacheLoader<>() {
                    @Override
                    public ApiResponse load(@NonNull String key) {
                        return fetchDataFromAlphaVantage(key);
                    }
                });
    }

    public ApiResponse getData(String key) {
        return cache.getUnchecked(key);
    }

    private ApiResponse fetchDataFromAlphaVantage(String symbol) {
        final String uri = String.format(
                "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                symbol, apiKey
        );
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(uri)).build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

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
            companyInfo.put("fullName", jsonObject.getString("Name"));
            companyInfo.put("industry", jsonObject.getString("Industry"));

            return companyInfo;
        } catch (IOException e) {
            logger.error("IOException occurred while fetching OVERVIEW data", e);
        } catch (InterruptedException e) {
            logger.error("Interrupted Exception occurred while OVERVIEW data", e);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    public Map<String,List<StockChange>> calculateWinnersAndLosers(String symbol, int amount)
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

            StockChange change = new StockChange(symbol, percentChange, LocalDate.parse(dates[i]));
            if (percentChange > 0) {
                winners.add(change);
            } else {
                losers.add(change);
            }
        }
        winners.sort((o1, o2) -> Double.compare(o2.getPercentageRate(), o1.getPercentageRate()));
        losers.sort((o1, o2) -> Double.compare(o2.getPercentageRate(), o1.getPercentageRate()));

        List<StockChange> winnersWithInfo = winners.stream()
                .map(winner -> createStockChangeWithInfo(winner, companyInfo)).toList();

        List<StockChange> losersWithInfo = losers.stream()
                .map(loser -> createStockChangeWithInfo(loser, companyInfo)).toList();

        Map<String, List<StockChange>> result = new HashMap<>();

        result.put("winners", winnersWithInfo.size() > amount ? winnersWithInfo.subList(0, amount) : winnersWithInfo);
        result.put("losers", losersWithInfo.size() > amount ? losersWithInfo.subList(0, amount) : losersWithInfo);

        return result;
    }

    private StockChange createStockChangeWithInfo(StockChange stockChange, Map<String, String> companyInfo) {
        String name = companyInfo != null ? companyInfo.getOrDefault("fullName", "Unknown Company") : "Unknown Company";
        String industry = companyInfo != null ? companyInfo.getOrDefault("industry", "Unknown Industry") : "Unknown Industry";

        return new StockChange(stockChange.getSymbol(), stockChange.getPercentageRate(), stockChange.getDate(), name, industry);
    }
}
