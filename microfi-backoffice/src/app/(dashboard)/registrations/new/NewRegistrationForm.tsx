"use client";

import { useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Input } from "@/components/Input";
import { Button } from "@/components/Button";
import { ErrorDialog } from "@/components/ErrorDialog";
import { Icon, type IconName } from "@/components/Icon";
import { PageHeader } from "@/components/PageHeaderContext";
import { kycFormatFor, matchesKycFormat } from "@/lib/kycFormats";
import { COUNTRY_CODES } from "@/lib/countryCodes";
import type { AdminRole, BranchResponse, RegistrationTargetRole } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { Dictionary } from "@/lib/i18n/dictionaries";

type WizardStep = "role-branch" | "identity" | "documents" | "review";

function stepsList(dict: Dictionary): { key: WizardStep; label: string }[] {
  return [
    { key: "role-branch", label: dict.registrations.newForm.steps.roleBranch },
    { key: "identity", label: dict.registrations.newForm.steps.identity },
    { key: "documents", label: dict.registrations.newForm.steps.documents },
    { key: "review", label: dict.registrations.newForm.steps.review },
  ];
}

function roleMeta(dict: Dictionary): Record<RegistrationTargetRole, { label: string; icon: IconName }> {
  return {
    AGENT: { label: dict.agents.fieldAgentLabel, icon: "person" },
    BRANCH_MANAGER: { label: dict.roles.BRANCH_MANAGER, icon: "agents" },
    BRANCH_CASHIER: { label: dict.roles.BRANCH_CASHIER, icon: "account-balance-wallet" },
  };
}

