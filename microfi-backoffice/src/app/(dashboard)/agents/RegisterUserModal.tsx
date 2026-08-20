"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { Input } from "@/components/Input";
import { Button } from "@/components/Button";
import { ErrorDialog } from "@/components/ErrorDialog";
import { Icon, type IconName } from "@/components/Icon";
import type { AdminRole, AdminUserResponse, BranchResponse } from "@/lib/types";

type Category = "AGENT" | "BRANCH_MANAGER" | "BRANCH_CASHIER";

const CATEGORY_META: Record<Category, { label: string; icon: IconName; verb: string }> = {
  AGENT: { label: "Field Agent", icon: "person", verb: "Enroll" },
  BRANCH_MANAGER: { label: "Branch Manager", icon: "agents", verb: "Register" },
  BRANCH_CASHIER: { label: "Branch Cashier", icon: "account-balance-wallet", verb: "Register" },
};

export function RegisterUserModal({
  branches,
  users,
  callerRole,
  callerBranchId,
}: {
  branches: BranchResponse[];
  users: AdminUserResponse[];
  callerRole: AdminRole;
  callerBranchId: string | null;
}) {
  const router = useRouter();
  // A BRANCH_MANAGER may enroll agents and cashiers in their own branch, but not other managers
  // (mirrors AdminUserManagementController.create's authz exactly — offering it here would just
  // produce a 403 on submit).
  const availableCategories: Category[] =
    callerRole === "ADMIN" ? ["AGENT", "BRANCH_MANAGER", "BRANCH_CASHIER"] : ["AGENT", "BRANCH_CASHIER"];
  const selectableBranches = callerRole === "BRANCH_MANAGER" ? branches.filter((b) => b.id === callerBranchId) : branches;

  const [open, setOpen] = useState(false);
  const [category, setCategory] = useState<Category>("AGENT");
  const [agentForm, setAgentForm] = useState({
    fullName: "",
    phone: "+237",
    username: "",
    email: "",
    password: "",
    pin: "",
  });
  const [userForm, setUserForm] = useState({ login: "", password: "", fullName: "", phone: "+237" });
  const [branchId, setBranchId] = useState(selectableBranches[0]?.id ?? "");
  const [replaceUserId, setReplaceUserId] = useState<string>("");
  const [confirmReplaceManager, setConfirmReplaceManager] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  // Each branch has exactly one active manager at a time; registering a new one while one exists
  // requires an explicit confirm-to-replace instead of silently allowing two.
  const existingManager =
    category === "BRANCH_MANAGER"
      ? (users.find((u) => u.role === "BRANCH_MANAGER" && u.branchId === branchId && u.status === "ACTIVE") ?? null)
      : null;

  const branchCashierCap = branches.find((b) => b.id === branchId)?.maxCashiers ?? 1;
  const activeCashiers =
    category === "BRANCH_CASHIER"
      ? users.filter((u) => u.role === "BRANCH_CASHIER" && u.branchId === branchId && u.status === "ACTIVE")
      : [];
  const cashierCapReached = category === "BRANCH_CASHIER" && activeCashiers.length >= branchCashierCap;

  const blockedByManagerConflict = existingManager !== null && !confirmReplaceManager;
  const blockedByCashierCap = cashierCapReached && replaceUserId === "";

  function reset() {
    setAgentForm({ fullName: "", phone: "+237", username: "", email: "", password: "", pin: "" });
    setUserForm({ login: "", password: "", fullName: "", phone: "+237" });
    setBranchId(selectableBranches[0]?.id ?? "");
    setReplaceUserId("");
    setConfirmReplaceManager(false);
    setError(null);
  }

  function selectCategory(next: Category) {
    setCategory(next);
    setReplaceUserId("");
    setConfirmReplaceManager(false);
    setError(null);
  }

  function selectBranch(next: string) {
    setBranchId(next);
    setReplaceUserId("");
    setConfirmReplaceManager(false);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const isAgent = category === "AGENT";
      const replaceId = category === "BRANCH_MANAGER" ? existingManager?.id : category === "BRANCH_CASHIER" ? replaceUserId || undefined : undefined;
      const res = await fetch(isAgent ? "/api/agents" : "/api/admin-users", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(
          isAgent
            ? { ...agentForm, branchId }
            : { ...userForm, role: category, branchId, replaceUserId: replaceId }
        ),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? "Failed to register");
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setOpen(false);
        setSucceeded(false);
        reset();
        router.refresh();
      }, 600);
    } catch {
      setError("Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }

  const meta = CATEGORY_META[category];

  return (
    <>
      <Button onClick={() => setOpen(true)} disabled={selectableBranches.length === 0}>
        <Icon name="plus" className="size-5" />
        Register User
      </Button>
      <Modal open={open} onClose={() => setOpen(false)} title={`${meta.verb} ${meta.label}`}>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="grid grid-cols-3 gap-2">
            {availableCategories.map((c) => {
              const active = c === category;
              const m = CATEGORY_META[c];
              return (
                <button
                  key={c}
                  type="button"
                  onClick={() => selectCategory(c)}
                  className={`flex flex-col items-center gap-1.5 py-3 px-2 rounded-[var(--radius-sm)] border-2 cursor-pointer transition-[background-color,border-color,color,transform] duration-150 ease-out hover:scale-[1.02] active:scale-95 ${
                    active
                      ? "border-primary bg-primary-container/10 text-primary"
                      : "border-outline-variant text-on-surface-variant hover:border-primary/40"
                  }`}
                >
                  <Icon name={m.icon} className="size-5" />
                  <span className="text-xs font-semibold text-center leading-tight">{m.label}</span>
                </button>
              );
            })}
          </div>

          {category === "AGENT" ? (
            <>
              <Input
                label="Full Name"
                name="fullName"
                value={agentForm.fullName}
                onChange={(e) => setAgentForm((f) => ({ ...f, fullName: e.target.value }))}
                required
              />
              <div>
                <Input
                  label="Phone"
                  name="phone"
                  type="tel"
                  placeholder="+237600000000"
                  value={agentForm.phone}
                  onChange={(e) => setAgentForm((f) => ({ ...f, phone: e.target.value }))}
                  required
                />
                <p className="text-xs text-on-surface-variant mt-1">Include the country code (e.g. +237 for Cameroon). Must be unique — no two agents can share a phone number.</p>
              </div>
              <Input
                label="Username (for login)"
                name="agentUsername"
                value={agentForm.username}
                onChange={(e) => setAgentForm((f) => ({ ...f, username: e.target.value }))}
                autoComplete="off"
                required
              />
              <Input
                label="Email"
                name="agentEmail"
                type="email"
                value={agentForm.email}
                onChange={(e) => setAgentForm((f) => ({ ...f, email: e.target.value }))}
                required
              />
              <Input
                label="Password (for login, min. 8 characters)"
                name="agentPassword"
                type="password"
                value={agentForm.password}
                onChange={(e) => setAgentForm((f) => ({ ...f, password: e.target.value }))}
                autoComplete="new-password"
                required
                minLength={8}
              />
              <div>
                <Input
                  label="Starting Transaction PIN (4–10 chars)"
                  name="pin"
                  type="password"
                  value={agentForm.pin}
                  onChange={(e) => setAgentForm((f) => ({ ...f, pin: e.target.value }))}
                  required
                  minLength={4}
                  maxLength={10}
                />
                <p className="text-xs text-on-surface-variant mt-1">
                  Only used to confirm collections, never to log in. The agent must replace it with their own PIN before their first collection.
                </p>
              </div>
            </>
          ) : (
            <>
              <Input
                label="Full Name"
                name="userFullName"
                value={userForm.fullName}
                onChange={(e) => setUserForm((f) => ({ ...f, fullName: e.target.value }))}
                required
              />
              <div>
                <Input
                  label="Phone"
                  name="userPhone"
                  type="tel"
                  placeholder="+237600000000"
                  value={userForm.phone}
                  onChange={(e) => setUserForm((f) => ({ ...f, phone: e.target.value }))}
                  required
                />
                <p className="text-xs text-on-surface-variant mt-1">Include the country code (e.g. +237 for Cameroon). Must be unique.</p>
              </div>
              <Input
                label="Username (for login)"
                name="login"
                value={userForm.login}
                onChange={(e) => setUserForm((f) => ({ ...f, login: e.target.value }))}
                autoComplete="off"
                required
              />
              <div>
                <Input
                  label="Starting Password (min. 8 characters)"
                  name="password"
                  type="password"
                  value={userForm.password}
                  onChange={(e) => setUserForm((f) => ({ ...f, password: e.target.value }))}
                  autoComplete="new-password"
                  required
                  minLength={8}
                />
                <p className="text-xs text-on-surface-variant mt-1">
                  They&apos;ll be required to set their own password the first time they log in.
                </p>
              </div>
            </>
          )}

          <div className="flex flex-col gap-1">
            <label htmlFor="register-user-branch" className="text-sm font-semibold text-on-surface">
              Branch
            </label>
            <select
              id="register-user-branch"
              value={branchId}
              onChange={(e) => selectBranch(e.target.value)}
              disabled={selectableBranches.length === 1}
              className="min-h-12 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm disabled:text-text-grey-disabled disabled:bg-surface-grey-100"
              required
            >
              {selectableBranches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} ({b.code})
                </option>
              ))}
            </select>
          </div>

          {existingManager && (
            <div className="flex flex-col gap-2 p-3 rounded-[var(--radius-sm)] border-2 border-warning-amber/50 bg-warning-amber/10">
              <p className="text-sm text-on-surface">
                This branch already has a manager: <strong>{existingManager.login}</strong>. Registering a new one will suspend that account (it can be reactivated later).
              </p>
              <label className="flex items-center gap-2 text-sm font-semibold text-on-surface cursor-pointer">
                <input
                  type="checkbox"
                  checked={confirmReplaceManager}
                  onChange={(e) => setConfirmReplaceManager(e.target.checked)}
                />
                Replace {existingManager.login}
              </label>
            </div>
          )}

          {cashierCapReached && (
            <div className="flex flex-col gap-2 p-3 rounded-[var(--radius-sm)] border-2 border-warning-amber/50 bg-warning-amber/10">
              <p className="text-sm text-on-surface">
                This branch has reached its cashier limit ({branchCashierCap}). Pick an existing cashier to replace.
              </p>
              <select
                value={replaceUserId}
                onChange={(e) => setReplaceUserId(e.target.value)}
                className="min-h-12 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm"
                required
              >
                <option value="">Select a cashier to replace…</option>
                {activeCashiers.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.login}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="flex justify-end gap-2 mt-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)} disabled={succeeded}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant={succeeded ? "success" : "primary"}
              loading={loading}
              disabled={succeeded || blockedByManagerConflict || blockedByCashierCap}
            >
              {succeeded ? (
                <>
                  <Icon name="check-circle" className="size-5" />
                  Registered
                </>
              ) : (
                meta.verb
              )}
            </Button>
          </div>
        </form>
      </Modal>
      <ErrorDialog open={error !== null} message={error} onClose={() => setError(null)} title="Registration Failed" />
    </>
  );
}
