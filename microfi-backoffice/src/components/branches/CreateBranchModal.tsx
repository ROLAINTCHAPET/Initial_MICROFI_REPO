"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { Input } from "@/components/Input";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";

export function CreateBranchModal() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [timezone, setTimezone] = useState("Africa/Douala");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch("/api/branches", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code, name, phone: phone.trim() || undefined, timezone }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? "Failed to create branch");
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setOpen(false);
        setSucceeded(false);
        setCode("");
        setName("");
        setPhone("");
        router.refresh();
      }, 600);
    } catch {
      setError("Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Button onClick={() => setOpen(true)}>
        <Icon name="plus" className="size-5" />
        Create Branch
      </Button>
      <Modal open={open} onClose={() => setOpen(false)} title="Create Branch">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Input label="Code" name="code" value={code} onChange={(e) => setCode(e.target.value)} required />
          <Input label="Name" name="name" value={name} onChange={(e) => setName(e.target.value)} required />
          <Input
            label="Contact Number (optional)"
            name="phone"
            type="tel"
            icon={<Icon name="phone" className="size-5" />}
            placeholder="+237600000000"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
          />
          <Input
            label="Timezone (IANA)"
            name="timezone"
            value={timezone}
            onChange={(e) => setTimezone(e.target.value)}
            required
          />
          {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
          <div className="flex justify-end gap-2 mt-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)} disabled={succeeded}>
              Cancel
            </Button>
            <Button type="submit" variant={succeeded ? "success" : "primary"} loading={loading} disabled={succeeded}>
              {succeeded ? (
                <>
                  <Icon name="check-circle" className="size-5" />
                  Created
                </>
              ) : (
                "Create"
              )}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  );
}