function documentFields(dict: Dictionary): { key: keyof WizardFiles; label: string }[] {
  return [
    { key: "nationalId", label: dict.registrations.documents.nationalId },
    { key: "criminalRecord", label: dict.registrations.documents.criminalRecord },
    { key: "medicalFitness", label: dict.registrations.documents.medicalFitness },
    { key: "locationPlan", label: dict.registrations.documents.locationPlan },
    { key: "passportPhoto", label: dict.registrations.documents.passportPhoto },
  ];
}

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
// Name, Phone, Username, Email for agents, National ID Number, Unique Identification Number (UIN),
// all five documents). Everything else (Employee Code, Place of Residence, Date of Birth, Criminal
// Record Issue Date) is optional and left unmarked.
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
  const searchParams = useSearchParams();
  // Where "back"/"cancel" return to — whichever page linked here (e.g. /agents, /team), so
  // leaving the wizard doesn't strand the user on the registrations list when they never started
  // there. Falls back to /registrations for direct entry (e.g. from the registrations page itself).
  const backHref = searchParams.get("from") ?? "/registrations";
  const dict = useDictionary();
  const STEPS = stepsList(dict);
  const ROLE_META = roleMeta(dict);
  const DOCUMENT_FIELDS = documentFields(dict);
  const availableRoles: RegistrationTargetRole[] =
    callerRole === "ADMIN" ? ["AGENT", "BRANCH_MANAGER", "BRANCH_CASHIER"] : ["AGENT", "BRANCH_CASHIER"];
  const selectableBranches = callerRole === "BRANCH_MANAGER" ? branches.filter((b) => b.id === callerBranchId) : branches;

  const [step, setStep] = useState<WizardStep>("role-branch");
  const [targetRole, setTargetRole] = useState<RegistrationTargetRole>("AGENT");
  const [branchId, setBranchId] = useState(selectableBranches[0]?.id ?? "");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
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

  // National ID/UIN are mandatory, but format is only checked once something is actually typed —
  // an unmatched/future country still falls back to a generous check rather than ever hard-blocking.
  const countryIso = COUNTRY_CODES.find((c) => c.code === countryCode)?.iso ?? "";
  const kycFormat = kycFormatFor(countryIso);
  const nationalIdError =
    nationalIdNumber.trim() !== "" && !matchesKycFormat(nationalIdNumber, kycFormat.nationalId.pattern)
      ? t(dict.registrations.newForm.expectedFormat, { hint: kycFormat.nationalId.hint })
      : undefined;
  const taxIdError =
    taxIdNumber.trim() !== "" && !matchesKycFormat(taxIdNumber, kycFormat.taxId.pattern)
      ? t(dict.registrations.newForm.expectedFormat, { hint: kycFormat.taxId.hint })
      : undefined;
  const emailFormatError =
    isAgent && email.trim() !== "" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
      ? dict.registrations.newForm.invalidEmail
      : undefined;

  // No two agents/cashiers/managers may share a username, phone, email, National ID Number, or
  // Unique Identification Number (UIN) — checked live as each field is filled in, not only at the final submit.
  const loginAvailability = useAvailability("LOGIN", login);
  const phoneAvailability = useAvailability("PHONE", localNumber.trim() ? countryCode + localNumber.replace(/\D/g, "") : "");
  const emailAvailability = useAvailability("EMAIL", isAgent && !emailFormatError ? email : "");
  const nationalIdAvailability = useAvailability("NATIONAL_ID", nationalIdError ? "" : nationalIdNumber);
  const taxIdAvailability = useAvailability("TAX_ID", taxIdError ? "" : taxIdNumber);

  const loginError = loginAvailability.taken ? dict.registrations.newForm.usernameTaken : undefined;
  const phoneError = phoneAvailability.taken ? dict.registrations.newForm.phoneTaken : undefined;
  const emailError = emailFormatError ?? (emailAvailability.taken ? dict.registrations.newForm.emailTaken : undefined);
  const nationalIdDisplayError = nationalIdError ?? (nationalIdAvailability.taken ? dict.registrations.newForm.nationalIdTaken : undefined);
  const taxIdDisplayError = taxIdError ?? (taxIdAvailability.taken ? dict.registrations.newForm.taxIdTaken : undefined);

  const identityValid =
    firstName.trim() !== "" &&
    lastName.trim() !== "" &&
    localNumber.trim() !== "" &&
    login.trim() !== "" &&
    (!isAgent || email.trim() !== "") &&
    nationalIdNumber.trim() !== "" &&
    taxIdNumber.trim() !== "" &&
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
        dateOfBirth: dateOfBirth || undefined,
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
        setError(body?.message ?? dict.registrations.newForm.failedToSubmit);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        router.push("/registrations");
        router.refresh();
      }, 800);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-4xl mx-auto w-full flex flex-col gap-6">
      <Link href={backHref} className="text-sm text-primary hover:underline underline-offset-2 font-medium flex items-center gap-1 w-fit">
        <Icon name="arrow-upward" className="size-4 -rotate-90" />
        {dict.common.back}
      </Link>
      <PageHeader title={dict.registrations.newForm.pageTitle} subtitle={dict.registrations.newForm.pageSubtitle} />

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant p-6">
        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div className="flex items-center gap-2">
            {STEPS.map((s, i) => (
              <div key={s.key} className={`h-1.5 flex-1 rounded-full ${i <= stepIndex ? "bg-primary" : "bg-outline-variant"}`} />
            ))}
          </div>
          <p className="text-sm font-semibold text-on-surface-variant uppercase tracking-widest">
            {t(dict.registrations.newForm.stepIndicator, { current: stepIndex + 1, total: STEPS.length, label: STEPS[stepIndex].label })}
          </p>

          {step === "role-branch" && (
            <>
              <div>
                <p className="text-base font-semibold text-on-surface mb-2">
                  {dict.registrations.newForm.roleLabel}
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
                  {dict.registrations.newForm.branchLabel}
                  <Req />
                </label>
                <select
                  id="wizard-branch"
                  value={branchId}
                  onChange={(e) => setBranchId(e.target.value)}
                  disabled={selectableBranches.length === 1}
                  className="min-h-12 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-base disabled:text-text-grey-disabled disabled:bg-surface-grey-100 w-full min-w-0 max-w-full"
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
              <Input label={<>{dict.registrations.fields.firstName}<Req /></>} labelClassName="!text-base" className="!text-base" value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
              <Input label={<>{dict.registrations.fields.lastName}<Req /></>} labelClassName="!text-base" className="!text-base" value={lastName} onChange={(e) => setLastName(e.target.value)} required />
              <Input
                label={dict.registrations.fields.dateOfBirth}
                labelClassName="!text-base"
                className="!text-base"
                type="date"
                value={dateOfBirth}
                onChange={(e) => setDateOfBirth(e.target.value)}
              />
              <div className="flex flex-col gap-1">
                <label htmlFor="wizard-phone" className="text-base font-semibold text-on-surface">
                  {dict.registrations.fields.phone}
                  <Req />
                </label>
                <div className="flex gap-2">
                  <select
                    aria-label={dict.registrations.newForm.countryCodeAriaLabel}
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
                  label={<>{dict.registrations.fields.username}<Req /></>}
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
                    label={<>{dict.registrations.fields.email}<Req /></>}
                    labelClassName="!text-base"
                    className="!text-base"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    error={emailError}
                    required
                  />
                  <Input label={dict.registrations.fields.employeeCode} labelClassName="!text-base" className="!text-base" value={employeeCode} onChange={(e) => setEmployeeCode(e.target.value)} />
                </>
              )}
              <div>
                <Input
                  label={<>{dict.registrations.fields.nationalIdNumber}<Req /></>}
                  labelClassName="!text-base"
                  className="!text-base"
                  value={nationalIdNumber}
                  onChange={(e) => setNationalIdNumber(e.target.value)}
                  error={nationalIdDisplayError}
                  required
                />
                {!nationalIdDisplayError && <p className="text-sm text-on-surface-variant mt-1">{t(dict.registrations.newForm.formatHint, { hint: kycFormat.nationalId.hint })}</p>}
              </div>
              <div>
                <Input
                  label={<>{dict.registrations.fields.taxIdNumber}<Req /></>}
                  labelClassName="!text-base"
                  className="!text-base"
                  value={taxIdNumber}
                  onChange={(e) => setTaxIdNumber(e.target.value)}
                  error={taxIdDisplayError}
                  required
                />
                {!taxIdDisplayError && <p className="text-sm text-on-surface-variant mt-1">{t(dict.registrations.newForm.formatHint, { hint: kycFormat.taxId.hint })}</p>}
              </div>
              <div className="col-span-2">
                <Input label={dict.registrations.newForm.placeOfResidenceLabel} labelClassName="!text-base" className="!text-base" value={placeOfResidence} onChange={(e) => setPlaceOfResidence(e.target.value)} />
              </div>
              <div className="col-span-2">
                <Input
                  label={dict.registrations.newForm.criminalRecordIssueDateLabel}
                  labelClassName="!text-base"
                  className="!text-base"
                  type="date"
                  value={criminalRecordIssuedDate}
                  onChange={(e) => setCriminalRecordIssuedDate(e.target.value)}
                />
                <p className="text-sm text-on-surface-variant mt-1">{dict.registrations.newForm.criminalRecordHint}</p>
              </div>
            </div>
          )}

          {step === "documents" && (
            <div className="grid grid-cols-2 gap-4">
              <p className="text-sm text-on-surface-variant col-span-2">{dict.registrations.newForm.documentsIntro}</p>
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
              <p><span className="text-on-surface-variant">{dict.registrations.newForm.review.role}</span> {ROLE_META[targetRole].label}</p>
              <p><span className="text-on-surface-variant">{dict.registrations.newForm.review.branch}</span> {selectableBranches.find((b) => b.id === branchId)?.name}</p>
              <p><span className="text-on-surface-variant">{dict.registrations.newForm.review.name}</span> {firstName} {lastName}</p>
              {dateOfBirth && <p><span className="text-on-surface-variant">{dict.registrations.newForm.review.dateOfBirth}</span> {dateOfBirth}</p>}
              <p><span className="text-on-surface-variant">{dict.registrations.newForm.review.phone}</span> {countryCode}{localNumber}</p>
              <p><span className="text-on-surface-variant">{dict.registrations.newForm.review.username}</span> {login}</p>
              {isAgent && <p><span className="text-on-surface-variant">{dict.registrations.newForm.review.email}</span> {email}</p>}
              <p className="col-span-2"><span className="text-on-surface-variant">{dict.registrations.newForm.review.documentsAttached}</span> {Object.values(files).filter(Boolean).length} / 5</p>
              <p className="text-sm text-on-surface-variant col-span-2 mt-1">
                {dict.registrations.newForm.review.complianceNotice}
              </p>
            </div>
          )}

          <div className="flex justify-between gap-2 mt-2">
            <Button type="button" variant="ghost" onClick={stepIndex === 0 ? () => router.push(backHref) : goBack} disabled={succeeded}>
              {stepIndex === 0 ? dict.common.cancel : dict.common.back}
            </Button>
            {step !== "review" ? (
              <Button
                type="button"
                onClick={goNext}
                disabled={(step === "identity" && !identityValid) || (step === "documents" && !documentsValid)}
              >
                {dict.registrations.newForm.next}
              </Button>
            ) : (
              <Button type="submit" variant={succeeded ? "success" : "primary"} loading={loading} disabled={succeeded}>
                {succeeded ? (
                  <>
                    <Icon name="check-circle" className="size-5" />
                    {dict.registrations.newForm.submitted}
                  </>
                ) : (
                  dict.registrations.newForm.submitForReview
                )}
              </Button>
            )}
          </div>
        </form>
      </div>
      <ErrorDialog open={error !== null} message={error} onClose={() => setError(null)} title={dict.registrations.newForm.submissionFailedTitle} />
    </div>
  );
}
