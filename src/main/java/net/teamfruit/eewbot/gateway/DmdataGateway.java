package net.teamfruit.eewbot.gateway;

import com.google.gson.JsonSyntaxException;
import net.teamfruit.eewbot.EEWBot;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.DmdataEEW;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataContract;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataError;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataSocketList;
import net.teamfruit.eewbot.entity.dmdataapi.DmdataSocketStart;
import net.teamfruit.eewbot.entity.dmdataapi.ws.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

public abstract class DmdataGateway implements Gateway<DmdataEEW> {

    public static final String API_BASE = "https://api.dmdata.jp/v2";

    private final HttpRequest.Builder requestBuilder;
    private final String appName;
    private final boolean debug;

    public DmdataGateway(String apiKey, String origin, String appName, boolean debug) {
        this.requestBuilder = HttpRequest.newBuilder()
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                .header("Origin", origin).header("Content-Type", "application/json")
                .header("User-Agent", "eewbot");
        this.appName = appName;
        this.debug = debug;
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-dmdata-thread");

            DmdataContract contract = contract();
            Log.logger.info(contract.toString());

            boolean hasForecastContract = contract.getItems().stream().anyMatch(item -> item.getClassification().equals("eew.forecast"));
            boolean hasWarningContract = contract.getItems().stream().anyMatch(item -> item.getClassification().equals("eew.warning"));

            if (!hasForecastContract && !hasWarningContract) {
                Log.logger.error("DMDATA contract does not have eew.forecast or eew.warning");
                onError(new EEWGatewayException("DMDATA contract does not have eew.forecast or eew.warning"));
                return;
            }

            connectWebSocket(hasForecastContract);
        } catch (DmdataGatewayException e) {
            Log.logger.error(e.getDmdataError().toString());
            onError(e);
        } catch (IOException | InterruptedException e) {
            onError(new EEWGatewayException("Failed to connect to DMDATA", e));
        }
    }

    private DmdataContract contract() throws IOException, InterruptedException, DmdataGatewayException {
        HttpRequest request = this.requestBuilder.copy().GET().uri(URI.create(API_BASE + "/contract")).build();
        HttpResponse<String> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return EEWBot.GSON.fromJson(response.body(), DmdataContract.class);
        } else {
            throw new DmdataGatewayException(EEWBot.GSON.fromJson(response.body(), DmdataError.class));
        }
    }

    private DmdataSocketList openSocketList() throws IOException, InterruptedException, DmdataGatewayException {
        HttpRequest request = this.requestBuilder.copy().GET().uri(URI.create(API_BASE + "/socket?status=open")).build();
        HttpResponse<String> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return EEWBot.GSON.fromJson(response.body(), DmdataSocketList.class);
        } else {
            throw new DmdataGatewayException(EEWBot.GSON.fromJson(response.body(), DmdataError.class));
        }
    }

    private DmdataSocketStart.Response socketStart(DmdataSocketStart.Request body) throws IOException, InterruptedException, DmdataGatewayException {
        HttpRequest request = this.requestBuilder.copy().POST(HttpRequest.BodyPublishers.ofString(EEWBot.GSON.toJson(body))).uri(URI.create(API_BASE + "/socket")).build();
        HttpResponse<String> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return EEWBot.GSON.fromJson(response.body(), DmdataSocketStart.Response.class);
        } else {
            throw new DmdataGatewayException(EEWBot.GSON.fromJson(response.body(), DmdataError.class));
        }
    }

    private void socketClose(String socketId) throws IOException, InterruptedException, DmdataGatewayException {
        HttpRequest request = this.requestBuilder.copy().DELETE().uri(URI.create(API_BASE + "/socket/" + socketId)).build();
        HttpResponse<String> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new DmdataGatewayException(EEWBot.GSON.fromJson(response.body(), DmdataError.class));
        }
    }

    private void connectWebSocket(boolean hasForecastContract) throws IOException, InterruptedException, DmdataGatewayException {
        DmdataSocketList socketList = openSocketList();
        Log.logger.info(socketList.toString());

        for (DmdataSocketList.Item item : socketList.getItems()) {
            Log.logger.info("DMDATA Socket closing: {}", item.getId());
            socketClose(String.valueOf(item.getId()));
            Log.logger.info("DMDATA Socket closed: {}", item.getId());
        }

        List<String> types = new ArrayList<>();
        if (hasForecastContract) {
            types.add("VXSE45");
        } else {
            types.add("VXSE43");
        }
        if (debug) {
            types.add("VXSE42");
        }

        DmdataSocketStart.Response socketStart = socketStart(new DmdataSocketStart.Request.Builder()
                .setAppName(this.appName)
                .setClassifications(Collections.singletonList(hasForecastContract ? "eew.forecast" : "eew.warning"))
                .setTypes(types)
                .setTest("including")
                .setFormatMode("json")
                .build());
        Log.logger.info(socketStart.toString());

        EEWBot.instance.getHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(socketStart.getWebsocket().getUrl()), new WebSocket.Listener() {
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
                                case START:
                                    DmdataWSStart wsStart = EEWBot.GSON.fromJson(dataString, DmdataWSStart.class);
                                    Log.logger.info("DMDATA WebSocket start: {}", wsStart);
                                    break;
                                case PING:
                                    DmdataWSPing wsPing = EEWBot.GSON.fromJson(dataString, DmdataWSPing.class);
                                    Log.logger.debug("DMDATA WebSocket ping: {}", wsPing.getPingId());

                                    DmdataWSPong wsPong = new DmdataWSPong(wsPing.getPingId());
                                    webSocket.sendText(EEWBot.GSON.toJson(wsPong), true);
                                    Log.logger.debug("DMDATA WebSocket pong: {}", wsPong.getPingId());
                                    break;
                                case DATA:
                                    DmdataWSData wsData = EEWBot.GSON.fromJson(dataString, DmdataWSData.class);
                                    Log.logger.info("DMDATA WebSocket data: {}", wsData);

                                    if (!wsData.getVersion().equals("2.0")) {
                                        Log.logger.warn("DMDATA WebSocket data version is not 2.0, may not be compatible");
                                    }

                                    String bodyString = wsData.getEncoding().equals("base64") ? new String(Base64.getDecoder().decode(wsData.getBody())) : wsData.getBody();

                                    if (wsData.getHead().isTest()) {
                                        Log.logger.info("DMDATA WebSocket test data body: {}", bodyString);
                                    } else {
                                        Log.logger.info("DMDATA WebSocket data body: {}", bodyString);
                                    }
                                    break;
                                case ERROR:
                                    DmdataWSError wsError = EEWBot.GSON.fromJson(dataString, DmdataWSError.class);
                                    Log.logger.error("DMDATA WebSocket error: {}", wsError);
                                    break;
                            }
                        } catch (JsonSyntaxException e) {
                            DmdataGateway.this.onError(new EEWGatewayException("Failed to parse DMDATA WebSocket message: " + dataString, e));
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        Log.logger.info("DMDATA WebSocket closed: {} {}", statusCode, reason);
                        try {
                            Log.logger.info("DMDATA WebSocket reconnecting");
                            connectWebSocket(hasForecastContract);
                        } catch (DmdataGatewayException e) {
                            Log.logger.error(e.getDmdataError().toString());
                            DmdataGateway.this.onError(e);
                        } catch (IOException | InterruptedException e) {
                            DmdataGateway.this.onError(new EEWGatewayException("Failed to reconnect to DMDATA", e));
                        }
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        Log.logger.error("DMDATA WebSocket error", error);
                        DmdataGateway.this.onError(new EEWGatewayException("DMDATA WebSocket error", error));
                    }
                });
    }
}
