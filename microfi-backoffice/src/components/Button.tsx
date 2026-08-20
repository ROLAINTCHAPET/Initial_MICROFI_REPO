"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";

export type ButtonVariant = "primary" | "danger" | "success" | "ghost";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
  children: ReactNode;
}

// DESIGN.md Components/Buttons: full-height 48px touch target, Primary Navy fill / 2px stroke
// secondary, radius-md (12px). Default/Disabled/Loading/Success/Danger states.
const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: "bg-primary text-on-primary hover:bg-primary/90",
  danger: "border-2 border-danger-red text-danger-red hover:bg-danger-red/10",
  success: "bg-success-emerald text-white hover:bg-success-emerald/90",
  ghost: "border-2 border-outline-variant text-primary hover:bg-surface-container-low",
};

export function Button({ variant = "primary", loading = false, disabled, className = "", children, ...props }: ButtonProps) {
  const isDisabled = disabled || loading;
  // A disabled "success" button means the action just completed, not that it can't be used —
  // it should keep reading as affirmative (green), not fall back to the inert grey disabled look.
  const greyedOut = isDisabled && variant !== "success";
  return (
    <button
      {...props}
      disabled={isDisabled}
      className={`inline-flex items-center justify-center gap-2 min-h-12 px-4 rounded-[var(--radius-md)] text-sm font-semibold cursor-pointer
        transition-[background-color,border-color,color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]
        focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary
        disabled:cursor-not-allowed disabled:hover:scale-100 disabled:active:scale-100
        ${greyedOut ? "bg-[#E5E7EB] text-text-grey-disabled border-transparent" : VARIANT_CLASSES[variant]} ${className}`}
    >
      {loading && (
        <span
          aria-hidden
          className="h-4 w-4 rounded-full border-2 border-white/40 border-t-white animate-spin"
        />
      )}
      {children}
    </button>
  );
}
