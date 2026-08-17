package it.signflow.fse;

/** Provider-neutral boundary for FSE validation, submission and reconciliation. */
public interface FseGatewayAdapter {
    String code();
    AdapterResult validate(DeliveryRequest request);
    AdapterResult submit(DeliveryRequest request);
    AdapterResult reconcile(ReconciliationRequest request);
}
