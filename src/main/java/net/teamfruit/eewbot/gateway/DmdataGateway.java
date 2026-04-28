package net.teamfruit.eewbot.gateway;

import com.google.gson.JsonSyntaxException;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEW;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEWUpdate;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataContract;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataError;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataSocketList;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataSocketStart;
import net.teamfruit.eewbot.entity.dmdata.ws.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

public class DmdataGateway implements Gateway<DmdataEEWUpdate> {

    public static final String WS_BASE = "wss://ws.api.dmdata.jp/v2/websocket";
    public static final String WS_BASE_TOKYO = "wss://ws-tokyo.api.dmdata.jp/v2/websocket";
    public static final String WS_BASE_OSAKA = "wss://ws-osaka.api.dmdata.jp/v2/websocket";

    public static final String WS_BASE_TEST = "";

    private final java.net.http.HttpClient httpClient;
    private final DmdataAPI dmdataAPI;
    private final String appName;
    private final boolean multiConnect;
    private final Listener listener;
    private final ScheduledExecutorService reconnectScheduler;

    private volatile boolean closed;

    private WebSocketConnection webSocket1;
    private WebSocketConnection webSocket2;

    private final Map<String, EEWState> prev = new ConcurrentHashMap<>();

    private record EEWState(DmdataEEW eew, SeismicIntensity maxIntensitySoFar) {
    }

    @FunctionalInterface
    public interface Listener {
        void onNewData(DmdataEEWUpdate update);
    }

    public DmdataGateway(java.net.http.HttpClient httpClient, DmdataAPI api, long appId, boolean multiConnect, Listener listener, ScheduledExecutorService reconnectScheduler) {
        this.httpClient = httpClient;
        this.dmdataAPI = api;
        this.appName = "eewbot" + "-" + encodeAppId(appId);
        this.multiConnect = multiConnect;
        this.listener = listener;
        this.reconnectScheduler = reconnectScheduler;
    }

    @Override
    public void onNewData(DmdataEEWUpdate data) {
        if (this.closed)
            return;
        this.listener.onNewData(data);
    }

    public void close() {
        this.closed = true;
        cancelPendingReconnect(this.webSocket1);
        cancelPendingReconnect(this.webSocket2);
        closeWebSocketConnection(this.webSocket1);
        closeWebSocketConnection(this.webSocket2);
    }

    private void cancelPendingReconnect(WebSocketConnection connection) {
        if (connection != null && connection.pendingReconnect != null) {
            connection.pendingReconnect.cancel(false);
        }
    }

    private void closeWebSocketConnection(WebSocketConnection connection) {
        if (connection != null && connection.getWebSocket() != null) {
            try {
                connection.getWebSocket().sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception e) {
                Log.logger.debug("Error closing WebSocket: {}", e.getMessage());
            }
        }
    }

    public WebSocketConnection getWebSocket1() {
        return this.webSocket1;
    }

    public WebSocketConnection getWebSocket2() {
        return this.webSocket2;
    }

    private String encodeAppId(long appId) {
        byte[] bytes = ByteBuffer.allocate(Long.BYTES).putLong(appId).array();
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String getWebSocketName(int index) {
        return this.appName + "-" + index;
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("eewbot-dmdata-thread");

            if (StringUtils.isEmpty(WS_BASE_TEST)) {
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
                if (this.multiConnect) {
                    String ws1Name = getWebSocketName(1), ws2Name = getWebSocketName(2);
                    closeWebSocketIfExist(socketList, ws1Name);
                    this.webSocket1 = connectWebSocket(WS_BASE_TOKYO, ws1Name, hasForecastContract);
                    closeWebSocketIfExist(socketList, ws2Name);
                    this.webSocket2 = connectWebSocket(WS_BASE_OSAKA, ws2Name, hasForecastContract);
                } else {
                    String wsName = getWebSocketName(1);
                    closeWebSocketIfExist(socketList, wsName);
                    this.webSocket1 = connectWebSocket(WS_BASE, wsName, hasForecastContract);
                }
            } else {
                Log.logger.info("DMDATA WebSocket test mode");
                this.webSocket1 = connectWebSocket(WS_BASE_TEST, this.appName + "-1", true);
                if (this.multiConnect)
                    this.webSocket2 = connectWebSocket(WS_BASE_TEST, this.appName + "-2", true);
            }
        } catch (EEWGatewayException e) {
            onError(e);
        } catch (IOException | InterruptedException e) {
            onError(new EEWGatewayException("Failed to connect to DMDATA", e));
        }
    }

