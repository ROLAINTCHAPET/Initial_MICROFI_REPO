class ReceiptDenominationLine {
  final int faceValueXaf;
  final int quantity;
  final int lineTotalXaf;

  ReceiptDenominationLine({required this.faceValueXaf, required this.quantity, required this.lineTotalXaf});

  factory ReceiptDenominationLine.fromJson(Map<String, dynamic> json) => ReceiptDenominationLine(
        faceValueXaf: (json['faceValueXaf'] as num).toInt(),
        quantity: json['quantity'] as int,
        lineTotalXaf: (json['lineTotalXaf'] as num).toInt(),
      );
}

/// Structured fields behind the downloadable PDF receipt (see ReceiptDataComposer on the
/// backend) — a styled card matching Graphical Design/microfi_receipt_design.html, distinct from
/// the plain-text receiptText used for Bluetooth thermal printing.
class ReceiptData {
  final String branchName;
  final String dateFormatted;
  final String agentEmployeeCode;
  final String agentShortName;
  final String clientMemberNo;
  final String clientFullName;
  final int amountXaf;
  final String amountWords;
  final List<ReceiptDenominationLine> denominationLines;
  final String uniqueRef;
  final String signature;

  ReceiptData({
    required this.branchName,
    required this.dateFormatted,
    required this.agentEmployeeCode,
    required this.agentShortName,
    required this.clientMemberNo,
    required this.clientFullName,
    required this.amountXaf,
    required this.amountWords,
    required this.denominationLines,
    required this.uniqueRef,
    required this.signature,
  });

  factory ReceiptData.fromJson(Map<String, dynamic> json) => ReceiptData(
        branchName: json['branchName'] as String,
        dateFormatted: json['dateFormatted'] as String,
        agentEmployeeCode: json['agentEmployeeCode'] as String,
        agentShortName: json['agentShortName'] as String,
        clientMemberNo: json['clientMemberNo'] as String,
        clientFullName: json['clientFullName'] as String,
        amountXaf: (json['amountXaf'] as num).toInt(),
        amountWords: json['amountWords'] as String,
        denominationLines: (json['denominationLines'] as List<dynamic>)
            .map((e) => ReceiptDenominationLine.fromJson(e as Map<String, dynamic>))
            .toList(),
        uniqueRef: json['uniqueRef'] as String,
        signature: json['signature'] as String,
      );
}
