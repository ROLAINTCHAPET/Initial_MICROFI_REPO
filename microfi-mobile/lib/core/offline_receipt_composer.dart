import '../features/collection/collection_repository.dart' show DenominationLine;
import '../features/collection/receipt_models.dart';

/// Dart port of the backend's NumberToWordsConverter / ReceiptTemplateComposer /
/// ReceiptDataComposer (microfi-core/.../notifications/service/) — kept deliberately faithful to
/// that source so an offline-composed receipt is indistinguishable from one the server would have
/// produced for the same collection. Needed because when there's no connectivity, there's no
/// server to ask for either the printable text or the structured PDF data; this builds both
/// entirely from what's already known locally (client, amount, denominations, agent identity,
/// plus the cached org/branch name from ReceiptContextCache).
///
/// `french` on every method mirrors the agent's currently selected app language
/// (LocalePreference) — matches the backend's own `NotifyCollectionRequest.locale`/
/// `NotificationService` french flag exactly, so an offline receipt and an online one for the
/// same agent read identically regardless of which path composed them.
class OfflineReceiptComposer {
  static const _canonicalDenominations = [10000, 5000, 2000, 1000, 500, 200, 100, 50, 25];
  static const _months = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
  ];
  static const _monthsFr = [
    'Janv', 'Févr', 'Mars', 'Avr', 'Mai', 'Juin', 'Juil', 'Août', 'Sept', 'Oct', 'Nov', 'Déc',
  ];

  /// The 32-column plain-text block for Bluetooth thermal printing — matches
  /// ReceiptTemplateComposer.compose() field-for-field.
  static String composeText({
    required String mfiName,
    required String branchName,
    required DateTime collectedAtUtc,
    required String employeeCode,
    required String agentFullName,
    required String clientMemberNo,
    required String clientFullName,
    required int amountXaf,
    required List<DenominationLine> denominationLines,
    required String deviceTxId,
    required bool french,
  }) {
    const width = 32;
    final majorRule = '=' * width;
    final minorRule = '-' * width;
    final r = StringBuffer();

    r.writeln(majorRule);
    r.writeln(_center('MICROFI COLLECT', width));
    r.writeln(_center(mfiName.toUpperCase(), width));
    r.writeln(_center(french ? 'ENCAISSEMENT NUMÉRIQUE CEMAC' : 'DIGITAL CASH COLLECTION CEMAC', width));
    r.writeln(majorRule);
    r.writeln('${french ? 'Agence' : 'Branch'} : $branchName');
    r.writeln('Date   : ${_ddMMyyyyHHmm(collectedAtUtc)} UTC');
    r.writeln('Agent  : $employeeCode (${_shortName(agentFullName)})');
    r.writeln(minorRule);
    r.writeln();
    r.writeln('${french ? 'ID Client' : 'Client ID'} : $clientMemberNo');
    r.writeln('${french ? 'Nom      ' : 'Name     '} : ${clientFullName.toUpperCase()}');
    r.writeln(minorRule);
    r.writeln();
    r.writeln('${french ? 'Montant Encaissé' : 'Amount Collected'} : ${_grouped(amountXaf)} XAF');
    r.writeln('(${french ? _numberToWordsFrench(amountXaf) : _numberToWords(amountXaf)} ${french ? 'Francs CFA' : 'CFA Francs'})');
    r.writeln();
    r.writeln(french ? 'Mode de Paiement : ESPÈCES' : 'Payment Method  : CASH');
    r.writeln(french ? 'Statut du Ticket : ENREGISTRÉ (*)' : 'Ticket Status   : RECORDED (*)');
    r.writeln();
    r.writeln(minorRule);
    r.writeln(_center(french ? 'DÉTAIL DES COUPURES (XAF)' : 'DENOMINATION BREAKDOWN (XAF)', width));
    r.writeln(minorRule);
    for (final line in denominationLines) {
      if (line.quantity <= 0) continue;
      final lineTotal = line.faceValueXaf * line.quantity;
      final face = _grouped(line.faceValueXaf).padLeft(6);
      final qty = '${line.quantity}'.padRight(2);
      final total = _grouped(lineTotal).padLeft(8);
      r.writeln('$face x $qty = $total');
    }
    r.writeln(minorRule);
    r.writeln();
    r.writeln('${french ? 'Réf. Unique' : 'Unique Ref'} : $deviceTxId (${french ? 'UUID local' : 'local UUID'})');
    r.writeln(french ? 'Signature Numérique Sécurisée :' : 'Secure Digital Signature:');
    r.writeln('[XAF-GEOTAG-VALID-${_ddMMyy(collectedAtUtc)}]');
    r.writeln();
    r.writeln(minorRule);
    if (french) {
      r.writeln('(*) Ce reçu électronique fait office');
      r.writeln('de preuve de votre dépôt en cas de');
      r.writeln('panne réseau temporaire.');
    } else {
      r.writeln('(*) This electronic receipt serves as');
      r.writeln('proof of your deposit in case of');
      r.writeln('temporary network outage.');
    }
    r.writeln(majorRule);
    r.writeln(_center(french ? 'MERCI POUR VOTRE CONFIANCE' : 'THANK YOU FOR YOUR TRUST', width));
    r.write(majorRule);
    return r.toString();
  }

  /// The structured fields behind the downloadable PDF — matches ReceiptDataComposer.compose(),
  /// including showing every canonical denomination (even at zero), not just what was counted.
  static ReceiptData composeData({
    required String branchName,
    required DateTime collectedAtUtc,
    required String employeeCode,
    required String agentFullName,
    required String clientMemberNo,
    required String clientFullName,
    required int amountXaf,
    required List<DenominationLine> denominationLines,
    required String deviceTxId,
    required bool french,
  }) {
    final byFaceValue = {for (final face in _canonicalDenominations) face: 0};
    for (final line in denominationLines) {
      byFaceValue[line.faceValueXaf] = line.quantity;
    }
    final lines = byFaceValue.entries.toList()
      ..sort((a, b) => b.key.compareTo(a.key));

    return ReceiptData(
      branchName: branchName,
      dateFormatted: _formattedDate(collectedAtUtc, french),
      agentEmployeeCode: employeeCode,
      agentShortName: _shortName(agentFullName),
      clientMemberNo: clientMemberNo,
      clientFullName: clientFullName,
      amountXaf: amountXaf,
      amountWords: _amountWords(amountXaf, french),
      denominationLines: lines
          .map((e) => ReceiptDenominationLine(faceValueXaf: e.key, quantity: e.value, lineTotalXaf: e.key * e.value))
          .toList(),
      uniqueRef: deviceTxId,
      signature: 'XAF · GEOTAG VALID · ${_ddMMyy(collectedAtUtc)}',
    );
  }

  /// Public wrappers around the same formatting used above — reused by QrReceiptSigner so a
  /// receipt reconstructed from a scanned QR looks identical to one composed directly, rather
  /// than a separately-maintained (and inevitably drifting) approximation of the same fields.
  static String formattedDate(DateTime collectedAtUtc, bool french) => _formattedDate(collectedAtUtc, french);

  static String amountWords(int amountXaf, bool french) => _amountWords(amountXaf, french);

  static String shortName(String fullName) => _shortName(fullName);

  static String _formattedDate(DateTime collectedAtUtc, bool french) =>
      '${_ddMMMyyyy(collectedAtUtc, french)} · ${_hhmm(collectedAtUtc)} UTC';

  static String _amountWords(int amountXaf, bool french) {
    final numberWords = (french ? _numberToWordsFrench(amountXaf) : _numberToWords(amountXaf)).toLowerCase();
    final sentenceCase = '${numberWords[0].toUpperCase()}${numberWords.substring(1)}';
    return french ? '$sentenceCase francs CFA' : '$sentenceCase CFA francs';
  }

  static String _center(String text, int width) {
    if (text.length >= width) return text;
    final left = (width - text.length) ~/ 2;
    return ' ' * left + text;
  }

  /// "Fouda Marie" -> "Fouda M." — surname in full, given name(s) reduced to initials.
  static String _shortName(String fullName) {
    final parts = fullName.trim().split(RegExp(r'\s+'));
    if (parts.length <= 1) return fullName;
    final buffer = StringBuffer(parts[0]);
    for (var i = 1; i < parts.length; i++) {
      if (parts[i].isEmpty) continue;
      buffer.write(' ${parts[i][0].toUpperCase()}.');
    }
    return buffer.toString();
  }

  static String _grouped(int n) {
    final digits = n.abs().toString();
    final buffer = StringBuffer();
    var count = 0;
    for (var i = digits.length - 1; i >= 0; i--) {
      buffer.write(digits[i]);
      count++;
      if (count % 3 == 0 && i != 0) buffer.write(' ');
    }
    return (n < 0 ? '-' : '') + buffer.toString().split('').reversed.join();
  }

  static String _pad2(int n) => n.toString().padLeft(2, '0');
  static String _ddMMyyyyHHmm(DateTime d) => '${_pad2(d.day)}/${_pad2(d.month)}/${d.year} ${_pad2(d.hour)}:${_pad2(d.minute)}';
  static String _ddMMyy(DateTime d) => '${_pad2(d.day)}${_pad2(d.month)}${(d.year % 100).toString().padLeft(2, '0')}';
  static String _ddMMMyyyy(DateTime d, bool french) => '${_pad2(d.day)} ${(french ? _monthsFr : _months)[d.month - 1]} ${d.year}';
  static String _hhmm(DateTime d) => '${_pad2(d.hour)}:${_pad2(d.minute)}';

  static const _ones = [
    '', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
    'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen',
  ];
  static const _tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

  static String _numberToWords(int amount) {
    if (amount == 0) return 'Zero';
    final words = StringBuffer();
    var remaining = amount;
    for (final scale in [1000000000, 1000000, 1000]) {
      if (remaining >= scale) {
        final scaleName = scale == 1000000000 ? 'Billion' : (scale == 1000000 ? 'Million' : 'Thousand');
        words.write('${_threeDigitsToWords(remaining ~/ scale)} $scaleName ');
        remaining %= scale;
      }
    }
    if (remaining > 0) words.write(_threeDigitsToWords(remaining));
    return words.toString().trim();
  }

  static String _threeDigitsToWords(int n) {
    final sb = StringBuffer();
    if (n >= 100) {
      sb.write('${_ones[n ~/ 100]} Hundred ');
      n %= 100;
    }
    if (n >= 20) {
      sb.write(_tens[n ~/ 10]);
      if (n % 10 > 0) sb.write('-${_ones[n % 10]}');
    } else if (n > 0) {
      sb.write(_ones[n]);
    }
    return sb.toString().trim();
  }

  /// French ones/teens 0-19 — also doubles as the 10-19 "teens" set (onze..dix-neuf), since
  /// French has no separate teens vocabulary the way English does.
  static const _onesFr = [
    '', 'un', 'deux', 'trois', 'quatre', 'cinq', 'six', 'sept', 'huit', 'neuf', 'dix',
    'onze', 'douze', 'treize', 'quatorze', 'quinze', 'seize', 'dix-sept', 'dix-huit', 'dix-neuf',
  ];
  static const _tensFr = ['', '', 'vingt', 'trente', 'quarante', 'cinquante', 'soixante'];

  /// French cardinal numbers, traditional (non-1990-reform) spelling — the style used throughout
  /// Francophone Africa's official/financial documents. Mirrors
  /// NumberToWordsConverter.toWordsFrench() in the backend field-for-field, including the same
  /// irregularities: "vingt et un" (space), "soixante-dix"/"quatre-vingts"/"quatre-vingt-dix" for
  /// 70/80/90, "mille" invariable (never "un mille", never pluralizes), "cent"/"million"/
  /// "milliard" pluralize with -s only when an exact multiple with nothing following.
  static String _numberToWordsFrench(int amount) {
    if (amount == 0) return 'zéro';
    final words = StringBuffer();
    var remaining = amount;

    if (remaining >= 1000000000) {
      final count = remaining ~/ 1000000000;
      remaining %= 1000000000;
      words.write('${_threeDigitsToWordsFrench(count)}${count > 1 ? ' milliards ' : ' milliard '}');
    }
    if (remaining >= 1000000) {
      final count = remaining ~/ 1000000;
      remaining %= 1000000;
      words.write('${_threeDigitsToWordsFrench(count)}${count > 1 ? ' millions ' : ' million '}');
    }
    if (remaining >= 1000) {
      final count = remaining ~/ 1000;
      remaining %= 1000;
      // "mille" is invariable: never "un mille", never "milles" — omit the leading "un".
      words.write(count == 1 ? 'mille ' : '${_threeDigitsToWordsFrench(count)} mille ');
    }
    if (remaining > 0) {
      words.write(_threeDigitsToWordsFrench(remaining));
    }
    return words.toString().trim();
  }

  static String _threeDigitsToWordsFrench(int n) {
    final sb = StringBuffer();
    if (n >= 100) {
      final hundreds = n ~/ 100;
      final rest = n % 100;
      if (hundreds > 1) sb.write('${_onesFr[hundreds]} ');
      // "cent" only pluralizes when it's an exact multiple of 100 with nothing after it.
      sb.write('cent${hundreds > 1 && rest == 0 ? 's' : ''}');
      n = rest;
    }
    if (n > 0) {
      if (sb.isNotEmpty) sb.write(' ');
      sb.write(_twoDigitsToWordsFrench(n));
    }
    return sb.toString().trim();
  }

  static String _twoDigitsToWordsFrench(int n) {
    if (n < 20) return _onesFr[n];
    if (n < 70) {
      final tens = n ~/ 10;
      final ones = n % 10;
      final tensWord = _tensFr[tens];
      if (ones == 0) return tensWord;
      if (ones == 1) return '$tensWord et un';
      return '$tensWord-${_onesFr[ones]}';
    }
    if (n < 80) {
      // 70-79 = "soixante" + (10-19), e.g. 70 -> soixante-dix, 72 -> soixante-douze.
      final teen = n - 60;
      if (teen == 11) return 'soixante et onze';
      return 'soixante-${_onesFr[teen]}';
    }
    // 80-99 = "quatre-vingt(s)" + (0-19) — the trailing "s" on "vingts" only appears at exactly
    // 80 (nothing follows); 81-99 never use "et" (unlike the 20-69 range).
    final rest = n - 80;
    if (rest == 0) return 'quatre-vingts';
    return 'quatre-vingt-${_onesFr[rest]}';
  }
}
