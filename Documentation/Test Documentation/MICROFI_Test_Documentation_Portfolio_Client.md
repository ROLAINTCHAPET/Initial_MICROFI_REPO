# MICROFI — Test Documentation Addendum: "Portefeuille Client" (Agent Client Portfolio)

**Companion to:** MICROFI_Test_Documentation-2.pdf (v1.3) — same IEEE 829–style convention (`TC-MODULE-NN`, priority, Objective/Preconditions/Steps/Expected/Refs), new module prefix `TC-PORT`.

**Feature under test:** an agent may only record a collection for a client already in their "portfolio." A client enters an agent's portfolio automatically when that agent sponsors their UC-19 activation, or is set/cleared manually by an ADMIN/BRANCH_MANAGER. The restriction is a per-branch opt-in (`Branch#requireClientPortfolio`, off by default) — an unassigned client is always collectible by any agent in the branch, regardless of this setting.

**Status:** All cases below are automated (JUnit 5 / Mockito unit and `@WebFluxTest` slice tests) and passing as of this addendum. No manual/device execution required — this is server-side authorization logic, not a UI or GPS/offline path.

---

## Coverage summary

| Category | Count |
|---|---|
| Total cases in this addendum | 16 |
| P0 | 11 |
| P1 | 5 |
| Automated | 16 of 16 (100%) |

## Seed data / shared preconditions

