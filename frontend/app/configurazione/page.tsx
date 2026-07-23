import { AdminUsersPanel } from "../../components/AdminUsersPanel";
import { AdminOrganizationPanel } from "../../components/AdminOrganizationPanel";
import { AdminUiTextsPanel } from "../../components/AdminUiTextsPanel";

export default function ConfigurationPage() {
  return <div className="configuration-stack"><AdminUsersPanel /><AdminOrganizationPanel /><AdminUiTextsPanel /></div>;
}
