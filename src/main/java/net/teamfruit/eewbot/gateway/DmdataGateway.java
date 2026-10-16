package net.teamfruit.eewbot.gateway;

import com.fasterxml.jackson.core.JacksonException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.gson.JsonSyntaxException;
import net.teamfruit.eewbot.Codecs;
import net.teamfruit.eewbot.Log;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.entity.dmdata.DmdataEEWUpdate;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataContract;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataError;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataSocketList;
import net.teamfruit.eewbot.entity.dmdata.api.DmdataSocketStart;
import net.teamfruit.eewbot.entity.dmdata.ws.*;
import net.teamfruit.eewbot.entity.jma.JMAInfoType;
import net.teamfruit.eewbot.entity.jma.JMAStatus;
import net.teamfruit.eewbot.entity.jma.telegram.AbstractEEW;
import net.teamfruit.eewbot.entity.jma.telegram.EEW;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE43Impl;
import net.teamfruit.eewbot.entity.jma.telegram.VXSE45Impl;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

public class DmdataGateway implements Gateway<DmdataEEWUpdate> {

    public static final String WS_BASE = "wss://ws.api.dmdata.jp/v2/websocket";
    public static final String WS_BASE_TOKYO = "wss://ws-tokyo.api.dmdata.jp/v2/websocket";
    public static final String WS_BASE_OSAKA = "wss://ws-osaka.api.dmdata.jp/v2/websocket";

    private final java.net.http.HttpClient httpClient;
    private final DmdataAPI dmdataAPI;
    private final String appName;
    private final boolean multiConnect;
    private final Listener listener;
    private final ScheduledExecutorService reconnectScheduler;
    private final String wsBaseTest;

    private volatile boolean closed;

    private WebSocketConnection webSocket1;
    private WebSocketConnection webSocket2;

    private final Cache<String, EEWState> eventStates;
    private final Cache<String, Boolean> receivedTelegramIds;

    private record EEWState(EEW eew, int serialNo, SeismicIntensity maxIntensitySoFar,
                            DmdataEEWUpdate.DeliveryHistory deliveryHistory, boolean finalReceived, boolean cancelled) {
        boolean isActive() {
            // Completion history stays terminal even if later ordinary telegrams are delivered.
            return !this.finalReceived && !this.cancelled;
        }
    }

    @FunctionalInterface
    public interface Listener {
        void onNewData(DmdataEEWUpdate update);
    }

    public DmdataGateway(java.net.http.HttpClient httpClient, DmdataAPI api, long appId, boolean multiConnect, Listener listener, ScheduledExecutorService reconnectScheduler, String wsBaseTest) {
        this(httpClient, api, appId, multiConnect, listener, reconnectScheduler, wsBaseTest, Ticker.systemTicker());
    }

    DmdataGateway(java.net.http.HttpClient httpClient, DmdataAPI api, long appId, boolean multiConnect, Listener listener, ScheduledExecutorService reconnectScheduler, String wsBaseTest, Ticker ticker) {
        this.httpClient = httpClient;
        this.dmdataAPI = api;
        this.appName = "eewbot" + "-" + encodeAppId(appId);
        this.multiConnect = multiConnect;
        this.listener = listener;
        this.reconnectScheduler = reconnectScheduler;
        this.wsBaseTest = wsBaseTest != null ? wsBaseTest : "";
        this.eventStates = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(10_000)
                .ticker(ticker)
                .build();
        this.receivedTelegramIds = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(100_000)
                .ticker(ticker)
                .build();
    }

    // Serialize acceptance and listener submission, including active-event numbering across sockets.
    // The listener only enqueues delivery; Discord I/O remains asynchronous.
    synchronized void processEEW(@Nullable String telegramId, EEW eew, String xml) {
        if (this.closed)
            return;
        String telegramKey = telegramKey(telegramId, xml);
        // Read without rewriting duplicates, so retransmission does not renew the TTL.
        if (this.receivedTelegramIds.getIfPresent(telegramKey) != null)
            return;
        int serialNo = Integer.parseInt(eew.getSerial());
        EEWState state = this.eventStates.getIfPresent(eew.getEventId());
        EEW previous = state != null ? state.eew() : null;
        int maxActiveIndex = this.eventStates.asMap().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(eew.getEventId()) && entry.getValue().isActive())
                .mapToInt(entry -> entry.getValue().eew().getConcurrentIndex())
                .max().orElse(0);
        if (previous == null) {
            eew.setConcurrentIndex(maxActiveIndex + 1);
            eew.setConcurrent(maxActiveIndex > 0);
        } else {
            eew.setConcurrentIndex(previous.getConcurrentIndex());
            eew.setConcurrent(previous.isConcurrent() || state.isActive() && maxActiveIndex > 0);
        }

        SeismicIntensity maxBefore = state != null ? state.maxIntensitySoFar() : SeismicIntensity.UNKNOWN;
        DmdataEEWUpdate.DeliveryHistory history = state != null ? state.deliveryHistory() : DmdataEEWUpdate.DeliveryHistory.EMPTY;
        DmdataEEWUpdate update = new DmdataEEWUpdate(eew, previous, maxBefore, history);
        SeismicIntensity runningMax = update.maxIntensityEEW();
        if (!eew.isCancelReport() && update.isDeliveryTarget()) {
            // Accumulate delivery history even when this telegram does not replace the latest state.
            history = history.include(eew.isEEWWarning(), runningMax);
        }
        boolean latest = state == null || replacesLatest(eew, serialNo, state);
        EEWState next = new EEWState(latest ? eew : previous, latest ? serialNo : state.serialNo(), runningMax, history,
                state != null && state.finalReceived() || latest && !eew.isCancelReport() && eew.isLastInfo(),
                state != null && state.cancelled() || latest && eew.isCancelReport());

