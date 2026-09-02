"use client";

import { useState, type FormEvent } from "react";
import { Modal } from "@/components/Modal";
import { Input } from "@/components/Input";
import { Button } from "@/components/Button";
import { ActionCard } from "@/components/ActionCard";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function ResetPasswordModal({ userId, login }: { userId: string; login: string }) {
  const dict = useDictionary();
  const [open, setOpen] = useState(false);
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  function close() {
    setOpen(false);
    setNewPassword("");
    setError(null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/admin-users/${userId}/password`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ newPassword }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.team.resetPassword.failedToReset);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        close();
        setSucceeded(false);
      }, 800);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <ActionCard
        icon="lock"
        title={dict.team.resetPassword.button}
        description={dict.team.resetPassword.cardDescription}
        onClick={() => setOpen(true)}
      />
      <Modal open={open} onClose={close} title={dict.team.resetPassword.title}>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <p className="text-sm text-on-surface-variant">
            {dict.team.resetPassword.descriptionPrefix} <span className="font-semibold text-on-surface">{login}</span>
            {dict.team.resetPassword.descriptionSuffix}
          </p>
          <Input
            label={dict.team.resetPassword.newPasswordLabel}
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            autoComplete="new-password"
            required
            minLength={8}
          />
          {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
          <div className="flex justify-end gap-2 mt-2">
            <Button type="button" variant="ghost" onClick={close} disabled={succeeded}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" variant={succeeded ? "success" : "primary"} loading={loading} disabled={succeeded}>
              {succeeded ? (
                <>
                  <Icon name="check-circle" className="size-5" />
                  {dict.team.resetPassword.resetDone}
                </>
              ) : (
                dict.team.resetPassword.setNewPassword
              )}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  );
}
