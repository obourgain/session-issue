package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() throws Exception {
        var uri = URI.create("http://localhost:" + port);
        var httpClient = HttpClient.newHttpClient();

        // send the first request as admin
        var request1 = HttpRequest.newBuilder(uri)
                .headers("SM_USER", "admin")
                .build();
        var response1 = httpClient.send(request1, HttpResponse.BodyHandlers.ofString());
        Optional<String> header1 = response1.headers().firstValue("Set-Cookie");
        assertThat(header1).isPresent();
        assertThat(header1.get()).startsWith("JSESSIONID");
        assertThat(response1.body()).contains("Username=admin");
        // a session was created by Spring Security even if using SessionCreationPolicy.NEVER
        // this is done in AbstractPreAuthenticatedProcessingFilter.successfulAuthentication() because AbstractPreAuthenticatedProcessingFilter
        // instantiate a HttpSessionSecurityContextRepository and doesn't configure it to not allow session creation

        // Now perform a request with another user, but with the same session id
        // that's not a so weird use case. Assume the caller is an application performing API calls on behalf of users, and for some
        // reason it's HTTP client stores and sends the cookies.
        var request2 = HttpRequest.newBuilder(uri)
                .headers(
                        "SM_USER", "user",
                        "cookie", header1.get())
                .build();
        var response2 = httpClient.send(request2, HttpResponse.BodyHandlers.ofString());
        // 😱 That should have been "Username=user" !
        assertThat(response2.body()).contains("Username=admin");

        // If we send the request without the session cookie, all is fine.
        // Spring Security should not:
        // * create a session if SessionCreationPolicy is NEVER
        // * favor retrieving the authentication from the session instead of from the request headers in RequestHeaderAuthenticationFilter
        var request3 = HttpRequest.newBuilder(uri)
                .headers("SM_USER", "user")
                .build();
        var response3 = httpClient.send(request3, HttpResponse.BodyHandlers.ofString());
        // this is what is expected
        assertThat(response3.body()).contains("Username=user");

        // note that MockMvc doesn't exhibit the same behavior. It seems to create a MockHttpSession to store the authentication and the Set-Cookie header is not sent, so I
        // had to start a real server for this test.
    }
}
