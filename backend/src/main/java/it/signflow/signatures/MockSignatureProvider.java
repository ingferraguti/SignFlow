package it.signflow.signatures;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockSignatureProvider implements SignatureProviderAdapter {
    static final String MOCK_NOTICE = "MOCK ONLY - NON È UNA FIRMA DIGITALE VALIDA";

    @Override
    public String adapterType() {
        return "MOCK";
    }

    @Override
    public AuthenticationResult authenticate(AuthenticationCommand command) {
        boolean valid = "000000".equals(command.authorizationCode());
        return new AuthenticationResult(valid, valid ? "Sessione mock autorizzata"
                : "Codice mock non valido; usare 000000 nell'ambiente demo");
    }

    @Override
    public SignatureResult sign(SignatureCommand command) {
        if (command.retryCount() < command.failuresBeforeSuccess()) {
            return SignatureResult.failure("MOCK_PLANNED_FAILURE",
                    "Errore simulato dal provider mock per verificare retry e successo parziale");
        }
        String reference = "MOCK-NON-LEGAL-" + UUID.randomUUID();
        String filename = "MOCK-ONLY-" + command.reportIdentifier() + ".txt";
        String content = MOCK_NOTICE + "\n"
                + "Artefatto di solo collaudo SignFlow.\n"
                + "Referto fittizio: " + command.reportIdentifier() + "\n"
                + "Riferimento mock: " + reference + "\n"
                + "Generato: " + OffsetDateTime.now() + "\n"
                + "Questo file non contiene certificati, firme crittografiche o valore legale.\n";
        return SignatureResult.success(reference, filename, content);
    }
}
