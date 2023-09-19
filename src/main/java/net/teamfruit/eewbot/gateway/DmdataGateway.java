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
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

public abstract class DmdataGateway implements Gateway<DmdataEEW> {

    public static final String API_BASE = "https://api.dmdata.jp/v2";

    private final HttpRequest.Builder requestBuilder;
    private final String appName;
    private final int connections;
    private final boolean debug;

    private final Map<String, DmdataEEW> prev = new ConcurrentHashMap<>();

    public DmdataGateway(String apiKey, String origin, long appId, boolean multiConnect, boolean debug) {
        this.requestBuilder = HttpRequest.newBuilder()
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiKey + ":").getBytes()))
                .header("Origin", origin)
                .header("Content-Type", "application/json")
                .header("User-Agent", "eewbot");
        this.appName = "eewbot" + "-" + Long.toHexString(appId);
        this.connections = multiConnect ? 2 : 1;
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

            DmdataSocketList socketList = openSocketList();
            Log.logger.info(socketList.toString());
            for (int i = 1; i <= this.connections; i++) {
                connectWebSocket(this.appName + "-" + i, hasForecastContract, openSocketList());
            }
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

    private DmdataError socketClose(String socketId) throws IOException, InterruptedException, DmdataGatewayException {
        HttpRequest request = this.requestBuilder.copy().DELETE().uri(URI.create(API_BASE + "/socket/" + socketId)).build();
        HttpResponse<String> response = EEWBot.instance.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return EEWBot.GSON.fromJson(response.body(), DmdataError.class);
        }
        return null;
    }

    private void connectWebSocket(String connectionName, boolean hasForecastContract, DmdataSocketList socketList) throws IOException, InterruptedException, DmdataGatewayException {
        for (DmdataSocketList.Item item : socketList.getItems()) {
            if (StringUtils.equals(item.getAppName(), connectionName)) {
                Log.logger.info("DMDATA Socket closing: {}", item.getId());
                DmdataError closeError = socketClose(String.valueOf(item.getId()));
                if (closeError != null) {
                    if (closeError.getError().getCode() == 404) {
                        Log.logger.info("DMDATA Socket already closed: {}", item.getId());
                    } else {
                        throw new DmdataGatewayException(closeError);
                    }
                } else {
                    Log.logger.info("DMDATA Socket closed: {}", item.getId());
                }
            }
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
                .setAppName(connectionName)
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

                                    String bodyString;
                                    if (StringUtils.equals(wsData.getCompression(), "gzip")) {
                                        bodyString = decompressGZIPBase64(wsData.getBody());
                                    } else if (StringUtils.equals(wsData.getCompression(), "zip")) {
                                        bodyString = decompressZipBase64(wsData.getBody());
                                    } else {
                                        bodyString = wsData.getBody();
                                    }
                                    Log.logger.debug("DMDATA WebSocket data body: {}", bodyString);

                                    DmdataEEW eew = EEWBot.GSON.fromJson(bodyString, DmdataEEW.class);
                                    boolean isTest = wsData.getHead().isTest() || !eew.status.equals("通常");
                                    Log.logger.info(isTest ? "DMDATA WebSocket test EEW: {}" : "DMDATA WebSocket EEW: {}", eew);

                                    if (eew.schema.type.equals("eew-information") && !eew.schema.version.equals("1.0.0")) {
                                        Log.logger.warn("DMDATA WebSocket EEW schema version is not 1.0.0, may not be compatible");
                                    }

                                    if (!isTest) {
                                        DmdataEEW prev = DmdataGateway.this.prev.get(eew.eventId);
                                        if (prev != null) {
                                            if (Integer.parseInt(prev.serialNo) < Integer.parseInt(eew.serialNo)) {
                                                eew.prev = prev;
                                                if (eew.body.isLastInfo) {
                                                    DmdataGateway.this.prev.remove(eew.eventId);
                                                } else {
                                                    DmdataGateway.this.prev.put(eew.eventId, eew);
                                                }
                                                onNewData(eew);
                                            }
                                        } else {
                                            DmdataGateway.this.prev.put(eew.eventId, eew);
                                            onNewData(eew);
                                        }
                                    }
                                    break;
                                case ERROR:
                                    DmdataWSError wsError = EEWBot.GSON.fromJson(dataString, DmdataWSError.class);
                                    Log.logger.error("DMDATA WebSocket error message: {}", wsError);
                                    break;
                            }
                        } catch (JsonSyntaxException e) {
                            DmdataGateway.this.onError(new EEWGatewayException("Failed to parse DMDATA WebSocket message: " + dataString, e));
                        } catch (IOException e) {
                            DmdataGateway.this.onError(new EEWGatewayException("Failed to decompress DMDATA WebSocket message: " + dataString, e));
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        Log.logger.info("DMDATA WebSocket closed: {} {}", statusCode, reason);
                        reconnectWebSocket(hasForecastContract, connectionName);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        Log.logger.error("DMDATA WebSocket error", error);
                        DmdataGateway.this.onError(new EEWGatewayException("DMDATA WebSocket error", error));
                        if (webSocket.isOutputClosed() || webSocket.isInputClosed()) {
                            reconnectWebSocket(hasForecastContract, connectionName);
                        }
                    }
                });
    }

    private void reconnectWebSocket(boolean hasForecastContract, String connectionName) {
        try {
            Log.logger.info("DMDATA WebSocket reconnecting");
            connectWebSocket(connectionName, hasForecastContract, openSocketList());
        } catch (DmdataGatewayException e) {
            Log.logger.error(e.getDmdataError().toString());
            DmdataGateway.this.onError(e);
        } catch (IOException | InterruptedException e) {
            DmdataGateway.this.onError(new EEWGatewayException("Failed to reconnect to DMDATA", e));
        }
    }

    public static String decompressGZIPBase64(String compressedBase64) throws IOException {
        byte[] compressedBytes = Base64.getDecoder().decode(compressedBase64);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedBytes);
        GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len;
        while ((len = gzipInputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, len);
        }

        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }

    public static String decompressZipBase64(String compressedBase64) throws IOException {
        byte[] compressedBytes = Base64.getDecoder().decode(compressedBase64);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedBytes);
        ZipInputStream zipInputStream = new ZipInputStream(byteArrayInputStream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        zipInputStream.getNextEntry();

        byte[] buffer = new byte[1024];
        int len;
        while ((len = zipInputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, len);
        }

        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }
}