    private WebSocketConnection connectWebSocket(String wsBaseURI, String connectionName, boolean hasForecastContract) throws EEWGatewayException {
        List<String> types = new ArrayList<>();
        if (hasForecastContract) {
            types.add("VXSE45");
        } else {
            types.add("VXSE43");
        }

        if (StringUtils.isEmpty(WS_BASE_TEST)) {
            DmdataSocketStart.Response socketStart;
            try {
                socketStart = this.dmdataAPI.socketStart(new DmdataSocketStart.Request.Builder()
                        .setAppName(connectionName)
                        .setClassifications(Collections.singletonList(hasForecastContract ? "eew.forecast" : "eew.warning"))
                        .setTypes(types)
                        .setTest("no")
                        .setFormatMode("json")
                        .build());
            } catch (IOException | InterruptedException e) {
                throw new EEWGatewayException(e);
            }
            Log.logger.info(socketStart.toString());

            WebSocketConnection connection = new WebSocketConnection(connectionName, wsBaseURI, hasForecastContract);
            CompletableFuture<WebSocket> future = this.httpClient.newWebSocketBuilder().buildAsync(URI.create(wsBaseURI + "?ticket=" + socketStart.getTicket()), connection.getListener());
            future.whenComplete((ws, ex) -> {
                MDC.put("gateway", "dmdata");
                MDC.put("gateway.connection", connectionName);
                try {
                    if (ex != null) {
                        Log.logger.error("DMDATA WebSocket {}: handshake failed", connectionName, ex);
                        onError(new EEWGatewayException("WebSocket handshake failed", ex));
                        connection.reconnectFailed = true;
                    } else {
                        connection.setWebSocket(ws);
                    }
                } finally {
                    MDC.clear();
                }
            });
            return connection;
        } else {
            WebSocketConnection connection = new WebSocketConnection(connectionName, wsBaseURI, hasForecastContract);
            CompletableFuture<WebSocket> future = this.httpClient.newWebSocketBuilder().buildAsync(URI.create(wsBaseURI), connection.getListener());
            future.whenComplete((ws, ex) -> {
                MDC.put("gateway", "dmdata");
                MDC.put("gateway.connection", connectionName);
                try {
                    if (ex != null) {
                        Log.logger.error("DMDATA WebSocket {}: handshake failed", connectionName, ex);
                        onError(new EEWGatewayException("WebSocket handshake failed", ex));
                        connection.reconnectFailed = true;
                    } else {
                        connection.setWebSocket(ws);
                    }
                } finally {
                    MDC.clear();
                }
            });
            return connection;
        }
    }

    public void reconnectWebSocket(WebSocketConnection listener) throws EEWGatewayException {
        this.reconnectWebSocket(listener.getWsBaseURI(), listener.getConnectionName(), listener.hasForecastContract());
    }

    private void reconnectWebSocket(String wsBaseURI, String connectionName, boolean hasForecastContract) throws EEWGatewayException {
        if (this.closed)
            return;
        try {
            Log.logger.info("DMDATA WebSocket reconnecting");
            closeWebSocketIfExist(this.dmdataAPI.openSocketList(), connectionName);
            WebSocketConnection listener = connectWebSocket(wsBaseURI, connectionName, hasForecastContract);
            if (connectionName.endsWith("-2")) {
                if (this.webSocket2 != null) {
                    this.webSocket2.replaced = true;
                    cancelPendingReconnect(this.webSocket2);
                }
                this.webSocket2 = listener;
            } else {
                if (this.webSocket1 != null) {
                    this.webSocket1.replaced = true;
                    cancelPendingReconnect(this.webSocket1);
                }
                this.webSocket1 = listener;
            }
        } catch (IOException | InterruptedException e) {
            throw new EEWGatewayException("Failed to reconnect to DMDATA", e);
        }
    }

