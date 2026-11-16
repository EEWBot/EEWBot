package net.teamfruit.eewbot.gateway;

import com.google.gson.JsonParseException;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataContract;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataError;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataSocketList;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataSocketStart;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public class DmdataAPI {

    public static final String API_BASE = "https://api.dmdata.jp/v2";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final HttpRequest.Builder requestBuilder;

    public DmdataAPI(HttpClient httpClient, String apiKey, String origin) {
        this.httpClient = httpClient;
        this.requestBuilder = HttpRequest.newBuilder()
                // Without a deadline a stalled call would pin a reconnect thread indefinitely.
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                .header("Origin", origin)
                .header("Content-Type", "application/json")
                .header("User-Agent", "eewbot");
    }

    public DmdataContract contract() throws IOException, InterruptedException, DmdataGatewayException {
        HttpRequest request = this.requestBuilder.copy().GET().uri(URI.create(API_BASE + "/contract")).build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return parse(response, DmdataContract.class);
        } else {
            throw new DmdataGatewayException(parse(response, DmdataError.class));
        }
    }

    public DmdataSocketList openSocketList() throws IOException, InterruptedException, DmdataGatewayException {
        HttpRequest request = this.requestBuilder.copy().GET().uri(URI.create(API_BASE + "/socket?status=open")).build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return parse(response, DmdataSocketList.class);
        } else {
            throw new DmdataGatewayException(parse(response, DmdataError.class));
        }
    }

    public DmdataSocketStart.Response socketStart(DmdataSocketStart.Request body) throws IOException, InterruptedException, DmdataGatewayException {
        HttpRequest request = this.requestBuilder.copy().POST(HttpRequest.BodyPublishers.ofString(Codecs.GSON.toJson(body))).uri(URI.create(API_BASE + "/socket")).build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return parse(response, DmdataSocketStart.Response.class);
        } else {
            throw new DmdataGatewayException(parse(response, DmdataError.class));
        }
    }

    public DmdataError socketClose(String socketId) throws IOException, InterruptedException, DmdataGatewayException {
        HttpRequest request = this.requestBuilder.copy().DELETE().uri(URI.create(API_BASE + "/socket/" + socketId)).build();
        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return parse(response, DmdataError.class);
        }
        return null;
    }

    // Proxies and outages answer with empty or non-JSON bodies. Surface those as a checked
    // gateway failure so callers' retry paths handle them instead of an unchecked parse error.
    private static <T> T parse(HttpResponse<String> response, Class<T> type) throws DmdataGatewayException {
        T parsed;
        try {
            parsed = Codecs.GSON.fromJson(response.body(), type);
        } catch (JsonParseException e) {
            throw new DmdataGatewayException("DMDATA API returned an unparseable response: HTTP " + response.statusCode(), e);
        }
        if (parsed == null)
            throw new DmdataGatewayException("DMDATA API returned an empty response: HTTP " + response.statusCode(), null);
        return parsed;
    }

}
