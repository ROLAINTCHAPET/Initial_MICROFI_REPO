class ClientSelfProfile {
  final String id;
  final String mfiMemberNo;
  final String fullName;
  final String phone;
  final String branchId;
  final String tokenStatus; // NONE, ACTIVE, EXPIRED
  final DateTime? tokenExpiresAt;

  ClientSelfProfile({
    required this.id,
    required this.mfiMemberNo,
    required this.fullName,
    required this.phone,
    required this.branchId,
    required this.tokenStatus,
    required this.tokenExpiresAt,
  });

  factory ClientSelfProfile.fromJson(Map<String, dynamic> json) => ClientSelfProfile(
        id: json['id'] as String,
        mfiMemberNo: json['mfiMemberNo'] as String,
        fullName: json['fullName'] as String,
        phone: json['phone'] as String? ?? '',
        branchId: json['branchId'] as String,
        tokenStatus: json['tokenStatus'] as String,
        tokenExpiresAt: json['tokenExpiresAt'] != null ? DateTime.parse(json['tokenExpiresAt'] as String) : null,
      );
}

/// Result of confirming the client's own half of UC-19's two-party activation gate — `status` is
/// `AWAITING_SPONSORSHIP` if the agent hasn't sponsored yet, or `ACTIVE` once both sides are done
/// and the booklet token is issued (agent-side status also stops at `AWAITING_PAYMENT` if the
/// client hasn't confirmed yet — see ClientActivationResponse on the backend).
class ClientActivationConfirmation {
  final String status;

  ClientActivationConfirmation({required this.status});

  factory ClientActivationConfirmation.fromJson(Map<String, dynamic> json) =>
      ClientActivationConfirmation(status: json['status'] as String);
}

class ClientBalance {
  final int balanceXaf;
  final DateTime asOf;

  ClientBalance({required this.balanceXaf, required this.asOf});

  factory ClientBalance.fromJson(Map<String, dynamic> json) => ClientBalance(
        balanceXaf: (json['balanceXaf'] as num).toInt(),
        asOf: DateTime.parse(json['asOf'] as String),
      );
}

/// A collection MICROFI has recorded but the CBS hasn't posted yet (see
/// ClientSelfServiceController#recentCollections) — shown ahead of the official
/// ClientHistoryEntry list, which only catches up at end-of-day export.
class ClientRecentCollection {
  final String id;
  final int amountXaf;
  final String? locationName;
  final DateTime collectedAt;

  ClientRecentCollection({required this.id, required this.amountXaf, required this.locationName, required this.collectedAt});

  factory ClientRecentCollection.fromJson(Map<String, dynamic> json) => ClientRecentCollection(
        id: json['id'] as String,
        amountXaf: (json['amountXaf'] as num).toInt(),
        locationName: json['locationName'] as String?,
        collectedAt: DateTime.parse(json['collectedAt'] as String),
      );
}

class ClientHistoryEntry {
  final String reference;
  final int amountXaf;
  final DateTime date;
  final String type;

  ClientHistoryEntry({required this.reference, required this.amountXaf, required this.date, required this.type});

  factory ClientHistoryEntry.fromJson(Map<String, dynamic> json) => ClientHistoryEntry(
        reference: json['reference'] as String,
        amountXaf: (json['amountXaf'] as num).toInt(),
        date: DateTime.parse(json['date'] as String),
        type: json['type'] as String,
      );
}
