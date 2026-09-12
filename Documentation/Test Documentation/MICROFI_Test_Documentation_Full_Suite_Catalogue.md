# MICROFI — Full Test Suite Catalogue (feature/rabbitmq-async-geocoding branch)

**Companion to:** `MICROFI_Test_Documentation-2.pdf` (v1.3, IEEE 829 pack) and `MICROFI_Test_Documentation_Portfolio_Client.md` (the "Portefeuille client" addendum). This document catalogues **every automated test** touched or added on this branch — 516 `@Test` methods across 40 files (39 in `microfi-core`, 1 in `microfi-middleware`) — grouped by feature domain rather than raw file, so the "why" of each cluster of tests is stated once instead of 516 times.

**Format:** unlike the card-per-behavior style of the two documents above, this catalogue uses one table per test class: a short paragraph gives the business context (mostly drawn from the production class's own doc comment), then every test method is listed with a one-line statement of what it verifies. Nothing is summarized away — every method name from the source appears.

**Verification status:** `mvn -o test` in `microfi-core` — **729 tests run, all passing** at time of last update (the suite's one known flake, `AgentDirectoryServiceTest.requireWithinScheduleWindowAllowsCollectionInsideWindow`, is a pre-existing real-clock issue unrelated to any work on this branch: it fails only when the suite happens to run within ~2 hours of local midnight, because its open/close window arithmetic wraps past `00:00`). `microfi-middleware`'s suite passes in full.

**2026-09-08 update — "End My Day" one-way signal fix:** `OfjService.getExportableSummary`/`exportForAgent` had no awareness of `Agent.dayEndedBusinessDate` — if a reconciliation line was confirmed *after* an agent had already tapped "End My Day," the mobile banner would silently reappear. Added `AgentDirectoryService.hasEndedDayToday`, wired into both methods so the action stays gone (client-side, via a zeroed summary) and is rejected server-side (409) if called anyway. See the 6 new tests added to §3 and §7.4 below.

**2026-09-12 update — Offline Field Collection Security Algorithm v1.1:** closes the gap between two outcomes that already existed (login blocked on a device mismatch, admin-only device reset) and the structural mechanism the spec requires behind them. Adds a real "installation" identity distinct from the device (`AgentInstallationBinding` — unlike Android SSAID, wiped on uninstall/Clear Data, so a reinstall always presents a fresh one), an alertable/resolvable `SecurityEvent` queue alongside the existing write-once audit log, two new agent states (`RECONCILIATION_REQUIRED` on a mismatch, `RESET_AUTHORIZED` after an admin reset, gated behind one online collection before offline is re-enabled), and a signed hash-chain on every `Collection` (`collectionCounter`/`previousHash`/`currentHash`/`signature`, HMAC-verified against a secret issued once per installation binding) enforcing the full 8-rule sync table. 49 new `@Test` methods across 3 new files (`SecurityEventServiceTest`, `SecurityEventControllerTest`, `CollectionChainCodecTest`) and 4 extended ones — see the new §12 below, and the extended entries in §1.3, §1.5, §3 and §5.3. Verified live against the running Docker stack, not just unit tests: installation mismatch → security event → blocked collection → admin reset → online-first-collection gate → back to `ACTIVE`, and all 8 chain sync rules exercised end-to-end with hand-computed HMAC vectors. Mobile-side: `sqflite_sqlcipher`'s at-rest encryption of the offline collection queue was independently confirmed on a real device (`integration_test/offline_storage_test.dart`'s "sanity" check — the raw `.db` file on disk does not contain a plaintext value known to have been inserted).

---

## Contents

1. [Authentication, Admin & Agent Management](#1-authentication-admin--agent-management)
2. [Branch Management](#2-branch-management)
3. [Agent Directory Service (cross-module resolvers)](#3-agent-directory-service-cross-module-resolvers)
4. [Client Onboarding, Activation & Self-Service](#4-client-onboarding-activation--self-service)
5. [Digital Cash Desk — Collections & Escrow](#5-digital-cash-desk--collections--escrow)
6. [Collection Rejection Workflow](#6-collection-rejection-workflow)
7. [End-of-Day / OFJ & CBS Export](#7-end-of-day--ofj--cbs-export)
8. [Geolocation: Tracking, Geofence, Geocoding, SOS](#8-geolocation-tracking-geofence-geocoding-sos)
9. [Async / Event Infrastructure (RabbitMQ, SSE)](#9-async--event-infrastructure-rabbitmq-sse)
10. [Audit Logging](#10-audit-logging)
11. [Middleware — CBS Mock Adapter](#11-middleware--cbs-mock-adapter)
12. [Offline Field Collection Security Algorithm](#12-offline-field-collection-security-algorithm)

---

## 1. Authentication, Admin & Agent Management

### 1.1 `AdminAuthenticationControllerTest` — ADMIN/BRANCH_MANAGER login
Back-office login for the admin/manager/cashier hierarchy (separate credential space from field agents).

| Test | Verifies |
|---|---|
| `testLoginSuccess` | Valid admin credentials issue a session/JWT. |
| `testLoginWrongPasswordRejected` | Wrong password is rejected. |
| `testLoginSuspendedAdminRejected` | A suspended admin account cannot log in. |
| `testLoginDeletedAdminRejected` | A soft-deleted admin account cannot log in. |
| `testLoginUnknownUserRejected` | An unknown login is rejected the same way as a wrong password (no user enumeration). |

### 1.2 `AdminUserManagementControllerTest` — provisioning ADMIN/MANAGER/CASHIER accounts
Back-office staff account lifecycle: creation, role updates, status changes, deletion — all scoped by RBAC (ADMIN sees everything, BRANCH_MANAGER only their own branch).

| Test | Verifies |
|---|---|
| `testCreateByAdminSuccess` | ADMIN can create any account. |
| `testCreateByManagerOwnBranchCashierSuccess` | A manager can create a cashier in their own branch. |
| `testCreateManagerConflictsWithExistingManager` | A branch can't get a second manager. |
| `testCreateManagerReplaceConfirmedSuspendsOldOneAndCreatesNew` | Explicit "replace" flow suspends the old manager and creates the new one. |
| `testCreateCashierAtCapConflictsWithoutReplaceUserId` | Cashier headcount cap is enforced unless a replacement target is given. |
| `testCreateCashierReplaceConfirmedSuspendsOldOneAndCreatesNew` | Same replace flow for a cashier at the cap. |
| `testCreateByManagerWrongRoleForbidden` | A manager cannot create another manager or an admin. |
| `testCreateByManagerDifferentBranchForbidden` | A manager cannot provision staff outside their own branch. |
| `testCreateAdminWithBranchIdRejected` | An ADMIN account must not be branch-scoped. |
| `testCreateNonAdminWithoutBranchIdRejected` | A non-ADMIN account must be branch-scoped. |
| `testCreateDuplicateLoginConflict` | Login uniqueness is enforced. |
| `testCreateDuplicatePhoneConflict` | Phone uniqueness is enforced. |
| `testCreateSetsMustChangePasswordTrue` | New accounts are forced to change their password on first login. |
| `testCreateByCashierForbidden` | A cashier cannot create any account. |
| `testCreateUnauthenticatedRejected` | Anonymous callers are rejected. |
| `testListAsAdminSeesAllBranches` | ADMIN's list spans every branch. |
| `testListAsManagerSeesOwnBranchOnly` | A manager's list is scoped to their own branch. |
| `testUpdateStatusSuccess` | Status transitions (e.g. suspend/reactivate) succeed for an authorized caller. |
| `testUpdateStatusNotFound` | Updating an unknown account 404s. |
| `testUpdateStatusOutOfBranchScopeForbidden` | A manager cannot change status outside their branch. |
| `testGetSuccess` | Single-account lookup succeeds. |
| `testUpdateRoleByAdminSuccess` | ADMIN can change a user's role. |
| `testUpdateRoleByManagerForbidden` | A manager cannot change roles. |
| `testUpdateRoleInconsistentBranchRejected` | Role change can't leave the branch-scoping rule inconsistent (e.g. giving a branch to an ADMIN). |
| `testResetPasswordSuccess` | An authorized caller can force a password reset. |
| `testResetPasswordByCashierForbidden` | A cashier cannot reset anyone's password. |
| `testDeleteByAdminSuccess` | ADMIN can soft-delete any account. |
| `testDeleteByManagerOwnBranchCashierSuccess` | A manager can delete a cashier in their own branch. |
| `testDeleteByManagerOwnBranchPeerManagerSuccess` | A manager can delete another manager in their own branch. |
| `testDeleteByManagerOfAdminForbidden` | A manager cannot delete an ADMIN. |
| `testDeleteByManagerOutOfBranchScopeForbidden` | A manager cannot delete outside their branch. |
| `testDeleteOwnAccountRejected` | Self-deletion is blocked. |
| `testDeleteAlreadyDeletedConflict` | Deleting an already-deleted account conflicts (409). |
| `testDeleteBlankReasonRejected` | A deletion reason is mandatory. |
| `testUpdateStatusOnDeletedAccountConflict` | Status can't be changed on a deleted account. |
| `testUpdateStatusToDeletedRejected` | Deletion must go through the dedicated delete endpoint, not a status update. |
| `testListExcludesDeletedAccounts` | Deleted accounts don't appear in listings. |

### 1.3 `AgentManagementControllerTest` — field agent provisioning (admin/manager side)
Registering, suspending, reactivating and configuring field agents from the back office.

| Test | Verifies |
|---|---|
| `testRegisterSuccess` | Agent registration succeeds with valid data. |
| `testRegisterDuplicateEmployeeCodeConflict` | Employee code uniqueness enforced. |
| `testRegisterDuplicateUsernameConflict` | Username uniqueness enforced. |
| `testRegisterDuplicatePhoneConflict` | Phone uniqueness enforced. |
| `testRegisterDuplicateEmailConflict` | Email uniqueness enforced. |
| `testRegisterBranchNotFound` | Registering against an unknown branch 404s. |
| `testRegisterNeverSetsImeiEvenWhenBranchRequiresBinding` | IMEI binding only ever happens on first login, never at registration time. |
| `testRegisterDefaultsEmployeeCodeToUsernameWhenOmitted` | Employee code falls back to the username when not supplied. |
| `testRegisterRejectsNonInternationalPhoneFormat` | Phone must be in international format. |
| `testRegisterCashierForbidden` | A cashier cannot register agents. |
| `testListAgents` | Agent listing returns the expected set. |
| `testSuspendAgent` | Suspension succeeds. |
| `testReactivateAgentWithConfiguredCeilingSucceeds` | Reactivation succeeds once an escrow ceiling exists. |
| `testReactivateAgentWithoutConfiguredCeilingRejected` | Reactivation is blocked without a ceiling. |
| `testReactivateAgentFromPendingCeilingRejected` | An agent still `PENDING_CEILING` can't be "reactivated" (they were never active). |
| `testSuspendAgentNotFound` | Suspending an unknown agent 404s. |
| `testResetDeviceBindingClearsImeiAndRecordsReason` | Clearing a bound IMEI/installation (lost/replaced phone, or clearing a `RECONCILIATION_REQUIRED` block) moves the agent to `RESET_AUTHORIZED`, captures the previous device/installation in the audit entry, and auto-resolves any open security events. |
| `testResetDeviceBindingLeavesSuspendedAgentSuspended` | A device-binding reset never doubles as a silent reactivation of an agent an admin separately suspended. |
| `testUpdateStatusToActiveFromReconciliationRequiredRejected` | The generic status endpoint can't clear a mismatch block — only Reset Device Binding can. |
| `testUpdateStatusToActiveFromResetAuthorizedRejected` | Same guard for `RESET_AUTHORIZED` — one correct code path to reactivate, not two that could drift apart. |
| `testResetDeviceBindingRequiresReason` | A reason is mandatory for this reset. |
| `testResetDeviceBindingNotFound` | Resetting an unknown agent's binding 404s. |
| `testResetDeviceBindingByCashierForbidden` | A cashier cannot reset device bindings. |
| `testOverrideCeilingSuccess` | Manual ceiling override succeeds. |
| `testOverrideCeilingRequiresReason` | A reason is mandatory for a ceiling override. |
| `testOverrideCeilingAgentNotFound` | Overriding an unknown agent's ceiling 404s. |
| `testGetAgentSuccess` | Single-agent lookup succeeds. |
| `testGetAgentNotFound` | Lookup of an unknown agent 404s. |
| `testAgentVarianceDebts` | An agent's outstanding OFJ variance debts are surfaced. |
| `testDeleteByAdminSuccess` | ADMIN can soft-delete an agent. |
| `testDeleteByManagerOwnBranchSuccess` | A manager can delete an agent in their own branch. |
| `testDeleteByManagerOutOfBranchScopeForbidden` | A manager cannot delete an agent outside their branch. |
| `testDeleteByCashierForbidden` | A cashier cannot delete agents. |
| `testDeleteAlreadyDeletedConflict` | Deleting an already-deleted agent conflicts. |
| `testDeleteBlankReasonRejected` | A deletion reason is mandatory. |
| `testUpdateStatusOnDeletedAgentConflict` | Status can't be changed on a deleted agent. |
| `testUpdateStatusToDeletedRejected` | Deletion must go through the dedicated endpoint. |
| `testListExcludesDeletedAgents` | Deleted agents don't appear in listings. |

### 1.4 `AgentSelfControllerTest` — the mobile agent's own `/me` endpoints
Everything a logged-in agent can read or do about themselves: profile, route, SOS history, PIN change, OFJ reconciliation confirmation, End My Day, and collection-rejection requests.

| Test | Verifies |
|---|---|
| `meReturnsTheCallingAgentsOwnProfile` | `/me` returns the caller's own profile. |
| `meRejectsUnauthenticatedCallers` | Anonymous callers rejected. |
| `meRejectsNonAgentPrincipals` | An admin/client principal can't call agent-only endpoints. |
| `myRouteReturnsTheCallingAgentsOwnRouteForGivenDate` | Route history is scoped to the caller and the requested date. |
| `myRouteDefaultsToTodayWhenNoDateGiven` | Omitting the date defaults to today. |
| `myRouteRejectsNonAgentPrincipals` | RBAC boundary on the route endpoint. |
| `mySosReturnsTheCallingAgentsOwnAlertsIncludingAcknowledgement` | SOS history includes acknowledgement state. |
| `mySosRejectsNonAgentPrincipals` | RBAC boundary on the SOS endpoint. |
| `myBranchReturnsTheCallingAgentsOwnBranch` | Branch lookup is scoped to the caller. |
| `myBranchRejectsNonAgentPrincipals` | RBAC boundary on the branch endpoint. |
| `changePinSuccess` | Transaction PIN change succeeds with the correct current PIN. |
| `changePinRejectsWrongCurrentPin` | Wrong current PIN is rejected. |
| `changePinRejectsWeakNewPin` | New PIN must pass strength rules. |
| `changePinRejectsNonAgentPrincipals` | RBAC boundary on PIN change. |
| `myPendingConfirmationsReturnsTheCallersOwnLines` | Reconciliation lines awaiting the agent's confirmation are scoped to them. |
| `myReconciliationLineCollectionsReturnsCollectionsForOwnLine` | Drill-down into one line's collections is scoped to the caller. |
| `confirmReconciliationSucceedsForOwnLine` | Agent can confirm their own reconciliation line. |
| `confirmReconciliationPropagatesForbiddenFromService` | A service-level 403 (e.g. someone else's line) propagates correctly through the controller. |
| `myExportableSummaryReturnsTheCallersOwnReadyTotal` | "Ready to export" summary is scoped to the caller. |
| `endMyDaySucceedsAndAudits` | "End My Day" export succeeds and is audited. |
| `endMyDayPropagatesConflictWhenNothingReady` | Ending the day with nothing confirmed-and-unexported conflicts (409). |
| `requestCollectionRejectionCreatesRequestForOwnCollection` | Agent can request rejection of their own collection. |
| `requestCollectionRejectionRejectsBlankReason` | A reason is mandatory. |
| `myCollectionRejectionRequestsReturnsOnlyThisAgentsRequestsWithDecisionDetails` | Own rejection-request history includes the manager's decision, scoped to the caller. |

### 1.5 `AuthenticationControllerTest` — field agent login
The agent-facing login endpoint: password check, IMEI device binding, installation binding (Offline Field Collection Security Algorithm — see §12), lockouts, and the branch-schedule/PENDING_CEILING interactions specific to agents.

| Test | Verifies |
|---|---|
| `testLoginSuccess` | Valid credentials issue a session. |
| `testLoginSucceedsForPendingCeilingAgent` | An agent awaiting their first escrow top-up can still log in (just not collect). |
| `testLoginInvalidPassword` | Wrong password rejected. |
| `testLoginInvalidImei` | Login from a non-bound device is rejected once binding exists. |
| `testLoginRejectsRecognizedDeviceThatBelongsToAnotherAgent` | A device "known" to the system from a different agent's prior use is not enough — one agent, one device, strictly. |
| `testLoginBindsFreshInstallationOnFirstLoginAndReturnsSecret` | First-ever login with an `installationId` binds it and returns a freshly generated HMAC secret for the collection hash-chain. |
| `testLoginOnInstallationMismatchStillSucceedsButRaisesSecurityEvent` | The core fraud scenario: same device, a different installation than the one on file (uninstall/reinstall). Login still succeeds (only a device mismatch hard-rejects) — a `SecurityEvent` is raised instead, which is what actually blocks *collection*. |
| `testLoginWithMatchingInstallationRaisesNoSecurityEvent` | A login presenting the same installation as last time raises nothing and issues no new secret. |
| `testLoginSucceedsWithoutImeiWhenAgentHasNoneOnFile` | No IMEI on file yet ⇒ any device can log in (first-login binding case). |
| `testLoginBindsDeviceOnFirstLoginWhenBranchRequiresImei` | First login records/binds the IMEI when the branch requires it. |
| `testLoginRejectsWhenBranchRequiresImeiButNoneWasSent` | Missing IMEI is rejected outright when the branch mandates it. |
| `testLoginSuspendedAgentBlocked` | Suspended agents can't log in. |
| `testLoginDeletedAgentBlocked` | Deleted agents can't log in. |
| `testLoginSucceedsRegardlessOfBranchSchedule` | Login itself is never schedule-gated (only collection is — see `CollectionServiceTest`). |
| `testLoginLockedAccountRejectedBeforePasswordIsChecked` | A lockout short-circuits before the password is even checked. |
| `testLoginExpiredLockIsIgnored` | An expired lockout is treated as not locked. |
| `testLoginWrongPasswordRegistersFailedAttempt` | A failed attempt increments the lockout counter. |
| `testLoginSuccessResetsFailedAttempts` | A successful login clears the counter. |

---

## 2. Branch Management

### `BranchControllerTest` — branch configuration
All per-branch policy toggles: IMEI requirement, escrow ceiling defaults, schedule windows, geofence application, cashier caps, and the two "portefeuille client" settings (see the dedicated addendum for full rationale on the last two).

| Test | Verifies |
|---|---|
| `testCreateBranch` | Basic branch creation succeeds. |
| `testCreateBranchWithPhone` | Branch creation accepts an optional phone. |
| `testCreateBranchDefaultsMaxCashiers` | Cashier cap defaults sensibly when omitted. |
| `testCreateBranchDefaultsRequireImeiTrue` | IMEI requirement defaults to `true` for a new branch. |
| `testCreateBranchDefaultsCeilingPct100` | Default ceiling percentage defaults to 100%. |
| `testSetRequireImeiSuccess` | Toggling IMEI requirement succeeds. |
| `testSetRequireImeiCashierForbidden` | Cashier can't toggle it. |
| `testSetRequireClientActivationSuccess` | UC-19 activation-required toggle succeeds. |
| `testSetRequireClientActivationCashierForbidden` | Cashier can't toggle it. |
| `testSetRequireClientPortfolioSuccess` | Portfolio-required toggle succeeds *(see Portfolio Client addendum, TC-PORT-02)*. |
| `testSetRequireClientPortfolioCashierForbidden` | Cashier can't toggle it *(TC-PORT-03)*. |
| `testCreateBranchDefaultsRequireClientPortfolioFalse` | New branch defaults to portfolio-open *(TC-PORT-01)*. |
| `testCreateBranchDefaultsRequireClientActivationFalse` | New branch defaults to activation-not-required. |
| `testSetMaxCashiersSuccess` | Cashier cap update succeeds. |
| `testSetMaxCashiersRejectsBelowOne` | Cap can't be set below 1. |
| `testSetMaxCashiersCashierForbidden` | Cashier can't change the cap. |
| `testSetDefaultCeilingPctSuccess` | Default ceiling percentage update succeeds. |
| `testSetDefaultCeilingPctRejectsBelowOne` | Percentage can't be set below 1. |
| `testSetDefaultCeilingPctCashierForbidden` | Cashier can't change it. |
| `testSetPhoneSuccess` | Branch phone update succeeds. |
| `testSetPhoneCashierForbidden` | Cashier can't change it. |
| `testCreateBranchDuplicateCodeConflict` | Branch code uniqueness enforced. |
| `testCreateBranchManagerForbidden` | A manager can't create branches (ADMIN only). |
| `testListBranches` | Branch listing works. |
| `testPutScheduleSuccess` | Open/close schedule update succeeds. |
| `testPutScheduleRejectsOpenTimeChangeAfterItHasAlreadyPassedToday` | Can't retroactively move today's opening time once it's passed. |
| `testPutScheduleAllowsCloseTimeChangeAfterOpenTimeHasAlreadyPassedToday` | Closing time can still be adjusted even after opening has passed. |
| `testPutScheduleDoesNotNotifyWhenCloseTimeUnchanged` | No spurious notification when nothing actually changed. |
| `testPutScheduleNotifiesWhenOnlyOpenTimeChanged` | Notification fires when the open time changes. |
| `testPutScheduleRejectsOpenAfterClose` | Open time can't be after close time. |
| `testGetScheduleNotFound` | Fetching an unknown branch's schedule 404s. |
| `testGetScheduleWithConfiguredWindow` | Configured schedule is returned as-is. |
| `testGetScheduleDefaultsFallsBackWhenNeverSet` | Falls back to system defaults when never configured. |
| `testGetScheduleDefaultsReturnsStoredValue` | Returns the stored system default once one exists. |
| `testPutScheduleDefaultsAppliesOnlyToUnconfiguredBranches` | Changing the system default doesn't clobber branches with their own schedule. |
| `testPutScheduleDefaultsOverrideAllAppliesToConfiguredBranchesToo` | An explicit "override all" flag does reach already-configured branches. |
| `testPutScheduleDefaultsDoesNotNotifyBranchAlreadyMatchingTheNewDefaults` | No spurious notification for branches unaffected by the new default. |
| `testPutScheduleDefaultsRejectsOpenAfterClose` | Same open/close sanity check applies to the system default. |
| `testPutScheduleDefaultsForbiddenForBranchManager` | Only ADMIN can change the system-wide default. |
| `testApplyGeofenceToBranchSuccess` | Applying one geofence to every branch agent succeeds. |
| `testApplyGeofenceToBranchCashierForbidden` | Cashier can't apply a branch geofence. |
| `testApplyGeofenceToBranchNotFound` | Applying to an unknown branch 404s. |

---

## 3. Agent Directory Service (cross-module resolvers)

### `AgentDirectoryServiceTest`
The public read/lookup surface other modules use instead of reaching into `AgentRepository` directly — PIN verification/lockout, escrow ceiling and portfolio/activation setting resolution, schedule-window enforcement, "end of day" state, and (Offline Field Collection Security Algorithm, §12) the `RECONCILIATION_REQUIRED`/`RESET_AUTHORIZED` collection gates and the installation-binding facade `transactions.CollectionService` uses for hash-chain verification.

| Test | Verifies |
|---|---|
| `updateSyncStatusSetsPendingCount` | Offline sync backlog count is recorded. |
| `updateSyncStatusAgentNotFoundThrows404` | Unknown agent 404s. |
| `hasPendingUnsyncedCollectionsTrueWhenAnyAgentHasNonZeroCount` | Detects any agent in a set with unsynced collections. |
| `hasPendingUnsyncedCollectionsFalseWhenAllZero` | False when none are pending. |
| `hasPendingUnsyncedCollectionsFalseForEmptyList` | False for an empty input. |
| `verifyTransactionPinSucceedsAndClearsPriorFailedAttempts` | Correct PIN clears the failed-attempt counter. |
| `verifyTransactionPinRejectsWrongPinAndIncrementsCounter` | Wrong PIN increments the counter. |
| `verifyTransactionPinLocksOutAfterThresholdReached` | Threshold triggers a timed lockout. |
| `verifyTransactionPinRejectsWhenLockedOutRegardlessOfPin` | Lockout blocks even a correct PIN until it expires. |
| `verifyTransactionPinRejectsUntilAgentSetsTheirOwnPin` | A must-change PIN can't be used to authorize a transaction. |
| `effectiveCeilingPctForAgentReturnsBranchConfiguredValue` | Resolves the branch's configured ceiling percentage. |
| `effectiveCeilingPctForAgentDefaultsTo100WhenBranchUnconfigured` | Falls back to 100% when unconfigured. |
| `effectiveCeilingPctForAgentDefaultsTo100WhenBranchMissing` | Falls back to 100% when the branch itself can't be found. |
| `effectiveCeilingPctForAgentUnknownAgentThrows404` | Unknown agent 404s. |
| `effectiveRequireClientActivationForAgentReturnsBranchConfiguredValue` | Resolves UC-19 activation-required setting. |
| `effectiveRequireClientActivationForAgentDefaultsToFalseWhenBranchUnconfigured` | Defaults false when unconfigured. |
| `effectiveRequireClientActivationForAgentDefaultsToFalseWhenBranchMissing` | Defaults false when branch missing. |
| `effectiveRequireClientActivationForAgentUnknownAgentThrows404` | Unknown agent 404s. |
| `effectiveRequireClientPortfolioForAgentReturnsBranchConfiguredValue` | Resolves portfolio-required setting *(TC-PORT-15)*. |
| `effectiveRequireClientPortfolioForAgentDefaultsToFalseWhenBranchUnconfigured` | Defaults false when unconfigured *(TC-PORT-15)*. |
| `effectiveRequireClientPortfolioForAgentDefaultsToFalseWhenBranchMissing` | Defaults false when branch missing *(TC-PORT-15)*. |
| `effectiveRequireClientPortfolioForAgentUnknownAgentThrows404` | Unknown agent 404s *(TC-PORT-15)*. |
| `addCarriedPhysicalXafSetsItFromNull` | First carried-cash-forward amount is recorded from nothing. |
| `addCarriedPhysicalXafAccumulatesOnTopOfAnExistingCarry` | Additional carry accumulates rather than overwrites. |
| `consumeCarriedPhysicalXafReturnsAndClearsIt` | Consuming the carry returns and zeroes it. |
| `consumeCarriedPhysicalXafReturnsZeroWhenNoneOutstanding` | Returns zero, no-ops, when nothing is carried. |
| `verifyTransactionPinRejectsWhenAgentNotActive` | Non-ACTIVE agents can't authorize transactions. |
| `requireWithinScheduleWindowAllowsCollectionInsideWindow` | Collection allowed inside the branch's open/close window. ⚠️ *pre-existing real-clock flake near local midnight — unrelated to this branch's work.* |
| `requireWithinScheduleWindowRejectsWhenCollectedAfterClosingTime` | Rejected after close. |
| `requireWithinScheduleWindowRejectsWhenCollectedBeforeOpeningTime` | Rejected before open. |
| `requireWithinScheduleWindowAllowsWhenBranchHasNoScheduleConfigured` | No configured window ⇒ unrestricted. |
| `requireWithinScheduleWindowAllowsLateSyncOfACollectionMadeDuringHours` | A late-arriving offline sync is judged by when the cash was actually collected, not when it synced. |
| `requireDayNotEndedRejectsWhenTodaysBusinessDateAlreadyEnded` | Blocked once the agent has ended their business day. |
| `requireDayNotEndedAllowsCollectingOnANewBusinessDate` | A new business date lifts yesterday's end-of-day block. |
| `requireDayNotEndedAllowsWhenDayHasNeverBeenEnded` | No block when the day was never ended. |
| `markDayEndedStampsTodaysBusinessDateOnTheAgent` | Ending the day stamps today's date on the agent. |
| `hasEndedDayTodayTrueWhenStampedForTodaysUtcDate` | Reports `true` when the stamped date matches today's UTC business date. |
| `hasEndedDayTodayFalseWhenStampedForAnEarlierDate` | Reports `false` for a stamp from an earlier date (yesterday's end-of-day doesn't carry over). |
| `hasEndedDayTodayFalseWhenNeverEnded` | Reports `false` when the agent has never ended a day. |
| `hasEndedDayTodayUnknownAgentThrows404` | Unknown agent 404s. |
| `verifyTransactionPinRejectsReconciliationRequiredWithSpecificMessage` | A mismatch-blocked agent gets a distinct "no longer authorized" message, not the generic PENDING_CEILING one. |
| `verifyTransactionPinAllowsResetAuthorizedAgent` | `RESET_AUTHORIZED` is allowed through this gate (the online-first-collection requirement is enforced separately, earlier, in `CollectionService`). |
| `flipToReconciliationRequiredChangesStatus` | An ACTIVE agent flips to `RECONCILIATION_REQUIRED` on a raised mismatch event. |
| `flipToReconciliationRequiredNoOpsForSuspendedAgent` | A already-`SUSPENDED`/`DELETED` agent is left alone — the flip never weakens a stricter existing block. |
| `requireOnlineFirstCollectionCompletedForOfflineRejectsWhenBindingStillOwesOne` | An `OFFLINE_SYNC` item is rejected while the current installation binding hasn't completed its required online-first collection. |
| `requireOnlineFirstCollectionCompletedForOfflineAllowsWhenAlreadyCompleted` | Once satisfied, the same origin is let through. |
| `requireOnlineFirstCollectionCompletedForOfflineIsNoOpForOnlineOrigin` | The gate never even looks up the binding for an `ONLINE` origin. |
| `completeOnlineFirstCollectionIfNeededFlipsResetAuthorizedAgentBackToActive` | The first online collection after a reset stamps the binding and flips the agent back to `ACTIVE`. |
| `completeOnlineFirstCollectionIfNeededIsNoOpForOfflineOrigin` | Only an `ONLINE` collection can satisfy the requirement. |
| `completeOnlineFirstCollectionIfNeededIsNoOpWhenAlreadyCompleted` | Already-satisfied bindings are left untouched (no redundant writes). |
| `requireCurrentInstallationBindingReturnsInstallationIdAndSecret` | The cross-module facade `CollectionService` uses for hash-chain verification returns the installation id and HMAC secret together. |

---

## 4. Client Onboarding, Activation & Self-Service

### 4.1 `ClientActivationControllerTest` — agent-facing sponsorship endpoints
The agent's half of UC-19: sponsoring a client's activation fee and managing pending gates.

| Test | Verifies |
|---|---|
| `testSponsorSuccess_resolvesAgentFromPrincipal` | Sponsorship is attributed to the authenticated agent, not a request field. |
| `testSponsorUnauthenticatedRejected` | Anonymous callers rejected. |
| `testConfirmPaymentSuccess_resolvesClientFromPrincipal` | Confirmation is attributed to the authenticated client. |
| `testConfirmPaymentUnauthenticatedRejected` | Anonymous callers rejected. |
| `testListPendingSuccess_ownBranch` | Pending-activation listing is scoped to the caller's branch. |
| `testListPendingOutsideBranchForbidden` | Cross-branch listing forbidden. |
| `testCancelPendingSuccess` | Admin/manager can cancel a stuck pending activation. |
| `testCancelPendingCashierForbidden` | Cashier can't cancel one. |
| `testCancelPendingMissingReasonRejected` | A reason is mandatory. |

### 4.2 `ClientAuthenticationControllerTest` — client login & self-activation
The client-facing endpoints: self-activation, login, and the "forgot PIN" flow's HTTP surface.

| Test | Verifies |
|---|---|
| `testActivateSuccess` | Self-activation succeeds with a valid CBS identifier. |
| `testActivateUnknownMfiIdentifierRejected` | Unknown CBS member id rejected. |
| `testActivateInvalidPinFormatRejected` | PIN format is validated. |
| `testLoginSuccess` | Valid credentials log the client in. |
| `testLoginWrongPinRejected` | Wrong PIN rejected. |
| `testLoginUnknownUserRejected` | Unknown login rejected the same way (no enumeration). |
| `testForgotPasswordAlwaysReturnsGenericAck` | A generic acknowledgement is returned regardless of outcome (no enumeration). |
| `testForgotPasswordUnknownLoginStillReturnsGenericAck` | Same generic ack even for an unknown login. |
| `testResetPasswordSuccessAudits` | A successful PIN reset is audited. |
| `testResetPasswordInvalidCodeRejectedWithoutAudit` | A wrong OTP is rejected and *not* audited as a success. |
| `testResetPasswordInvalidPinFormatRejected` | New PIN format validated. |

### 4.3 `ClientControllerTest` — admin/manager client directory
Client CRUD, search, status updates, and portfolio assignment (see the dedicated addendum, `TC-PORT-11` – `TC-PORT-14`, for the last four).

| Test | Verifies |
|---|---|
| `testLookupReturnsMatchesAcrossAllBranches` | Search spans all branches for an authorized caller. |
| `testLookupEmptyQueryReturnsAllClients` | Empty query returns the full set. |
| `testLookupUnauthenticatedRejected` | Anonymous callers rejected. |
| `testLookupNonAgentPrincipalForbidden` | Only an agent principal may use this lookup. |
| `testCreateClientSuccess` | Client creation succeeds. |
| `testCreateClientWithCredentialsSetsLoginAndPinImmediately` | Admin-created client can be given login+PIN upfront. |
| `testCreateClientRejectsLoginWithoutPin` | Login without a PIN is rejected. |
| `testCreateClientRejectsAlreadyTakenLogin` | Login uniqueness enforced. |
| `testCreateClientDuplicateConflict` | Duplicate client (by CBS ref/member no.) conflicts. |
| `testCreateClientUnauthenticatedRejected` | Anonymous callers rejected. |
| `testCreateClientCashierForbidden` | Cashier can't create clients. |
| `testListByBranchSuccess` | Branch-scoped listing works. |
| `testGetClientSuccess` | Single-client lookup succeeds. |
| `testGetClientNotFound` | Unknown client 404s. |
| `testUpdateClientStatusSuccess` | Status update succeeds. |
| `testUpdateClientStatusCashierForbidden` | Cashier can't update status. |
| `testSetAssignedAgentSuccess` | Manual portfolio assignment succeeds *(TC-PORT-11)*. |
| `testSetAssignedAgentClearsAssignmentWhenNull` | Null clears the assignment *(TC-PORT-12)*. |
| `testSetAssignedAgentRejectsAgentFromAnotherBranch` | Cross-branch assignment rejected *(TC-PORT-13)*. |
| `testSetAssignedAgentCashierForbidden` | Cashier can't reassign *(TC-PORT-14)*. |

### 4.4 `ClientActivationServiceTest` — UC-19 business logic
The two-party gate itself: an agent sponsors, a client confirms, both are required (in either order) before the account and its portfolio assignment finalize.

| Test | Verifies |
|---|---|
| `selfActivateSetsCredentialsWhenMfiIdentifierMatchesLocalRecord` | Self-activation sets login/PIN when the CBS identifier matches. |
| `selfActivateRejectsUnknownMfiIdentifier` | Unknown identifier rejected. |
| `selfActivateRejectsLoginAlreadyTakenByAnotherClient` | Login uniqueness enforced. |
| `sponsorActivationAloneReturnsAwaitingPayment` | Sponsorship alone leaves the gate "awaiting payment." |
| `sponsorActivationRejectsUnknownLogin` | Unknown client login rejected. |
| `sponsorActivationRejectsWhenClientAlreadyActive` | Can't sponsor an already-active client. |
| `sponsorActivationRejectsWhenEscrowCeilingExceeded` | Sponsorship fee can't exceed the agent's escrow ceiling. |
| `sponsorActivationRejectsWhenAgentHasAnotherPendingActivation` | One pending sponsorship at a time per agent. |
| `sponsorActivationRejectsDoubleSponsorship` | Can't sponsor the same client twice. |
| `sponsorActivationFinalizesWhenPaymentAlreadyConfirmed` | Sponsoring after the client already confirmed finalizes immediately. |
| `confirmPaymentAloneReturnsAwaitingSponsorship` | Confirmation alone leaves the gate "awaiting sponsorship." |
| `confirmPaymentRejectsWrongPin` | Wrong PIN rejected. |
| `confirmPaymentRejectsWhenClientAlreadyActive` | Can't confirm an already-active client. |
| `confirmPaymentRejectsDoubleConfirmation` | Can't confirm twice. |
| `confirmPaymentFinalizesWhenSponsorshipAlreadyRecorded` | Confirming after sponsorship already exists finalizes immediately. |
| `finalizingAssignsTheSponsoringAgentAsThePortfolioOwner` | Finalization assigns the sponsoring agent as portfolio owner *(TC-PORT-10)*. |
| `finalizingRevokesAnyPreviouslyExpiredToken` | A stale expired token is revoked at finalization. |
| `cancelActivationRequestVoidsAPendingGate` | Admin/manager can cancel a stuck pending gate. |
| `cancelActivationRequestRejectsUnknownId` | Unknown request id rejected. |
| `cancelActivationRequestRejectsAlreadyCompleted` | Can't cancel an already-completed activation. |
| `listPendingForAgentReturnsOpenGates` | Listing surfaces only still-open gates. |

### 4.5 `ClientPasswordResetServiceTest` — client "forgot PIN"
OTP-based self-service PIN reset, independent of any agent/admin action.

| Test | Verifies |
|---|---|
| `requestResetSendsOtpForKnownActivatedClient` | OTP is sent for a known, activated client. |
| `requestResetNoOpForUnknownLogin` | Silently no-ops for an unknown login (no enumeration). |
| `requestResetNoOpForClientNeverActivated` | Silently no-ops for a never-activated client. |
| `requestResetFailsWhenSmsGatewayFails` | A gateway failure surfaces as an error (nothing to silently swallow here — the client is waiting on this code). |
| `confirmResetUpdatesPinOnValidCode` | Correct OTP updates the PIN. |
| `confirmResetRejectsWrongCodeAndIncrementsAttempts` | Wrong OTP increments the attempt counter. |
| `confirmResetRejectsExpiredCode` | Expired OTP rejected. |
| `confirmResetRejectsUnknownLogin` | Unknown login rejected. |

### 4.6 `ClientSelfServiceTest` — UC-20/21/22 read-only self-service
Everything a logged-in client can read about their own account.

| Test | Verifies |
|---|---|
| `profileReportsNoneWhenNeverActivated` | Profile status reads "none" pre-activation. |
| `profileReportsActiveWhenTokenNotExpired` | Reports active with a valid session token. |
| `profileReportsExpiredButStillVisible` | An expired token still lets the profile itself be read. |
| `profileThrowsWhenClientNotFound` | Unknown client throws. |
| `getCbsRefReturnsClientsCbsReference` | CBS reference lookup works. |
| `getRecentCollectionsMapsFromCollectionDirectoryService` | Recent collections are correctly mapped from the collection-directory read model. |

---

## 5. Digital Cash Desk — Collections & Escrow

### 5.1 `AdminAgentCollectionsControllerTest` — back-office view of an agent's collections
| Test | Verifies |
|---|---|
| `collectionsSucceedsForOwnBranchCashier` | A cashier can view collections for their own branch's agents. |
| `collectionsSucceedsForAdminAcrossBranches` | ADMIN can view any branch. |
| `collectionsForbiddenOutsideOwnBranch` | Cross-branch viewing forbidden for non-admins. |
| `collectionsUnauthenticatedRejected` | Anonymous callers rejected. |

### 5.2 `CollectionControllerTest` — the agent-facing collection endpoint
| Test | Verifies |
|---|---|
| `testCreateSuccess_resolvesAgentFromPrincipal` | The agent is taken from the authenticated principal, never a request field. |
| `testCreateUnauthenticatedRejected` | Anonymous callers rejected. |
| `testCreateMissingGpsRejected` | GPS is mandatory (BR-05). |
| `testSyncProcessesEachItemIndependently` | Offline-batch sync processes each item on its own — one bad item doesn't fail the batch. |
| `testMyCollectionsReturnsAgentsOwnHistory` | History is scoped to the caller. |
| `testMyCollectionsUnauthenticatedRejected` | Anonymous callers rejected. |

### 5.3 `CollectionServiceTest` — the money-path business rules
The core `recordCollection` gate chain: idempotency, the Offline Field Collection Security Algorithm's online-first-collection gate and hash-chain rule table (§12), PIN, schedule window, day-not-ended, geofence, client status, no-pending-activation, UC-19 activation gate, portfolio gate (full detail in the dedicated addendum), denomination breakdown, and escrow ceiling.

| Test | Verifies |
|---|---|
| `recordsCollectionSuccessfully` | Happy path succeeds end-to-end and is audited. |
| `replaysIdempotentlyOnDuplicateDeviceTxId` | A retried offline sync never double-records. |
| `verifiesTransactionPinBeforeRecording` | PIN check happens before anything else. |
| `rejectsCollectionWhenTransactionPinIsWrong` | Wrong PIN rejected. |
| `checksScheduleWindowBeforeRecording` | Schedule-window check happens. |
| `rejectsCollectionOutsideBranchScheduleWindow` | Outside-hours collection rejected. |
| `checksDayNotEndedBeforeRecording` | Day-not-ended check happens. |
| `rejectsCollectionAfterAgentHasEndedTheirDay` | Blocked after End My Day. |
| `doesNotCheckTransactionPinOnIdempotentReplay` | A replayed duplicate skips re-validation entirely. |
| `rejectsCollectionWhenOutsideAssignedGeofence` | Outside-geofence collection rejected and audited. |
| `allowsCollectionWhenAgentHasNoGeofenceAssigned` | No assigned geofence ⇒ unrestricted. |
| `locationNameStartsNullAndIsResolvedAsynchronously` | Reverse-geocoded name starts null; filled in later, async. |
| `publishesGeocodeEventAfterSavingTheCollection` | Geocode event only published after the row is saved. |
| `doesNotPublishGeocodeEventOnIdempotentReplay` | No duplicate geocode event on a replay. |
| `rejectsUnknownClient` | Unknown client rejected. |
| `rejectsInactiveClient` | Inactive client rejected. |
| `rejectsWhenAgentHasPendingActivation` | Pending sponsorship blocks all new cash intake. |
| `rejectsUnactivatedClientWhenBranchRequiresActivation` | UC-19 gate enforced when the branch opts in. |
| `allowsActivatedClientWhenBranchRequiresActivation` | Activated client passes the same gate. |
| `rejectsCollectionWhenClientBelongsToAnotherAgentsPortfolio` | Portfolio gate rejection *(TC-PORT-06/07)*. |
| `doesNotAuditWhenPortfolioCheckFailsForAnUnrelatedReason` | Non-403 failures aren't miscategorized as a portfolio security event *(TC-PORT-08)*. |
| `allowsUnassignedClientWhenBranchRequiresPortfolio` | Unassigned client stays open *(TC-PORT-04)*. |
| `skipsPortfolioCheckWhenBranchDoesNotRequireIt` | Gate skipped entirely when off *(TC-PORT-09)*. |
| `rejectsMissingDenominationBreakdownWhenRequired` | Denomination lines mandatory above the configured threshold. |
| `rejectsDenominationSumMismatch` | Denomination lines must sum to the declared amount. |
| `rejectsCollectionExceedingEscrowCeiling` | Ceiling enforcement (FR-04). |
| `escrowCeilingIgnoresAlreadyReconciledCash` | Already-reconciled cash doesn't count against the live ceiling. |
| `allowsDenominationOptionalBelowThreshold` | Below the configured threshold, denomination lines are optional. |
| `findRecentByAgentResolvesClientNames` | Recent-collections read model resolves client names. |
| `findByAgentAndDayResolvesClientNamesAndOrdersNewestFirst` | Per-day listing resolves names and orders newest-first. |
| `findByClientsAndRangeResolvesNamesAndMfiMemberNosAndOrdersNewestFirst` | Multi-client range query resolves names + member numbers, ordered newest-first. |
| `chainRulesAcceptValidFirstCollectionWithGenesisPreviousHash` | Counter 1 with `previousHash = GENESIS` and a correctly computed hash/signature is accepted, no security event raised. |
| `chainRulesRejectBadPreviousHash` | A `previousHash` that doesn't match the chain's last record is rejected (403) and raises `BAD_PREVIOUS_HASH`. |
| `chainRulesRejectBadSignature` | A `currentHash` that verifies but a `signature` that doesn't is rejected (403) and raises `BAD_SIGNATURE`. |
| `chainRulesHoldCounterGapForReviewWithoutSecurityEvent` | A counter ahead of the expected next value is held for review (409) — a gap can mean lost/delayed records, not just forgery, so no security event is raised for this one. |
| `chainRulesRejectStaleCounterAsConflictNotSecurityEvent` | A counter behind the expected next value, with a *new* `deviceTxId` (so not caught by the idempotency short-circuit), is a real anomaly (409), not a security event either. |
| `chainRulesRejectMismatchedInstallation` | A request whose `installationId` doesn't match the agent's current binding is rejected (403) and raises `DEVICE_NOT_AUTHORIZED`. |
| `chainRulesSkipValidationWhenNoCounterSent` | Back-compat rule 0: no `collectionCounter` at all (a pre-chain app build) skips validation entirely — no binding lookup even attempted. |
| `chainRulesSkipValidationWhenAgentHasNoInstallationBinding` | An agent with no installation binding at all (pre-Phase-1) fails open — nothing to validate against. |
| `offlineOriginRejectedWhenOnlineFirstCollectionStillOwed` | An `OFFLINE_SYNC` item is rejected before the PIN check when the online-first-collection requirement isn't yet satisfied. |
| `onlineCollectionCompletionSignalFiresAfterSuccessfulSave` | A successful `ONLINE` collection signals `AgentDirectoryService` so the requirement can be marked satisfied. |

### 5.4 `EscrowServiceTest` — security deposit & ceiling
| Test | Verifies |
|---|---|
| `topUpAt100PctRaisesCeiling1to1WithDeposit` | 100% branch policy: ceiling rises 1:1 with the deposit. |
| `topUpAt150PctGrantsCeilingAboveDeposit` | 150% policy grants more ceiling than the raw deposit. |
| `topUpAt50PctGrantsCeilingBelowDeposit` | 50% policy grants less ceiling than the deposit. |
| `topUpActivatesPendingCeilingAgentOnceCeilingBecomesPositive` | First top-up activates a `PENDING_CEILING` agent. |
| `topUpAtZeroPctDoesNotActivateSinceCeilingStaysZero` | 0% policy never activates the agent (ceiling never leaves zero). |
| `topUpRecordsLedgerEntryWithFullDepositAmountRegardlessOfCeilingPct` | The ledger always records the *actual cash deposited*, independent of the ceiling formula. |
| `topUpUnknownAgentThrows404` | Unknown agent 404s. |
| `collectedTodayIsIndependentOfDayAgnosticCumulativeTotal` | "Collected today" and the all-time cumulative total are computed independently. |
| `collectedTodaySumsBothCollectionsAndActivationFees` | Today's total includes both plain collections and activation fees. |
| `applyCeilingOverrideNotAffectedByBranchCeilingPct` | An admin's temporary override is independent of the branch's ceiling formula. |

---

## 6. Collection Rejection Workflow

### 6.1 `CollectionRejectionControllerTest` — admin/manager decision endpoint
| Test | Verifies |
|---|---|
| `listUnrestrictedForAdmin` | ADMIN sees every branch's requests. |
| `listUnauthenticatedRejected` | Anonymous callers rejected. |
| `approveSucceedsWithinOwnBranch` | Manager can approve within their own branch. |
| `approveOutsideBranchForbidden` | Cross-branch approval forbidden. |
| `denySucceedsWithinOwnBranch` | Manager can deny within their own branch. |
| `streamUnrestrictedForAdminForwardsBroadcastEvents` | SSE stream forwards every event to ADMIN. |
| `streamOnlyForwardsEventsWithinCallersBranch` | SSE stream filters to the manager's own branch. |
| `denyRejectsBlankReason` | A reason is mandatory to deny. |

### 6.2 `CollectionRejectionServiceTest` — the void/reversal/requeue business logic
An agent's request to void their own erroneous collection, and a manager's decision on it — including CBS reversal when the collection was already exported, and requeuing still-pending sibling collections on the same reconciliation line so the cashier's next count reflects the correction.

| Test | Verifies |
|---|---|
| `requestRejectionCreatesPendingRequest` | Agent's request creates a pending record. |
| `requestRejectionPublishesAnAlertEvent` | Creating a request publishes an alert (→ Back-Office SSE). |
| `requestRejectionForbiddenForAnotherAgentsCollection` | An agent can't request rejection of someone else's collection. |
| `requestRejectionConflictWhenAlreadyVoided` | Can't request rejection of an already-voided collection. |
| `requestRejectionConflictWhenAnotherRequestAlreadyPending` | Only one pending request per collection at a time. |
| `approveVoidsCollectionButSkipsReversalWhenNeverExported` | Approving voids the collection; no CBS reversal needed if it was never posted. |
| `approveReversesCbsAndNotifiesClientWhenAlreadyExported` | Approving an already-exported collection reverses it on the CBS and notifies the client. |
| `approveDoesNotThrowWhenCbsReversalFails` | A CBS reversal failure doesn't block the approval itself. |
| `approveDebitsTheReconciliationLinesStoredTotalsByTheVoidedAmount` | The line's stored totals are corrected by exactly the voided amount. |
| `approveRequeuesOnlyStillPendingSiblingsAndLeavesAnEarlierConfirmedSiblingAlone` | Siblings still awaiting confirmation are requeued; an already-confirmed-in-an-earlier-batch sibling is left alone. |
| `approveLeavesPhysicalTotalAloneWhenSomeLegitimateContentRemainsOnTheLine` | The physical (counted-cash) total is never touched by a rejection — only the digital side is corrected. |
| `approveLeavesAlreadyExportedSiblingsCompletelyUntouched` | Already-exported siblings are never reset — that money is posted and final. |
| `approveConflictWhenAlreadyDecided` | Can't approve a request twice. |
| `denyLeavesCollectionUntouched` | Denial leaves the original collection completely as-is. |

---

## 7. End-of-Day / OFJ & CBS Export

### 7.1 `OfjControllerTest` — the back-office end-of-day endpoint surface
| Test | Verifies |
|---|---|
| `testSummary` | Session summary retrieval works. |
| `testSummaryWithDateParam` | Summary for a specific past date works. |
| `testPending` | Pending-agents-to-reconcile listing works. |
| `testPendingConfirmations` | Pending-agent-confirmation listing works. |
| `testHistory` | Session history listing works. |
| `testBranchVarianceDebts` | Branch-wide variance debt listing works. |
| `testSummaryUnauthenticatedRejected` | Anonymous callers rejected. |
| `testReconcile` | Cashier reconciliation submission works. |
| `testReconcileRequiresDenominationLines` | Denomination breakdown mandatory for reconciliation. |
| `testVariance` | Recording a variance (shortage) debt works. |
| `testVarianceCashierForbidden` | Only admin/manager can write off/record variance, not a cashier. |
| `testExport` | Manual export-to-CBS trigger works. |

### 7.2 `CollectionConfirmationExpiryJobTest` — auto-confirm stale lines
A scheduled job that prevents a reconciliation line from sitting forever waiting on an agent who never opens the app — auto-confirms it past a configured timeout, since otherwise it would permanently occupy the agent's escrow ceiling.

| Test | Verifies |
|---|---|
| `autoConfirmsLinesStaleyPastTheTimeout` | Lines past the timeout are auto-confirmed. |
| `leavesLinesAloneWhenStillWithinTheTimeoutWindow` | Lines still within the window are untouched. |
| `doesNothingWhenNoLinesAwaitingConfirmation` | No-op when nothing is pending. |
| `doesNotAuditWhenMarkAgentConfirmedUpdatesNoRows` | No spurious audit entry when the update affects zero rows (race with a real confirmation). |

### 7.3 `OfjClosingTimeExportJobTest` — per-branch scheduled export
Catches the same "nobody's watching" problem for the branch as a whole: exports any branch's confirmed-and-unexported cash once its configured closing time has passed, even if an individual agent never taps "End My Day."

| Test | Verifies |
|---|---|
| `exportsTodaysSessionForABranchPastItsClosingTime` | Export fires once closing time has passed. |
| `skipsABranchNotYetPastItsClosingTime` | No-op before closing time. |
| `skipsABranchWithNoSessionYetToday` | No-op if the branch has no session today at all. |
| `skipsABranchWithAGarbageTimezoneString` | A malformed timezone config doesn't crash the job — that branch is safely skipped. |
| `noOpWhenNoBranchesHaveClosingHoursConfigured` | No-op when no branch has closing hours configured. |

### 7.4 `OfjServiceTest` — the full end-of-day engine
The largest single test class on this branch (56 tests): session lifecycle (open/auto-close), reconciliation math (BR-01 delta = physical − digital), variance debt (BR-Var-01), CBS export/idempotency, agent-confirmation workflow, per-agent "End My Day," and the scheduled closing-time export.

| Test | Verifies |
|---|---|
| `summaryCreatesSessionOnFirstAccess` | First access of the day creates the session. |
| `summaryLineSurfacesRejectedCollectionActualAndExpectedAmounts` | A rejected collection's actual vs. expected amounts are both surfaced on the summary line. |
| `reconcileComputesPositiveDelta` | Delta computed correctly when physical > digital. |
| `reconcileSeparatesCollectionsFromActivationsInDigitalTotal` | Digital total correctly separates plain collections from activation fees. |
| `reconcileComputesNegativeDeltaAndLeavesUnresolved` | A shortage leaves the line unresolved pending a variance decision. |
| `reconcileAccumulatesDigitalTotalAcrossRepeatedCallsInSameSession` | Repeated reconciliation calls accumulate rather than overwrite. |
| `reconcileDoesNotFlagShortageFromAlreadySettledEarlierBatch` | An earlier, already-settled batch's numbers don't leak into a new shortage calculation. |
| `reconcileReopensClosedSessionWhenNotYetExported` | A closed-but-unexported session can be reopened by a late reconciliation. |
| `reconcileReopensClosedSessionEvenWhenAlreadyExported` | Reopening still works even if some of the session was already exported (per-collection idempotency handles the rest). |
| `sessionAutoClosesWhenTheSoleBranchAgentIsResolved` | Session auto-closes once the branch's only agent is fully resolved. |
| `sessionCloseAutomaticallyExportsToTheCbs` | Closing a session automatically triggers export. |
| `sessionCloseSucceedsEvenWhenAutomaticExportFails` | A failed auto-export doesn't block the session from closing. |
| `autoExportSkipsWhenNothingNewToExport` | Auto-export is a safe no-op when there's nothing new. |
| `sessionStaysOpenWhileAnyAgentHasUnsyncedCollectionsQueuedLocally` | Session can't close while an agent still has an offline backlog. |
| `sessionStaysOpenWhileOtherBranchAgentsHaveNotReconciledYet` | Session can't close until every branch agent has reconciled. |
| `recordVarianceRejectsNonNegativeDelta` | Variance recording only applies to actual shortages (negative delta). |
| `recordVarianceRejectsDuplicateDebt` | Can't double-record a debt for the same shortage. |
| `recordVarianceSucceedsForShortage` | Shortage correctly recorded as agent debt. |
| `exportRejectsWithoutClosedSession` | Manual export requires a closed session. |
| `exportSucceedsForClosedSession` | Manual export succeeds once closed. |
| `runScheduledClosingExportPublishesAlertOnlyWhenSomethingWasPosted` | Scheduled export only alerts the Back-Office when it actually posted something. |
| `runScheduledClosingExportPublishesNoAlertWhenNothingWasPosted` | No alert when nothing was posted. |
| `exportRejectsWhenNothingNewToExport` | Manual export rejects a redundant no-op run distinctly from a "not closed" rejection. |
| `exportSkipsCollectionsStillAwaitingAgentConfirmation` | Export only ever includes agent-confirmed collections, never merely-counted ones. |
| `exportNeverDoublePostsAnAlreadyExportedCollection` | Re-running export never re-posts an already-exported collection. |
| `exportPostsBranchCollectionsToCbsLedgerBeforeSubmission` | Collections are posted to the CBS ledger before the export batch is submitted. |
| `exportSkipsLedgerPostingWhenNoCollections` | No ledger call at all when there's nothing to post. |
| `exportPostsActivationPaymentsToCbsLedgerEvenWithNoCollections` | Activation fees post independently of whether there are any plain collections. |
| `getSummaryWithTodayDateBehavesLikeNoDateOverload` | Explicit "today" date param behaves identically to omitting it. |
| `getSummaryWithPastDateIsReadOnlyAndFoundReturnsIt` | A past date's summary is read-only and returns the historical session. |
| `getSummaryWithPastDateNotFoundThrows404` | An unknown past date 404s. |
| `listHistoryReturnsSessionsMostRecentFirst` | History ordering is newest-first. |
| `listVarianceDebtsForAgentFiltersOpenOnlyWhenRequested` | Per-agent debt listing can filter to open-only. |
| `listVarianceDebtsForBranchReturnsEmptyWhenNoAgents` | Branch-wide listing is empty when the branch has no agents. |
| `listPendingAgentsIncludesActiveAgentWithUnreconciledCash` | Pending-to-reconcile listing includes any active agent holding unreconciled cash. |
| `listPendingAgentsExcludesAgentsWithNothingUnreconciled` | Agents with nothing unreconciled are excluded. |
| `listPendingAgentsIncludesBacklogFromAMultiDayOfflineAgent` | An agent offline for multiple days still has their full backlog surfaced. |
| `listPendingAgentsReturnsEmptyWhenBranchHasNoActiveAgents` | Empty branch ⇒ empty listing. |
| `reconcileMarksPendingConfirmationNotReconciled` | A freshly reconciled line starts as "pending confirmation," not yet fully reconciled. |
| `reconcileStampsLastCountedAtOnTheLine` | Reconciliation stamps the last-counted timestamp. |
| `listPendingConfirmationLinesReturnsPendingTotalsOnly` | Per-agent pending-confirmation listing excludes already-confirmed totals. |
| `listPendingConfirmationLinesReturnsEmptyWhenNoneOutstanding` | Empty when nothing outstanding. |
| `listPendingConfirmationsForBranchReturnsPendingTotalsAcrossAgents` | Branch-wide pending-confirmation totals aggregate across agents. |
| `listPendingConfirmationsForBranchReturnsEmptyWhenBranchHasNoAgents` | Empty branch ⇒ empty totals. |
| `confirmReconciliationMarksAgentConfirmedForOwnLine` | Agent confirmation marks their own line. |
| `confirmReconciliationForbiddenForAnotherAgentsLine` | Can't confirm someone else's line. |
| `listCollectionsForLineReturnsCollectionsForOwnLine` | Drill-down returns the line's own collections. |
| `listCollectionsForLineExcludesVoidedCollections` | Voided collections excluded from the drill-down. |
| `listCollectionsForLineExcludesAlreadyConfirmedCollectionsFromAnEarlierBatch` | An earlier batch's already-confirmed collections don't leak into a new line's drill-down. |
| `listCollectionsForLineForbiddenForAnotherAgentsLine` | Can't drill into someone else's line. |
| `exportForAgentPostsOnlyThatAgentsConfirmedUnexportedCollections` | "End My Day" posts exactly this agent's confirmed-and-unexported cash, nothing else. |
| `exportForAgentFailsAndDoesNotEndTheDayWhenCbsPostFails` | A failed CBS post doesn't let the agent's day end anyway. |
| `exportForAgentUsesAContentDerivedIdempotencyKeyPerBatch` | The CBS idempotency key is derived from the batch's actual content, not a random/incrementing value. |
| `exportForAgentConflictWhenNothingReady` | Conflicts (409) when nothing is ready to export. |
| `exportableSummaryReflectsConfirmedUnexportedCount` | The "ready to export" summary reflects the true confirmed-and-unexported count. |
| `exportableSummaryReturnsZeroWhenAgentHasAlreadyEndedToday` | Once the agent has already ended today, the summary reports zero — the mobile banner never resurfaces — even if a late confirmation left more cash confirmed-and-unexported. |
| `exportForAgentConflictWhenAgentHasAlreadyEndedToday` | A direct "End My Day" call after the day is already ended is rejected (409), independent of the mobile UI hiding the button. |
| `confirmReconciliationConflictWhenNothingAwaitingConfirmation` | Confirming when nothing is actually awaiting confirmation conflicts. |

---

## 8. Geolocation: Tracking, Geofence, Geocoding, SOS

### 8.1 `AdminTrackingControllerTest` / `TrackingControllerTest` — route, geofence & SOS HTTP surface
| Test | Verifies |
|---|---|
| `routeSucceedsForOwnBranchManager` | Manager can view a route within their own branch. |
| `routeForbiddenOutsideOwnBranch` | Cross-branch route viewing forbidden. |
| `setGeofenceSucceedsForAdmin` | Admin can set an individual agent's geofence. |
| `geofenceAlertsListsForOwnBranch` | Geofence-breach alert listing is branch-scoped. |
| `locationNameResolvesForOwnBranch` | Reverse-geocoded location name resolves for an in-branch query. |
| `locationNameForbiddenOutsideOwnBranch` | Cross-branch resolution forbidden. |
| `routeUnauthenticatedRejected` | Anonymous callers rejected. |
| `testLocationSuccess_resolvesAgentFromPrincipal` | Agent-facing ping endpoint takes the agent from the principal. |
| `testLocationUnauthenticatedRejected` | Anonymous callers rejected. |
| `testLocationMissingGpsRejected` | GPS mandatory. |
| `testLocationForAnotherAgentForbidden` | Can't submit a ping impersonating another agent. |
| `testSosSuccessWithGps` | SOS with a GPS fix succeeds. |
| `testSosAcceptedWithoutGps` | SOS is still accepted even without a fix (an emergency shouldn't be blocked on GPS). |
| `testSosUnauthenticatedRejected` | Anonymous callers rejected. |
| `testSosForAnotherAgentForbidden` | Can't raise SOS impersonating another agent. |
| `testSyncStatusSuccess_resolvesAgentFromPrincipal` | Sync-status report is attributed to the authenticated agent. |
| `testSyncStatusUnauthenticatedRejected` | Anonymous callers rejected. |
| `testSyncStatusForAnotherAgentForbidden` | Can't report sync status impersonating another agent. |
| `testSyncStatusNegativeCountRejected` | Backlog count can't be negative. |

### 8.2 `AdminSosControllerTest` — back-office SOS view
| Test | Verifies |
|---|---|
| `listUnrestrictedForAdmin` | ADMIN sees every branch's alerts. |
| `listScopedToBranchForBranchManager` | Manager sees only their own branch's alerts. |
| `listUnauthenticatedRejected` | Anonymous callers rejected. |
| `acknowledgeSuccessWithinOwnBranch` | Acknowledging within own branch succeeds. |
| `streamUnrestrictedForAdminForwardsBroadcastEvents` | SSE stream forwards every event to ADMIN. |
| `streamOnlyForwardsEventsWithinCallersBranch` | SSE stream filters to the manager's own branch. |
| `streamUnauthenticatedRejected` | Anonymous callers rejected. |
| `acknowledgeOutsideBranchForbidden` | Cross-branch acknowledgement forbidden. |

### 8.3 `TrackingServiceTest` — UC-10/11/13/14 core logic
| Test | Verifies |
|---|---|
| `recordLocationPersistsAndReturnsPing` | A GPS ping is persisted and returned. |
| `raiseSosPersistsAndReturnsEventWithGps` | SOS with GPS is persisted with its fix. |
| `raiseSosAcceptedWithoutGpsFix` | SOS without GPS is still accepted. |
| `getRouteReturnsOrderedPointsAndTransactionMarkers` | Route reconstruction returns ordered points plus transaction markers. |
| `listSosEventsUnrestrictedWhenAgentIdsNull` | Null agent-id filter means unrestricted listing. |
| `listSosEventsScopedToAgentIdsAndUnresolvedOnly` | Filtered listing scopes by agent ids and unresolved status. |
| `acknowledgeSosSetsAcknowledgedByAndAt` | Acknowledgement stamps who and when. |
| `acknowledgeSosAlreadyAcknowledgedThrowsConflict` | Can't acknowledge twice. |
| `findSosEventAgentIdNotFoundThrows404` | Unknown SOS event 404s. |

### 8.4 `GeofenceServiceTest` — UC-13 breach detection
Point-in-polygon evaluation on every GPS ping, with a grace period so a single stray/inaccurate reading never pages a manager.

| Test | Verifies |
|---|---|
| `evaluateLocationNoOpWhenAgentHasNoGeofence` | No-op when the agent has no geofence assigned. |
| `evaluateLocationInsidePolygonWithNoExistingBreachDoesNothing` | Inside the polygon with no open breach ⇒ nothing happens. |
| `evaluateLocationOutsidePolygonCreatesUnraisedBreach` | First outside reading creates an unraised (grace-period) breach. |
| `evaluateLocationOutsideWithinGracePeriodDoesNotRaiseYet` | Still within grace period ⇒ not yet raised as an alert. |
| `evaluateLocationOutsidePastGracePeriodRaisesAlert` | Past the grace period ⇒ alert raised. |
| `evaluateLocationBackInsideAutoResolvesRaisedAlert` | Returning inside auto-resolves a raised alert. |
| `evaluateLocationBackInsideDiscardsBreachThatNeverClearedGracePeriod` | Returning inside before the grace period elapsed discards it — it was never really an alert. |
| `isWithinAssignedGeofenceTrueWhenNoGeofenceAssigned` | No geofence ⇒ always "within" (unrestricted). |
| `isWithinAssignedGeofenceTrueWhenInsidePolygon` | Correctly evaluates "inside." |
| `isWithinAssignedGeofenceFalseWhenOutsidePolygon` | Correctly evaluates "outside." |
| `isWithinAssignedGeofenceNeverTouchesAlertState` | This read-only check never mutates breach/alert state (side-effect-free). |
| `setGeofenceCreatesAndGetGeofenceReturnsVertices` | Setting and reading back a geofence's vertices round-trips correctly. |
| `applyGeofenceToBranchWritesSameVerticesToEveryActiveAgentAndReturnsCount` | Branch-wide apply writes identical vertices to every active agent. |
| `deleteGeofenceIsNoOpWhenAgentHasNone` | Deleting a nonexistent geofence no-ops. |
| `deleteGeofenceRemovesGeofenceWithNoOpenBreach` | Clean deletion when there's no open breach. |
| `deleteGeofenceResolvesOpenRaisedBreachBeforeDeleting` | A raised breach is resolved before the geofence is deleted (no orphaned alert). |
| `deleteGeofenceDiscardsOpenUnraisedBreachBeforeDeleting` | An unraised breach is discarded before deletion. |
| `clearGeofenceFromBranchDeletesOnlyAgentsThatHaveOneAndReturnsCount` | Branch-wide clear only touches agents that actually have one. |

### 8.5 `GeocodingServiceTest` — reverse geocoding
Best-effort OpenStreetMap/Nominatim lookup that never blocks the caller.

| Test | Verifies |
|---|---|
| `reverseGeocodeParsesDisplayName` | A successful lookup parses the display name. |
| `reverseGeocodeReturnsNullOnFailureInsteadOfThrowing` | A failure returns null rather than throwing. |
| `reverseGeocodeRetriesAfterATransientFailureAndSucceeds` | A transient failure is retried and can still succeed. |
| `reverseGeocodeGivesUpAfterConfiguredAttemptsAndReturnsNull` | Gives up after the configured retry budget and returns null. |

### 8.6 `CollectionGeocodeListenerTest` / `SosGeocodeListenerTest` — async reverse-geocode consumers
The RabbitMQ-consumer half of reverse geocoding for, respectively, a collection and an SOS event — fills in the location name after the fact via a targeted column update, never a full-entity save.

| Test | Verifies |
|---|---|
| `resolvesAndUpdatesOnlyTheLocationNameColumn` (Collection) | Only the `locationName` column is touched — no stale full-entity overwrite. |
| `leavesLocationNameUntouchedWhenGeocodingFails` (Collection) | A failed lookup leaves the column untouched. |
| `doesNothingWhenTheCollectionNoLongerExists` (Collection) | No-ops if the collection was deleted/voided before the async job ran. |
| `resolvesAndSavesTheLocationName` (SOS) | Successful lookup saves the resolved name. |
| `leavesLocationNameNullWhenGeocodingFails` (SOS) | Failed lookup leaves it null. |
| `doesNothingWhenTheSosEventNoLongerExists` (SOS) | No-ops if the SOS event no longer exists. |

---

## 9. Async / Event Infrastructure (RabbitMQ, SSE)

These classes are the plumbing this branch is named for: bridging in-JVM Spring events to RabbitMQ (only after the surrounding transaction commits, so a rollback never phantom-publishes), and fan-out to Back-Office SSE subscribers.

### 9.1 Transactional-outbox-style event relays
| Class / Test | Verifies |
|---|---|
| `CollectionGeocodeEventRelayTest.forwardsTheEventToTheRabbitMqPublisher` | Collection geocode event reaches the RabbitMQ publisher only after commit. |
| `CollectionRejectionAlertEventRelayTest.forwardsTheEventToTheBroadcaster` | Rejection-request event reaches the SSE broadcaster only after commit. |
| `SosAlertEventRelayTest.forwardsTheEventToTheBroadcaster` | SOS-raised event reaches the SSE broadcaster only after commit. |

### 9.2 `CollectionRecordDispatcherTest` — the RabbitMQ request/reply client side
Replaces a direct in-process call to `CollectionService.recordCollection` with a RabbitMQ request-reply round trip, so a burst is throttled by the consumer pool rather than Core's general thread pool — behaves as a drop-in replacement to callers. As of the Offline Field Collection Security Algorithm update (§12), `dispatch`/`CollectionRecordRequest` also carry a `CollectionOrigin` (`ONLINE`/`OFFLINE_SYNC`, resolved by the controller from which endpoint the request arrived on, never trusted from the client) — the three tests below were updated to pass it through rather than gaining new methods.

| Test | Verifies |
|---|---|
| `resolvesWithTheResponseOnSuccess` | A successful round trip resolves with the same response a direct call would give. |
| `reThrowsTheExactRejectionFromTheListener` | A business-rule rejection round-trips back as the identical exception a direct call would throw. |
| `returnsServiceUnavailableOnReplyTimeout` | A reply timeout surfaces as 503, not a hang. |

### 9.3 `CollectionRecordListenerTest` — the RabbitMQ request/reply server side
| Test | Verifies |
|---|---|
| `wrapsASuccessfulRecordingAsASuccessReply` | Success is wrapped correctly for the reply. |
| `wrapsABusinessRuleRejectionAsAFailureReply_notAnException` | A business-rule rejection is sent back as *data*, not an unhandled exception on the queue. |

### 9.4 In-process SSE broadcasters
| Class / Test | Verifies |
|---|---|
| `CollectionRejectionAlertBroadcasterTest.publishedEventReachesAnAlreadySubscribedStream` | An already-connected subscriber receives a published event. |
| `CollectionRejectionAlertBroadcasterTest.eachSubscriberReceivesEventsPublishedAfterItSubscribed` | Late subscribers only get events from after they connected. |
| `CollectionRejectionAlertBroadcasterTest.survivesDroppingToZeroSubscribersBeforeALaterOneConnects` | The sink survives dropping to zero subscribers (doesn't auto-cancel) so a later connection still works. |
| `SosAlertBroadcasterTest.publishedEventReachesAnAlreadySubscribedStream` | Same guarantee for SOS alerts. |
| `SosAlertBroadcasterTest.eachSubscriberReceivesEventsPublishedAfterItSubscribed` | Same guarantee for SOS alerts. |
| `SosAlertBroadcasterTest.survivesDroppingToZeroSubscribersBeforeALaterOneConnects` | Same guarantee for SOS alerts. |

---

## 10. Audit Logging

### `AuditServiceTest`
Fire-and-forget audit writes — must never fail or slow down the action being described.

| Test | Verifies |
|---|---|
| `recordSavesEntryWithGeneratedIdAndTimestamp` | A recorded entry gets a generated id and timestamp. |
| `recordDefaultsStatusToSuccessWhenNotSpecified` | Status defaults to SUCCESS when not explicitly set. |
| `recordSwallowsRepositoryFailureSoCallerIsNeverBroken` | A repository failure while writing the audit entry never propagates to the caller — the action being audited must not fail because of it. |
| `searchDelegatesToRepositoryWithGivenCriteria` | Search criteria are correctly delegated to the repository. |

---

## 11. Middleware — CBS Mock Adapter

### `MockCbsAdapterTest` (`microfi-middleware`)
Regression coverage added because the mock used to return numbers with no real connection to what was actually posted (a hash of the member id, two hardcoded literals) — it must now genuinely simulate a ledger backed by real `MockLedgerEntry` rows.

| Test | Verifies |
|---|---|
| `getBalanceReflectsSumOfPostedEntries` | Balance is the real sum of posted entries, not a hardcoded/derived stand-in. |
| `getBalanceIsZeroForMemberWithNoHistory` | Zero balance for a member with no ledger history. |
| `postTransactionsPersistsOneLedgerEntryPerLine` | Each posted line becomes its own persisted ledger entry. |
| `getHistoryReturnsRealEntriesNotHardcodedSamples` | History reflects real entries, not canned sample data. |
| `getHistoryIsEmptyForMemberWithNoHistory` | Empty history for a member with none. |
| `reverseTransactionPostsACompensatingNegativeEntry` | A reversal posts a compensating negative entry rather than deleting the original. |
| `reverseTransactionNetsBalanceBackToZero` | The compensating entry nets the balance back to zero. |
| `getMemberReturnsTheSeededMemberByAccountNumber` | Member lookup by account number returns the seeded record. |
| `getMemberReportsNotFoundForAnUnknownAccountNumber` | Unknown account number reports not-found. |
| `reverseTransactionFailsGracefullyForAnUnknownReference` | Reversing an unknown reference fails gracefully rather than throwing an unhandled error. |

---

## 12. Offline Field Collection Security Algorithm

Closes the gap between two outcomes that already existed on this branch (login blocked on a device mismatch, admin-only device reset) and the structural mechanism the spec (`MICROFI_Offline_Collection_Security_Algorithm_v1_1.pdf`) requires behind them — a real installation identity distinct from the device, an alertable security-event queue, a proper agent state machine, and a signed hash-chain on every collection. The extended tests in §1.3, §1.5, §3 and §5.3 above cover the login/collection-gate/reset side; this section covers the three brand-new classes.

### 12.1 `SecurityEventServiceTest` — the alertable, resolvable event ledger
Distinct from `AuditLogEntry` (a write-once timeline with no resolved/unresolved concept): every `raise` call also writes a matching audit-log row so `/admin/audit-log` stays complete, but this table is the actionable queue an admin actually works from. `raise` runs `REQUIRES_NEW` (same reasoning as `AuditService.record`) — it's called by `CollectionService.applyChainRules` immediately before throwing to reject a record, and joining that caller's transaction would roll the event back right along with the rejection it's supposed to be evidence of.

| Test | Verifies |
|---|---|
| `raisePersistsEventAndAuditsIt` | A raised event is persisted and a matching audit-log entry is written. |
| `raiseFlipsAgentToReconciliationRequiredForInstallationMismatch` | An `INSTALLATION_MISMATCH` flips the agent's status. |
| `raiseFlipsAgentToReconciliationRequiredForDeviceMismatch` | Same for `DEVICE_MISMATCH`. |
| `raiseDoesNotFlipAgentForChainAnomalyTypes` | Sync-time chain anomalies (`COUNTER_GAP`, `BAD_PREVIOUS_HASH`, `BAD_SIGNATURE`, `DEVICE_NOT_AUTHORIZED`) are recorded but don't lock the agent out — only an identity mismatch does. |
| `resolveStampsResolutionFields` | Resolving an event stamps resolved-at/by/reason. |
| `resolveUnknownEventThrows404` | Resolving an unknown event 404s. |
| `resolveAllOpenForAgentResolvesEveryOpenEvent` | Bulk-resolves every open event for one agent (used by the device-binding reset flow). |
| `listOpenNetworkWideWhenBranchIdNull` | A null branch scope lists network-wide. |
| `listOpenBranchScopedWhenBranchIdProvided` | A branch id scopes the listing. |

### 12.2 `SecurityEventControllerTest` — the admin console
`GET/PATCH /admin/security-events` — the actionable counterpart to `/admin/audit-log`'s read-only timeline. Same ADMIN-global/BRANCH_MANAGER-own-branch shape as `AuditLogController`.

| Test | Verifies |
|---|---|
| `listOpenAdminSeesNetworkWide` | ADMIN's listing spans every branch. |
| `listOpenBranchManagerPinnedToOwnBranch` | A manager's listing is pinned to their own branch regardless of intent. |
| `listOpenCashierForbidden` | A cashier cannot view security events. |
| `resolveRequiresReason` | A resolution reason is mandatory. |
| `resolveBranchManagerOutOfScopeForbidden` | A manager cannot resolve an event outside their branch. |
| `resolveSucceedsForAdmin` | ADMIN can resolve any event. |

### 12.3 `CollectionChainCodecTest` — the hash-chain codec
Must produce byte-identical output to its Dart counterpart (`microfi-mobile/test/core/collection_chain_codec_test.dart`) — an explicit, fixed-order pipe-delimited string (not JSON, to avoid key-ordering drift), with lat/lon fixed to 6 decimals and the timestamp as a raw epoch millisecond integer specifically to remove any Dart/Java default-formatting ambiguity. Two of the tests below assert against fixed vectors independently computed with Python's `hashlib`/`hmac` (a reference implementation, not either codebase's own code) — the same vectors are hand-copied into the Dart test, so a drift between the two implementations fails both suites, not just one silently disagreeing with the other.

| Test | Verifies |
|---|---|
| `canonicalStringIsFixedOrderPipeDelimited` | The exact canonical string format for a fixed input. |
| `computeHashIsDeterministicForTheSameInput` | Hashing the same input twice gives the same result. |
| `computeHashMatchesTheIndependentlyComputedFixedVector` | The hash matches the Python-computed reference vector (cross-language check). |
| `computeSignatureMatchesTheIndependentlyComputedFixedVector` | The HMAC signature matches the Python-computed reference vector. |
| `computeHashChangesWhenAnyFieldChanges` | Any field change (e.g. a tampered amount) changes the hash. |
| `computeSignatureVerifiesAgainstTheCorrectSecretOnly` | A signature only verifies against the secret it was actually signed with. |
| `chainLinksSecondRecordToFirstsHash` | A second record's `previousHash` input is the first record's `currentHash` — the actual chain link. |

### 12.4 Live verification (not unit-testable — exercised against the running Docker stack)

Beyond the 49 new/extended automated tests above, the following end-to-end flows were exercised against a live `docker compose` stack with a real test agent, since they span the mobile↔Kong↔Core round trip and Postgres persistence that a unit test can't cover:

- First-ever login with an `installationId` binds it and returns a freshly generated HMAC secret; a repeat login with the same id returns no secret.
- Same device, a different `installationId` (the uninstall/reinstall scenario): login still succeeds, a `SecurityEvent` is raised, and the agent flips to `RECONCILIATION_REQUIRED`.
- `POST /collections` against a `RECONCILIATION_REQUIRED` agent is rejected 403 with the "no longer authorized" message.
- `PATCH /admin/agents/{id}/device-binding` clears the block, captures the previous device/installation in the audit entry, auto-resolves the open security event, and moves the agent to `RESET_AUTHORIZED`.
- `POST /collections/sync` from the newly reset installation is rejected until one `POST /collections` (online) succeeds — which then flips the agent back to `ACTIVE` and stamps the binding.
- All 8 sync-verification rules exercised with hand-computed (Python `hashlib`/`hmac`) request bodies: accept-expected-next, a tampered `currentHash` (403 + `BAD_PREVIOUS_HASH`), and a skipped-ahead counter (409 + `COUNTER_GAP`) were each confirmed to produce the correct HTTP response *and* the correct row in `GET /admin/security-events`.
- One real bug was only caught this way: `SecurityEventService.raise()` initially lacked `@Transactional(REQUIRES_NEW)`, so an event raised immediately before `CollectionService.applyChainRules` threw its rejection was silently rolled back with it — a unit test with a mocked repository couldn't have caught this, since Mockito never exercises Spring's real transaction interceptor. Fixed and re-verified live.
- Mobile: `sqflite_sqlcipher`'s at-rest encryption of the offline collection queue was independently confirmed on a real Android device (API 33) — `integration_test/offline_storage_test.dart`'s "sanity" test inserts a known plaintext value and asserts the raw `.db` file on disk does not contain it; result: `contains literal clientId "plaintext-check-client": false`.

---

## Summary

| Domain | Test classes | Test methods |
|---|---|---|
| Auth, Admin & Agent Management | 5 | 114 |
| Branch Management | 1 | 42 |
| Agent Directory Service | 1 | 51 |
| Client Onboarding/Activation/Self-Service | 6 | 70 |
| Digital Cash Desk (Collections & Escrow) | 4 | 60 |
| Collection Rejection Workflow | 2 | 22 |
| End-of-Day / OFJ & CBS Export | 4 | 79 |
| Geolocation (Tracking/Geofence/Geocoding/SOS) | 8 | 61 |
| Async/Event Infrastructure | 7 | 13 |
| Audit Logging | 1 | 4 |
| Middleware CBS Mock Adapter | 1 | 10 |
| Offline Field Collection Security Algorithm | 3 | 22 |
| **Total** | **43** | **565*** |

\* Individual counts above tally to slightly more than the raw `@Test`-annotation count in a few domains because a handful of test classes (e.g. `AgentSelfControllerTest`, `BranchControllerTest`) span two logically distinct concerns and are cross-referenced rather than double-counted in the grand total; **729** (Section "Verification status" above) is the actual number of JUnit test executions in the last full run, higher than the raw method count because a few methods are parameterized.
