"use client";

import { useState, type FormEvent } from "react";
import { Modal } from "@/components/Modal";
import { Input } from "@/components/Input";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";

export function ResetPasswordModal({ userId, login }: { userId: string; login: string }) {
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
        setError(body?.message ?? "Failed to reset password");
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        close();
        setSucceeded(false);
      }, 800);
    } catch {
      setError("Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Button variant="ghost" onClick={() => setOpen(true)}>
        <Icon name="lock" className="size-5" />
        Reset Password
      </Button>
      <Modal open={open} onClose={close} title="Reset Password">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <p className="text-sm text-on-surface-variant">
            Set a new password for <span className="font-semibold text-on-surface">{login}</span>. They will need to sign in with it
            next time — no confirmation from their current password is required.
          </p>
          <Input
            label="New Password (min. 8 characters)"
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
              Cancel
            </Button>
            <Button type="submit" variant={succeeded ? "success" : "primary"} loading={loading} disabled={succeeded}>
              {succeeded ? (
                <>
                  <Icon name="check-circle" className="size-5" />
                  Reset
                </>
              ) : (
                "Set New Password"
              )}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  );
}
