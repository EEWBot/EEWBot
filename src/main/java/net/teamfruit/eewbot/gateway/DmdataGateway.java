package net.teamfruit.eewbot.gateway;

import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.DmdataEEW;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataContract;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataError;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataSocketList;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataSocketStart;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public abstract class DmdataGateway implements Gateway<DmdataEEW> {

    public static final String API_BASE = "https://api.dmdata.jp/v2";

    private final HttpRequest.Builder requestBuilder;
    private final String appName;

    public DmdataGateway(String apiKey, String origin, String appName) {
        this.requestBuilder = HttpRequest.newBuilder()
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                .header("Origin", origin).header("Content-Type", "application/json")
                .header("User-Agent", "eewbot");
        this.appName = appName;
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-dmdata-thread");

            DmdataContract contract = contract();
            Log.logger.info(contract.toString());

            DmdataSocketList socketList = openSocketList();
            Log.logger.info(socketList.toString());

            socketList.getItems().forEach(item -> {
                try {
                    socketClose(String.valueOf(item.getId()));
                } catch (IOException | InterruptedException e) {
                    onError(new EEWGatewayException("Failed to close DMDATA socket", e));
                }
            });

            DmdataSocketStart.Response socketStart = socketStart(new DmdataSocketStart.Request.Builder()
                    .setAppName(this.appName)
                    .setClassifications(Collections.singletonList("eew.forecast"))
                    .setTypes(List.of("VXSE42", "VXSE43", "VXSE45"))
                    .setTest("including")
                    .setFormatMode("json")
                    .build());
            Log.logger.info(socketStart.toString());
        } catch (DmdataGatewayException e) {
            Log.logger.error(e.getDmdataError().toString());
            onError(e);
        } catch (final Exception e) {
            onError(new EEWGatewayException("Failed to connect to DMDATA", e));
        }
    }

    private DmdataContract contract() throws IOException, InterruptedException {
        HttpRequest request = this.requestBuilder.copy().GET().uri(URI.create(API_BASE + "/contract")).build();
        HttpResponse<String> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return EEWBot.GSON.fromJson(response.body(), DmdataContract.class);
        } else {
            throw new DmdataGatewayException(EEWBot.GSON.fromJson(response.body(), DmdataError.class));
        }
    }

    private DmdataSocketList openSocketList() throws IOException, InterruptedException {
        HttpRequest request = this.requestBuilder.copy().GET().uri(URI.create(API_BASE + "/socket?status=open")).build();
        HttpResponse<String> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return EEWBot.GSON.fromJson(response.body(), DmdataSocketList.class);
        } else {
            throw new DmdataGatewayException(EEWBot.GSON.fromJson(response.body(), DmdataError.class));
        }
    }

    private DmdataSocketStart.Response socketStart(DmdataSocketStart.Request body) throws IOException, InterruptedException {
        HttpRequest request = this.requestBuilder.copy().POST(HttpRequest.BodyPublishers.ofString(EEWBot.GSON.toJson(body))).uri(URI.create(API_BASE + "/socket")).build();
        HttpResponse<String> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return EEWBot.GSON.fromJson(response.body(), DmdataSocketStart.Response.class);
        } else {
            throw new DmdataGatewayException(EEWBot.GSON.fromJson(response.body(), DmdataError.class));
        }
    }

    private void socketClose(String socketId) throws IOException, InterruptedException {
        HttpRequest request = this.requestBuilder.copy().DELETE().uri(URI.create(API_BASE + "/socket/" + socketId)).build();
        HttpResponse<String> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new DmdataGatewayException(EEWBot.GSON.fromJson(response.body(), DmdataError.class));
        }
    }
}