Branch `BR-1` with `requireClientPortfolio` togglable; agents `A1` (has a client in their portfolio), `A2` (another agent, no relation to A1's clients); client `C1` (owned by A1), client `C2` (unassigned — never sponsored, e.g. bulk CBS import). All cases are unit-level: repositories and collaborating services are mocked, not a live database.

---

## TC-PORT — Client Portfolio

### TC-PORT-01 — P0 — Portfolio requirement defaults off for a new branch
**Objective:** A newly created branch does not restrict collection by portfolio unless explicitly opted in.
**Preconditions:** None (branch creation).
**Steps:** Create a branch via `POST /api/v1/admin/branches` without specifying `requireClientPortfolio`.
**Expected:** Response's `requireClientPortfolio` equals `Branch.DEFAULT_REQUIRE_CLIENT_PORTFOLIO` (`false`).
**Refs:** UC-19 (ext.), `Branch#requireClientPortfolio`.
**Automated:** `BranchControllerTest.testCreateBranchDefaultsRequireClientPortfolioFalse`

### TC-PORT-02 — P0 — Admin/manager enables the portfolio requirement for a branch
**Objective:** An authorized caller can opt a branch into the restriction.
**Preconditions:** Branch `BR-1` exists.
**Steps:** `PATCH /api/v1/admin/branches/{id}/require-client-portfolio` with `{"requireClientPortfolio": true}`, authenticated as ADMIN.
**Expected:** 200 OK; response reflects `requireClientPortfolio: true`.
**Refs:** UC-19 (ext.).
**Automated:** `BranchControllerTest.testSetRequireClientPortfolioSuccess`

### TC-PORT-03 — P0 — Branch cashier cannot toggle the portfolio requirement
**Objective:** Only ADMIN/BRANCH_MANAGER may change this setting.
**Preconditions:** Caller authenticated as BRANCH_CASHIER.
**Steps:** `PATCH .../require-client-portfolio` as BRANCH_CASHIER.
**Expected:** 403 Forbidden.
**Refs:** UC-19 (ext.), RBAC.
**Automated:** `BranchControllerTest.testSetRequireClientPortfolioCashierForbidden`

### TC-PORT-04 — P0 — Unassigned client stays collectible by any agent
**Objective:** The restriction only narrows an *existing* assignment; it never blocks an untouched client — protects bulk-imported/CBS clients that were never sponsored by an agent.
**Preconditions:** Client `C2.assignedAgentId = null`; branch has `requireClientPortfolio = true`.
**Steps:** Agent `A1` (or any agent) attempts to record a collection for `C2`.
**Expected:** No rejection from the portfolio gate; collection proceeds.
**Refs:** UC-19 (ext.) — explicit product decision ("open to any agent in the branch").
**Automated:** `ClientDirectoryServiceTest.requireInPortfolioAllowsWhenClientIsUnassigned`, `CollectionServiceTest.allowsUnassignedClientWhenBranchRequiresPortfolio`

### TC-PORT-05 — P0 — Assigned agent can collect from their own client
**Objective:** The owning agent is never blocked by their own assignment.
**Preconditions:** Client `C1.assignedAgentId = A1.id`.
**Steps:** Agent `A1` records a collection for `C1` with `requireClientPortfolio = true`.
**Expected:** No rejection; collection proceeds.
**Refs:** UC-19 (ext.).
**Automated:** `ClientDirectoryServiceTest.requireInPortfolioAllowsWhenClientIsAssignedToThisAgent`

### TC-PORT-06 — P0 — Agent rejected collecting from another agent's client
**Objective:** Core authorization rule — an agent cannot collect for a client already owned by a different agent.
**Preconditions:** Client `C1.assignedAgentId = A1.id`; branch requires portfolio.
**Steps:** Agent `A2` attempts to record a collection for `C1`.
**Expected:** 403 Forbidden; no `Collection` row persisted.
**Refs:** UC-19 (ext.).
**Automated:** `ClientDirectoryServiceTest.requireInPortfolioRejectsWhenClientBelongsToAnotherAgent`, `CollectionServiceTest.rejectsCollectionWhenClientBelongsToAnotherAgentsPortfolio`

### TC-PORT-07 — P0 — Rejected portfolio attempt is captured in the security audit log
**Objective:** A wrong-agent collection attempt is visible to a branch manager reviewing `/audit`, same reasoning as auditing failed logins or geofence violations.
**Preconditions:** Same as TC-PORT-06.
**Steps:** Trigger the TC-PORT-06 rejection.
**Expected:** `AuditService.record` called once with `eventType = COLLECTION_REJECTED_PORTFOLIO`, `actorType = AGENT`, `status = FAILED`, correct `agentId`.
**Refs:** UC-19 (ext.), audit trail parity with `COLLECTION_REJECTED_GEOFENCE`.
**Automated:** `CollectionServiceTest.rejectsCollectionWhenClientBelongsToAnotherAgentsPortfolio`

### TC-PORT-08 — P1 — A non-authorization failure is not miscategorized as a portfolio security event
**Objective:** Only a `FORBIDDEN` (wrong-agent) rejection is a portfolio audit event; an unrelated failure (e.g. unknown client) must not be recorded as one.
**Preconditions:** `requireInPortfolio` throws `404 NOT_FOUND` instead of `403`.
**Steps:** Trigger the collection attempt under this condition.
**Expected:** Exception propagates with `404`; `AuditService.record` is never called.
**Refs:** UC-19 (ext.).
**Automated:** `CollectionServiceTest.doesNotAuditWhenPortfolioCheckFailsForAnUnrelatedReason`

### TC-PORT-09 — P0 — Portfolio check is skipped when the branch has not opted in
**Objective:** Default (off) behavior must not invoke the gate at all, regardless of any client's assignment.
**Preconditions:** Branch `requireClientPortfolio = false` (default, no stubbing needed).
**Steps:** Record a collection for any client.
**Expected:** `ClientDirectoryService.requireInPortfolio` is never invoked; collection succeeds.
**Refs:** UC-19 (ext.).
**Automated:** `CollectionServiceTest.skipsPortfolioCheckWhenBranchDoesNotRequireIt`

### TC-PORT-10 — P0 — Completing a sponsored activation assigns the sponsoring agent as portfolio owner
**Objective:** The primary, automatic way a client "enters" an agent's portfolio (per the feature's original request: "how does a client enter into the pocket of an agent — if the agent has activated the client's account").
**Preconditions:** An `ActivationRequest` sponsored by agent `A1` for client `C3`, client confirms payment (UC-19 completion).
**Steps:** `ClientActivationService.confirmPayment` runs to completion for `C3`.
**Expected:** `C3.assignedAgentId == A1.id` after completion.
**Refs:** UC-19.
**Automated:** `ClientActivationServiceTest.finalizingAssignsTheSponsoringAgentAsThePortfolioOwner`

### TC-PORT-11 — P1 — Admin/manager manually assigns a client to an agent
**Objective:** A manager can hand-set the portfolio owner outside the activation flow (e.g. correcting a mistake, or reassigning after an agent leaves).
**Preconditions:** Client and target agent both belong to the same branch.
**Steps:** `PATCH /api/v1/admin/clients/{id}/assigned-agent` with `{"agentId": "<A1>"}`, authenticated as ADMIN.
**Expected:** 200 OK; `assignedAgentId` in the response equals `A1`.
**Refs:** UC-19 (ext.).
**Automated:** `ClientDirectoryServiceTest.setAssignedAgentSetsTheOwningAgent`, `ClientControllerTest.testSetAssignedAgentSuccess`

