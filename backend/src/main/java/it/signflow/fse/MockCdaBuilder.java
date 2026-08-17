package it.signflow.fse;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Explicit local-test adapter. It records invocation through the ingestion
 * pipeline but intentionally emits no clinical artifact and no usable CDA.
 */
@Component
public class MockCdaBuilder implements CdaBuilder {
    @Override
    public Optional<byte[]> build(CdaBuildRequest request) {
        return Optional.empty();
    }
}
