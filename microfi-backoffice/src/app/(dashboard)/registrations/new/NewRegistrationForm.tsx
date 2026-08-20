"use client";

import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Input } from "@/components/Input";
import { Button } from "@/components/Button";
import { ErrorDialog } from "@/components/ErrorDialog";
import { Icon, type IconName } from "@/components/Icon";
import { PageHeader } from "@/components/PageHeaderContext";
import { kycFormatFor, matchesKycFormat } from "@/lib/kycFormats";
import type { AdminRole, BranchResponse, RegistrationTargetRole } from "@/lib/types";

type WizardStep = "role-branch" | "identity" | "documents" | "review";

const STEPS: { key: WizardStep; label: string }[] = [
  { key: "role-branch", label: "Role & Branch" },
  { key: "identity", label: "Identity" },
  { key: "documents", label: "Documents" },
  { key: "review", label: "Review" },
];

// CEMAC member states — the platform's actual deployment zone, not a generic country list. `iso`
// doubles as the key into kycFormats.ts, so National ID/Tax ID validation follows whichever
// country is selected here.
const COUNTRY_CODES: { code: string; iso: string; flag: string; label: string }[] = [
  { code: "+237", iso: "CM", flag: "🇨🇲", label: "Cameroon" },
  { code: "+241", iso: "GA", flag: "🇬🇦", label: "Gabon" },
  { code: "+235", iso: "TD", flag: "🇹🇩", label: "Chad" },
  { code: "+236", iso: "CF", flag: "🇨🇫", label: "Central African Republic" },
  { code: "+242", iso: "CG", flag: "🇨🇬", label: "Congo" },
  { code: "+240", iso: "GQ", flag: "🇬🇶", label: "Equatorial Guinea" },
];

const ROLE_META: Record<RegistrationTargetRole, { label: string; icon: IconName }> = {
  AGENT: { label: "Field Agent", icon: "person" },
  BRANCH_MANAGER: { label: "Branch Manager", icon: "agents" },
  BRANCH_CASHIER: { label: "Branch Cashier", icon: "account-balance-wallet" },
};

const DOCUMENT_FIELDS: { key: keyof WizardFiles; label: string }[] = [
  { key: "nationalId", label: "National ID / Passport scan" },
  { key: "criminalRecord", label: "Criminal record (casier judiciaire)" },
  { key: "medicalFitness", label: "Medical fitness certificate" },
  { key: "locationPlan", label: "Location / home plan sketch" },
  { key: "passportPhoto", label: "Passport photo" },
];

interface WizardFiles {
  nationalId: File | null;
  criminalRecord: File | null;
  medicalFitness: File | null;
  locationPlan: File | null;
  passportPhoto: File | null;
}

const EMPTY_FILES: WizardFiles = {
  nationalId: null,
  criminalRecord: null,
  medicalFitness: null,
  locationPlan: null,
  passportPhoto: null,
};

type AvailabilityField = "LOGIN" | "PHONE" | "EMAIL" | "NATIONAL_ID" | "TAX_ID";

/**
 * Live per-field uniqueness check (debounced ~500ms) against GET /admin/registration-applications/
 * availability — surfaces a duplicate username/phone/email/National ID/Tax ID immediately while
 * the person is still filling in the Identity step, not only when they hit "Submit for Review"
 * on the final step. A blank value is always reported available (no network call needed).
 */
