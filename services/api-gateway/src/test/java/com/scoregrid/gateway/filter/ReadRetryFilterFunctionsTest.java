package com.scoregrid.gateway.filter;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which proxy failures RetryReads retries. RestClient wraps every I/O failure
 * in the same ResourceAccessException, so the decision rests on the cause.
 */
class ReadRetryFilterFunctionsTest {

    @Test
    void failuresToOpenTheConnection_areRetried() {
        assertThat(ReadRetryFilterFunctions.isConnectFailure(proxied(new HttpHostConnectException("refused")))).isTrue();
        assertThat(ReadRetryFilterFunctions.isConnectFailure(proxied(new ConnectException("refused")))).isTrue();
        assertThat(ReadRetryFilterFunctions.isConnectFailure(proxied(new NoRouteToHostException("no route")))).isTrue();
        assertThat(ReadRetryFilterFunctions.isConnectFailure(proxied(new ConnectTimeoutException("connect timed out")))).isTrue();
        assertThat(ReadRetryFilterFunctions.isConnectFailure(proxied(new HttpConnectTimeoutException("connect timed out")))).isTrue();
    }

    @Test
    void failuresAfterTheRequestWasSent_areNot() {
        // HttpClient 5's response timeout.
        assertThat(ReadRetryFilterFunctions.isConnectFailure(proxied(new SocketTimeoutException("Read timed out")))).isFalse();
        // The JDK client's.
        assertThat(ReadRetryFilterFunctions.isConnectFailure(proxied(new HttpTimeoutException("request timed out")))).isFalse();
        assertThat(ReadRetryFilterFunctions.isConnectFailure(proxied(new SocketException("Connection reset")))).isFalse();
        assertThat(ReadRetryFilterFunctions.isConnectFailure(proxied(new IOException("Premature EOF")))).isFalse();
    }

    @Test
    void responsesAndNothing_areNot() {
        // How the lb() filter reports "no instance": a response, not a connect failure.
        assertThat(ReadRetryFilterFunctions.isConnectFailure(
                new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance"))).isFalse();
        assertThat(ReadRetryFilterFunctions.isConnectFailure(null)).isFalse();
    }

    private static ResourceAccessException proxied(IOException cause) {
        return new ResourceAccessException("I/O error on GET request for \"http://10.0.0.7:8082/api/tournaments\"", cause);
    }
}
