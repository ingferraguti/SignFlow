package it.signflow.fse;

import java.util.Optional;

/** Port for a future FSE-profile-specific CDA R2 generator. */
public interface CdaBuilder {
    Optional<byte[]> build(CdaBuildRequest request);
}
