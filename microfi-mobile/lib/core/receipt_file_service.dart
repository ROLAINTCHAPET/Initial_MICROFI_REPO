import 'package:file_saver/file_saver.dart';
import 'package:open_filex/open_filex.dart';

import '../features/collection/receipt_models.dart';
import 'receipt_pdf_builder.dart';
import '../l10n/app_localizations.dart';

class ReceiptFileDownloadFailed implements Exception {
  final String message;
  ReceiptFileDownloadFailed(this.message);

  @override
  String toString() => message;
}

/// Renders the same styled receipt (Graphical Design/microfi_receipt_design.html) the agent would
/// otherwise only see printed, as a PDF, then opens it immediately — no printer needed, and no
/// share sheet. Writes directly (no save-location picker), to the app's own external files
/// directory — not the public Downloads folder, since file_saver's direct saveFile() path is
/// app-scoped storage — then hands the path to open_filex, which wraps it in a content:// URI so
/// the OS can still open it with whatever PDF viewer the device resolves, regardless of that
/// private location.
class ReceiptFileService {
  Future<void> downloadReceipt(ReceiptData receiptData, {required String fileNameHint, required AppLocalizations l10n}) async {
    final bytes = await ReceiptPdfBuilder.build(receiptData, l10n);
    final path = await FileSaver.instance.saveFile(
      name: 'receipt_$fileNameHint',
      bytes: bytes,
      fileExtension: 'pdf',
      mimeType: MimeType.pdf,
    );
    final result = await OpenFilex.open(path, type: 'application/pdf');
    if (result.type != ResultType.done) {
      throw ReceiptFileDownloadFailed(result.message);
    }
  }
}
