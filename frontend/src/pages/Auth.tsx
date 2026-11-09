import { useState, type FormEvent, type ReactElement, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import Field, { validateForm } from "../components/Field";
import { vEmail, vPassword, vPersonName, vRequired, type Validator } from "../components/validation";

function AuthCard({
  title,
  children,
  footer,
}: {
  title: string;
  children: ReactNode;
  footer: ReactNode;
}): ReactElement {
  return (
    <div className="container mx-auto px-4 py-12 max-w-md">
      <div className="bg-white border border-gray-200 rounded-xl shadow-sm overflow-hidden">
        <img
          src="/logo.png"
          alt="poslitodal – bazar jednoduše"
          className="w-full h-44 object-contain bg-white p-2"
        />
        <div className="p-6">
          <h1 className="text-2xl font-bold text-navydeep mb-4">{title}</h1>
          {children}
          <div className="mt-4 text-sm text-gray-600">{footer}</div>
        </div>
      </div>
    </div>
  );
}

export function ErrorBox({ message }: { message: string }): ReactElement | null {
  if (!message) return null;
  return (
    <p className="bg-red-50 border border-red-200 text-brand rounded px-3 py-2 text-sm mb-4">
      {message}
    </p>
  );
}

export const inputCls =
  "w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-navy focus:border-navy";

export default function Login(): ReactElement {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const errs = validateForm(
      { email, password },
      { email: vEmail, password: vRequired("Heslo") },
    );
    if (Object.keys(errs).length) {
      setError(errs.email ?? errs.password ?? "");
      return;
    }
    setBusy(true);
    try {
      await login(email.trim(), password);
      nav("/");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthCard
      title="Přihlášení"
      footer={
        <>
          Nemáte účet?{" "}
          <Link to="/registrace" className="text-brand font-semibold">
            Zaregistrujte se
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <ErrorBox message={error} />
        <Field
          label="E-mail"
          required
          type="email"
          value={email}
          onChange={setEmail}
          validate={vEmail}
          placeholder="např. jan.novak@email.cz"
          autoComplete="email"
        />
        <Field
          label="Heslo"
          required
          type="password"
          value={password}
          onChange={setPassword}
          validate={vRequired("Heslo")}
          placeholder="Vaše heslo"
          autoComplete="current-password"
        />
        <button
          disabled={busy}
          className="bg-brand hover:bg-branddark text-white rounded py-2.5 font-semibold disabled:opacity-60"
        >
          {busy ? "Přihlásím…" : "Přihlásit"}
        </button>
      </form>
    </AuthCard>
  );
}

const registerRules: Record<string, Validator> = {
  email: vEmail,
  password: vPassword,
  firstName: vPersonName("Jméno"),
  lastName: vPersonName("Příjmení"),
};

type RegisterForm = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  sellerType: string;
}

export function Register(): ReactElement {
  const { register } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState<RegisterForm>({
    email: "",
    password: "",
    firstName: "",
    lastName: "",
    sellerType: "private",
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const set = (k: keyof RegisterForm, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const errs = validateForm(form, registerRules);
    const first = Object.values(errs)[0];
    if (first) {
      setError(first);
      return;
    }
    setError("");
    setBusy(true);
    try {
      const displayName = `${form.firstName.trim()} ${form.lastName.trim()}`;
      await register(form.email.trim(), form.password, displayName, form.sellerType);
      nav("/");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthCard
      title="Registrace"
      footer={
        <>
          Máte účet?{" "}
          <Link to="/prihlaseni" className="text-brand font-semibold">
            Přihlaste se
          </Link>
        </>
      }
    >
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <ErrorBox message={error} />
        <Field
          label="E-mail"
          required
          type="email"
          value={form.email}
          onChange={(v) => set("email", v)}
          validate={vEmail}
          placeholder="např. jan.novak@email.cz"
          autoComplete="email"
        />
        <Field
          label="Heslo"
          required
          type="password"
          value={form.password}
          onChange={(v) => set("password", v)}
          validate={vPassword}
          placeholder="Minimálně 8 znaků"
          autoComplete="new-password"
          hint="Alespoň 8 znaků."
        />
        <div className="grid grid-cols-2 gap-3">
          <Field
            label="Jméno"
            required
            value={form.firstName}
            onChange={(v) => set("firstName", v)}
            validate={registerRules.firstName}
            placeholder="např. Jan"
            autoComplete="given-name"
          />
          <Field
            label="Příjmení"
            required
            value={form.lastName}
            onChange={(v) => set("lastName", v)}
            validate={registerRules.lastName}
            placeholder="např. Novák"
            autoComplete="family-name"
          />
        </div>
        <label className="text-sm">
          <span className="font-medium text-gray-700">Typ prodejce</span>
          <select
            value={form.sellerType}
            onChange={(e) => set("sellerType", e.target.value)}
            className={`${inputCls} mt-1`}
          >
            <option value="private">Soukromník</option>
            <option value="trader">Obchodník (IČO + doklad + čestné prohlášení)</option>
          </select>
        </label>
        <button
          disabled={busy}
          className="bg-brand hover:bg-branddark text-white rounded py-2.5 font-semibold disabled:opacity-60"
        >
          {busy ? "Registruji…" : "Registrovat"}
        </button>
        <p className="text-xs text-gray-500">
          Registrací souhlasíte se zpracováním e-mailu a hesla pro účely účtu. Daňové údaje
          vyplníte až v sekci <Link to="/ucet" className="underline">Můj účet</Link>.
        </p>
      </form>
    </AuthCard>
  );
}