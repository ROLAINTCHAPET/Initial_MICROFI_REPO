"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { AgentResponse, SosResponse } from "@/lib/types";

// Browsers refuse to play audio (and even refuse to create/resume an AudioContext) without a
// prior user gesture — there is no way around this, it's an anti-autoplay-annoyance policy
// enforced by the browser itself, not something this app's code controls. Crucially, that unlock
// does NOT survive a page reload/navigation — it's tied to this specific document instance, not
// the tab — so this is deliberately never persisted (sessionStorage or otherwise): a value that
// said "already enabled" from before a reload would hide the button while the actual AudioContext
// underneath it had been silently destroyed by the reload, leaving the toast working but the
// sound permanently dead until the next reload happened to remind the admin to click it again.
function playAlertTone(ctx: AudioContext) {
  // Two short beeps rather than one continuous tone — reads as an alarm, not a UI blip, without
  // needing to source/license an actual audio asset file.
  [0, 0.22].forEach((offset) => {
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = "square";
    oscillator.frequency.value = 880;
    gain.gain.setValueAtTime(0.15, ctx.currentTime + offset);
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + offset + 0.18);
    oscillator.connect(gain).connect(ctx.destination);
    oscillator.start(ctx.currentTime + offset);
    oscillator.stop(ctx.currentTime + offset + 0.2);
  });
}

interface ToastEntry {
  id: string;
  agentLabel: string;
}

// Mounted once in the dashboard layout (sibling to Header/Sidebar) so it's alive on every screen,
// not just /sos — the whole point is hearing an SOS while working on something else entirely.
export function SosAlertListener() {
  const dict = useDictionary();
  const [soundEnabled, setSoundEnabled] = useState(false);
  const [toasts, setToasts] = useState<ToastEntry[]>([]);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const agentsRef = useRef<Map<string, AgentResponse>>(new Map());

  useEffect(() => {
    fetch("/api/agents")
      .then((r) => (r.ok ? r.json() : []))
      .then((agents: AgentResponse[]) => {
        agentsRef.current = new Map(agents.map((a) => [a.id, a]));
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const source = new EventSource("/api/sos-events/stream");
    source.onmessage = (message) => {
      let event: SosResponse;
      try {
        event = JSON.parse(message.data);
      } catch {
        return;
      }
      const agent = agentsRef.current.get(event.agentId);
      setToasts((current) => [...current, { id: event.id, agentLabel: agent ? agent.fullName : event.agentId }]);
      setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== event.id)), 15000);

      if (audioCtxRef.current) {
        playAlertTone(audioCtxRef.current);
      }
    };
    // EventSource reconnects on its own on a dropped connection — no custom retry logic needed.
    return () => source.close();
  }, []);

  function enableSound() {
    const ctx = new AudioContext();
    audioCtxRef.current = ctx;
    // Some browsers still hand back a "suspended" context even when constructed inside a gesture
    // handler — resume() is a no-op if it's already running, but a required extra step if not.
    ctx.resume().finally(() => playAlertTone(ctx));
    setSoundEnabled(true);
  }

  return (
    <>
      {!soundEnabled && (
        <button
          onClick={enableSound}
          className="fixed bottom-4 left-4 md:left-[calc(16rem+1rem)] z-[2000] flex items-center gap-2 px-4 py-2.5 rounded-[var(--radius-md)] bg-primary text-on-primary text-sm font-semibold shadow-[var(--shadow-elevation-2)] cursor-pointer transition-transform duration-150 ease-out hover:scale-[1.03] active:scale-95"
        >
          <Icon name="bell" filled className="size-4" />
          {dict.sos.enableSoundAlerts}
        </button>
      )}

      <div className="fixed top-24 right-4 z-[2000] flex flex-col gap-2 w-80 max-w-[calc(100%-2rem)]">
        {toasts.map((toast) => (
          <Link
            key={toast.id}
            href="/sos"
            className="flex items-center gap-3 px-4 py-3 rounded-[var(--radius-md)] bg-error-container border-2 border-error shadow-[var(--shadow-elevation-2)] panel-scale-in hover:scale-[1.01] transition-transform duration-150 ease-out"
          >
            <Icon name="bell" filled className="size-5 text-on-error-container shrink-0" />
            <span className="flex-1 text-sm font-semibold text-on-error-container">
              {t(dict.sos.newAlertToast, { agent: toast.agentLabel })}
            </span>
            <span className="text-xs font-bold text-on-error-container underline underline-offset-2 shrink-0">
              {dict.sos.viewAction}
            </span>
          </Link>
        ))}
      </div>
    </>
  );
}
