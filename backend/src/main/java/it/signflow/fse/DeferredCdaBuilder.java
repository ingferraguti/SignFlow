package it.signflow.fse;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DeferredCdaBuilder implements CdaBuilder {
    @Override
    public Optional<byte[]> build(CdaBuildRequest request) {
        return Optional.empty();
    }
}
