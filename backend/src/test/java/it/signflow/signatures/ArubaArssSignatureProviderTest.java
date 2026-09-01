package it.signflow.signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ArubaArssSignatureProviderTest {
    @Test
    void opensArubaSessionSignsPdfAndReturnsTheSignedDocument() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<String> open = mock(HttpResponse.class);
        @SuppressWarnings("unchecked") HttpResponse<String> sign = mock(HttpResponse.class);
        when(open.statusCode()).thenReturn(200);
        when(open.body()).thenReturn(envelope("<return>aruba-provider-session</return>"));
        when(sign.statusCode()).thenReturn(200);
        when(sign.body()).thenReturn(envelope("<return><status>OK</status><return_code>ARUBA-REF-1</return_code>"
                + "<binaryoutput>c2lnbmVkLXBkZg==</binaryoutput></return>"));
        when(client.<String>send(any(), any())).thenReturn(open, sign);
        ArubaArssSignatureProvider provider = new ArubaArssSignatureProvider(
                new ArubaSignatureProperties("https://example.invalid/arss", "test-user", "test-password", "demo", "AS0"), client);

        var session = provider.openSession(new SignatureProviderAdapter.OpenSessionCommand(
                "ARUBA-REMOTE", "test-account", "corr-aruba", Duration.ofSeconds(2)));
        var challenge = provider.requestChallenge(new SignatureProviderAdapter.ChallengeCommand(
                session.sessionReference(), "corr-aruba", Duration.ofSeconds(2)));
        var authenticated = provider.authenticate(new SignatureProviderAdapter.AuthenticationCommand(
                session.sessionReference(), challenge.challengeReference(), "123456", "corr-aruba", Duration.ofSeconds(2)));
        assertThat(authenticated.authenticated()).isTrue();

        var submitted = provider.submit(new SignatureProviderAdapter.SignatureCommand(session.sessionReference(),
                "RPT-FICTIONAL", SignatureProviderAdapter.PayloadMode.DOCUMENT, new byte[]{1, 2, 3}, "SHA-256",
                0, 0, "aruba-idempotency", new SignatureProviderAdapter.RetryPolicy(1, Duration.ZERO),
                "corr-aruba", Duration.ofSeconds(2)));
        assertThat(submitted.state()).isEqualTo(SignatureProviderAdapter.SubmissionState.SUCCEEDED);
        assertThat(submitted.providerReference()).isEqualTo("ARUBA-REF-1");
        var document = provider.retrieve(new SignatureProviderAdapter.RetrieveCommand(session.sessionReference(),
                submitted.operationReference(), "corr-aruba", Duration.ofSeconds(2)));
        assertThat(document.mediaType()).isEqualTo("application/pdf");
        assertThat(document.content()).containsExactly("signed-pdf".getBytes());
    }

    private static String envelope(String body) {
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body>"
                + body + "</soapenv:Body></soapenv:Envelope>";
    }
}