    private void closeWebSocketIfExist(DmdataSocketList socketList, String connectionName) throws EEWGatewayException {
        try {
            for (DmdataSocketList.Item item : socketList.getItems()) {
                if (Strings.CS.equals(item.getAppName(), connectionName)) {
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

    public void reconnectDeadWebSocketsBasedOnDmData() throws EEWGatewayException {
        if (this.closed)
            return;
        try {
            DmdataSocketList socketList = this.dmdataAPI.openSocketList();
            if (isWebSocketDead(socketList, 1)) {
                Log.logger.warn("DMDATA WebSocket 1 is dead, reconnecting...");
                if (this.webSocket1.getWebSocket() != null)
                    this.webSocket1.getWebSocket().sendClose(1011, "Socket remains, but is not recognized by the server");
            }
            if (this.multiConnect && isWebSocketDead(socketList, 2)) {
                Log.logger.warn("DMDATA WebSocket 2 is dead, reconnecting...");
                if (this.webSocket2.getWebSocket() != null)
                    this.webSocket2.getWebSocket().sendClose(1011, "Socket remains, but is not recognized by the server");
            }
        } catch (IOException | InterruptedException e) {
            throw new EEWGatewayException("Failed to reconnect to DMDATA", e);
        }
    }

    private boolean isWebSocketDead(DmdataSocketList socketList, int index) {
        return socketList.getItems().stream()
                .map(DmdataSocketList.Item::getAppName)
                .noneMatch(appName -> Strings.CS.equals(appName, getWebSocketName(index)));
    }

    public class WebSocketConnection {

        private final String connectionName;
        private final String wsBaseURI;
        private final boolean hasForecastContract;

        private WebSocket webSocket;

        private volatile boolean reconnecting;
        private volatile boolean reconnectFailed;
        volatile boolean replaced;
        volatile ScheduledFuture<?> pendingReconnect;

        public WebSocketConnection(String connectionName, String wsBaseURI, boolean hasForecastContract) {
            this.connectionName = connectionName;
            this.wsBaseURI = wsBaseURI;
            this.hasForecastContract = hasForecastContract;
        }

        public String getConnectionName() {
            return this.connectionName;
        }

        public String getWsBaseURI() {
            return this.wsBaseURI;
        }

        public boolean hasForecastContract() {
            return this.hasForecastContract;
        }

        public @Nullable WebSocket getWebSocket() {
            return this.webSocket;
        }

        public void setWebSocket(WebSocket webSocket) {
            if (DmdataGateway.this.closed) {
                try {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
                } catch (Exception e) {
                    Log.logger.debug("Error closing late WebSocket: {}", e.getMessage());
                }
                return;
            }
            this.webSocket = webSocket;
        }

        public boolean isReconnectFailed() {
            return this.reconnectFailed && !this.reconnecting;
        }

        public WebSocketListener getListener() {
            return new WebSocketListener();
        }

        public class WebSocketListener implements WebSocket.Listener {

            @Override
            public void onOpen(WebSocket webSocket) {
                MDC.put("gateway", "dmdata");
                MDC.put("gateway.connection", WebSocketConnection.this.connectionName);
                try {
                    if (DmdataGateway.this.closed) {
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
                        return;
                    }
                    Log.logger.info("DMDATA WebSocket opened: {}", WebSocketConnection.this.connectionName);
                    WebSocket.Listener.super.onOpen(webSocket);
                } finally {
                    MDC.clear();
                }
            }

            private final StringBuilder buffer = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                if (!last) {
                    this.buffer.append(data);
                    return WebSocket.Listener.super.onText(webSocket, data, false);
                }

                String dataString;
                if (this.buffer.length() <= 0) {
                    dataString = data.toString();
                } else {
                    this.buffer.append(data);
                    dataString = this.buffer.toString();
                    this.buffer.setLength(0);
                }

                MDC.put("gateway", "dmdata");
                MDC.put("gateway.connection", WebSocketConnection.this.connectionName);
                try {
                    DmdataWSMessage message = Codecs.GSON.fromJson(dataString, DmdataWSMessage.class);
                    switch (message.getType()) {
                        case START:
                            DmdataWSStart wsStart = Codecs.GSON.fromJson(dataString, DmdataWSStart.class);
                            Log.logger.info("DMDATA WebSocket {}: start: {}", WebSocketConnection.this.connectionName, wsStart);
                            break;
                        case PING:
                            DmdataWSPing wsPing = Codecs.GSON.fromJson(dataString, DmdataWSPing.class);
                            Log.logger.trace("DMDATA WebSocket {}: ping: {}", WebSocketConnection.this.connectionName, wsPing.getPingId());

                            DmdataWSPong wsPong = new DmdataWSPong(wsPing.getPingId());
                            webSocket.sendText(Codecs.GSON.toJson(wsPong), true);
                            Log.logger.trace("DMDATA WebSocket {}: pong: {}", WebSocketConnection.this.connectionName, wsPong.getPingId());
                            break;
                        case DATA:
                            DmdataWSData wsData = Codecs.GSON.fromJson(dataString, DmdataWSData.class);
                            Log.logger.info("DMDATA WebSocket {}: data: {}", WebSocketConnection.this.connectionName, wsData);

                            if (!wsData.getVersion().equals("2.0")) {
                                Log.logger.warn("DMDATA WebSocket {}: data version is not 2.0, may not be compatible", WebSocketConnection.this.connectionName);
                            }

                            String bodyString;
                            if (Strings.CS.equals(wsData.getCompression(), "gzip")) {
                                bodyString = decompressGZIPBase64(wsData.getBody());
                            } else if (Strings.CS.equals(wsData.getCompression(), "zip")) {
                                bodyString = decompressZipBase64(wsData.getBody());
                            } else {
                                bodyString = wsData.getBody();
                            }
                            Log.logger.debug("DMDATA WebSocket {}: data body: {}", WebSocketConnection.this.connectionName, bodyString);

                            DmdataEEW eew = Codecs.GSON.fromJson(bodyString, DmdataEEW.class);
                            eew.setRawData(bodyString);
                            boolean isTest = wsData.getHead().isTest() || !eew.getStatus().equals("通常");
                            MDC.put("event.id", eew.getEventId());
                            MDC.put("event.type", "eew");
                            Log.logger.info(isTest ? "DMDATA WebSocket {}: test EEW: {}" : "DMDATA WebSocket {}:  EEW: {}", WebSocketConnection.this.connectionName, eew);

                            if (eew.getSchema().getType().equals("eew-information") && !eew.getSchema().getVersion().equals("1.0.0")) {
                                Log.logger.warn("DMDATA WebSocket {}: EEW schema version is not 1.0.0, may not be compatible", WebSocketConnection.this.connectionName);
                            }

                            if (!isTest) {
                                int currentSerialNo = Integer.parseInt(eew.getSerialNo());
                                AtomicBoolean update = new AtomicBoolean(false);
                                AtomicReference<DmdataEEW> capturedPrev = new AtomicReference<>();
                                AtomicReference<SeismicIntensity> capturedMaxBefore = new AtomicReference<>(SeismicIntensity.UNKNOWN);
                                DmdataGateway.this.prev.compute(eew.getEventId(), (key, value) -> {
                                    int size = DmdataGateway.this.prev.size();
                                    DmdataEEW prevEEW = value != null ? value.eew() : null;
                                    if (prevEEW == null) {
                                        eew.setConcurrentIndex(size + 1);
                                        if (size >= 1)
                                            eew.setConcurrent(true);
                                    } else {
                                        eew.setConcurrentIndex(prevEEW.getConcurrentIndex());
                                        eew.setConcurrent(prevEEW.isConcurrent() || size >= 2);
                                    }
                                    if (prevEEW == null || Integer.parseInt(prevEEW.getSerialNo()) < currentSerialNo ||
                                            (eew.getBody().isCanceled() && !prevEEW.getBody().isCanceled())) {
                                        SeismicIntensity maxBefore = value != null ? value.maxIntensitySoFar() : SeismicIntensity.UNKNOWN;
                                        DmdataEEW.Body.Intensity currentIntensityField = eew.getBody().getIntensity();
                                        SeismicIntensity currentBodyIntensity = currentIntensityField != null
                                                ? SeismicIntensity.get(currentIntensityField.getForecastMaxInt().getFrom())
                                                : SeismicIntensity.UNKNOWN;
                                        SeismicIntensity newRunningMax = currentBodyIntensity.compareTo(maxBefore) > 0 ? currentBodyIntensity : maxBefore;
                                        update.set(true);
                                        capturedPrev.set(prevEEW);
                                        capturedMaxBefore.set(maxBefore);
                                        return new EEWState(eew, newRunningMax);
                                    } else {
                                        return value;
                                    }
                                });
                                if (update.get()) {
                                    onNewData(new DmdataEEWUpdate(eew, capturedPrev.get(), capturedMaxBefore.get()));
                                    if (!DmdataGateway.this.multiConnect && eew.getBody().isLastInfo()) {
                                        DmdataGateway.this.prev.remove(eew.getEventId());
                                    }
                                } else if (DmdataGateway.this.multiConnect && eew.getBody().isLastInfo()) {
                                    DmdataGateway.this.prev.remove(eew.getEventId());
                                }
                            }
                            break;
                        case ERROR:
                            DmdataWSError wsError = Codecs.GSON.fromJson(dataString, DmdataWSError.class);
                            Log.logger.error("DMDATA WebSocket {}: error message: {}", WebSocketConnection.this.connectionName, wsError);
                            break;
                    }
                } catch (JsonSyntaxException e) {
                    Log.logger.error("DMDATA WebSocket {}: failed to parse message: {}", WebSocketConnection.this.connectionName, dataString, e);
                } catch (IOException e) {
                    Log.logger.error("DMDATA WebSocket {}: failed to decompress message: {}", WebSocketConnection.this.connectionName, dataString, e);
                } finally {
                    MDC.clear();
                }
                return WebSocket.Listener.super.onText(webSocket, data, true);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                MDC.put("gateway", "dmdata");
                MDC.put("gateway.connection", WebSocketConnection.this.connectionName);
                try {
                    Log.logger.info("DMDATA WebSocket {}: closed: {} {}", WebSocketConnection.this.connectionName, statusCode, reason);
                    onDisconnected();
                } finally {
                    MDC.clear();
                }
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                MDC.put("gateway", "dmdata");
                MDC.put("gateway.connection", WebSocketConnection.this.connectionName);
                try {
                    Log.logger.error("DMDATA WebSocket {}: error", WebSocketConnection.this.connectionName, error);
                    DmdataGateway.this.onError(new EEWGatewayException("DMDATA WebSocket error", error));
                    if (webSocket.isOutputClosed() || webSocket.isInputClosed()) {
                        onDisconnected();
                    }
                } finally {
                    MDC.clear();
                }
            }

            private void onDisconnected() {
                if (DmdataGateway.this.closed || WebSocketConnection.this.reconnecting || WebSocketConnection.this.replaced) {
                    return;
                }

                WebSocketConnection.this.reconnecting = true;
                WebSocketConnection.this.pendingReconnect = DmdataGateway.this.reconnectScheduler.schedule(() -> {
                    MDC.put("gateway", "dmdata");
                    MDC.put("gateway.connection", WebSocketConnection.this.connectionName);
                    MDC.put("gateway.task", "reconnect");
                    try {
                        if (DmdataGateway.this.closed || WebSocketConnection.this.replaced) {
                            return;
                        }
                        reconnectWebSocket(WebSocketConnection.this);
                    } catch (EEWGatewayException e) {
                        DmdataGateway.this.onError(e);
                        WebSocketConnection.this.reconnectFailed = true;
                    } finally {
                        WebSocketConnection.this.reconnecting = false;
                        MDC.clear();
                    }
                }, 3, TimeUnit.SECONDS);
            }
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
