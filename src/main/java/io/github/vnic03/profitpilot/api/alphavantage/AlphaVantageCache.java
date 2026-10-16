package io.github.vnic03.profitpilot.api.alphavantage;

import io.github.vnic03.profitpilot.api.CacheBase;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.time.Duration;


@Component
public class AlphaVantageCache extends CacheBase<ApiResponse> {

    @Value("${profitpilot.alpha-vantage.enabled:false}")
    private boolean providerEnabled;

    @Override
    protected boolean isProviderEnabled() {
        return providerEnabled;
    }

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
        HttpRequest request =
            HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds(30))
            .build();

        try {
            HttpResponse<String> response = sendExternalRequest(request);
            logger.info(
                    "Received daily price response for {} with HTTP status {}.",
                    symbol,
                    response.statusCode()
            );

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
}
