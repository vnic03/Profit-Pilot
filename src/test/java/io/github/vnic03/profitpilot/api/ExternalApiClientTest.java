package io.github.vnic03.profitpilot.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class ExternalApiClientTest {

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void requiresBothPermissionsBeforeSending(boolean globalEnabled, boolean providerEnabled)
            throws Exception {

        ExternalApiClient cache = mock(ExternalApiClient.class, CALLS_REAL_METHODS);
        HttpClient httpClient = mock(HttpClient.class);

        ReflectionTestUtils.setField(cache, "externalApiEnabled", globalEnabled);
        ReflectionTestUtils.setField(cache, "httpClient", httpClient);

        when(cache.isProviderEnabled()).thenReturn(providerEnabled);

        HttpRequest request =
                HttpRequest.newBuilder().uri(URI.create("https://example.invalid/test")).build();

        if (globalEnabled && providerEnabled) {
            assertTrue(cache.isExternalApiEnabled());

            cache.sendExternalRequest(request);

            verify(httpClient).send(eq(request), any());
        } else {
            assertFalse(cache.isExternalApiEnabled());

            assertThrows(IOException.class, () -> cache.sendExternalRequest(request));

            verifyNoInteractions(httpClient);
        }
    }
}
