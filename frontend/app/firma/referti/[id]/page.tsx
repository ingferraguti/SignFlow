import { SignerReportDetailPanel } from "../../../../components/SignerReportDetailPanel";

export default async function SignerReportPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params; return <SignerReportDetailPanel reportId={id} />;
}
