import { ApproverReportPanel } from "../../../components/ApproverReportPanel";
export default async function ApproverReportPage({ params }: { params: Promise<{ id: string }> }) { const { id } = await params; return <ApproverReportPanel reportId={id} />; }
