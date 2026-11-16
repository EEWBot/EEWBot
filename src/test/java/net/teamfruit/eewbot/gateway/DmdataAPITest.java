package net.teamfruit.eewbot.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DmdataAPITest {

    @SuppressWarnings("unchecked")
    private static HttpClient clientReturning(int status, String body) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        doReturn(response).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        return client;
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html><body>502 Bad Gateway</body></html>", ""})
    void unparseableErrorBodyIsReportedAsGatewayException(String body) throws Exception {
        DmdataAPI api = new DmdataAPI(clientReturning(502, body), "key", "https://example.com");

        assertThatThrownBy(api::openSocketList).isInstanceOf(DmdataGatewayException.class);
        assertThatThrownBy(api::contract).isInstanceOf(DmdataGatewayException.class);
        assertThatThrownBy(() -> api.socketClose("1")).isInstanceOf(DmdataGatewayException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestsCarryATimeout() throws Exception {
        HttpClient client = clientReturning(200, "{\"items\":[]}");
        new DmdataAPI(client, "key", "https://example.com").openSocketList();

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().timeout()).isPresent();
    }
}
