package it.signflow.fse;

import static it.signflow.fse.ExternalDeliveryModels.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@ConditionalOnProperty(prefix = "signflow.external-delivery", name = "demo-enabled", havingValue = "true")
public class DemoExternalDeliveryInitializer implements ApplicationRunner {
    private static final UUID ACCEPTED = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1");
    private static final UUID REJECTED = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff2");
    private final ExternalDeliveryWorkflowService service;
    private final JdbcClient jdbc;

    public DemoExternalDeliveryInitializer(ExternalDeliveryWorkflowService service, JdbcClient jdbc) {
        this.service = service; this.jdbc = jdbc;
    }

    @Override public void run(ApplicationArguments args) {
        progressAcceptedDemo();
        progressRejectedDemo();
    }

    private void progressAcceptedDemo() {
        OperationDetail fse = find(ACCEPTED, "FSE").orElseGet(() -> service.sendFse(ACCEPTED,
                command(ACCEPTED, "demo-fse-send"), "demo.admin"));
        if (fse.operation().state() == State.FSE_SENT || fse.operation().state() == State.TIMEOUT) {
            fse = service.reconcile(fse.operation().id(),
                    new CommandRequest(fse.operation().reportVersion(), "demo-fse-reconcile"), "demo.admin");
        }
        if (fse.operation().state() != State.FSE_ACCEPTED) return;
        OperationDetail conservation = find(ACCEPTED, "CONSERVATION").orElseGet(() -> service.sendConservation(
                ACCEPTED, command(ACCEPTED, "demo-conservation-send"), "demo.admin"));
        if (conservation.operation().state() == State.CONSERVATION_SENT || conservation.operation().state() == State.TIMEOUT) {
            service.reconcile(conservation.operation().id(),
                    new CommandRequest(conservation.operation().reportVersion(), "demo-conservation-reconcile"), "demo.admin");
        }
    }

    private void progressRejectedDemo() {
        OperationDetail fse = find(REJECTED, "FSE").orElseGet(() -> service.sendFse(REJECTED,
                command(REJECTED, "demo-fse-reject-send"), "demo.admin"));
        if (fse.operation().state() == State.FSE_SENT || fse.operation().state() == State.TIMEOUT) {
            service.reconcile(fse.operation().id(),
                    new CommandRequest(fse.operation().reportVersion(), "demo-fse-reject-reconcile"), "demo.admin");
        }
    }

    private CommandRequest command(UUID reportId, String key) {
        long version = jdbc.sql("select workflow_version from reports where id=:id").param("id", reportId)
                .query(Long.class).single();
        return new CommandRequest(version, key);
    }

    private Optional<OperationDetail> find(UUID reportId, String channel) {
        return jdbc.sql("select id from external_delivery_operations where report_id=:reportId and channel=:channel")
                .param("reportId", reportId).param("channel", channel).query(UUID.class).optional().map(service::detail);
    }
}