        // Success means the listener has classified the telegram and enqueued delivery (or intentionally
        // filtered it). Do not commit state or the ID if parsing, classification or enqueueing fails.
        // Actual Discord/external-webhook delivery is asynchronous and outside this guarantee.
        onNewData(new DmdataEEWUpdate(eew, previous, maxBefore, history));
        this.eventStates.put(eew.getEventId(), next);
        this.receivedTelegramIds.put(telegramKey, true);
    }

    private static String telegramKey(@Nullable String id, String xml) {
        if (StringUtils.isNotBlank(id))
            return "id:" + id;
        // Only missing IDs use a body hash; keep its namespace separate from DMDATA IDs.
        try {
            return "xml:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static boolean replacesLatest(EEW current, int serialNo, EEWState state) {
        EEW previous = state.eew();
        int timeOrder = Comparator.<Instant>nullsFirst(Comparator.naturalOrder())
                .compare(current.getReportDateTime(), previous.getReportDateTime());
        if (timeOrder < 0)
            return false;
        // Equal timestamps can contain successive reports; a higher serial takes precedence
        // over the previous telegram's InfoType, including corrections and cancellations.
        if (timeOrder == 0 && serialNo > state.serialNo())
            return true;
        int typeOrder = Integer.compare(infoTypePriority(current.getInfoType()), infoTypePriority(previous.getInfoType()));
        if (timeOrder == 0 && typeOrder < 0)
            return false;
        // Later corrections/cancellations may reuse or lower the serial. With equal times and
        // no serial increase, prefer cancellation, correction, announcement, then delayed reports.
        boolean administrative = current.getInfoType() == JMAInfoType.訂正 || current.isCancelReport();
        if (administrative && (timeOrder > 0 || typeOrder > 0))
            return true;
        return serialNo >= state.serialNo();
    }

    private static int infoTypePriority(@Nullable JMAInfoType type) {
        if (type == null)
            return -1;
        return switch (type) {
            case 遅延 -> 0;
            case 発表 -> 1;
            case 訂正 -> 2;
            case 取消 -> 3;
        };
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

            if (StringUtils.isEmpty(this.wsBaseTest)) {
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
                this.webSocket1 = connectWebSocket(this.wsBaseTest, this.appName + "-1", true);
                if (this.multiConnect)
                    this.webSocket2 = connectWebSocket(this.wsBaseTest, this.appName + "-2", true);
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

        if (StringUtils.isEmpty(this.wsBaseTest)) {
            DmdataSocketStart.Response socketStart;
            try {
                socketStart = this.dmdataAPI.socketStart(new DmdataSocketStart.Request.Builder()
                        .setAppName(connectionName)
                        .setClassifications(Collections.singletonList(hasForecastContract ? "eew.forecast" : "eew.warning"))
                        .setTypes(types)
                        .setTest("no")
                        .setFormatMode("raw")
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
            if (StringUtils.isEmpty(this.wsBaseTest)) {
                closeWebSocketIfExist(this.dmdataAPI.openSocketList(), connectionName);
            }
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

                            String telegramType = wsData.getHead().getType();
                            Class<? extends AbstractEEW> eewClass;
                            if ("VXSE45".equals(telegramType)) {
                                eewClass = VXSE45Impl.class;
                            } else if ("VXSE43".equals(telegramType)) {
                                eewClass = VXSE43Impl.class;
                            } else {
                                Log.logger.warn("DMDATA WebSocket {}: unknown telegram type: {}", WebSocketConnection.this.connectionName, telegramType);
                                break;
                            }

                            AbstractEEW eew = Codecs.XML_MAPPER.readValue(bodyString, eewClass);
                            eew.setRawData(bodyString);
                            boolean isTest = wsData.getHead().isTest() || eew.getStatus() != JMAStatus.通常;
                            MDC.put("event.id", eew.getEventId());
                            MDC.put("event.type", "eew");
                            Log.logger.info(isTest ? "DMDATA WebSocket {}: test EEW: {}" : "DMDATA WebSocket {}:  EEW: {}", WebSocketConnection.this.connectionName, eew);

                            if (!isTest) {
                                processEEW(wsData.getId(), eew, bodyString);
                            }
                            break;
                        case ERROR:
                            DmdataWSError wsError = Codecs.GSON.fromJson(dataString, DmdataWSError.class);
                            Log.logger.error("DMDATA WebSocket {}: error message: {}", WebSocketConnection.this.connectionName, wsError);
                            break;
                    }
                } catch (JsonSyntaxException | JacksonException e) {
                    Log.logger.error("DMDATA WebSocket {}: failed to parse message: {}", WebSocketConnection.this.connectionName, dataString, e);
                } catch (IOException e) {
                    Log.logger.error("DMDATA WebSocket {}: failed to decompress message: {}", WebSocketConnection.this.connectionName, dataString, e);
                } catch (RuntimeException e) {
                    Log.logger.error("DMDATA WebSocket {}: failed to process message", WebSocketConnection.this.connectionName, e);
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
