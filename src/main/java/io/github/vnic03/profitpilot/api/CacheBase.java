package io.github.vnic03.profitpilot.api;

import org.springframework.beans.factory.annotation.Value;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.cdimascio.dotenv.Dotenv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.util.concurrent.TimeUnit;
import java.time.Duration;


public abstract class CacheBase<T> {

    protected HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    protected Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    protected Cache<String, T> cache;
    protected String apiKey;
    protected Logger logger = LoggerFactory.getLogger(CacheBase.class);

    @Value("${profitpilot.external-api.enabled:false}")
    private boolean externalApiEnabled;

    protected CacheBase() {
        cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(1, TimeUnit.DAYS)
                .build();
    }

    protected abstract T fetchData(String key);

    protected abstract boolean isProviderEnabled();

    public T getData(String key) {
        T response = null;
        try {
            response = cache.get(key, () -> fetchData(key));

        } catch (Exception e) {
            logger.info("Error getting data from cache for key: " + key, e);
        }
        return response;
    }

    protected HttpResponse<String> sendExternalRequest(HttpRequest request)
          throws IOException, InterruptedException {

        // Enforce the switches at the network boundary, not just in callers.
        if (!isExternalApiEnabled()) {
          throw new IOException ("External requests are disabled for this provider");
        }

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public boolean isExternalApiEnabled() {
        return externalApiEnabled && isProviderEnabled();
    }
}
