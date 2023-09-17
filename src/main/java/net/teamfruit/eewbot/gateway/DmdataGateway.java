package net.teamfruit.eewbot.gateway;

import com.google.gson.JsonSyntaxException;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.DmdataEEW;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataContract;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataError;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataSocketList;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataSocketStart;
import net.teamfruit.eewbot.entity.dmdataapi.ws.DmdataWSMessage;
import net.teamfruit.eewbot.entity.dmdataapi.ws.DmdataWSPing;
import net.teamfruit.eewbot.entity.dmdataapi.ws.DmdataWSPong;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.CompletionStage;

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
            Log.logger.debug(contract.toString());

            boolean hasForecastContract = contract.getItems().stream().anyMatch(item -> item.getClassification().equals("eew.forecast"));
            boolean hasWarningContract = contract.getItems().stream().anyMatch(item -> item.getClassification().equals("eew.warning"));

            if (!hasForecastContract && !hasWarningContract) {
                Log.logger.error("DMDATA contract does not have eew.forecast or eew.warning");
                onError(new EEWGatewayException("DMDATA contract does not have eew.forecast or eew.warning"));
                return;
            }

            DmdataSocketList socketList = openSocketList();
            Log.logger.info(socketList.toString());

            for (DmdataSocketList.Item item : socketList.getItems()) {
                Log.logger.info("DMDATA Socket closing: {}", item.getId());
                socketClose(String.valueOf(item.getId()));
                Log.logger.info("DMDATA Socket closed: {}", item.getId());
            }

            DmdataSocketStart.Response socketStart = socketStart(new DmdataSocketStart.Request.Builder()
                    .setAppName(this.appName)
                    .setClassifications(Collections.singletonList(hasForecastContract ? "eew.forecast" : "eew.warning"))
                    .setTypes(Collections.singletonList(hasForecastContract ? "VXSE45" : "VXSE43"))
                    .setTest("including")
                    .setFormatMode("json")
                    .build());
            Log.logger.info(socketStart.toString());

            EEWBot.instance.getHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create(socketStart.getWebsocket().getUrl()), new WebSocketListener(this));
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

    private static class WebSocketListener implements WebSocket.Listener {

        private final Gateway<DmdataEEW> gateway;

        public WebSocketListener(Gateway<DmdataEEW> gateway) {
            this.gateway = gateway;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            Log.logger.info("DMDATA WebSocket opened");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String dataString = data.toString();
            try {
                DmdataWSMessage message = EEWBot.GSON.fromJson(dataString, DmdataWSMessage.class);
                switch (message.getType()) {
                    case PING:
                        DmdataWSPing ping = EEWBot.GSON.fromJson(dataString, DmdataWSPing.class);
                        Log.logger.debug("DMDATA WebSocket ping: {}", ping.getPingId());

                        DmdataWSPong pong = new DmdataWSPong(ping.getPingId());
                        webSocket.sendText(EEWBot.GSON.toJson(pong), true);
                        Log.logger.debug("DMDATA WebSocket pong: {}", pong.getPingId());
                        break;
                }
            } catch (JsonSyntaxException e) {
                this.gateway.onError(new EEWGatewayException("Failed to parse DMDATA WebSocket message: " + dataString, e));
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            Log.logger.info("DMDATA WebSocket closed: {} {}", statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            Log.logger.error("DMDATA WebSocket error", error);
            this.gateway.onError(new EEWGatewayException("DMDATA WebSocket error", error));
        }
    }
}