function useAvailability(field: AvailabilityField, rawValue: string): { taken: boolean } {
  const value = rawValue.trim();
  // Only ever set from inside the async callback (never synchronously in the effect body) —
  // `checkedValue` tracks which value the last result actually belongs to, so a blank field or a
  // still-pending/stale check simply renders as "not taken" without needing an effect-driven
  // reset. The final submit-time check on the backend remains the authoritative backstop either way.
  const [checkedValue, setCheckedValue] = useState<string | null>(null);
  const [taken, setTaken] = useState(false);

  useEffect(() => {
    if (!value) {
      return;
    }
    let cancelled = false;
    const timeout = setTimeout(() => {
      fetch(`/api/registration-applications/availability?field=${field}&value=${encodeURIComponent(value)}`)
        .then((res) => (res.ok ? res.json() : { available: true }))
        .then((body: { available: boolean }) => {
          if (cancelled) return;
          setCheckedValue(value);
          setTaken(!body.available);
        })
        .catch(() => {
          if (cancelled) return;
          setCheckedValue(value);
          setTaken(false);
        });
    }, 500);
    return () => {
      cancelled = true;
      clearTimeout(timeout);
    };
  }, [field, value]);

  return { taken: value !== "" && checkedValue === value && taken };
}

// Red asterisk — the visual marker for every mandatory field on this form (Role, Branch, First/Last
// Name, Phone, Username, Email for agents, all five documents). Everything else (Employee Code,
// National ID/Tax ID Number, Place of Residence, Criminal Record Issue Date) is optional and left unmarked.
function Req() {
  return (
    <span className="text-danger-red ml-0.5" aria-hidden>
      *
    </span>
  );
}

