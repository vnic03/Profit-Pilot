package org.example.profitpilot.api.alpha_vantage;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.util.concurrent.*;
import org.example.profitpilot.database.DatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ImportOutageTest {
    /** Route through the real HTTP client, with a short timeout to keep tests fast. */
    static class LocalCache extends AlphaVantageCache {
        URI endpoint;
        @Override
        protected HttpResponse<String> sendExternalRequest(HttpRequest request)
                throws IOException, InterruptedException {
            assertEquals(Duration.ofSeconds(30), request.timeout().orElseThrow());
            return super.sendExternalRequest(HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(200)).build());
        }
    }

    @Test
    void hangingProviderTimesOutAndLaterImportRecovers() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch release = new CountDownLatch(1);
        server.setExecutor(executor);
        server.createContext("/hang", exchange -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.createContext("/ok", exchange -> {
            byte[] body = """
                    {"Time Series (Daily)":{"2026-10-13":{
                    "1. open":"100","2. high":"110","3. low":"95",
                    "4. close":"105","5. volume":"1000"}}}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            var cache = new LocalCache();
            ReflectionTestUtils.setField(cache, "externalApiEnabled", true);
            ReflectionTestUtils.setField(cache, "providerEnabled", true);
            var database = mock(DatabaseService.class);
            when(database.tryStartDailySync("IBM")).thenReturn(true);
            var client = new ApiClient(database, cache, Clock.fixed(
                    Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC));
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            cache.endpoint = URI.create(base + "/hang");
            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThrows(IllegalStateException.class, () -> client.syncDailyPrices("IBM")));
            verify(database).markDailySyncFailed("IBM");
            verify(database, never()).insertSharePrice(anyList());
            release.countDown();
            cache.endpoint = URI.create(base + "/ok");
            assertEquals(ApiClient.SyncResult.IMPORTED, client.syncDailyPrices("IBM"));
            verify(database).markDailySyncSuccessful("IBM");
            verify(database, times(2)).tryStartDailySync("IBM");
            verify(database, times(1)).insertSharePrice(anyList());
            server.stop(0);
            assertThrows(IllegalStateException.class, () -> client.syncDailyPrices("IBM"));
            verify(database, times(2)).markDailySyncFailed("IBM");
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
