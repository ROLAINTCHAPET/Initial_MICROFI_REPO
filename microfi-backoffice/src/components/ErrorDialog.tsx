"use client";

import { Modal } from "@/components/Modal";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";

/** The one error-dialog implementation for the app — every form failure surfaces the backend's
    actual message this way instead of inline red text easy to miss. */
export function ErrorDialog({ open, message, onClose, title }: { open: boolean; message: string | null; onClose: () => void; title?: string }) {
  const dict = useDictionary();
  return (
    <Modal open={open} onClose={onClose} title={title ?? dict.common.errorTitle}>
      <div className="flex flex-col items-center gap-4 text-center">
        <div className="h-12 w-12 rounded-full bg-danger-red/10 text-danger-red flex items-center justify-center shrink-0">
          <Icon name="warning" className="size-6" />
        </div>
        <p className="text-sm text-on-surface">{message}</p>
        <Button onClick={onClose} className="mt-1">
          {dict.common.ok}
        </Button>
      </div>
    </Modal>
  );
}
