"use client";

import { useEffect, useState } from "react";
import { fetchReportAuditTimeline, type AuditEvent } from "../lib/adminAudit";
import { useUiTexts } from "./UiTextProvider";

export function AdminReportAuditTimeline({ reportId }: { reportId: string }) {
  const { text } = useUiTexts();
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [error, setError] = useState<string>();
  useEffect(() => { fetchReportAuditTimeline(reportId).then(setEvents).catch((reason: Error) => setError(reason.message)); }, [reportId]);
  return <section className="workflow-panel audit-timeline"><h3>{text("label.reportTimeline")}</h3><p className="form-hint">Workflow, documenti, revisioni e tentativi di firma in ordine cronologico.</p>
    {error ? <p className="inline-error">{error}</p> : null}
    <div className="decision-timeline">{events.map((event) => <article key={event.id}><span className={`audit-outcome ${event.outcome.toLowerCase()}`}>{event.outcome}</span><div><strong>{event.eventType}</strong><small>{new Date(event.occurredAt).toLocaleString("it-IT")} · {event.actorId}</small><small>Correlation ID: {event.correlationId}</small>{event.reason ? <p>{event.reason}</p> : null}</div></article>)}</div>
    {!events.length && !error ? <p className="empty-state">Nessun evento ancora registrato.</p> : null}
  </section>;
}
