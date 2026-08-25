"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";

interface TimePickerProps {
  label?: string;
  value: string; // "HH:mm" or "HH:mm:ss", 24-hour — same shape the native time input already used
  onChange: (value: string) => void;
  disabled?: boolean;
  id?: string;
  name?: string;
}

const HOURS_12 = Array.from({ length: 12 }, (_, i) => i + 1); // 1..12
const MINUTES = Array.from({ length: 60 }, (_, i) => i); // 0..59
type Period = "AM" | "PM";

function parse(value: string): { hour12: number; minute: number; period: Period } {
  const [hRaw, mRaw] = value.split(":");
  const h = Number(hRaw) || 0;
  const minute = Number(mRaw) || 0;
  const period: Period = h >= 12 ? "PM" : "AM";
  const hour12 = h % 12 === 0 ? 12 : h % 12;
  return { hour12, minute, period };
}

function to24Hour(hour12: number, minute: number, period: Period): string {
  const h = period === "PM" ? (hour12 % 12) + 12 : hour12 % 12;
  return `${String(h).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

// Native <input type="time"> renders completely differently per browser — Chrome gets a popup
// picker, Firefox only gets inline editable segments with no popup at all (see Input.tsx's
// history). This replaces the native control entirely so the picker itself looks and behaves
// identically everywhere: one trigger button, one dropdown with hour/minute/AM-PM columns.
export function TimePicker({ label, value, onChange, disabled = false, id, name }: TimePickerProps) {
  const dict = useDictionary();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const inputId = id ?? name;

  const { hour12, minute, period } = parse(value || "00:00");

  useEffect(() => {
    if (!open) return;
    function onMouseDown(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) setOpen(false);
    }
    function onKeyDown(e: globalThis.KeyboardEvent) {
      if (e.key === "Escape") {
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener("mousedown", onMouseDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onMouseDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  useEffect(() => {
    if (!open || !panelRef.current) return;
    panelRef.current.querySelectorAll<HTMLElement>('[data-active="true"]').forEach((el) => el.scrollIntoView({ block: "center" }));
  }, [open]);

  function handleColumnKeyDown(e: KeyboardEvent<HTMLButtonElement>) {
    if (e.key !== "ArrowDown" && e.key !== "ArrowUp") return;
    e.preventDefault();
    const sibling = e.key === "ArrowDown" ? e.currentTarget.nextElementSibling : e.currentTarget.previousElementSibling;
    if (sibling instanceof HTMLElement) sibling.focus();
  }

  function optionClass(active: boolean) {
    return `w-full text-center py-2 text-sm cursor-pointer transition-colors ${
      active ? "bg-primary text-on-primary font-semibold" : "text-on-surface hover:bg-primary-container/20"
    }`;
  }

  return (
    <div className="flex flex-col gap-1" ref={containerRef}>
      {label && (
        <label htmlFor={inputId} className="text-sm font-semibold text-on-surface">
          {label}
        </label>
      )}
      <div className="relative">
        <span className="absolute left-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none">
          <Icon name="clock" className="size-5" />
        </span>
        <button
          ref={triggerRef}
          type="button"
          id={inputId}
          disabled={disabled}
          aria-haspopup="listbox"
          aria-expanded={open}
          onClick={() => setOpen((o) => !o)}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown" && !open) {
              e.preventDefault();
              setOpen(true);
            }
          }}
          className={`w-full min-h-12 pl-10 pr-3 rounded-[var(--radius-sm)] outline-none text-sm text-left bg-surface-container-lowest
            border-2 border-outline-variant focus-visible:border-primary
            disabled:cursor-not-allowed disabled:bg-surface-grey-100 disabled:text-text-grey-disabled disabled:border-transparent
            ${!disabled ? "cursor-pointer" : ""}`}
        >
          {pad(hour12)}:{pad(minute)} {period}
        </button>

        {open && !disabled && (
          <div
            ref={panelRef}
            role="listbox"
            aria-label={label ? t(dict.common.timePicker.labelPicker, { label }) : dict.common.timePicker.picker}
            className="absolute z-20 mt-2 left-0 bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-sm)] shadow-[var(--shadow-elevation-2)] flex overflow-hidden"
          >
            <div className="w-16 max-h-52 overflow-y-auto py-1 border-r border-outline-variant" role="group" aria-label={dict.common.timePicker.hour}>
              {HOURS_12.map((h) => (
                <button
                  key={h}
                  type="button"
                  role="option"
                  data-active={h === hour12}
                  aria-selected={h === hour12}
                  onClick={() => onChange(to24Hour(h, minute, period))}
                  onKeyDown={handleColumnKeyDown}
                  className={optionClass(h === hour12)}
                >
                  {pad(h)}
                </button>
              ))}
            </div>
            <div className="w-16 max-h-52 overflow-y-auto py-1 border-r border-outline-variant" role="group" aria-label={dict.common.timePicker.minute}>
              {MINUTES.map((m) => (
                <button
                  key={m}
                  type="button"
                  role="option"
                  data-active={m === minute}
                  aria-selected={m === minute}
                  onClick={() => onChange(to24Hour(hour12, m, period))}
                  onKeyDown={handleColumnKeyDown}
                  className={optionClass(m === minute)}
                >
                  {pad(m)}
                </button>
              ))}
            </div>
            <div className="w-16 flex flex-col py-1" role="group" aria-label={dict.common.timePicker.ampm}>
              {(["AM", "PM"] as const).map((p) => (
                <button
                  key={p}
                  type="button"
                  role="option"
                  data-active={p === period}
                  aria-selected={p === period}
                  onClick={() => onChange(to24Hour(hour12, minute, p))}
                  onKeyDown={handleColumnKeyDown}
                  className={optionClass(p === period)}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
