"use client";

import type { InputHTMLAttributes, ReactNode } from "react";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: ReactNode;
  error?: string;
  success?: boolean;
  icon?: ReactNode;
  trailing?: ReactNode;
  /** Extra classes appended to the label — e.g. a caller-specific size override. Every existing caller leaves this unset, so nothing changes for them. */
  labelClassName?: string;
}

// DESIGN.md Components/Input Fields: 1px border thickening to 2px Primary on focus, labels
// always visible. Default/Focus/Error/Disabled/Success states; optional leading icon + trailing
// slot (e.g. the login screen's person/lock icons and password show/hide toggle).
export function Input({ label, error, success = false, disabled, id, icon, trailing, className = "", labelClassName = "", ...props }: InputProps) {
  const inputId = id ?? props.name;
  const borderClass = error
    ? "border-2 border-danger-red"
    : success
      ? "border-2 border-success-emerald"
      : "border-2 border-outline-variant focus-visible:border-primary";
  // type="time"/"date" behave like compound picker controls rather than free-text entry —
  // browsers default them to a text cursor, which reads as non-interactive; override it.
  const isPicker = props.type === "time" || props.type === "date";

  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label htmlFor={inputId} className={`text-sm font-semibold text-on-surface ${labelClassName}`}>
          {label}
        </label>
      )}
      <div className="relative">
        {icon && <span className="absolute left-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none">{icon}</span>}
        <input
          {...props}
          id={inputId}
          disabled={disabled}
          className={`w-full min-h-12 ${icon ? "pl-10" : "pl-3"} ${trailing ? "pr-10" : "pr-3"} rounded-[var(--radius-sm)] outline-none text-sm bg-surface-container-lowest
            ${isPicker ? "cursor-pointer" : ""} disabled:cursor-not-allowed
            disabled:bg-surface-grey-100 disabled:text-text-grey-disabled disabled:border-transparent
            ${borderClass} ${className}`}
        />
        {trailing && <span className="absolute right-3 top-1/2 -translate-y-1/2">{trailing}</span>}
      </div>
      {error && <p className="text-xs text-danger-red">{error}</p>}
    </div>
  );
}
