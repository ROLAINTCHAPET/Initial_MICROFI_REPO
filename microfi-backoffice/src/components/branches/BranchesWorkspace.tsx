"use client";

import { useState, type ReactNode } from "react";
import { Toast } from "@/components/Toast";
import type { ScheduleDefaultsResponse } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { GlobalThresholds } from "./GlobalThresholds";
import { BranchDirectory, type BranchRow } from "./BranchDirectory";

export function BranchesWorkspace({
  scheduleDefaults,
  editable,
  branches,
  actions,
}: {
  scheduleDefaults: ScheduleDefaultsResponse;
  editable: boolean;
  branches: BranchRow[];
  actions?: ReactNode;
}) {
  const dict = useDictionary();
  const [locked, setLocked] = useState(false);
  const [toastVisible, setToastVisible] = useState(false);

  return (
    <>
      <GlobalThresholds
        initial={scheduleDefaults}
        editable={editable}
        onSaveStart={() => setLocked(true)}
        onSaveSettled={() => {
          setLocked(false);
          setToastVisible(true);
        }}
      />
      <BranchDirectory branches={branches} actions={actions} locked={locked} />
      {toastVisible && <Toast message={dict.branches.workspace.scheduleSaved} onDismiss={() => setToastVisible(false)} />}
    </>
  );
}
