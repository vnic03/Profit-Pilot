package io.github.vnic03.profitpilot.api;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public abstract class ExternalApiClient {

    protected HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    protected final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${profitpilot.external-api.enabled:false}")
    private boolean externalApiEnabled;

    protected abstract boolean isProviderEnabled();

    public boolean isExternalApiEnabled() {
        return externalApiEnabled && isProviderEnabled();
    }

    protected HttpResponse<String> sendExternalRequest(HttpRequest request)
            throws IOException, InterruptedException {

        if (!isExternalApiEnabled()) {
            throw new IOException("External requests are disabled for this provider");
        }

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
