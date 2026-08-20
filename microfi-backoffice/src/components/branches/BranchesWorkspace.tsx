"use client";

import { useState, type ReactNode } from "react";
import { Toast } from "@/components/Toast";
import type { ScheduleDefaultsResponse } from "@/lib/types";
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
      {toastVisible && <Toast message="New schedule saved" onDismiss={() => setToastVisible(false)} />}
    </>
  );
}
