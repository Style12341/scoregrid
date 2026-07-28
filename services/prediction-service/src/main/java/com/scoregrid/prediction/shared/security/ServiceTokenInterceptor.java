package com.scoregrid.prediction.shared.security;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    private final ServiceTokenProvider tokenProvider;

    public ServiceTokenInterceptor(ServiceTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().setBearerAuth(tokenProvider.generate());
        return execution.execute(request, body);
    }
}
