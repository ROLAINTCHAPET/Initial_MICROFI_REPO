import 'package:esc_pos_utils_plus/esc_pos_utils_plus.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:print_bluetooth_thermal/print_bluetooth_thermal.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../l10n/app_localizations.dart';

enum PrinterUnavailableReason { noPrinterSelected, bluetoothUnavailable, connectionFailed, didNotConfirm }

class PrinterUnavailable implements Exception {
  final PrinterUnavailableReason reason;
  final String? printerName;
  PrinterUnavailable(this.reason, {this.printerName});

  /// Localized message for [reason] — resolved at catch time, since every call site that catches
  /// this already has a BuildContext (a printing flow driven by a widget).
  String message(AppLocalizations l10n) => switch (reason) {
        PrinterUnavailableReason.noPrinterSelected => l10n.printerErrorNoPrinterSelected,
        PrinterUnavailableReason.bluetoothUnavailable => l10n.printerErrorBluetoothUnavailable,
        PrinterUnavailableReason.connectionFailed => l10n.printerErrorConnectionFailed(printerName ?? ''),
        PrinterUnavailableReason.didNotConfirm => l10n.printerErrorDidNotConfirm,
      };

  @override
  String toString() => reason.toString();
}

class PairedPrinter {
  final String name;
  final String macAddress;

  PairedPrinter({required this.name, required this.macAddress});
}

/// UC-09's Bluetooth thermal-printer channel: the agent pairs a 58mm/80mm receipt printer once
/// (in Android/iOS Bluetooth settings, same as any other Bluetooth device), picks it here, and
/// the MAC address is remembered locally (mirrors DeviceIdService's persistence pattern) so
/// printing a receipt afterwards is a single tap. Printing is entirely device-local — the backend
/// only supplies the receipt text (NotificationService#prepare) and logs whether printing was
/// attempted, per NotifyCollectionRequest#printedReceipt.
class PrinterService {
  static const _macKey = 'printer_mac_address';
  static const _nameKey = 'printer_name';

  /// print_bluetooth_thermal can only check whether Bluetooth permission is granted, never
  /// request it — so without this, a fresh install with the OS radio already on would still fail
  /// forever with no way for the agent to ever be prompted for the (Android 12+) runtime grant.
  Future<bool> isReady() async {
    final granted = await _ensureBluetoothPermission();
    if (!granted) return false;
    return PrintBluetoothThermal.bluetoothEnabled;
  }

  Future<bool> _ensureBluetoothPermission() async {
    if (await PrintBluetoothThermal.isPermissionBluetoothGranted) return true;
    final statuses = await [Permission.bluetoothScan, Permission.bluetoothConnect].request();
    return statuses.values.every((status) => status.isGranted);
  }

  Future<List<PairedPrinter>> pairedPrinters() async {
    final devices = await PrintBluetoothThermal.pairedBluetooths;
    return devices.map((d) => PairedPrinter(name: d.name, macAddress: d.macAdress)).toList();
  }

  Future<PairedPrinter?> savedPrinter() async {
    final prefs = await SharedPreferences.getInstance();
    final mac = prefs.getString(_macKey);
    final name = prefs.getString(_nameKey);
    if (mac == null || mac.isEmpty) return null;
    return PairedPrinter(name: name ?? mac, macAddress: mac);
  }

  Future<void> savePrinter(PairedPrinter printer) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_macKey, printer.macAddress);
    await prefs.setString(_nameKey, printer.name);
  }

  Future<void> forgetPrinter() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_macKey);
    await prefs.remove(_nameKey);
  }

  /// Connects to the saved printer (or [printer] if given) and prints [receiptText] — the
  /// server-composed BR-Notif-01 text (MFI name, amount, date, agent ID). Returns true only if
  /// both the connection and the write actually succeeded.
  Future<bool> printReceipt(String receiptText, {PairedPrinter? printer}) async {
    final target = printer ?? await savedPrinter();
    if (target == null) {
      throw PrinterUnavailable(PrinterUnavailableReason.noPrinterSelected);
    }
    if (!await isReady()) {
      throw PrinterUnavailable(PrinterUnavailableReason.bluetoothUnavailable);
    }
    final connected = await PrintBluetoothThermal.connect(macPrinterAddress: target.macAddress);
    if (!connected) {
      throw PrinterUnavailable(PrinterUnavailableReason.connectionFailed, printerName: target.name);
    }
    try {
      final profile = await CapabilityProfile.load();
      final generator = Generator(PaperSize.mm58, profile);
      final bytes = <int>[];
      // receiptText (ReceiptTemplateComposer, server-side) is pre-formatted to the 32-column
      // width itself — headers/footers are manually centered, so printing left-aligned preserves
      // that layout instead of the printer re-centering (and breaking) the denomination table.
      for (final line in receiptText.split('\n')) {
        bytes.addAll(generator.text(line, styles: const PosStyles(align: PosAlign.left)));
      }
      bytes.addAll(generator.feed(2));
      bytes.addAll(generator.cut());
      return await PrintBluetoothThermal.writeBytes(bytes);
    } finally {
      await PrintBluetoothThermal.disconnect;
    }
  }
}
