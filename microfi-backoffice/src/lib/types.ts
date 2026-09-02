// Mirrors com.microfi.shared.dto response classes exactly — field names/types read directly
// from the actual DTO source, not guessed. Keep in sync if the backend DTOs change.

export type AdminRole = "ADMIN" | "BRANCH_MANAGER" | "BRANCH_CASHIER";
export type AgentStatus = "PENDING_CEILING" | "ACTIVE" | "SUSPENDED" | "DELETED";
export type AdminUserStatus = "ACTIVE" | "SUSPENDED" | "DELETED";
export type ClientStatus = "ACTIVE" | "INACTIVE";
export type VarianceDebtStatus = "OPEN" | "RESOLVED" | "WRITTEN_OFF";
export type OfjSessionStatus = "OPEN" | "CLOSED";

export interface AuthResponse {
  token: string;
}

export interface BranchResponse {
  id: string;
  code: string;
  name: string;
  phone: string | null;
  openTime: string | null; // LocalTime, e.g. "08:00:00"
  closeTime: string | null;
  openTimeLocked: boolean; // true once today's openTime has passed (branch's own timezone) — only closeTime can still be changed today
  timezone: string | null;
  maxCashiers: number;
  requireImei: boolean;
  defaultCeilingPct: number;
}

export interface ScheduleDefaultsResponse {
  openTime: string; // LocalTime, e.g. "08:00:00"
  closeTime: string;
  updatedAt: string | null;
}

export interface AgentResponse {
  id: string;
  employeeCode: string;
  username: string;
  email: string | null;
  fullName: string;
  phone: string;
  imei: string | null;
  branchId: string;
  status: AgentStatus;
  pinMustChange: boolean;
  deviceResetReason: string | null;
  deviceResetAt: string | null;
  deletionReason: string | null;
  deletedAt: string | null;
}

export interface AdminUserResponse {
  id: string;
  login: string;
  fullName: string | null;
  phone: string | null;
  role: AdminRole;
  branchId: string | null;
  status: AdminUserStatus;
  mustChangePassword: boolean;
  deletionReason: string | null;
  deletedAt: string | null;
}

export interface EscrowResponse {
  agentId: string;
  balanceXaf: number;
  baseCeilingXaf: number;
  effectiveCeilingXaf: number;
  cumulativeTodayXaf: number;
  activeOverrideReason: string | null;
  overrideValidUntil: string | null;
  updatedAt: string;
}

export interface OfjAgentLineResponse {
  id: string;
  agentId: string;
  digitalTotalXaf: number;
  collectionsTotalXaf: number;
  activationsTotalXaf: number;
  physicalTotalXaf: number;
  deltaXaf: number;
  resolved: boolean;
}

export interface OfjSummaryResponse {
  sessionId: string;
  branchId: string;
  businessDate: string; // LocalDate, "2026-08-06"
  status: OfjSessionStatus;
  agentLines: OfjAgentLineResponse[];
}

// An active agent who's collected cash today but has no OfjAgentLine yet — hasn't been
// reconciled at all. agentLines alone can't surface this; see OfjService#listPendingAgents.
export interface OfjPendingLineResponse {
  agentId: string;
  collectionsTotalXaf: number;
  activationsTotalXaf: number;
  digitalTotalXaf: number;
}

export interface VarianceDebtResponse {
  id: string;
  agentId: string;
  ofjAgentLineId: string;
  amountXaf: number;
  status: VarianceDebtStatus;
  createdAt: string;
  writtenOffReason: string | null;
  writtenOffBy: string | null;
  writtenOffAt: string | null;
}

export interface SosResponse {
  id: string;
  agentId: string;
  lat: number | null;
  lon: number | null;
  raisedAt: string;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
}

export interface CollectionResponse {
  id: string;
  agentId: string;
  clientId: string;
  clientName: string | null;
  amountXaf: number;
  locationName: string | null;
  collectedAt: string;
  reconciledAt: string | null;
  terminalId: string | null;
}

export interface ClientResponse {
  id: string;
  mfiMemberNo: string;
  fullName: string;
  phone: string;
  branchId: string;
  status: ClientStatus;
}

export interface RoutePointResponse {
  lat: number;
  lon: number;
  recordedAt: string;
}

export interface RouteTransactionResponse {
  collectionId: string;
  lat: number;
  lon: number;
  locationName: string | null;
  amountXaf: number;
  collectedAt: string;
}

export interface RouteResponse {
  agentId: string;
  date: string; // LocalDate, "2026-08-07"
  points: RoutePointResponse[];
  transactions: RouteTransactionResponse[];
}

export interface GeofenceVertex {
  lat: number;
  lon: number;
}

export interface GeofenceResponse {
  agentId: string;
  vertices: GeofenceVertex[];
}

export interface GeofenceAlertResponse {
  id: string;
  agentId: string;
  firstDetectedOutsideAt: string;
  raisedAt: string | null;
  resolvedAt: string | null;
  active: boolean;
}

export type RegistrationTargetRole = "AGENT" | "BRANCH_MANAGER" | "BRANCH_CASHIER";
export type RegistrationApplicationStatus = "SUBMITTED" | "APPROVED" | "REJECTED";
export type RegistrationDocumentType = "NATIONAL_ID" | "CRIMINAL_RECORD" | "MEDICAL_FITNESS" | "LOCATION_PLAN" | "PASSPORT_PHOTO";

export interface RegistrationApplicationResponse {
  id: string;
  targetRole: RegistrationTargetRole;
  branchId: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string | null; // LocalDate, "1990-05-01"
  phone: string;
  login: string;
  email: string | null;
  employeeCode: string | null;
  nationalIdNumber: string | null;
  taxIdNumber: string | null;
  placeOfResidence: string | null;
  criminalRecordIssuedDate: string | null; // LocalDate, "2026-05-01"
  status: RegistrationApplicationStatus;
  submittedBy: string;
  submittedAt: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  provisionedAgentId: string | null;
  provisionedAdminUserId: string | null;
  activationSmsStatus: "SENT" | "FAILED" | null;
  activationSmsSentAt: string | null;
  // Set once, only in the direct response to PATCH /{id}/approve — never persisted or retrievable again.
  tempPassword: string | null;
  tempPin: string | null;
}

export type AuditCategory = "SECURITY" | "COMPLIANCE" | "FINANCIAL";
export type AuditActorType = "ADMIN" | "AGENT" | "CLIENT" | "SYSTEM";
export type AuditStatus = "SUCCESS" | "FAILED";

export interface AuditLogResponse {
  id: string;
  occurredAt: string;
  category: AuditCategory;
  eventType: string;
  actorType: AuditActorType;
  actorLabel: string;
  actorRole: AdminRole | null;
  branchId: string | null;
  branchLabel: string | null;
  agentId: string | null;
  agentLabel: string | null;
  details: string;
  status: AuditStatus;
}

export interface ApiError {
  timestamp?: string;
  path?: string;
  status: number;
  error?: string;
  message?: string;
  requestId?: string;
}
