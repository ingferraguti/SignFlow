package it.signflow.fse;

/** Provider-neutral boundary for digital-preservation submission and reconciliation. */
public interface ConservationAdapter {
    String code();
    AdapterResult submit(DeliveryRequest request);
    AdapterResult reconcile(ReconciliationRequest request);
}
