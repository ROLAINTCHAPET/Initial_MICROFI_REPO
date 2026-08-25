import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_fr.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('fr'),
  ];

  /// App name shown on the role-select screen.
  ///
  /// In en, this message translates to:
  /// **'Microfi'**
  String get appName;

  /// No description provided for @roleSelectPrompt.
  ///
  /// In en, this message translates to:
  /// **'Who\'s signing in?'**
  String get roleSelectPrompt;

  /// No description provided for @roleFieldAgentTitle.
  ///
  /// In en, this message translates to:
  /// **'Field Agent'**
  String get roleFieldAgentTitle;

  /// No description provided for @roleFieldAgentSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Collect cash, manage clients and escrow'**
  String get roleFieldAgentSubtitle;

  /// No description provided for @roleClientTitle.
  ///
  /// In en, this message translates to:
  /// **'Client'**
  String get roleClientTitle;

  /// No description provided for @roleClientSubtitle.
  ///
  /// In en, this message translates to:
  /// **'View my digital savings booklet'**
  String get roleClientSubtitle;

  /// No description provided for @languagePickerTitle.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get languagePickerTitle;

  /// No description provided for @languageEnglish.
  ///
  /// In en, this message translates to:
  /// **'English'**
  String get languageEnglish;

  /// No description provided for @languageFrench.
  ///
  /// In en, this message translates to:
  /// **'Français'**
  String get languageFrench;

  /// Generic OK button label.
  ///
  /// In en, this message translates to:
  /// **'OK'**
  String get commonOk;

  /// Generic cancel button label.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get commonCancel;

  /// Generic confirm button label.
  ///
  /// In en, this message translates to:
  /// **'Confirm'**
  String get commonConfirm;

  /// Generic retry button label.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get commonRetry;

  /// Generic save button label.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get commonSave;

  /// Generic close button label.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get commonClose;

  /// Button label to call/contact the branch, used from the escrow ceiling alert and elsewhere.
  ///
  /// In en, this message translates to:
  /// **'Contact Branch'**
  String get commonContactBranch;

  /// Generic 'done' title/label.
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get commonDone;

  /// Sign out button/menu label, used across shells and error screens.
  ///
  /// In en, this message translates to:
  /// **'Sign Out'**
  String get commonSignOut;

  /// Shown when a 401 login/auth failure carries no specific backend message (dialogs.dart friendlyErrorMessage).
  ///
  /// In en, this message translates to:
  /// **'Incorrect login credentials.'**
  String get errorIncorrectCredentials;

  /// Shown when a network request times out (dialogs.dart friendlyErrorMessage).
  ///
  /// In en, this message translates to:
  /// **'The request took too long. Please try again.'**
  String get errorRequestTimeout;

  /// Shown when the app can't reach the backend at all (dialogs.dart friendlyErrorMessage).
  ///
  /// In en, this message translates to:
  /// **'Unable to reach the server. Check your connection.'**
  String get errorUnableToReachServer;

  /// Generic fallback error message (dialogs.dart friendlyErrorMessage).
  ///
  /// In en, this message translates to:
  /// **'Something went wrong. Please try again.'**
  String get errorSomethingWentWrong;

  /// Default title of the generic error dialog (dialogs.dart showErrorDialog).
  ///
  /// In en, this message translates to:
  /// **'Something Went Wrong'**
  String get errorDialogTitle;

  /// Title of the dialog prompting for the transaction PIN before syncing offline collections.
  ///
  /// In en, this message translates to:
  /// **'Enter Your PIN'**
  String get dialogEnterPinTitle;

  /// Title of the hard-stop alert card shown when the agent's escrow ceiling would be exceeded.
  ///
  /// In en, this message translates to:
  /// **'Escrow Ceiling Reached'**
  String get escrowCeilingReachedTitle;

  /// Title of the offline-mode banner.
  ///
  /// In en, this message translates to:
  /// **'Offline Mode'**
  String get offlineModeTitle;

  /// Subtitle of the offline-mode banner, counting queued collections awaiting sync.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, one{1 collection pending sync} other{{count} collections pending sync}}'**
  String offlineCollectionsPendingSync(int count);

  /// Button to trigger syncing queued offline collections now.
  ///
  /// In en, this message translates to:
  /// **'Sync Now'**
  String get syncNowButton;

  /// core/location.dart LocationUnavailable — device location services are off.
  ///
  /// In en, this message translates to:
  /// **'Location services are turned off on this device.'**
  String get locationErrorServicesDisabled;

  /// core/location.dart LocationUnavailable — location permission was denied.
  ///
  /// In en, this message translates to:
  /// **'Location permission was denied. Enable it to record a collection.'**
  String get locationErrorPermissionDenied;

  /// core/printer_service.dart PrinterUnavailable — no saved/selected printer.
  ///
  /// In en, this message translates to:
  /// **'No thermal printer selected yet.'**
  String get printerErrorNoPrinterSelected;

  /// core/printer_service.dart PrinterUnavailable — Bluetooth off or permission not granted.
  ///
  /// In en, this message translates to:
  /// **'Turn on Bluetooth and grant permission to print.'**
  String get printerErrorBluetoothUnavailable;

  /// core/printer_service.dart PrinterUnavailable — Bluetooth connection to the printer failed.
  ///
  /// In en, this message translates to:
  /// **'Could not connect to {printerName}.'**
  String printerErrorConnectionFailed(String printerName);

  /// collection_stepper_screen.dart — printer accepted the job but didn't confirm success.
  ///
  /// In en, this message translates to:
  /// **'Printer did not confirm the print.'**
  String get printerErrorDidNotConfirm;

  /// core/qr_receipt_signer.dart QrReceiptVerificationFailed — scanned content isn't valid JSON or has no signature.
  ///
  /// In en, this message translates to:
  /// **'This QR code isn\'t a MICROFI receipt.'**
  String get qrErrorNotMicrofiReceipt;

  /// core/qr_receipt_signer.dart QrReceiptVerificationFailed — decoded JSON is missing required fields.
  ///
  /// In en, this message translates to:
  /// **'This QR code is missing expected receipt data.'**
  String get qrErrorMissingData;

  /// core/qr_receipt_signer.dart QrReceiptVerificationFailed — signature mismatch.
  ///
  /// In en, this message translates to:
  /// **'This QR code has been altered or isn\'t a genuine MICROFI receipt.'**
  String get qrErrorTampered;

  /// Short network-failure fallback used inline (collection_stepper_screen.dart client search and submit).
  ///
  /// In en, this message translates to:
  /// **'Unable to reach the server.'**
  String get errorUnableToReachServerShort;

  /// Generic 'Total' label used on amount summaries.
  ///
  /// In en, this message translates to:
  /// **'Total'**
  String get commonTotal;

  /// An already-formatted amount (with thousands separators) suffixed with the XAF currency code.
  ///
  /// In en, this message translates to:
  /// **'{amount} XAF'**
  String amountXaf(String amount);

  /// Collection stepper — client search field hint text.
  ///
  /// In en, this message translates to:
  /// **'Name, phone, or member number'**
  String get csSearchHint;

  /// Collection stepper — empty client search with no query typed.
  ///
  /// In en, this message translates to:
  /// **'No clients registered yet.'**
  String get csNoClientsRegistered;

  /// Collection stepper — empty client search results for a typed query.
  ///
  /// In en, this message translates to:
  /// **'No clients found for that search.'**
  String get csNoClientsFound;

  /// Collection stepper — client search result subtitle (member number and phone).
  ///
  /// In en, this message translates to:
  /// **'{memberNo} • {phone}'**
  String csClientSubtitle(String memberNo, String phone);

  /// Collection stepper — AppBar title.
  ///
  /// In en, this message translates to:
  /// **'Collect Cash'**
  String get csCollectCashTitle;

  /// Collection stepper — GPS capture in progress.
  ///
  /// In en, this message translates to:
  /// **'Capturing GPS location…'**
  String get csCapturingGps;

  /// Collection stepper — GPS captured, with accuracy in meters.
  ///
  /// In en, this message translates to:
  /// **'Location captured (±{accuracy}m)'**
  String csLocationCaptured(int accuracy);

  /// Collection stepper — fallback text when GPS hasn't been captured and there's no specific error.
  ///
  /// In en, this message translates to:
  /// **'Location unavailable'**
  String get csLocationUnavailable;

  /// Collection stepper — denominations card header.
  ///
  /// In en, this message translates to:
  /// **'Denomination Breakdown'**
  String get csDenominationBreakdown;

  /// Collection stepper — running total label above the amount.
  ///
  /// In en, this message translates to:
  /// **'CALCULATED TOTAL'**
  String get csCalculatedTotal;

  /// Collection stepper — shown on the EscrowCeilingReachedCard when this collection would exceed the ceiling (online path).
  ///
  /// In en, this message translates to:
  /// **'This collection would push you to {projected} / {ceiling} XAF — {over} XAF over your daily ceiling.'**
  String csEscrowExceedMessage(String projected, String ceiling, String over);

  /// Collection stepper — button to move from denominations to the confirm step.
  ///
  /// In en, this message translates to:
  /// **'Verify & Continue'**
  String get csVerifyAndContinue;

  /// Collection stepper — confirm-step card header.
  ///
  /// In en, this message translates to:
  /// **'Confirm Collection'**
  String get csConfirmCollectionTitle;

  /// Collection stepper — one denomination line on the confirm step (face value × count).
  ///
  /// In en, this message translates to:
  /// **'{denom} XAF × {count}'**
  String csDenominationLine(int denom, int count);

  /// Collection stepper — captured GPS coordinates shown on the confirm step.
  ///
  /// In en, this message translates to:
  /// **'Location: {lat}, {lon} (±{accuracy}m)'**
  String csLocationLine(String lat, String lon, int accuracy);

  /// Collection stepper — PIN field label on the confirm step.
  ///
  /// In en, this message translates to:
  /// **'Enter Your PIN to Confirm'**
  String get csEnterPinToConfirm;

  /// Collection stepper — submit button on the confirm step.
  ///
  /// In en, this message translates to:
  /// **'Record Collection'**
  String get csRecordCollection;

  /// Collection stepper — success title when the submit was a duplicate (already recorded server-side).
  ///
  /// In en, this message translates to:
  /// **'Already Recorded'**
  String get csAlreadyRecorded;

  /// Collection stepper — success title for a newly recorded collection.
  ///
  /// In en, this message translates to:
  /// **'Collection Validated'**
  String get csCollectionValidated;

  /// Collection stepper — success subtitle with the location name known.
  ///
  /// In en, this message translates to:
  /// **'Transaction ID: {id} • {location}'**
  String csTransactionIdWithLocation(String id, String location);

  /// Collection stepper — success subtitle without a location name.
  ///
  /// In en, this message translates to:
  /// **'Transaction ID: {id}'**
  String csTransactionId(String id);

  /// Collection stepper — success card amount label.
  ///
  /// In en, this message translates to:
  /// **'Amount Collected'**
  String get csAmountCollected;

  /// Collection stepper — print-receipt button label once printed.
  ///
  /// In en, this message translates to:
  /// **'Receipt Printed'**
  String get csReceiptPrinted;

  /// Collection stepper — print-receipt button label while printing.
  ///
  /// In en, this message translates to:
  /// **'Printing…'**
  String get csPrinting;

  /// Collection stepper — print-receipt button default label.
  ///
  /// In en, this message translates to:
  /// **'Print Receipt'**
  String get csPrintReceipt;

  /// Collection stepper — download-receipt button label once downloaded.
  ///
  /// In en, this message translates to:
  /// **'Receipt Downloaded'**
  String get csReceiptDownloaded;

  /// Collection stepper — download-receipt button label while downloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading…'**
  String get csDownloading;

  /// Collection stepper — download-receipt button default label.
  ///
  /// In en, this message translates to:
  /// **'Download Receipt'**
  String get csDownloadReceipt;

  /// Collection stepper — button to show the receipt QR code.
  ///
  /// In en, this message translates to:
  /// **'Show QR for Client'**
  String get csShowQrForClient;

  /// Collection stepper — title of the queued-offline confirmation screen.
  ///
  /// In en, this message translates to:
  /// **'Saved Offline'**
  String get csSavedOffline;

  /// Collection stepper — body text of the queued-offline confirmation screen.
  ///
  /// In en, this message translates to:
  /// **'{amount} XAF for {clientName} is queued. It will sync automatically once you\'re back online.'**
  String csQueuedOfflineMessage(String amount, String clientName);

  /// Collection stepper — printer-picker dialog title.
  ///
  /// In en, this message translates to:
  /// **'Select Thermal Printer'**
  String get csSelectThermalPrinter;

  /// Collection stepper — step-indicator label (Step 1/2/3).
  ///
  /// In en, this message translates to:
  /// **'Step {number}'**
  String csStepLabel(int number);

  /// Collection stepper — small label above the selected client's name.
  ///
  /// In en, this message translates to:
  /// **'TARGET CLIENT'**
  String get csTargetClientLabel;

  /// Collection stepper — ceiling-preview card header.
  ///
  /// In en, this message translates to:
  /// **'Daily Ceiling Impact'**
  String get csDailyCeilingImpact;

  /// Collection stepper — validation error when submitting with an empty PIN field.
  ///
  /// In en, this message translates to:
  /// **'Enter your PIN to confirm this collection.'**
  String get csPinRequiredError;

  /// Collection stepper — offline PIN lockout error.
  ///
  /// In en, this message translates to:
  /// **'Too many incorrect PIN attempts. Try again in {minutes} min, or connect to the internet.'**
  String csTooManyPinAttempts(int minutes);

  /// Collection stepper — offline PIN verification failed.
  ///
  /// In en, this message translates to:
  /// **'Incorrect PIN.'**
  String get csIncorrectPin;

  /// Collection stepper — offline-path ceiling exceeded error, using the last-known ceiling snapshot.
  ///
  /// In en, this message translates to:
  /// **'This would push you to {projected} / {ceiling} XAF, over your daily ceiling (based on your last connection — reconnect for a fresh check, or contact your branch).'**
  String csCeilingExceedOfflineMessage(String projected, String ceiling);

  /// Collection stepper — generic (non-PrinterUnavailable) print failure.
  ///
  /// In en, this message translates to:
  /// **'Could not print the receipt.'**
  String get csCouldNotPrintReceipt;

  /// Collection stepper — the receipt PDF saved but the device couldn't open it.
  ///
  /// In en, this message translates to:
  /// **'Receipt saved, but could not open it: {error}'**
  String csReceiptSavedCouldNotOpen(String error);

  /// Collection stepper — generic receipt-download failure.
  ///
  /// In en, this message translates to:
  /// **'Could not download the receipt.'**
  String get csCouldNotDownloadReceipt;

  /// Collection stepper — printer picker couldn't proceed because Bluetooth isn't ready.
  ///
  /// In en, this message translates to:
  /// **'Turn on Bluetooth and grant permission, then try again.'**
  String get csBluetoothPermissionNeeded;

  /// Collection stepper — printer picker found no paired Bluetooth printers.
  ///
  /// In en, this message translates to:
  /// **'No paired thermal printer found — pair one in your phone\'s Bluetooth settings first.'**
  String get csNoPairedPrinterFound;

  /// Collection stepper — ceiling preview progress bar's numeric projected/ceiling readout.
  ///
  /// In en, this message translates to:
  /// **'{projected} / {ceiling} XAF'**
  String csCeilingProgressLine(int projected, int ceiling);

  /// Collection stepper — generic fallback noun used in the queued-offline message when the client's name isn't available.
  ///
  /// In en, this message translates to:
  /// **'client'**
  String get csGenericClient;

  /// Home screen — AppBar title of the collection history screen, and the quick-action label that opens it.
  ///
  /// In en, this message translates to:
  /// **'Collection History'**
  String get hsCollectionHistoryTitle;

  /// Home screen — SnackBar shown after a successful SOS send.
  ///
  /// In en, this message translates to:
  /// **'SOS sent — your branch has been alerted.'**
  String get hsSosSentMessage;

  /// Home screen — SnackBar shown when sending an SOS fails.
  ///
  /// In en, this message translates to:
  /// **'Unable to send SOS — check your connection and try again.'**
  String get hsSosSendFailedMessage;

  /// Home screen — SnackBar shown when a pending SOS is newly acknowledged.
  ///
  /// In en, this message translates to:
  /// **'Your SOS was acknowledged by your branch.'**
  String get hsSosAcknowledgedMessage;

  /// Home screen — primary CTA button to start a new collection.
  ///
  /// In en, this message translates to:
  /// **'New Collection'**
  String get hsNewCollection;

  /// Home screen — quick-action label opening the agent's route.
  ///
  /// In en, this message translates to:
  /// **'My Route'**
  String get hsMyRoute;

  /// Home screen — button opening the sponsor-activation flow.
  ///
  /// In en, this message translates to:
  /// **'Sponsor Client Activation'**
  String get hsSponsorClientActivation;

  /// Home screen — section header above the recent collections list.
  ///
  /// In en, this message translates to:
  /// **'Recent Collections'**
  String get hsRecentCollections;

  /// Home screen — link to view the full collection history.
  ///
  /// In en, this message translates to:
  /// **'See all'**
  String get hsSeeAll;

  /// Home screen — empty state for the recent collections list.
  ///
  /// In en, this message translates to:
  /// **'No collections recorded yet.'**
  String get hsNoCollectionsRecorded;

  /// Home screen — small label above today's cumulative collected amount on the ceiling gauge card.
  ///
  /// In en, this message translates to:
  /// **'TODAY\'S COLLECTIONS'**
  String get hsTodaysCollections;

  /// Home screen — label above the effective ceiling amount on the ceiling gauge card.
  ///
  /// In en, this message translates to:
  /// **'Ceiling'**
  String get hsCeilingLabel;

  /// Home screen — ceiling gauge caption when near the daily limit.
  ///
  /// In en, this message translates to:
  /// **'{percent}% capacity reached. Deposit soon.'**
  String hsCapacityReachedDepositSoon(int percent);

  /// Home screen — ceiling gauge caption when comfortably under the daily limit.
  ///
  /// In en, this message translates to:
  /// **'{percent}% capacity reached. Safe to continue.'**
  String hsCapacityReachedSafe(int percent);

  /// Home screen — fallback client name on a recent-collection row when the name isn't known.
  ///
  /// In en, this message translates to:
  /// **'Unknown client'**
  String get hsUnknownClient;

  /// Home screen — recent-collection row subtitle (time of day and payment method).
  ///
  /// In en, this message translates to:
  /// **'{time} • Cash'**
  String hsTimeCashLine(String time);

  /// Home screen — recent-collection row amount, prefixed with a plus sign.
  ///
  /// In en, this message translates to:
  /// **'+{amount} XAF'**
  String hsAmountCollectedPlus(String amount);

  /// Home screen — agent status pill when active.
  ///
  /// In en, this message translates to:
  /// **'Online'**
  String get hsStatusActive;

  /// Home screen — agent status pill when suspended.
  ///
  /// In en, this message translates to:
  /// **'Suspended'**
  String get hsStatusSuspended;

  /// Home screen — banner shown while an SOS alert is unacknowledged.
  ///
  /// In en, this message translates to:
  /// **'SOS pending — awaiting acknowledgement from your branch.'**
  String get hsSosPendingBanner;

  /// Client home screen — PIN prompt message when confirming activation payment.
  ///
  /// In en, this message translates to:
  /// **'Enter your PIN to confirm you paid the activation fee to your agent.'**
  String get chEnterPinConfirmPayment;

  /// Client home screen — success dialog title when both sides of activation are now confirmed.
  ///
  /// In en, this message translates to:
  /// **'Booklet Activated'**
  String get chBookletActivatedTitle;

  /// Client home screen — success dialog title when only the client's side of activation is confirmed.
  ///
  /// In en, this message translates to:
  /// **'Payment Confirmed'**
  String get chPaymentConfirmedTitle;

  /// Client home screen — success dialog body when activation completes.
  ///
  /// In en, this message translates to:
  /// **'Your booklet is now active — both your payment and your agent\'s sponsorship are confirmed.'**
  String get chBookletActiveMessage;

  /// Client home screen — success dialog body when only the client's payment is confirmed.
  ///
  /// In en, this message translates to:
  /// **'Payment confirmed on your side. Waiting on your agent to sponsor your activation.'**
  String get chPaymentConfirmedWaitingMessage;

  /// Client home screen — error dialog title when confirming activation payment fails.
  ///
  /// In en, this message translates to:
  /// **'Could Not Confirm Payment'**
  String get chCouldNotConfirmPaymentTitle;

  /// Client home screen — AppBar title of the contribution history screen.
  ///
  /// In en, this message translates to:
  /// **'Contribution History'**
  String get chContributionHistoryTitle;

  /// Client home screen — activation banner heading when the token has expired.
  ///
  /// In en, this message translates to:
  /// **'Renewal Needed'**
  String get chRenewalNeeded;

  /// Client home screen — activation banner heading while not yet activated.
  ///
  /// In en, this message translates to:
  /// **'Activation Pending'**
  String get chActivationPending;

  /// Client home screen — activation banner body text.
  ///
  /// In en, this message translates to:
  /// **'Once your agent has taken your activation fee in cash, confirm it here with your PIN.'**
  String get chConfirmOncePaidMessage;

  /// Client home screen — button to confirm the activation fee was paid.
  ///
  /// In en, this message translates to:
  /// **'Confirm Activation Payment'**
  String get chConfirmActivationPaymentButton;

  /// Client home screen — heading of the recently-collected card.
  ///
  /// In en, this message translates to:
  /// **'Just Collected'**
  String get chJustCollected;

  /// Client home screen — recently-collected card explanatory text.
  ///
  /// In en, this message translates to:
  /// **'Recorded by MICROFI — reflected in your official balance at end of day.'**
  String get chRecordedReflectedMessage;

  /// Client home screen — button opening the receipt QR scanner.
  ///
  /// In en, this message translates to:
  /// **'Scan Receipt from Agent'**
  String get chScanReceiptFromAgent;

  /// Client home screen — dialog title explaining how to add funds.
  ///
  /// In en, this message translates to:
  /// **'How to Top Up'**
  String get chHowToTopUpTitle;

  /// Client home screen — dialog body explaining how to add funds.
  ///
  /// In en, this message translates to:
  /// **'Hand cash to a field agent visiting you, or visit your branch directly. Every deposit appears here once recorded.'**
  String get chHowToTopUpMessage;

  /// Client home screen — button opening the top-up info dialog.
  ///
  /// In en, this message translates to:
  /// **'Top-up Info'**
  String get chTopUpInfo;

  /// Client home screen — section header above the recent contributions list.
  ///
  /// In en, this message translates to:
  /// **'Recent Contributions'**
  String get chRecentContributions;

  /// Client home screen — link to view the full contribution history.
  ///
  /// In en, this message translates to:
  /// **'View All'**
  String get chViewAll;

  /// Client home screen — empty state for the recent contributions list.
  ///
  /// In en, this message translates to:
  /// **'No contributions recorded yet.'**
  String get chNoContributionsRecorded;

  /// Client home screen — token status pill when active.
  ///
  /// In en, this message translates to:
  /// **'ACTIVE'**
  String get chStatusActive;

  /// Client home screen — token status pill when expired.
  ///
  /// In en, this message translates to:
  /// **'EXPIRED'**
  String get chStatusExpired;

  /// Client home screen — token status pill when never activated.
  ///
  /// In en, this message translates to:
  /// **'NOT ACTIVATED'**
  String get chStatusNotActivated;

  /// Receipt PDF — tagline printed under the MICROFI wordmark.
  ///
  /// In en, this message translates to:
  /// **'DIGITAL CASH COLLECTION · CEMAC'**
  String get rpTagline;

  /// Receipt PDF — branch field label.
  ///
  /// In en, this message translates to:
  /// **'Branch'**
  String get rpBranchLabel;

  /// Receipt PDF — date field label.
  ///
  /// In en, this message translates to:
  /// **'Date'**
  String get rpDateLabel;

  /// Receipt PDF — agent field label.
  ///
  /// In en, this message translates to:
  /// **'Agent'**
  String get rpAgentLabel;

  /// Receipt PDF — client member number field label.
  ///
  /// In en, this message translates to:
  /// **'CLIENT ID'**
  String get rpClientIdLabel;

  /// Receipt PDF — client name field label.
  ///
  /// In en, this message translates to:
  /// **'CLIENT NAME'**
  String get rpClientNameLabel;

  /// Receipt PDF — amount block heading.
  ///
  /// In en, this message translates to:
  /// **'AMOUNT COLLECTED'**
  String get rpAmountCollectedLabel;

  /// Receipt PDF — small pill labeling the payment method.
  ///
  /// In en, this message translates to:
  /// **'CASH'**
  String get rpCashPill;

  /// Receipt PDF — small pill confirming the collection was recorded.
  ///
  /// In en, this message translates to:
  /// **'✓ RECORDED'**
  String get rpRecordedPill;

  /// Receipt PDF — denomination table heading.
  ///
  /// In en, this message translates to:
  /// **'DENOMINATION BREAKDOWN (XAF)'**
  String get rpDenominationBreakdownLabel;

  /// Receipt PDF — unique transaction reference label above the QR code.
  ///
  /// In en, this message translates to:
  /// **'UNIQUE REF'**
  String get rpUniqueRefLabel;

  /// Receipt PDF — legal/compliance footnote.
  ///
  /// In en, this message translates to:
  /// **'This electronic receipt is proof of deposit even in the event of a temporary network outage, per FR-09 / BR-Notif-01.'**
  String get rpElectronicReceiptProofNote;

  /// Receipt PDF — closing line.
  ///
  /// In en, this message translates to:
  /// **'THANK YOU FOR YOUR TRUST'**
  String get rpThankYouTrust;

  /// Sponsor activation screen — AppBar title.
  ///
  /// In en, this message translates to:
  /// **'Sponsor Activation'**
  String get saTitle;

  /// Sponsor activation screen — confirmation dialog title.
  ///
  /// In en, this message translates to:
  /// **'Sponsor Activation?'**
  String get saSponsorActivationQuestion;

  /// Sponsor activation screen — confirmation dialog body.
  ///
  /// In en, this message translates to:
  /// **'Confirm you have received {name}\'s activation fee in cash.'**
  String saConfirmReceivedFee(String name);

  /// Sponsor activation screen — success dialog body when both sides are confirmed.
  ///
  /// In en, this message translates to:
  /// **'{name} is now fully activated — both sponsorship and payment are confirmed.'**
  String saClientFullyActivated(String name);

  /// Sponsor activation screen — success dialog body when only sponsorship is confirmed so far.
  ///
  /// In en, this message translates to:
  /// **'Sponsorship recorded for {name}. Still waiting on their own payment confirmation.'**
  String saSponsorshipRecorded(String name);

  /// Sponsor activation screen — success dialog title when both sides are confirmed.
  ///
  /// In en, this message translates to:
  /// **'Client Activated'**
  String get saClientActivatedTitle;

  /// Sponsor activation screen — success dialog title when only sponsorship is confirmed.
  ///
  /// In en, this message translates to:
  /// **'Sponsorship Recorded'**
  String get saSponsorshipRecordedTitle;

  /// Sponsor activation screen — error dialog title.
  ///
  /// In en, this message translates to:
  /// **'Sponsorship Failed'**
  String get saSponsorshipFailedTitle;

  /// Sponsor activation screen — empty state with no query typed.
  ///
  /// In en, this message translates to:
  /// **'No clients are currently awaiting activation.'**
  String get saNoClientsAwaiting;

  /// Sponsor activation screen — chip label on a client already sponsored, awaiting their own payment confirmation.
  ///
  /// In en, this message translates to:
  /// **'Awaiting payment'**
  String get saAwaitingPayment;

  /// Sponsor activation screen — button to sponsor a client's activation.
  ///
  /// In en, this message translates to:
  /// **'Sponsor'**
  String get saSponsorButton;

  /// Wallet screen — fallback error text when the escrow fetch fails with no specific message.
  ///
  /// In en, this message translates to:
  /// **'Wallet unavailable.'**
  String get wsWalletUnavailable;

  /// Wallet screen — page heading.
  ///
  /// In en, this message translates to:
  /// **'Wallet'**
  String get wsWalletTitle;

  /// Wallet screen — balance card label.
  ///
  /// In en, this message translates to:
  /// **'Escrow Wallet Balance'**
  String get wsEscrowWalletBalance;

  /// Wallet screen — base ceiling readout under the balance progress bar.
  ///
  /// In en, this message translates to:
  /// **'Base ceiling: {amount} XAF'**
  String wsBaseCeiling(String amount);

  /// Wallet screen — info card heading.
  ///
  /// In en, this message translates to:
  /// **'Effective Ceiling'**
  String get wsEffectiveCeilingTitle;

  /// Wallet screen — info row label.
  ///
  /// In en, this message translates to:
  /// **'Effective ceiling (today)'**
  String get wsEffectiveCeilingToday;

  /// Wallet screen — info row label (sentence case, distinct from the Home screen's all-caps card header).
  ///
  /// In en, this message translates to:
  /// **'Today\'s collections'**
  String get wsTodaysCollectionsLabel;

  /// Wallet screen — info row label.
  ///
  /// In en, this message translates to:
  /// **'Remaining today'**
  String get wsRemainingToday;

  /// Wallet screen — heading of the active-ceiling-waiver notice.
  ///
  /// In en, this message translates to:
  /// **'Active waiver'**
  String get wsActiveWaiver;

  /// Wallet screen — footnote explaining the screen is read-only.
  ///
  /// In en, this message translates to:
  /// **'Wallet top-ups are administered by a branch cashier — this screen is read-only.'**
  String get wsTopUpAdminNote;

  /// History screen — summary card label.
  ///
  /// In en, this message translates to:
  /// **'Total Collected This Month'**
  String get histTotalCollectedThisMonth;

  /// History screen — collection row subtitle (date, time, payment method).
  ///
  /// In en, this message translates to:
  /// **'{date} • {time} • Cash'**
  String histDateTimeCashLine(String date, String time);

  /// Client receipt view screen — AppBar title.
  ///
  /// In en, this message translates to:
  /// **'Your Receipt'**
  String get crvYourReceiptTitle;

  /// Client receipt view screen — caption under the amount confirming signature verification.
  ///
  /// In en, this message translates to:
  /// **'Verified deposit receipt'**
  String get crvVerifiedDepositReceipt;

  /// Client receipt view screen — row label for the client's own name.
  ///
  /// In en, this message translates to:
  /// **'Client'**
  String get crvClientLabel;

  /// Client receipt view screen — row label for the client's member number.
  ///
  /// In en, this message translates to:
  /// **'Member No.'**
  String get crvMemberNoLabel;

  /// Client receipt view screen — row label for the transaction reference.
  ///
  /// In en, this message translates to:
  /// **'Reference'**
  String get crvReferenceLabel;

  /// Client receipt view screen — one denomination line (face value formatted with thousands separators × count).
  ///
  /// In en, this message translates to:
  /// **'{denom} XAF × {count}'**
  String crvDenominationLine(String denom, int count);

  /// Generic form-field validator message for a required field left empty.
  ///
  /// In en, this message translates to:
  /// **'Required'**
  String get commonRequiredField;

  /// Client activation screen — error dialog title.
  ///
  /// In en, this message translates to:
  /// **'Activation Failed'**
  String get caActivationFailedTitle;

  /// Client activation screen — AppBar title.
  ///
  /// In en, this message translates to:
  /// **'Activate My Booklet'**
  String get caActivateMyBookletTitle;

  /// Client activation screen — success heading after setting login/PIN.
  ///
  /// In en, this message translates to:
  /// **'Credentials Set'**
  String get caCredentialsSetTitle;

  /// Client activation screen — button returning to the login screen after success.
  ///
  /// In en, this message translates to:
  /// **'Back to Login'**
  String get caBackToLogin;

  /// Client activation screen — explanatory intro text above the form.
  ///
  /// In en, this message translates to:
  /// **'Enter the Activation ID your branch gave you and choose a login and PIN. Afterwards, ask your agent to sponsor your activation, then confirm the payment yourself to receive your booklet.'**
  String get caIntroMessage;

  /// Client activation screen — activation ID field label.
  ///
  /// In en, this message translates to:
  /// **'Activation ID'**
  String get caActivationIdLabel;

  /// Client activation screen — login field label.
  ///
  /// In en, this message translates to:
  /// **'Choose a Login'**
  String get caChooseLoginLabel;

  /// Client activation screen — PIN field label.
  ///
  /// In en, this message translates to:
  /// **'Choose a PIN'**
  String get caChoosePinLabel;

  /// Client activation screen — PIN field validator error when not 4-6 digits.
  ///
  /// In en, this message translates to:
  /// **'4–6 digits'**
  String get caPinDigitsError;

  /// Client activation screen — submit button.
  ///
  /// In en, this message translates to:
  /// **'Set My Credentials'**
  String get caSetCredentialsButton;

  /// Client history screen — summary card label.
  ///
  /// In en, this message translates to:
  /// **'Total Contributions This Month'**
  String get chTotalContributionsThisMonth;

  /// Client history screen — contribution row subtitle (date, time, transaction reference).
  ///
  /// In en, this message translates to:
  /// **'{date}, {time} • {reference}'**
  String chDateTimeReferenceLine(String date, String time, String reference);

  /// Client wallet screen — page heading.
  ///
  /// In en, this message translates to:
  /// **'My Account'**
  String get cwMyAccountTitle;

  /// Client wallet screen — card heading.
  ///
  /// In en, this message translates to:
  /// **'Booklet Token'**
  String get cwBookletTokenTitle;

  /// Client wallet screen — token status row label.
  ///
  /// In en, this message translates to:
  /// **'Status'**
  String get cwStatusLabel;

  /// Client wallet screen — token expiry row label.
  ///
  /// In en, this message translates to:
  /// **'Expires'**
  String get cwExpiresLabel;

  /// Client wallet screen — disabled withdrawal button label.
  ///
  /// In en, this message translates to:
  /// **'Request Withdrawal — Coming Soon'**
  String get cwRequestWithdrawalComingSoon;

  /// Client wallet screen — footnote under the disabled withdrawal button.
  ///
  /// In en, this message translates to:
  /// **'Withdrawal requests aren\'t available in the app yet — visit your branch to withdraw funds.'**
  String get cwWithdrawalNotAvailableNote;

  /// Client login screen — AppBar title.
  ///
  /// In en, this message translates to:
  /// **'Client Login'**
  String get clClientLoginTitle;

  /// Client login screen — heading.
  ///
  /// In en, this message translates to:
  /// **'My Booklet'**
  String get clMyBooklet;

  /// Client login screen — subtitle under the heading.
  ///
  /// In en, this message translates to:
  /// **'Your digital savings booklet'**
  String get clDigitalSavingsBooklet;

  /// Client login screen — login field label.
  ///
  /// In en, this message translates to:
  /// **'Login'**
  String get clLoginLabel;

  /// Client login screen — PIN field label.
  ///
  /// In en, this message translates to:
  /// **'PIN'**
  String get clPinLabel;

  /// Client login screen — PIN field validator error when too short.
  ///
  /// In en, this message translates to:
  /// **'Min. 4 digits'**
  String get clPinMinDigitsError;

  /// Client login screen — submit button.
  ///
  /// In en, this message translates to:
  /// **'Sign In'**
  String get clSignInButton;

  /// Client login screen — link to the activation screen.
  ///
  /// In en, this message translates to:
  /// **'First time? Activate my booklet'**
  String get clFirstTimeActivate;

  /// PIN setup screen — validation error when the new PIN and confirmation differ.
  ///
  /// In en, this message translates to:
  /// **'New PIN and confirmation do not match.'**
  String get psPinMismatchError;

  /// PIN setup screen — success dialog message for a voluntary PIN change.
  ///
  /// In en, this message translates to:
  /// **'Your PIN has been updated.'**
  String get psPinUpdatedMessage;

  /// PIN setup screen — AppBar title for a voluntary PIN change.
  ///
  /// In en, this message translates to:
  /// **'Change PIN'**
  String get psChangePinTitle;

  /// PIN setup screen — heading for the mandatory first-time PIN change.
  ///
  /// In en, this message translates to:
  /// **'Set Your Transaction PIN'**
  String get psSetYourPinTitle;

  /// PIN setup screen — explanatory text for the mandatory first-time PIN change.
  ///
  /// In en, this message translates to:
  /// **'Your branch assigned a starting PIN. Replace it with one only you know before you can record a collection.'**
  String get psSetYourPinIntro;

  /// PIN setup screen — current-PIN field label when mandatory (first-time change).
  ///
  /// In en, this message translates to:
  /// **'Starting PIN (given by your branch)'**
  String get psStartingPinLabel;

  /// PIN setup screen — current-PIN field label for a voluntary change.
  ///
  /// In en, this message translates to:
  /// **'Current PIN'**
  String get psCurrentPinLabel;

  /// PIN setup screen — new-PIN field label.
  ///
  /// In en, this message translates to:
  /// **'New PIN'**
  String get psNewPinLabel;

  /// PIN setup screen — helper text under the new-PIN field.
  ///
  /// In en, this message translates to:
  /// **'Not all the same digit or a simple run (e.g. 1234)'**
  String get psNewPinHelperText;

  /// PIN setup screen — new-PIN field validator error.
  ///
  /// In en, this message translates to:
  /// **'4–10 digits'**
  String get psPinLengthError;

  /// PIN setup screen — confirm-PIN field label.
  ///
  /// In en, this message translates to:
  /// **'Confirm New PIN'**
  String get psConfirmNewPinLabel;

  /// PIN setup screen — submit button.
  ///
  /// In en, this message translates to:
  /// **'Set PIN'**
  String get psSetPinButton;

  /// Login screen — heading.
  ///
  /// In en, this message translates to:
  /// **'Microfi Agent'**
  String get lgMicrofiAgent;

  /// Login screen — subtitle under the heading.
  ///
  /// In en, this message translates to:
  /// **'Field collection & cash desk'**
  String get lgFieldCollectionCashDesk;

  /// Login screen — username field label.
  ///
  /// In en, this message translates to:
  /// **'Username'**
  String get lgUsernameLabel;

  /// Login screen — password field label.
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get lgPasswordLabel;

  /// Login screen — footnote under the form.
  ///
  /// In en, this message translates to:
  /// **'Secure field-agent access only.'**
  String get lgSecureAccessOnly;

  /// Session entry screen — generic (non-LocationUnavailable) location-check failure.
  ///
  /// In en, this message translates to:
  /// **'Unable to check location.'**
  String get seUnableToCheckLocation;

  /// Session entry screen — profile load failure, with the technical error appended.
  ///
  /// In en, this message translates to:
  /// **'Unable to load your profile: {error}'**
  String seUnableToLoadProfile(String error);

  /// Session entry screen — heading of the location-error screen.
  ///
  /// In en, this message translates to:
  /// **'Location Required'**
  String get seLocationRequiredTitle;

  /// Session entry screen — button opening device location settings.
  ///
  /// In en, this message translates to:
  /// **'Open Settings'**
  String get seOpenSettings;

  /// Session entry screen — button on the profile-load-error screen.
  ///
  /// In en, this message translates to:
  /// **'Sign In Again'**
  String get seSignInAgain;

  /// Profile screen — AppBar title.
  ///
  /// In en, this message translates to:
  /// **'My Profile'**
  String get prMyProfileTitle;

  /// Profile screen — row label.
  ///
  /// In en, this message translates to:
  /// **'Employee Code'**
  String get prEmployeeCodeLabel;

  /// Profile screen — row label.
  ///
  /// In en, this message translates to:
  /// **'Email'**
  String get prEmailLabel;

  /// Profile screen — row label.
  ///
  /// In en, this message translates to:
  /// **'Phone'**
  String get prPhoneLabel;

  /// Profile screen — row label.
  ///
  /// In en, this message translates to:
  /// **'Device Binding'**
  String get prDeviceBindingLabel;

  /// Profile screen — device binding row value when the account is bound to a device.
  ///
  /// In en, this message translates to:
  /// **'Bound'**
  String get prBound;

  /// Profile screen — device binding row value when not bound.
  ///
  /// In en, this message translates to:
  /// **'Not bound (own device)'**
  String get prNotBoundOwnDevice;

  /// Profile screen — button opening the PIN change screen.
  ///
  /// In en, this message translates to:
  /// **'Change Transaction PIN'**
  String get prChangeTransactionPin;

  /// Profile screen — footnote explaining most fields are admin-controlled.
  ///
  /// In en, this message translates to:
  /// **'To change your username, password, or other details, contact your branch back-office.'**
  String get prContactBackOfficeNote;

  /// Route screen — AppBar title.
  ///
  /// In en, this message translates to:
  /// **'My Route — Today'**
  String get rtMyRouteTodayTitle;

  /// Route screen — empty state.
  ///
  /// In en, this message translates to:
  /// **'No GPS pings or collections recorded today.'**
  String get rtNoGpsOrCollections;

  /// Route screen — timeline entry label for a collection event.
  ///
  /// In en, this message translates to:
  /// **'Collection — {amount} XAF'**
  String rtCollectionLine(String amount);

  /// Route screen — timeline entry label for a plain GPS ping (no collection).
  ///
  /// In en, this message translates to:
  /// **'GPS ping'**
  String get rtGpsPing;

  /// Route screen — timeline entry subtitle (time and coordinates).
  ///
  /// In en, this message translates to:
  /// **'{time} • {lat}, {lon}'**
  String rtTimeLatLonLine(String time, String lat, String lon);

  /// App shell — header title (product name, kept as-is across locales).
  ///
  /// In en, this message translates to:
  /// **'MICROFI COLLECT'**
  String get asAppTitle;

  /// App shell — bottom nav tab label.
  ///
  /// In en, this message translates to:
  /// **'Home'**
  String get asHomeTab;

  /// App shell — bottom nav tab label.
  ///
  /// In en, this message translates to:
  /// **'History'**
  String get asHistoryTab;

  /// App shell — connectivity icon tooltip when offline.
  ///
  /// In en, this message translates to:
  /// **'Offline'**
  String get asOfflineTooltip;

  /// Contact-branch action — SnackBar when the branch has no phone number recorded.
  ///
  /// In en, this message translates to:
  /// **'No phone number on file for {branchName}.'**
  String cbNoPhoneOnFile(String branchName);

  /// Contact-branch action — SnackBar when the phone dialer can't be launched.
  ///
  /// In en, this message translates to:
  /// **'Unable to open the phone dialer.'**
  String get cbUnableToOpenDialer;

  /// Receipt QR screen — AppBar title.
  ///
  /// In en, this message translates to:
  /// **'Show to Client'**
  String get rqShowToClientTitle;

  /// Receipt QR screen — client name and amount heading.
  ///
  /// In en, this message translates to:
  /// **'{name} — {amount} XAF'**
  String rqClientAmountLine(String name, int amount);

  /// Receipt QR screen — instructions under the heading.
  ///
  /// In en, this message translates to:
  /// **'Have the client scan this from their own app to get their receipt.'**
  String get rqScanInstructions;

  /// Receipt QR screen — reference code under the QR image.
  ///
  /// In en, this message translates to:
  /// **'Ref: {ref}'**
  String rqRefLine(String ref);

  /// Client shell — header title.
  ///
  /// In en, this message translates to:
  /// **'MY BOOKLET'**
  String get cshMyBookletTitle;

  /// Client receipt scan screen — AppBar title.
  ///
  /// In en, this message translates to:
  /// **'Scan Your Receipt'**
  String get crsScanYourReceiptTitle;

  /// Client receipt scan screen — instructions overlay.
  ///
  /// In en, this message translates to:
  /// **'Point your camera at the QR your agent is showing you'**
  String get crsPointCameraInstructions;

  /// Client receipt scan screen — generic (non-QrReceiptVerificationFailed) scan failure.
  ///
  /// In en, this message translates to:
  /// **'Could not read this QR code.'**
  String get crsCouldNotReadQr;

  /// Client receipt scan screen — retry button after a failed scan.
  ///
  /// In en, this message translates to:
  /// **'Try Again'**
  String get crsTryAgain;

  /// Android foreground-service notification title shown while background location tracking is active.
  ///
  /// In en, this message translates to:
  /// **'MICROFI Collect — Tracking active'**
  String get ltsNotificationTitle;

  /// Android foreground-service notification body shown while background location tracking is active.
  ///
  /// In en, this message translates to:
  /// **'Sharing your position with your branch while your session is open.'**
  String get ltsNotificationText;

  /// Android notification channel name for the location-tracking foreground service, visible in the device's notification settings.
  ///
  /// In en, this message translates to:
  /// **'Field Tracking'**
  String get ltsNotificationChannelName;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'fr'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'fr':
      return AppLocalizationsFr();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
