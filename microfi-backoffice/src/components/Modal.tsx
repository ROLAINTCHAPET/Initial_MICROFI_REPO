"use client";

import { useEffect, useRef, type ReactNode } from "react";

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

// Native <dialog> gives us focus trap, Esc-to-close, and backdrop for free (handoff §3: "modals
// trap focus and return it to the triggering element on close" — the browser handles the return
// automatically when the triggering element is still focusable).
export function Modal({ open, onClose, title, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onCancel={onClose}
      className="modal-transition rounded-[var(--radius-md)] border-2 border-outline-variant p-0 shadow-[var(--shadow-elevation-2)] backdrop:bg-black/40 w-full max-w-md max-h-[85vh] overflow-y-auto"
    >
      <div className="p-6">
        <h2 className="text-lg font-bold text-on-surface mb-4">{title}</h2>
        {children}
      </div>
    </dialog>
  );
}
