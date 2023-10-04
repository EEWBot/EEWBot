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
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

public abstract class DmdataGateway implements Gateway<DmdataEEW> {

    public static final String WS_BASE = "wss://ws.api.dmdata.jp/v2/websocket";
    public static final String WS_BASE_TOKYO = "wss://ws-tokyo.api.dmdata.jp/v2/websocket";
    public static final String WS_BASE_OSAKA = "wss://ws-osaka.api.dmdata.jp/v2/websocket";

    private final DmdataAPI dmdataAPI;
    private final String appName;
    private final boolean multiConnect;
    private final boolean debug;

    private WebSocketListener webSocket1;
    private WebSocketListener webSocket2;

    private final Map<String, DmdataEEW> prev = new ConcurrentHashMap<>();

    public DmdataGateway(DmdataAPI api, long appId, boolean multiConnect, boolean debug) {
        this.dmdataAPI = api;
        this.appName = "eewbot" + "-" + Long.toHexString(appId);
        this.multiConnect = multiConnect;
        this.debug = debug;
    }

    public WebSocketListener getWebSocket1() {
        return webSocket1;
    }

    public WebSocketListener getWebSocket2() {
        return webSocket2;
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-dmdata-thread");

            DmdataContract contract = this.dmdataAPI.contract();
            Log.logger.info(contract.toString());

            boolean hasForecastContract = contract.getItems().stream().anyMatch(item -> item.getClassification().equals("eew.forecast"));
            boolean hasWarningContract = contract.getItems().stream().anyMatch(item -> item.getClassification().equals("eew.warning"));

            if (!hasForecastContract && !hasWarningContract) {
                Log.logger.error("DMDATA contract does not have eew.forecast or eew.warning");
                onError(new EEWGatewayException("DMDATA contract does not have eew.forecast or eew.warning"));
                return;
            }

            DmdataSocketList socketList = this.dmdataAPI.openSocketList();
            Log.logger.info(socketList.toString());
            if (multiConnect) {
                this.webSocket1 = connectWebSocket(WS_BASE_TOKYO, this.appName + "-1", hasForecastContract, socketList);
                this.webSocket2 = connectWebSocket(WS_BASE_OSAKA, this.appName + "-2", hasForecastContract, socketList);
            } else {
                this.webSocket1 = connectWebSocket(WS_BASE, this.appName + "-1", hasForecastContract, socketList);
            }
        } catch (EEWGatewayException e) {
            onError(e);
        } catch (IOException | InterruptedException e) {
            onError(new EEWGatewayException("Failed to connect to DMDATA", e));
        }
    }

    private WebSocketListener connectWebSocket(String wsBaseURI, String connectionName, boolean hasForecastContract, DmdataSocketList socketList) throws EEWGatewayException {
        closeWebSocketIfExist(socketList, connectionName);

        List<String> types = new ArrayList<>();
        if (hasForecastContract) {
            types.add("VXSE45");
        } else {
            types.add("VXSE43");
        }
        if (this.debug) {
            types.add("VXSE42");
        }

        DmdataSocketStart.Response socketStart;
        try {
            socketStart = this.dmdataAPI.socketStart(new DmdataSocketStart.Request.Builder()
                    .setAppName(connectionName)
                    .setClassifications(Collections.singletonList(hasForecastContract ? "eew.forecast" : "eew.warning"))
                    .setTypes(types)
                    .setTest(this.debug ? "including" : "no")
                    .setFormatMode("json")
                    .build());
        } catch (IOException | InterruptedException e) {
            throw new EEWGatewayException(e);
        }
        Log.logger.info(socketStart.toString());

        WebSocketListener listener = new WebSocketListener(connectionName, wsBaseURI, hasForecastContract);
        EEWBot.instance.getHttpClient().newWebSocketBuilder().buildAsync(URI.create(wsBaseURI + "?ticket=" + socketStart.getTicket()), listener);
        return listener;
    }

    public void reconnectWebSocket(WebSocketListener listener) throws EEWGatewayException {
        this.reconnectWebSocket(listener.getWsBaseURI(), listener.getConnectionName(), listener.hasForecastContract());
    }

    private void reconnectWebSocket(String wsBaseURI, String connectionName, boolean hasForecastContract) throws EEWGatewayException {
        try {
            Log.logger.info("DMDATA WebSocket reconnecting");
            connectWebSocket(wsBaseURI, connectionName, hasForecastContract, this.dmdataAPI.openSocketList());
        } catch (IOException | InterruptedException e) {
            throw new EEWGatewayException("Failed to reconnect to DMDATA", e);
        }
    }

    private void closeWebSocketIfExist(DmdataSocketList socketList, String connectionName) throws EEWGatewayException {
        try {
            for (DmdataSocketList.Item item : socketList.getItems()) {
                if (StringUtils.equals(item.getAppName(), connectionName)) {
                    Log.logger.info("DMDATA Socket closing: {}", item.getId());
                    DmdataError closeError = this.dmdataAPI.socketClose(String.valueOf(item.getId()));
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
        } catch (IOException | InterruptedException e) {
            throw new EEWGatewayException("Failed to close DMDATA Socket", e);
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

    public class WebSocketListener implements WebSocket.Listener {

        private final String connectionName;
        private final String wsBaseURI;
        private final boolean hasForecastContract;

        private long lastPingTime;
        private boolean reconnecting;

        public WebSocketListener(String connectionName, String wsBaseURI, boolean hasForecastContract) {
            this.connectionName = connectionName;
            this.wsBaseURI = wsBaseURI;
            this.hasForecastContract = hasForecastContract;
        }

        public String getConnectionName() {
            return connectionName;
        }

        public String getWsBaseURI() {
            return wsBaseURI;
        }

        public boolean hasForecastContract() {
            return hasForecastContract;
        }

        public long getLastPingTime() {
            return lastPingTime;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            Log.logger.info("DMDATA WebSocket opened: {}", connectionName);
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
                        Log.logger.info("DMDATA WebSocket {}: start: {}", connectionName, wsStart);
                        this.lastPingTime = System.currentTimeMillis();
                        break;
                    case PING:
                        DmdataWSPing wsPing = EEWBot.GSON.fromJson(dataString, DmdataWSPing.class);
                        Log.logger.debug("DMDATA WebSocket {}: ping: {}", connectionName, wsPing.getPingId());

                        DmdataWSPong wsPong = new DmdataWSPong(wsPing.getPingId());
                        webSocket.sendText(EEWBot.GSON.toJson(wsPong), true);
                        Log.logger.debug("DMDATA WebSocket {}: pong: {}", connectionName, wsPong.getPingId());
                        this.lastPingTime = System.currentTimeMillis();
                        break;
                    case DATA:
                        DmdataWSData wsData = EEWBot.GSON.fromJson(dataString, DmdataWSData.class);
                        Log.logger.info("DMDATA WebSocket {}: data: {}", connectionName, wsData);

                        if (!wsData.getVersion().equals("2.0")) {
                            Log.logger.warn("DMDATA WebSocket {}: data version is not 2.0, may not be compatible", connectionName);
                        }

                        String bodyString;
                        if (StringUtils.equals(wsData.getCompression(), "gzip")) {
                            bodyString = decompressGZIPBase64(wsData.getBody());
                        } else if (StringUtils.equals(wsData.getCompression(), "zip")) {
                            bodyString = decompressZipBase64(wsData.getBody());
                        } else {
                            bodyString = wsData.getBody();
                        }
                        Log.logger.debug("DMDATA WebSocket {}: data body: {}", connectionName, bodyString);

                        DmdataEEW eew = EEWBot.GSON.fromJson(bodyString, DmdataEEW.class);
                        boolean isTest = wsData.getHead().isTest() || !eew.getStatus().equals("通常");
                        Log.logger.info(isTest ? "DMDATA WebSocket {}: test EEW: {}" : "DMDATA WebSocket {}:  EEW: {}", connectionName, eew);

                        if (eew.getSchema().getType().equals("eew-information") && !eew.getSchema().getVersion().equals("1.0.0")) {
                            Log.logger.warn("DMDATA WebSocket {}: EEW schema version is not 1.0.0, may not be compatible", connectionName);
                        }

                        if (!isTest) {
                            DmdataEEW prev = DmdataGateway.this.prev.putIfAbsent(eew.getEventId(), eew);
                            if (prev == null) {
                                onNewData(eew);
                            } else if (Integer.parseInt(prev.getSerialNo()) < Integer.parseInt(eew.getSerialNo())) {
                                eew.setPrev(prev);
                                if (prev.getBody().isLastInfo()) {
                                    DmdataGateway.this.prev.remove(eew.getEventId());
                                } else {
                                    DmdataGateway.this.prev.put(eew.getEventId(), eew);
                                    onNewData(eew);
                                }
                            }
                        }
                        break;
                    case ERROR:
                        DmdataWSError wsError = EEWBot.GSON.fromJson(dataString, DmdataWSError.class);
                        Log.logger.error("DMDATA WebSocket {}: error message: {}", connectionName, wsError);
                        break;
                }
            } catch (JsonSyntaxException e) {
                Log.logger.error("DMDATA WebSocket {}: failed to parse message: {}", connectionName, dataString, e);
            } catch (IOException e) {
                Log.logger.error("DMDATA WebSocket {}: failed to decompress message: {}", connectionName, dataString, e);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            Log.logger.info("DMDATA WebSocket {}: closed: {} {}", connectionName, statusCode, reason);
            onDisconnected();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            Log.logger.error("DMDATA WebSocket {}: error", connectionName, error);
            DmdataGateway.this.onError(new EEWGatewayException("DMDATA WebSocket error", error));
            if (webSocket.isOutputClosed() || webSocket.isInputClosed()) {
                onDisconnected();
            }
        }

        private void onDisconnected() {
            if (this.reconnecting) {
                return;
            }

            this.reconnecting = true;
            try {
                Thread.sleep(3000);
                closeWebSocketIfExist(DmdataGateway.this.dmdataAPI.openSocketList(), connectionName);
                reconnectWebSocket(wsBaseURI, connectionName, hasForecastContract);
            } catch (EEWGatewayException e) {
                DmdataGateway.this.onError(e);
            } catch (IOException | InterruptedException e) {
                DmdataGateway.this.onError(new EEWGatewayException("Failed to reconnect to DMDATA", e));
            } finally {
                this.reconnecting = false;
            }
        }
    }
}
