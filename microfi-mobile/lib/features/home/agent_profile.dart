class AgentProfile {
  final String id;
  final String employeeCode;
  final String username;
  final String? email;
  final String fullName;
  final String phone;
  final String? imei;
  final String branchId;
  final String status;

  /// True until the agent replaces the admin-assigned starting transaction PIN with one of
  /// their own — gates collection recording until then (see PinSetupScreen).
  final bool pinMustChange;

  AgentProfile({
    required this.id,
    required this.employeeCode,
    required this.username,
    this.email,
    required this.fullName,
    required this.phone,
    this.imei,
    required this.branchId,
    required this.status,
    required this.pinMustChange,
  });

  factory AgentProfile.fromJson(Map<String, dynamic> json) => AgentProfile(
        id: json['id'] as String,
        employeeCode: json['employeeCode'] as String,
        username: json['username'] as String,
        email: json['email'] as String?,
        fullName: json['fullName'] as String,
        phone: json['phone'] as String,
        imei: json['imei'] as String?,
        branchId: json['branchId'] as String,
        status: json['status'] as String,
        pinMustChange: json['pinMustChange'] as bool? ?? false,
      );
}

class EscrowStatus {
  final String agentId;
  final int balanceXaf;
  final int baseCeilingXaf;
  final int effectiveCeilingXaf;
  final int cumulativeTodayXaf;
  final String? activeOverrideReason;

  EscrowStatus({
    required this.agentId,
    required this.balanceXaf,
    required this.baseCeilingXaf,
    required this.effectiveCeilingXaf,
    required this.cumulativeTodayXaf,
    this.activeOverrideReason,
  });

  // The ceiling gates cumulative cash collected today (BR-03), not the funded wallet balance —
  // this is what actually moves as the agent records collections, so it's what must be shown live.
  double get utilization => effectiveCeilingXaf > 0 ? (cumulativeTodayXaf / effectiveCeilingXaf).clamp(0, 1) : 0;
  bool get nearLimit => utilization >= 0.9;
  int get remainingCeilingXaf => (effectiveCeilingXaf - cumulativeTodayXaf).clamp(0, effectiveCeilingXaf);

  factory EscrowStatus.fromJson(Map<String, dynamic> json) => EscrowStatus(
        agentId: json['agentId'] as String,
        balanceXaf: (json['balanceXaf'] as num).toInt(),
        baseCeilingXaf: (json['baseCeilingXaf'] as num).toInt(),
        effectiveCeilingXaf: (json['effectiveCeilingXaf'] as num).toInt(),
        cumulativeTodayXaf: (json['cumulativeTodayXaf'] as num?)?.toInt() ?? 0,
        activeOverrideReason: json['activeOverrideReason'] as String?,
      );
}