### TC-PORT-12 — P1 — Admin/manager clears a client's assignment
**Objective:** A `null` `agentId` is a deliberate "reopen to any agent" signal, not an invalid input.
**Preconditions:** Client currently assigned to some agent.
**Steps:** `PATCH .../assigned-agent` with `{"agentId": null}`.
**Expected:** 200 OK; `assignedAgentId` absent/null in the response.
**Refs:** UC-19 (ext.).
**Automated:** `ClientDirectoryServiceTest.setAssignedAgentClearsAssignmentWhenGivenNull`, `ClientControllerTest.testSetAssignedAgentClearsAssignmentWhenNull`

### TC-PORT-13 — P0 — Reassignment rejected across branches
**Objective:** A client can never be assigned to an agent outside their own branch — otherwise the owning branch's manager could not scope or see that agent to enforce the portfolio rule.
**Preconditions:** Target agent's branch ≠ client's branch.
**Steps:** `PATCH .../assigned-agent` with that agent's id.
**Expected:** 409 Conflict; no assignment persisted.
**Refs:** UC-19 (ext.).
**Automated:** `ClientControllerTest.testSetAssignedAgentRejectsAgentFromAnotherBranch`

### TC-PORT-14 — P0 — Branch cashier cannot reassign a client's portfolio agent
**Objective:** Only ADMIN/BRANCH_MANAGER may reassign — same RBAC boundary as TC-PORT-03.
**Preconditions:** Caller authenticated as BRANCH_CASHIER.
**Steps:** `PATCH .../assigned-agent` as BRANCH_CASHIER.
**Expected:** 403 Forbidden.
**Refs:** UC-19 (ext.), RBAC.
**Automated:** `ClientControllerTest.testSetAssignedAgentCashierForbidden`

### TC-PORT-15 — P1 — Effective portfolio setting resolves correctly for an agent
**Objective:** `AgentDirectoryService.effectiveRequireClientPortfolioForAgent` correctly resolves the agent's branch setting, with safe fallbacks.
**Preconditions:** Four sub-cases — branch configured `true`; branch exists but unconfigured; branch missing entirely; agent itself unknown.
**Steps:** Call `effectiveRequireClientPortfolioForAgent(agentId)` under each precondition.
**Expected:** Returns `true` only in the configured case; `false` (== `Branch.DEFAULT_REQUIRE_CLIENT_PORTFOLIO`) for unconfigured/missing branch; throws `404` for an unknown agent.
**Refs:** UC-19 (ext.).
**Automated:** `AgentDirectoryServiceTest.effectiveRequireClientPortfolioForAgentReturnsBranchConfiguredValue` / `...DefaultsToFalseWhenBranchUnconfigured` / `...DefaultsToFalseWhenBranchMissing` / `...UnknownAgentThrows404`

### TC-PORT-16 — P0 — Unknown client throws 404 on portfolio operations
**Objective:** Both the read-side gate and the write-side setter must reject a nonexistent client id cleanly.
**Preconditions:** `clientId` has no matching `ClientProfile`.
**Steps:** Call `requireInPortfolio(agentId, clientId)` and, separately, `setAssignedAgent(clientId, agentId)`.
**Expected:** Both throw `ResponseStatusException` with `404`.
**Refs:** UC-19 (ext.).
**Automated:** `ClientDirectoryServiceTest.requireInPortfolioUnknownClientThrows404`, `ClientDirectoryServiceTest.setAssignedAgentUnknownClientThrows404`

---

## Traceability matrix

| Requirement / rule | Test cases |
|---|---|
| Default is off (backward-compatible) | TC-PORT-01, TC-PORT-09 |
| Toggle is ADMIN/BRANCH_MANAGER only | TC-PORT-02, TC-PORT-03 |
| Unassigned client always open | TC-PORT-04 |
| Own-client collection allowed | TC-PORT-05 |
| Wrong-agent collection blocked + audited | TC-PORT-06, TC-PORT-07, TC-PORT-08 |
| Activation is the automatic entry point | TC-PORT-10 |
| Manual reassignment (set/clear) | TC-PORT-11, TC-PORT-12 |
| Reassignment scoped to one branch | TC-PORT-13 |
| Reassignment is ADMIN/BRANCH_MANAGER only | TC-PORT-14 |
| Settings resolution correctness | TC-PORT-15 |
| Not-found handling | TC-PORT-16 |

## Automation notes

All 16 cases run as part of `mvn -o test` in `microfi-core` (no separate profile/tag needed). Full-suite run at the time of writing: 656 tests, 655 passing — the single unrelated failure (`AgentDirectoryServiceTest.requireWithinScheduleWindowAllowsCollectionInsideWindow`) is a pre-existing real-clock flake in an unrelated schedule-window test (fails when run within ~2h of local midnight because its open/close window wraps past 00:00) and predates this feature.
