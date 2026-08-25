import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/offline_receipt_composer.dart';

void main() {
  group('OfflineReceiptComposer French number words (mirrors backend NumberToWordsConverterTest)', () {
    final cases = <int, String>{
      0: 'zéro',
      1: 'un',
      16: 'seize',
      17: 'dix-sept',
      20: 'vingt',
      21: 'vingt et un',
      29: 'vingt-neuf',
      60: 'soixante',
      61: 'soixante et un',
      69: 'soixante-neuf',
      70: 'soixante-dix',
      71: 'soixante et onze',
      72: 'soixante-douze',
      79: 'soixante-dix-neuf',
      80: 'quatre-vingts',
      81: 'quatre-vingt-un',
      89: 'quatre-vingt-neuf',
      90: 'quatre-vingt-dix',
      91: 'quatre-vingt-onze',
      99: 'quatre-vingt-dix-neuf',
      100: 'cent',
      101: 'cent un',
      199: 'cent quatre-vingt-dix-neuf',
      200: 'deux cents',
      201: 'deux cent un',
      999: 'neuf cent quatre-vingt-dix-neuf',
      1000: 'mille',
      1001: 'mille un',
      1100: 'mille cent',
      2000: 'deux mille',
      21000: 'vingt et un mille',
      100000: 'cent mille',
      200000: 'deux cents mille',
      1000000: 'un million',
      2000000: 'deux millions',
      25000: 'vingt-cinq mille',
      42300: 'quarante-deux mille trois cents',
    };

    cases.forEach((amount, expectedWords) {
      test('$amount XAF -> "$expectedWords"', () {
        final result = OfflineReceiptComposer.amountWords(amount, true);
        final expectedSentence =
            '${expectedWords[0].toUpperCase()}${expectedWords.substring(1)} francs CFA';
        expect(result, expectedSentence);
      });
    });
  });

  group('OfflineReceiptComposer French template', () {
    test('composeText produces French labels and no leftover English', () {
      final text = OfflineReceiptComposer.composeText(
        mfiName: 'Microfi Test',
        branchName: 'Douala Centre',
        collectedAtUtc: DateTime.utc(2026, 8, 24, 14, 30),
        employeeCode: 'AG-001',
        agentFullName: 'Fouda Marie',
        clientMemberNo: 'CL-042',
        clientFullName: 'Ngono Paul',
        amountXaf: 25000,
        denominationLines: const [],
        deviceTxId: 'tx-123',
        french: true,
      );
      expect(text, contains('Agence : Douala Centre'));
      expect(text, contains('Montant Encaissé : 25 000 XAF'));
      // Unlike the English _ones/_tens arrays (capitalized), the French ones/tens are lowercase —
      // matching standard French orthography, where spelled-out numbers aren't capitalized
      // mid-sentence the way the English template's parenthetical aside happens to be.
      expect(text, contains('(vingt-cinq mille Francs CFA)'));
      expect(text, contains('MERCI POUR VOTRE CONFIANCE'));
      expect(text, isNot(contains('Branch')));
      expect(text, isNot(contains('THANK YOU')));
    });

    test('composeText french:false is unchanged (still English)', () {
      final text = OfflineReceiptComposer.composeText(
        mfiName: 'Microfi Test',
        branchName: 'Douala Centre',
        collectedAtUtc: DateTime.utc(2026, 8, 24, 14, 30),
        employeeCode: 'AG-001',
        agentFullName: 'Fouda Marie',
        clientMemberNo: 'CL-042',
        clientFullName: 'Ngono Paul',
        amountXaf: 25000,
        denominationLines: const [],
        deviceTxId: 'tx-123',
        french: false,
      );
      expect(text, contains('Branch : Douala Centre'));
      expect(text, contains('THANK YOU FOR YOUR TRUST'));
    });
  });
}
