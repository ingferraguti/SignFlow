package it.signflow.fse;

import java.util.Optional;

/**
 * Non-Spring fallback useful to consumers that want to explicitly defer CDA
 * generation. The running application uses {@link MockCdaBuilder} until a real,
 * profile-specific adapter is configured.
 */
public class DeferredCdaBuilder implements CdaBuilder {
    @Override
    public Optional<byte[]> build(CdaBuildRequest request) {
        return Optional.empty();
    }
}
