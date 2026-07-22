import { LoginButton } from "../../components/LoginButton";

export default function LoginPage() {
  return (
    <section className="card login-card">
      <h1>SignFlow login</h1>
      <p>Use the local Keycloak demo identities only in the Docker Compose environment.</p>
      <div className="demo-credentials">
        <p><strong>Administrator:</strong> demo.admin / local-admin-password</p>
        <p><strong>Signer:</strong> demo.signer / local-signer-password</p>
      </div>
      <LoginButton />
    </section>
  );
}