export function NewRegistrationForm({
  branches,
  callerRole,
  callerBranchId,
}: {
  branches: BranchResponse[];
  callerRole: AdminRole;
  callerBranchId: string | null;
}) {
  const router = useRouter();
  const availableRoles: RegistrationTargetRole[] =
    callerRole === "ADMIN" ? ["AGENT", "BRANCH_MANAGER", "BRANCH_CASHIER"] : ["AGENT", "BRANCH_CASHIER"];
  const selectableBranches = callerRole === "BRANCH_MANAGER" ? branches.filter((b) => b.id === callerBranchId) : branches;

  const [step, setStep] = useState<WizardStep>("role-branch");
  const [targetRole, setTargetRole] = useState<RegistrationTargetRole>("AGENT");
  const [branchId, setBranchId] = useState(selectableBranches[0]?.id ?? "");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [countryCode, setCountryCode] = useState(COUNTRY_CODES[0].code);
  const [localNumber, setLocalNumber] = useState("");
  const [login, setLogin] = useState("");
  const [email, setEmail] = useState("");
  const [employeeCode, setEmployeeCode] = useState("");
  const [nationalIdNumber, setNationalIdNumber] = useState("");
  const [taxIdNumber, setTaxIdNumber] = useState("");
  const [placeOfResidence, setPlaceOfResidence] = useState("");
  const [criminalRecordIssuedDate, setCriminalRecordIssuedDate] = useState("");
  const [files, setFiles] = useState<WizardFiles>(EMPTY_FILES);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleFileChange(key: keyof WizardFiles) {
    return (e: ChangeEvent<HTMLInputElement>) => setFiles((f) => ({ ...f, [key]: e.target.files?.[0] ?? null }));
  }

  const isAgent = targetRole === "AGENT";
  const stepIndex = STEPS.findIndex((s) => s.key === step);

  // National ID/Tax ID are optional — format is only checked once something is actually typed, an
  // unmatched/future country still falls back to a generous check rather than ever hard-blocking.
  const countryIso = COUNTRY_CODES.find((c) => c.code === countryCode)?.iso ?? "";
  const kycFormat = kycFormatFor(countryIso);
  const nationalIdError =
    nationalIdNumber.trim() !== "" && !matchesKycFormat(nationalIdNumber, kycFormat.nationalId.pattern)
      ? `Expected format: ${kycFormat.nationalId.hint}`
      : undefined;
  const taxIdError =
    taxIdNumber.trim() !== "" && !matchesKycFormat(taxIdNumber, kycFormat.taxId.pattern)
      ? `Expected format: ${kycFormat.taxId.hint}`
      : undefined;
  const emailFormatError =
    isAgent && email.trim() !== "" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
      ? "Enter a valid email address, e.g. name@example.com"
      : undefined;

  // No two agents/cashiers/managers may share a username, phone, email, National ID Number, or
  // Tax ID Number — checked live as each field is filled in, not only at the final submit.
  const loginAvailability = useAvailability("LOGIN", login);
  const phoneAvailability = useAvailability("PHONE", localNumber.trim() ? countryCode + localNumber.replace(/\D/g, "") : "");
  const emailAvailability = useAvailability("EMAIL", isAgent && !emailFormatError ? email : "");
  const nationalIdAvailability = useAvailability("NATIONAL_ID", nationalIdError ? "" : nationalIdNumber);
  const taxIdAvailability = useAvailability("TAX_ID", taxIdError ? "" : taxIdNumber);

  const loginError = loginAvailability.taken ? "This username is already in use or pending review" : undefined;
  const phoneError = phoneAvailability.taken ? "This phone number is already in use or pending review" : undefined;
  const emailError = emailFormatError ?? (emailAvailability.taken ? "This email address is already in use or pending review" : undefined);
  const nationalIdDisplayError = nationalIdError ?? (nationalIdAvailability.taken ? "This National ID Number is already in use or pending review" : undefined);
  const taxIdDisplayError = taxIdError ?? (taxIdAvailability.taken ? "This Tax ID Number is already in use or pending review" : undefined);

  const identityValid =
    firstName.trim() !== "" &&
    lastName.trim() !== "" &&
    localNumber.trim() !== "" &&
    login.trim() !== "" &&
    (!isAgent || email.trim() !== "") &&
    !nationalIdDisplayError &&
    !taxIdDisplayError &&
    !emailError &&
    !loginError &&
    !phoneError;
  const documentsValid = Object.values(files).every((f) => f !== null);

  function goNext() {
    const idx = STEPS.findIndex((s) => s.key === step);
    if (idx < STEPS.length - 1) setStep(STEPS[idx + 1].key);
  }
  function goBack() {
    const idx = STEPS.findIndex((s) => s.key === step);
    if (idx > 0) setStep(STEPS[idx - 1].key);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const metadata = {
        targetRole,
        branchId,
        firstName,
        lastName,
        phone: countryCode + localNumber.replace(/\D/g, ""),
        login,
        email: isAgent ? email : undefined,
        employeeCode: isAgent ? employeeCode || undefined : undefined,
        nationalIdNumber: nationalIdNumber || undefined,
        taxIdNumber: taxIdNumber || undefined,
        placeOfResidence: placeOfResidence || undefined,
        criminalRecordIssuedDate: criminalRecordIssuedDate || undefined,
      };
      const formData = new FormData();
      formData.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
      for (const { key } of DOCUMENT_FIELDS) {
        if (files[key]) formData.append(key, files[key] as File);
      }

      const res = await fetch("/api/registration-applications", { method: "POST", body: formData });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? "Failed to submit application");
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        router.push("/registrations");
        router.refresh();
      }, 800);
    } catch {
      setError("Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-4xl mx-auto w-full flex flex-col gap-6">
      <Link href="/registrations" className="text-sm text-primary hover:underline underline-offset-2 font-medium flex items-center gap-1 w-fit">
        <Icon name="arrow-upward" className="size-4 -rotate-90" />
        Back to Registrations
      </Link>
      <PageHeader title="New Registration Application" subtitle="Compliance-gated enrollment — no account is created until an ADMIN approves it." />

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant p-6">
        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div className="flex items-center gap-2">
            {STEPS.map((s, i) => (
              <div key={s.key} className={`h-1.5 flex-1 rounded-full ${i <= stepIndex ? "bg-primary" : "bg-outline-variant"}`} />
            ))}
          </div>
          <p className="text-sm font-semibold text-on-surface-variant uppercase tracking-widest">
            Step {stepIndex + 1} of {STEPS.length}: {STEPS[stepIndex].label}
          </p>

          {step === "role-branch" && (
            <>
              <div>
                <p className="text-base font-semibold text-on-surface mb-2">
                  Role
                  <Req />
                </p>
                <div className="grid grid-cols-3 gap-2">
                  {availableRoles.map((r) => {
                    const active = r === targetRole;
                    const meta = ROLE_META[r];
                    return (
                      <button
                        key={r}
                        type="button"
                        onClick={() => setTargetRole(r)}
                        className={`flex flex-col items-center gap-1.5 py-3 px-2 rounded-[var(--radius-sm)] border-2 cursor-pointer transition-[background-color,border-color,color,transform] duration-150 ease-out hover:scale-[1.02] active:scale-95 ${
                          active ? "border-primary bg-primary-container/10 text-primary" : "border-outline-variant text-on-surface-variant hover:border-primary/40"
                        }`}
                      >
                        <Icon name={meta.icon} className="size-6" />
                        <span className="text-sm font-semibold text-center leading-tight">{meta.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
              <div className="flex flex-col gap-1">
                <label htmlFor="wizard-branch" className="text-base font-semibold text-on-surface">
                  Branch
                  <Req />
                </label>
                <select
                  id="wizard-branch"
                  value={branchId}
                  onChange={(e) => setBranchId(e.target.value)}
                  disabled={selectableBranches.length === 1}
                  className="min-h-12 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-base disabled:text-text-grey-disabled disabled:bg-surface-grey-100"
                  required
                >
                  {selectableBranches.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.name} ({b.code})
                    </option>
                  ))}
                </select>
              </div>
            </>
          )}

          {step === "identity" && (
            <div className="grid grid-cols-2 gap-4">
              <Input label={<>First Name<Req /></>} labelClassName="!text-base" className="!text-base" value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
              <Input label={<>Last Name<Req /></>} labelClassName="!text-base" className="!text-base" value={lastName} onChange={(e) => setLastName(e.target.value)} required />
              <div className="flex flex-col gap-1 col-span-2">
                <label htmlFor="wizard-phone" className="text-base font-semibold text-on-surface">
                  Phone
                  <Req />
                </label>
                <div className="flex gap-2">
                  <select
                    aria-label="Country code"
                    value={countryCode}
                    onChange={(e) => setCountryCode(e.target.value)}
                    className="min-h-12 px-2 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-base shrink-0"
                  >
                    {COUNTRY_CODES.map((c) => (
                      <option key={c.code} value={c.code}>
                        {c.flag} {c.code}
                      </option>
                    ))}
                  </select>
                  <input
                    id="wizard-phone"
                    type="tel"
                    inputMode="numeric"
                    placeholder="600000000"
                    value={localNumber}
                    onChange={(e) => setLocalNumber(e.target.value.replace(/\D/g, ""))}
                    required
                    className={`flex-1 min-h-12 px-3 rounded-[var(--radius-sm)] outline-none text-base bg-surface-container-lowest border-2 focus-visible:border-primary ${phoneError ? "border-danger-red" : "border-outline-variant"}`}
                  />
                </div>
                {phoneError && <p className="text-sm text-danger-red mt-1">{phoneError}</p>}
              </div>
              <div className="col-span-2">
                <Input
                  label={<>Username (for eventual account access)<Req /></>}
                  labelClassName="!text-base"
                  className="!text-base"
                  value={login}
                  onChange={(e) => setLogin(e.target.value)}
                  autoComplete="off"
                  error={loginError}
                  required
                />
              </div>
              {isAgent && (
                <>
                  <Input
                    label={<>Email<Req /></>}
                    labelClassName="!text-base"
                    className="!text-base"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    error={emailError}
                    required
                  />
                  <Input label="Employee Code" labelClassName="!text-base" className="!text-base" value={employeeCode} onChange={(e) => setEmployeeCode(e.target.value)} />
                </>
              )}
              <div>
                <Input
                  label="National ID Number"
                  labelClassName="!text-base"
                  className="!text-base"
                  value={nationalIdNumber}
                  onChange={(e) => setNationalIdNumber(e.target.value)}
                  error={nationalIdDisplayError}
                />
                {!nationalIdDisplayError && <p className="text-sm text-on-surface-variant mt-1">Format: {kycFormat.nationalId.hint}</p>}
              </div>
              <div>
                <Input
                  label="Tax ID Number"
                  labelClassName="!text-base"
                  className="!text-base"
                  value={taxIdNumber}
                  onChange={(e) => setTaxIdNumber(e.target.value)}
                  error={taxIdDisplayError}
                />
                {!taxIdDisplayError && <p className="text-sm text-on-surface-variant mt-1">Format: {kycFormat.taxId.hint}</p>}
              </div>
              <div className="col-span-2">
                <Input label="Place of Residence (e.g. Akwa, Douala)" labelClassName="!text-base" className="!text-base" value={placeOfResidence} onChange={(e) => setPlaceOfResidence(e.target.value)} />
              </div>
              <div className="col-span-2">
                <Input
                  label="Criminal Record Issue Date"
                  labelClassName="!text-base"
                  className="!text-base"
                  type="date"
                  value={criminalRecordIssuedDate}
                  onChange={(e) => setCriminalRecordIssuedDate(e.target.value)}
                />
                <p className="text-sm text-on-surface-variant mt-1">If provided, must be within the last 90 days.</p>
              </div>
            </div>
          )}

          {step === "documents" && (
            <div className="grid grid-cols-2 gap-4">
              <p className="text-sm text-on-surface-variant col-span-2">PDF or JPEG only, up to 10MB each. All five are required.</p>
              {DOCUMENT_FIELDS.map(({ key, label }) => (
                <div key={key} className="flex flex-col gap-1">
                  <label className="text-base font-semibold text-on-surface">
                    {label}
                    <Req />
                  </label>
                  <input
                    type="file"
                    accept="application/pdf,image/jpeg"
                    onChange={handleFileChange(key)}
                    required
                    className="text-base file:mr-3 file:py-2 file:px-3 file:rounded-[var(--radius-sm)] file:border-0 file:bg-primary-container/10 file:text-primary file:font-semibold file:cursor-pointer cursor-pointer"
                  />
                </div>
              ))}
            </div>
          )}

          {step === "review" && (
            <div className="grid grid-cols-2 gap-3 text-base">
              <p><span className="text-on-surface-variant">Role:</span> {ROLE_META[targetRole].label}</p>
              <p><span className="text-on-surface-variant">Branch:</span> {selectableBranches.find((b) => b.id === branchId)?.name}</p>
              <p><span className="text-on-surface-variant">Name:</span> {firstName} {lastName}</p>
              <p><span className="text-on-surface-variant">Phone:</span> {countryCode}{localNumber}</p>
              <p><span className="text-on-surface-variant">Username:</span> {login}</p>
              {isAgent && <p><span className="text-on-surface-variant">Email:</span> {email}</p>}
              <p className="col-span-2"><span className="text-on-surface-variant">Documents attached:</span> {Object.values(files).filter(Boolean).length} / 5</p>
              <p className="text-sm text-on-surface-variant col-span-2 mt-1">
                Submitting sends this to compliance review — no account is created until an ADMIN approves it.
              </p>
            </div>
          )}

          <div className="flex justify-between gap-2 mt-2">
            <Button type="button" variant="ghost" onClick={stepIndex === 0 ? () => router.push("/registrations") : goBack} disabled={succeeded}>
              {stepIndex === 0 ? "Cancel" : "Back"}
            </Button>
            {step !== "review" ? (
              <Button
                type="button"
                onClick={goNext}
                disabled={(step === "identity" && !identityValid) || (step === "documents" && !documentsValid)}
              >
                Next
              </Button>
            ) : (
              <Button type="submit" variant={succeeded ? "success" : "primary"} loading={loading} disabled={succeeded}>
                {succeeded ? (
                  <>
                    <Icon name="check-circle" className="size-5" />
                    Submitted
                  </>
                ) : (
                  "Submit for Review"
                )}
              </Button>
            )}
          </div>
        </form>
      </div>
      <ErrorDialog open={error !== null} message={error} onClose={() => setError(null)} title="Submission Failed" />
    </div>
  );
}
