import { AdminUsersPanel } from "../../components/AdminUsersPanel";
import { AdminOrganizationPanel } from "../../components/AdminOrganizationPanel";
import { AdminUiTextsPanel } from "../../components/AdminUiTextsPanel";
import { AdminTechnicalConfigurationPanel } from "../../components/AdminTechnicalConfigurationPanel";

export default function ConfigurationPage() {
  return <div className="configuration-stack"><AdminUsersPanel /><AdminOrganizationPanel /><AdminTechnicalConfigurationPanel /><AdminUiTextsPanel /></div>;
}
