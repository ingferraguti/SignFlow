import { SignatureBatchDetailPanel } from "../../../../components/SignatureBatchDetailPanel";
export default async function SignatureBatchPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params; return <SignatureBatchDetailPanel batchId={id} />;
}
