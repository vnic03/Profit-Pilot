package org.example.profitpilot.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.cdimascio.dotenv.Dotenv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.concurrent.TimeUnit;

public abstract class CacheBase<T> {

    protected HttpClient httpClient = HttpClient.newHttpClient();
    protected Dotenv dotenv = Dotenv.load();
    protected Cache<String, T> cache;
    protected String apiKey;
    protected Logger logger = LoggerFactory.getLogger(CacheBase.class);

    protected CacheBase() {
        cache = CacheBuilder.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(1, TimeUnit.DAYS)
                .build();
    }

    protected abstract T fetchData(String key);

    public T getData(String key) {
        T response = null;
        try {
            response = cache.get(key, () -> fetchData(key));

        } catch (Exception e) {
            logger.info("Error getting data from cache for key: " + key, e);
        }
        return response;
    }
}
