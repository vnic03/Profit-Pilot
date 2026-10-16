package io.github.vnic03.profitpilot.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CacheBaseTest {

    @ParameterizedTest
    @CsvSource({
            "false, false",
            "false, true",
            "true, false",
            "true, true"
    })
    void requiresBothPermissionsBeforeSending(
            boolean globalEnabled,
            boolean providerEnabled) throws Exception {

        CacheBase<?> cache = mock(CacheBase.class, CALLS_REAL_METHODS);
        HttpClient httpClient = mock(HttpClient.class);

        ReflectionTestUtils.setField(
                cache, "externalApiEnabled", globalEnabled
        );
        ReflectionTestUtils.setField(cache, "httpClient", httpClient);

        when(cache.isProviderEnabled()).thenReturn(providerEnabled);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://example.invalid/test"))
                .build();

        if (globalEnabled && providerEnabled) {
            assertTrue(cache.isExternalApiEnabled());

            cache.sendExternalRequest(request);

            verify(httpClient).send(eq(request), any());
        } else {
            assertFalse(cache.isExternalApiEnabled());

            assertThrows(
                    IOException.class,
                    () -> cache.sendExternalRequest(request)
            );

            verifyNoInteractions(httpClient);
        }
    }
}
