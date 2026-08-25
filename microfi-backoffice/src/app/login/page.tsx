"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/Button";
import { Input } from "@/components/Input";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export default function LoginPage() {
  const router = useRouter();
  const dict = useDictionary();
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ login, password }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.login.loginFailed);
        return;
      }
      router.push("/");
      router.refresh();
    } catch {
      setError(dict.login.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex-1 flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm flex flex-col items-center">
        <div className="w-16 h-16 rounded-[var(--radius-md)] bg-primary flex items-center justify-center mb-5">
          <Icon name="building" className="size-8 text-on-primary" />
        </div>
        <h1 className="text-3xl font-bold text-on-surface text-center">{dict.login.title}</h1>
        <p className="text-sm text-text-slate mt-1 mb-8">{dict.login.subtitle}</p>

        <form
          onSubmit={handleSubmit}
          className="w-full bg-surface-container-lowest rounded-[var(--radius-md)] border border-outline-variant shadow-[var(--shadow-elevation-1)] p-8 flex flex-col gap-4"
        >
          <Input
            label={dict.login.emailOrUsername}
            name="login"
            placeholder={dict.login.credentialsPlaceholder}
            autoComplete="username"
            icon={<Icon name="person" className="size-5" />}
            value={login}
            onChange={(e) => setLogin(e.target.value)}
            required
          />
          <Input
            label={dict.login.password}
            name="password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            icon={<Icon name="lock" className="size-5" />}
            trailing={
              <button
                type="button"
                onClick={() => setShowPassword((s) => !s)}
                className="text-outline hover:text-on-surface cursor-pointer transition-transform duration-150 ease-out hover:scale-110 active:scale-90"
                aria-label={showPassword ? dict.login.hidePassword : dict.login.showPassword}
              >
                <Icon name={showPassword ? "eye-off" : "eye"} className="size-5" />
              </button>
            }
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          {error && (
            <p role="alert" className="text-sm text-danger-red bg-danger-red/10 rounded-[var(--radius-sm)] px-3 py-2">
              {error}
            </p>
          )}
          <Button type="submit" loading={loading} className="w-full mt-2">
            {dict.login.signIn}
          </Button>
        </form>
        <div className="w-full bg-surface-container-low rounded-b-[var(--radius-md)] border border-t-0 border-outline-variant px-8 py-3 -mt-1 flex items-center justify-center gap-2">
          <Icon name="shield-check" className="size-4 text-outline" />
          <p className="text-xs text-text-slate">{dict.login.secureAccess}</p>
        </div>
      </div>
    </div>
  );
}
