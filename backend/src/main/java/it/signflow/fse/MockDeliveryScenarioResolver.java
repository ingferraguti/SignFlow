package it.signflow.fse;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class MockDeliveryScenarioResolver {
    private final JdbcClient jdbc;

    MockDeliveryScenarioResolver(JdbcClient jdbc) { this.jdbc = jdbc; }

    AdapterResult.Status outcome(String contextReference, String channel, int submissionNumber,
                                 int reconciliationNumber) {
        UUID reportId = UUID.fromString(contextReference);
        Scenario scenario = jdbc.sql("""
                select * from mock_external_delivery_scenarios where report_id=:reportId
                """).param("reportId", reportId).query((rs, row) -> new Scenario(
                        rs.getInt("fse_rejections_before_acceptance"),
                        rs.getInt("fse_timeouts_before_result"),
                        rs.getInt("conservation_rejections_before_acceptance"),
                        rs.getInt("conservation_timeouts_before_result"))).optional()
                .orElse(new Scenario(0, 0, 0, 0));
        int timeouts = "FSE".equals(channel) ? scenario.fseTimeouts() : scenario.conservationTimeouts();
        int rejections = "FSE".equals(channel) ? scenario.fseRejections() : scenario.conservationRejections();
        if (reconciliationNumber <= timeouts) return AdapterResult.Status.TIMEOUT;
        return submissionNumber <= rejections ? AdapterResult.Status.REJECTED : AdapterResult.Status.ACCEPTED;
    }

    private record Scenario(int fseRejections, int fseTimeouts,
                            int conservationRejections, int conservationTimeouts) {}
}
