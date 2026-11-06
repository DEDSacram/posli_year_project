import { useState, type ReactElement } from "react";
import type { Validator } from "./validation";

/**
 * Pole formuláře s okamžitou validací. Atributy jsou vyjmenované, protože
 * komponenta renderuje buď `<input>`, nebo `<textarea>` — univerzální
 * `InputHTMLAttributes` nelze použít na obou.
 */
interface FieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  validate?: Validator;
  placeholder?: string;
  type?: string;
  required?: boolean;
  hint?: string;
  textarea?: boolean;
  rows?: number;
  autoComplete?: string;
  min?: number;
  maxLength?: number;
  disabled?: boolean;
}

/**
 * Pole s okamžitou validací: chyba se ukáže po odchodu z pole (blur),
 * pak už průběžně. Po dotyku a bez chyby svítí zelená značka.
 */
export default function Field({
  label, value, onChange, validate, placeholder, type = "text",
  required = false, hint = "", textarea = false, rows = 3,
  autoComplete, min, maxLength, disabled,
}: FieldProps): ReactElement {
  const [touched, setTouched] = useState(false);
  const error = validate ? validate(value) : "";
  const showError = touched && error;
  const showOk = touched && !error && String(value ?? "").trim() !== "";

  const cls = `w-full border rounded px-3 py-2 text-sm focus:outline-none focus:ring-1 ${
    showError
      ? "border-red-400 focus:ring-red-400 focus:border-red-400 bg-red-50/40"
      : showOk
        ? "border-green-500 focus:ring-green-500 focus:border-green-500"
        : "border-gray-300 focus:ring-navy focus:border-navy"
  }`;

  const control = textarea ? (
    <textarea
      value={value}
      rows={rows}
      placeholder={placeholder}
      maxLength={maxLength}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
      onBlur={() => setTouched(true)}
      className={cls}
    />
  ) : (
    <input
      type={type}
      value={value}
      placeholder={placeholder}
      autoComplete={autoComplete}
      min={min}
      maxLength={maxLength}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
      onBlur={() => setTouched(true)}
      className={cls}
    />
  );

  return (
    <label className="block text-sm">
      <span className="font-medium text-gray-700">
        {label} {required && <span className="text-brand">*</span>}
        {showOk && <span className="text-green-600 ml-1">✓</span>}
      </span>
      <span className="block mt-1">{control}</span>
      {showError && <span className="block text-red-600 text-xs mt-1">{error}</span>}
      {!showError && hint && <span className="block text-gray-400 text-xs mt-1">{hint}</span>}
    </label>
  );
}

/** Ověří celý objekt hodnot; vrací jen klíče, které neprošly. */
export function validateForm<T extends Record<string, unknown>>(
  values: T,
  rules: Record<string, Validator>,
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const [k, fn] of Object.entries(rules)) {
    const e = fn(values[k]);
    if (e) errors[k] = e;
  }
  return errors;
}

/** Možnosti jako `["Hodnota"]` nebo dvojice `[["val", "Popisek"]]`. */
export type SelectOption = string | readonly [string, string];

interface SelectFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  validate?: Validator;
  required?: boolean;
  hint?: string;
  options?: readonly SelectOption[];
  placeholder?: string;
}

/** Rozevírací se stejnou validační UX jako {@link Field}. */
export function SelectField({
  label, value, onChange, validate, required = false, hint = "",
  options = [], placeholder = "Vyberte…",
}: SelectFieldProps): ReactElement {
  const [touched, setTouched] = useState(false);
  const error = validate ? validate(value) : "";
  const showError = touched && error;
  const showOk = touched && !error && String(value ?? "").trim() !== "";

  const cls = `w-full border rounded px-3 py-2 text-sm bg-white focus:outline-none focus:ring-1 ${
    showError
      ? "border-red-400 focus:ring-red-400 focus:border-red-400 bg-red-50/40"
      : showOk
        ? "border-green-500 focus:ring-green-500 focus:border-green-500"
        : "border-gray-300 focus:ring-navy focus:border-navy"
  }`;

  return (
    <label className="block text-sm">
      <span className="font-medium text-gray-700">
        {label} {required && <span className="text-brand">*</span>}
        {showOk && <span className="text-green-600 ml-1">✓</span>}
      </span>
      <span className="block mt-1">
        <select
          value={value ?? ""}
          onChange={(e) => {
            onChange(e.target.value);
            setTouched(true);
          }}
          onBlur={() => setTouched(true)}
          className={cls}
        >
          <option value="">{placeholder}</option>
          {options.map((o) => {
            const [v, l] = Array.isArray(o) ? o : [o, o];
            return (
              <option key={v} value={v}>
                {l}
              </option>
            );
          })}
        </select>
      </span>
      {showError && <span className="block text-red-600 text-xs mt-1">{error}</span>}
      {!showError && hint && <span className="block text-gray-400 text-xs mt-1">{hint}</span>}
    </label>
  );
}