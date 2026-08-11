package it.signflow.signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SignatureProviderAdapterContractTest {
    private static Stream<Arguments> adapters() {
        var key = TestSignatureFixtures.testKeyMaterial();
        return Stream.of(
                Arguments.of(new AdapterCase("mock", new MockSignatureProvider(), "000000")),
                Arguments.of(new AdapterCase("local-test-pades",
                        new LocalTestSignatureProviderAdapter(new DssPadesSignatureEngine(), key.pkcs12(),
                                TestSignatureFixtures.PASSWORD), LocalTestSignatureProviderAdapter.TEST_AUTHORIZATION_CODE)));
    }

    @ParameterizedTest(name = "{0} completes the neutral lifecycle and is idempotent")
    @MethodSource("adapters")
    void completesProviderNeutralLifecycle(AdapterCase testCase) {
        SignatureProviderAdapter adapter = testCase.adapter();
        String correlation = "corr-contract-" + testCase.name();
        var session = adapter.openSession(new SignatureProviderAdapter.OpenSessionCommand(
                "TEST", "fictional-account", correlation, Duration.ofSeconds(5)));
        var challenge = adapter.requestChallenge(new SignatureProviderAdapter.ChallengeCommand(
                session.sessionReference(), correlation, Duration.ofSeconds(5)));
        var authentication = adapter.authenticate(new SignatureProviderAdapter.AuthenticationCommand(
                session.sessionReference(), challenge.challengeReference(), testCase.authorizationCode(),
                correlation, Duration.ofSeconds(5)));
        assertThat(authentication.authenticated()).isTrue();
        assertThat(authentication.correlationId()).isEqualTo(correlation);

        var command = new SignatureProviderAdapter.SignatureCommand(session.sessionReference(),
                "RPT-TEST-FICTIONAL-001", SignatureProviderAdapter.PayloadMode.DOCUMENT,
                TestSignatureFixtures.fictionalPdf(), "SHA-256", 0, 0, "idempotency-contract-001",
                new SignatureProviderAdapter.RetryPolicy(3, Duration.ofMillis(10)), correlation,
                Duration.ofSeconds(5));
        var submitted = adapter.submit(command);
        var repeated = adapter.submit(command);
        assertThat(repeated.operationReference()).isEqualTo(submitted.operationReference());

        var polled = adapter.poll(new SignatureProviderAdapter.PollCommand(session.sessionReference(),
                submitted.operationReference(), correlation, Duration.ofSeconds(5)));
        assertThat(polled.state()).isEqualTo(SignatureProviderAdapter.SubmissionState.SUCCEEDED);
        assertThat(polled.correlationId()).isEqualTo(correlation);
        var document = adapter.retrieve(new SignatureProviderAdapter.RetrieveCommand(session.sessionReference(),
                submitted.operationReference(), correlation, Duration.ofSeconds(5)));
        assertThat(document.content()).isNotEmpty();
        assertThat(document.providerReference()).isNotBlank();
        assertThat(document.correlationId()).isEqualTo(correlation);
    }

    @ParameterizedTest(name = "{0} returns typed authentication and timeout errors")
    @MethodSource("adapters")
    void exposesTypedErrorsAndTimeout(AdapterCase testCase) {
        SignatureProviderAdapter adapter = testCase.adapter();
        String correlation = "corr-errors-" + testCase.name();
        var session = adapter.openSession(new SignatureProviderAdapter.OpenSessionCommand(
                "TEST", "fictional-account", correlation, Duration.ofSeconds(5)));
        var challenge = adapter.requestChallenge(new SignatureProviderAdapter.ChallengeCommand(
                session.sessionReference(), correlation, Duration.ofSeconds(5)));
        var authentication = adapter.authenticate(new SignatureProviderAdapter.AuthenticationCommand(
                session.sessionReference(), challenge.challengeReference(), "wrong-code", correlation,
                Duration.ofSeconds(5)));
        assertThat(authentication.authenticated()).isFalse();
        assertThat(authentication.error().category()).isEqualTo(SignatureProviderAdapter.ErrorCategory.AUTHENTICATION);
        assertThat(authentication.error().retryable()).isFalse();

        assertThatThrownBy(() -> adapter.poll(new SignatureProviderAdapter.PollCommand(
                session.sessionReference(), "unused", correlation, Duration.ZERO)))
                .isInstanceOfSatisfying(SignatureProviderAdapter.ProviderAdapterException.class, exception -> {
                    assertThat(exception.error().category()).isEqualTo(SignatureProviderAdapter.ErrorCategory.TIMEOUT);
                    assertThat(exception.error().retryable()).isTrue();
                    assertThat(exception.correlationId()).isEqualTo(correlation);
                });
    }

    @ParameterizedTest(name = "{0} handles digest submission through the common result contract")
    @MethodSource("adapters")
    void acceptsDigestModeAtContractBoundary(AdapterCase testCase) {
        SignatureProviderAdapter adapter = testCase.adapter();
        String correlation = "corr-digest-" + testCase.name();
        var session = adapter.openSession(new SignatureProviderAdapter.OpenSessionCommand(
                "TEST", "fictional-account", correlation, Duration.ofSeconds(5)));
        var challenge = adapter.requestChallenge(new SignatureProviderAdapter.ChallengeCommand(
                session.sessionReference(), correlation, Duration.ofSeconds(5)));
        adapter.authenticate(new SignatureProviderAdapter.AuthenticationCommand(session.sessionReference(),
                challenge.challengeReference(), testCase.authorizationCode(), correlation, Duration.ofSeconds(5)));
        var result = adapter.submit(new SignatureProviderAdapter.SignatureCommand(session.sessionReference(),
                "RPT-DIGEST-FICTIONAL-001", SignatureProviderAdapter.PayloadMode.DIGEST,
                new byte[32], "SHA-256", 0, 0, "digest-contract-001",
                new SignatureProviderAdapter.RetryPolicy(2, Duration.ofMillis(10)), correlation,
                Duration.ofSeconds(5)));
        assertThat(result.correlationId()).isEqualTo(correlation);
        assertThat(result.state()).isIn(SignatureProviderAdapter.SubmissionState.SUCCEEDED,
                SignatureProviderAdapter.SubmissionState.FAILED);
        if (result.state() == SignatureProviderAdapter.SubmissionState.FAILED) {
            assertThat(result.error()).isNotNull();
            assertThat(result.error().category()).isEqualTo(SignatureProviderAdapter.ErrorCategory.VALIDATION);
        }
    }

    private record AdapterCase(String name, SignatureProviderAdapter adapter, String authorizationCode) {
        @Override public String toString() { return name; }
    }
}
