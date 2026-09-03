// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appName => 'Microfi';

  @override
  String get roleSelectPrompt => 'Who\'s signing in?';

  @override
  String get roleFieldAgentTitle => 'Field Agent';

  @override
  String get roleFieldAgentSubtitle =>
      'Collect cash, manage clients and escrow';

  @override
  String get roleClientTitle => 'Client';

  @override
  String get roleClientSubtitle => 'View my digital savings booklet';

  @override
  String get languagePickerTitle => 'Language';

  @override
  String get languageEnglish => 'English';

  @override
  String get languageFrench => 'Français';

  @override
  String get commonOk => 'OK';

  @override
  String get commonCancel => 'Cancel';

  @override
  String get commonConfirm => 'Confirm';

  @override
  String get commonRetry => 'Retry';

  @override
  String get commonSave => 'Save';

  @override
  String get commonClose => 'Close';

  @override
  String get commonContactBranch => 'Contact Branch';

  @override
  String get commonDone => 'Done';

  @override
  String get commonSignOut => 'Sign Out';

  @override
  String get errorIncorrectCredentials => 'Incorrect login credentials.';

  @override
  String get errorRequestTimeout =>
      'The request took too long. Please try again.';

  @override
  String get errorUnableToReachServer =>
      'Unable to reach the server. Check your connection.';

  @override
  String get errorSomethingWentWrong =>
      'Something went wrong. Please try again.';

  @override
  String get errorDialogTitle => 'Something Went Wrong';

  @override
  String get dialogEnterPinTitle => 'Enter Your PIN';

  @override
  String get escrowCeilingReachedTitle => 'Escrow Ceiling Reached';

  @override
  String get offlineModeTitle => 'Offline Mode';

  @override
  String offlineCollectionsPendingSync(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count collections pending sync',
      one: '1 collection pending sync',
    );
    return '$_temp0';
  }

  @override
  String get syncNowButton => 'Sync Now';

  @override
  String get locationErrorServicesDisabled =>
      'Location services are turned off on this device.';

  @override
  String get locationErrorPermissionDenied =>
      'Location permission was denied. Enable it to record a collection.';

  @override
  String get printerErrorNoPrinterSelected =>
      'No thermal printer selected yet.';

  @override
  String get printerErrorBluetoothUnavailable =>
      'Turn on Bluetooth and grant permission to print.';

  @override
  String printerErrorConnectionFailed(String printerName) {
    return 'Could not connect to $printerName.';
  }

  @override
  String get printerErrorDidNotConfirm => 'Printer did not confirm the print.';

  @override
  String get qrErrorNotMicrofiReceipt =>
      'This QR code isn\'t a MICROFI receipt.';

  @override
  String get qrErrorMissingData =>
      'This QR code is missing expected receipt data.';

  @override
  String get qrErrorTampered =>
      'This QR code has been altered or isn\'t a genuine MICROFI receipt.';

  @override
  String get errorUnableToReachServerShort => 'Unable to reach the server.';

  @override
  String get commonTotal => 'Total';

  @override
  String amountXaf(String amount) {
    return '$amount XAF';
  }

  @override
  String get csSearchHint => 'Name, phone, or member number';

  @override
  String get csNoClientsRegistered => 'No clients registered yet.';

  @override
  String get csNoClientsFound => 'No clients found for that search.';

  @override
  String csClientSubtitle(String memberNo, String phone) {
    return '$memberNo • $phone';
  }

  @override
  String get csCollectCashTitle => 'Collect Cash';

  @override
  String get csCapturingGps => 'Capturing GPS location…';

  @override
  String csLocationCaptured(int accuracy) {
    return 'Location captured (±${accuracy}m)';
  }

  @override
  String get csLocationUnavailable => 'Location unavailable';

  @override
  String get csDenominationBreakdown => 'Denomination Breakdown';

  @override
  String get csCalculatedTotal => 'CALCULATED TOTAL';

  @override
  String csEscrowExceedMessage(String projected, String ceiling, String over) {
    return 'This collection would push you to $projected / $ceiling XAF — $over XAF over your daily ceiling.';
  }

  @override
  String get csVerifyAndContinue => 'Verify & Continue';

  @override
  String get csConfirmCollectionTitle => 'Confirm Collection';

  @override
  String csDenominationLine(int denom, int count) {
    return '$denom XAF × $count';
  }

  @override
  String csLocationLine(String lat, String lon, int accuracy) {
    return 'Location: $lat, $lon (±${accuracy}m)';
  }

  @override
  String get csEnterPinToConfirm => 'Enter Your PIN to Confirm';

  @override
  String get csRecordCollection => 'Record Collection';

  @override
  String get csAlreadyRecorded => 'Already Recorded';

  @override
  String get csCollectionValidated => 'Collection Validated';

  @override
  String csTransactionIdWithLocation(String id, String location) {
    return 'Transaction ID: $id • $location';
  }

  @override
  String csTransactionId(String id) {
    return 'Transaction ID: $id';
  }

  @override
  String get csAmountCollected => 'Amount Collected';

  @override
  String get csReceiptPrinted => 'Receipt Printed';

  @override
  String get csPrinting => 'Printing…';

  @override
  String get csPrintReceipt => 'Print Receipt';

  @override
  String get csReceiptDownloaded => 'Receipt Downloaded';

  @override
  String get csDownloading => 'Downloading…';

  @override
  String get csDownloadReceipt => 'Download Receipt';

  @override
  String get csShowQrForClient => 'Show QR for Client';

  @override
  String get csSavedOffline => 'Saved Offline';

  @override
  String csQueuedOfflineMessage(String amount, String clientName) {
    return '$amount XAF for $clientName is queued. It will sync automatically once you\'re back online.';
  }

  @override
  String get csSelectThermalPrinter => 'Select Thermal Printer';

  @override
  String csStepLabel(int number) {
    return 'Step $number';
  }

  @override
  String get csTargetClientLabel => 'TARGET CLIENT';

  @override
  String get csDailyCeilingImpact => 'Daily Ceiling Impact';

  @override
  String get csPinRequiredError => 'Enter your PIN to confirm this collection.';

  @override
  String csTooManyPinAttempts(int minutes) {
    return 'Too many incorrect PIN attempts. Try again in $minutes min, or connect to the internet.';
  }

  @override
  String get csIncorrectPin => 'Incorrect PIN.';

  @override
  String csCeilingExceedOfflineMessage(String projected, String ceiling) {
    return 'This would push you to $projected / $ceiling XAF, over your daily ceiling (based on your last connection — reconnect for a fresh check, or contact your branch).';
  }

  @override
  String get csCouldNotPrintReceipt => 'Could not print the receipt.';

  @override
  String csReceiptSavedCouldNotOpen(String error) {
    return 'Receipt saved, but could not open it: $error';
  }

  @override
  String get csCouldNotDownloadReceipt => 'Could not download the receipt.';

  @override
  String get csBluetoothPermissionNeeded =>
      'Turn on Bluetooth and grant permission, then try again.';

  @override
  String get csNoPairedPrinterFound =>
      'No paired thermal printer found — pair one in your phone\'s Bluetooth settings first.';

  @override
  String csCeilingProgressLine(int projected, int ceiling) {
    return '$projected / $ceiling XAF';
  }

  @override
  String get csGenericClient => 'client';

  @override
  String get hsCollectionHistoryTitle => 'Collection History';

  @override
  String get hsQuickActionHistory => 'History';

  @override
  String get hsSosSentMessage => 'SOS sent — your branch has been alerted.';

  @override
  String get hsSosSendFailedMessage =>
      'Unable to send SOS — check your connection and try again.';

  @override
  String get hsSosAcknowledgedMessage =>
      'Your SOS was acknowledged by your branch.';

  @override
  String get hsNewCollection => 'New Collection';

  @override
  String get hsMyRoute => 'My Route';

  @override
  String get hsSponsorClientActivation => 'Sponsor Client Activation';

  @override
  String get hsRecentCollections => 'Recent Collections';

  @override
  String get hsSeeAll => 'See all';

  @override
  String get hsNoCollectionsRecorded => 'No collections recorded yet.';

  @override
  String get hsTodaysCollections => 'TODAY\'S COLLECTIONS';

  @override
  String get hsCeilingLabel => 'Ceiling';

  @override
  String hsCapacityReachedDepositSoon(int percent) {
    return '$percent% capacity reached. Deposit soon.';
  }

  @override
  String hsCapacityReachedSafe(int percent) {
    return '$percent% capacity reached. Safe to continue.';
  }

  @override
  String get hsUnknownClient => 'Unknown client';

  @override
  String hsTimeCashLine(String time) {
    return '$time • Cash';
  }

  @override
  String hsAmountCollectedPlus(String amount) {
    return '+$amount XAF';
  }

  @override
  String get hsStatusActive => 'Online';

  @override
  String get hsStatusSuspended => 'Suspended';

  @override
  String get hsSosPendingBanner =>
      'SOS pending — awaiting acknowledgement from your branch.';

  @override
  String get chEnterPinConfirmPayment =>
      'Enter your PIN to confirm you paid the activation fee to your agent.';

  @override
  String get chBookletActivatedTitle => 'Booklet Activated';

  @override
  String get chPaymentConfirmedTitle => 'Payment Confirmed';

  @override
  String get chBookletActiveMessage =>
      'Your booklet is now active — both your payment and your agent\'s sponsorship are confirmed.';

  @override
  String get chPaymentConfirmedWaitingMessage =>
      'Payment confirmed on your side. Waiting on your agent to sponsor your activation.';

  @override
  String get chCouldNotConfirmPaymentTitle => 'Could Not Confirm Payment';

  @override
  String get chContributionHistoryTitle => 'Contribution History';

  @override
  String get chRenewalNeeded => 'Renewal Needed';

  @override
  String get chActivationPending => 'Activation Pending';

  @override
  String get chConfirmOncePaidMessage =>
      'Once your agent has taken your activation fee in cash, confirm it here with your PIN.';

  @override
  String get chConfirmActivationPaymentButton => 'Confirm Activation Payment';

  @override
  String get chJustCollected => 'Just Collected';

  @override
  String get chRecordedReflectedMessage =>
      'Recorded by MICROFI — reflected in your official balance at end of day.';

  @override
  String get chScanReceiptFromAgent => 'Scan Receipt from Agent';

  @override
  String get chHowToTopUpTitle => 'How to Top Up';

  @override
  String get chHowToTopUpMessage =>
      'Hand cash to a field agent visiting you, or visit your branch directly. Every deposit appears here once recorded.';

  @override
  String get chTopUpInfo => 'Top-up Info';

  @override
  String get chRecentContributions => 'Recent Contributions';

  @override
  String get chViewAll => 'View All';

  @override
  String get chNoContributionsRecorded => 'No contributions recorded yet.';

  @override
  String get chStatusActive => 'ACTIVE';

  @override
  String get chStatusExpired => 'EXPIRED';

  @override
  String get chStatusNotActivated => 'NOT ACTIVATED';

  @override
  String get rpTagline => 'DIGITAL CASH COLLECTION · CEMAC';

  @override
  String get rpBranchLabel => 'Branch';

  @override
  String get rpDateLabel => 'Date';

  @override
  String get rpAgentLabel => 'Agent';

  @override
  String get rpClientIdLabel => 'CLIENT ID';

  @override
  String get rpClientNameLabel => 'CLIENT NAME';

  @override
  String get rpAmountCollectedLabel => 'AMOUNT COLLECTED';

  @override
  String get rpCashPill => 'CASH';

  @override
  String get rpRecordedPill => '✓ RECORDED';

  @override
  String get rpDenominationBreakdownLabel => 'DENOMINATION BREAKDOWN (XAF)';

  @override
  String get rpUniqueRefLabel => 'UNIQUE REF';

  @override
  String get rpElectronicReceiptProofNote =>
      'This electronic receipt is proof of deposit even in the event of a temporary network outage, per FR-09 / BR-Notif-01.';

  @override
  String get rpThankYouTrust => 'THANK YOU FOR YOUR TRUST';

  @override
  String get saTitle => 'Sponsor Activation';

  @override
  String get saSponsorActivationQuestion => 'Sponsor Activation?';

  @override
  String saConfirmReceivedFee(String name) {
    return 'Confirm you have received $name\'s activation fee in cash.';
  }

  @override
  String saClientFullyActivated(String name) {
    return '$name is now fully activated — both sponsorship and payment are confirmed.';
  }

  @override
  String saSponsorshipRecorded(String name) {
    return 'Sponsorship recorded for $name. Still waiting on their own payment confirmation.';
  }

  @override
  String get saClientActivatedTitle => 'Client Activated';

  @override
  String get saSponsorshipRecordedTitle => 'Sponsorship Recorded';

  @override
  String get saSponsorshipFailedTitle => 'Sponsorship Failed';

  @override
  String get saNoClientsAwaiting =>
      'No clients are currently awaiting activation.';

  @override
  String get saAwaitingPayment => 'Awaiting payment';

  @override
  String get saSponsorButton => 'Sponsor';

  @override
  String get wsWalletUnavailable => 'Wallet unavailable.';

  @override
  String get wsWalletTitle => 'Wallet';

  @override
  String get wsEscrowWalletBalance => 'Escrow Wallet Balance';

  @override
  String wsBaseCeiling(String amount) {
    return 'Base ceiling: $amount XAF';
  }

  @override
  String get wsEffectiveCeilingTitle => 'Effective Ceiling';

  @override
  String get wsEffectiveCeilingToday => 'Effective ceiling (today)';

  @override
  String get wsTodaysCollectionsLabel => 'Today\'s collections';

  @override
  String get wsRemainingToday => 'Remaining today';

  @override
  String get wsActiveWaiver => 'Active waiver';

  @override
  String get wsTopUpAdminNote =>
      'Wallet top-ups are administered by a branch cashier — this screen is read-only.';

  @override
  String get histTotalCollectedThisMonth => 'Total Collected This Month';

  @override
  String histDateTimeCashLine(String date, String time) {
    return '$date • $time • Cash';
  }

  @override
  String get crvYourReceiptTitle => 'Your Receipt';

  @override
  String get crvVerifiedDepositReceipt => 'Verified deposit receipt';

  @override
  String get crvClientLabel => 'Client';

  @override
  String get crvMemberNoLabel => 'Member No.';

  @override
  String get crvReferenceLabel => 'Reference';

  @override
  String crvDenominationLine(String denom, int count) {
    return '$denom XAF × $count';
  }

  @override
  String get commonRequiredField => 'Required';

  @override
  String get caActivationFailedTitle => 'Activation Failed';

  @override
  String get caActivateMyBookletTitle => 'Activate My Booklet';

  @override
  String get caCredentialsSetTitle => 'Credentials Set';

  @override
  String get caBackToLogin => 'Back to Login';

  @override
  String get caIntroMessage =>
      'Enter your MFI account number (the same number your branch already gave you) and choose a login and PIN. Afterwards, ask your agent to sponsor your activation, then confirm the payment yourself to receive your booklet.';

  @override
  String get caMfiIdentifierLabel => 'Your MFI Account Number';

  @override
  String caRegisteredWithMfi(String mfiName) {
    return 'You\'re now registered with $mfiName.';
  }

  @override
  String get caChooseLoginLabel => 'Choose a Login';

  @override
  String get caChoosePinLabel => 'Choose a PIN';

  @override
  String get caPinDigitsError => '4–6 digits';

  @override
  String get caSetCredentialsButton => 'Set My Credentials';

  @override
  String get chTotalContributionsThisMonth => 'Total Contributions This Month';

  @override
  String chDateTimeReferenceLine(String date, String time, String reference) {
    return '$date, $time • $reference';
  }

  @override
  String get cwMyAccountTitle => 'My Account';

  @override
  String get cwBookletTokenTitle => 'Booklet Token';

  @override
  String get cwStatusLabel => 'Status';

  @override
  String get cwExpiresLabel => 'Expires';

  @override
  String get cwRequestWithdrawalComingSoon =>
      'Request Withdrawal — Coming Soon';

  @override
  String get cwWithdrawalNotAvailableNote =>
      'Withdrawal requests aren\'t available in the app yet — visit your branch to withdraw funds.';

  @override
  String get clClientLoginTitle => 'Client Login';

  @override
  String get clMyBooklet => 'My Booklet';

  @override
  String get clDigitalSavingsBooklet => 'Your digital savings booklet';

  @override
  String get clLoginLabel => 'Login';

  @override
  String get clPinLabel => 'PIN';

  @override
  String get clPinMinDigitsError => 'Min. 4 digits';

  @override
  String get clSignInButton => 'Sign In';

  @override
  String get clFirstTimeActivate => 'First time? Activate my booklet';

  @override
  String get clForgotPinLink => 'Forgot PIN?';

  @override
  String get psPinMismatchError => 'New PIN and confirmation do not match.';

  @override
  String get psPinUpdatedMessage => 'Your PIN has been updated.';

  @override
  String get psChangePinTitle => 'Change PIN';

  @override
  String get psSetYourPinTitle => 'Set Your Transaction PIN';

  @override
  String get psSetYourPinIntro =>
      'Your branch assigned a starting PIN. Replace it with one only you know before you can record a collection.';

  @override
  String get psStartingPinLabel => 'Starting PIN (given by your branch)';

  @override
  String get psCurrentPinLabel => 'Current PIN';

  @override
  String get psNewPinLabel => 'New PIN';

  @override
  String get psNewPinHelperText =>
      'Not all the same digit or a simple run (e.g. 1234)';

  @override
  String get psPinLengthError => '4–10 digits';

  @override
  String get psConfirmNewPinLabel => 'Confirm New PIN';

  @override
  String get psSetPinButton => 'Set PIN';

  @override
  String get lgMicrofiAgent => 'Microfi Agent';

  @override
  String get lgFieldCollectionCashDesk => 'Field collection & cash desk';

  @override
  String get lgUsernameLabel => 'Username';

  @override
  String get lgPasswordLabel => 'Password';

  @override
  String get lgSecureAccessOnly => 'Secure field-agent access only.';

  @override
  String get lgForgotPasswordLink => 'Forgot password?';

  @override
  String get fpTitle => 'Forgot Password';

  @override
  String get fpRequestSubtitle =>
      'Enter your username. We\'ll send a verification code by SMS to your registered phone.';

  @override
  String get fpUsernameLabel => 'Username';

  @override
  String get fpSendCodeButton => 'Send Code';

  @override
  String get fpCodeSentMessage =>
      'If that username exists, a code has been sent by SMS.';

  @override
  String get fpConfirmSubtitle =>
      'Enter the code you received and choose a new password.';

  @override
  String get fpOtpLabel => 'Verification Code';

  @override
  String get fpNewPasswordLabel => 'New Password';

  @override
  String get fpConfirmPasswordLabel => 'Confirm New Password';

  @override
  String get fpPasswordTooShort => 'Password must be at least 8 characters';

  @override
  String get fpPasswordMismatch => 'Passwords do not match';

  @override
  String get fpResetButton => 'Reset Password';

  @override
  String get fpSuccessMessage => 'Password updated. You can now log in.';

  @override
  String get fpResendCode => 'Resend code';

  @override
  String get fpBackToLogin => 'Back to Login';

  @override
  String get cfpTitle => 'Forgot PIN';

  @override
  String get cfpRequestSubtitle =>
      'Enter your login. We\'ll send a verification code by SMS to your registered phone.';

  @override
  String get cfpLoginLabel => 'Login';

  @override
  String get cfpSendCodeButton => 'Send Code';

  @override
  String get cfpCodeSentMessage =>
      'If that login exists, a code has been sent by SMS.';

  @override
  String get cfpConfirmSubtitle =>
      'Enter the code you received and choose a new PIN.';

  @override
  String get cfpOtpLabel => 'Verification Code';

  @override
  String get cfpNewPinLabel => 'New PIN';

  @override
  String get cfpConfirmPinLabel => 'Confirm New PIN';

  @override
  String get cfpPinInvalidFormat => 'PIN must be 4 to 6 digits';

  @override
  String get cfpPinMismatch => 'PINs do not match';

  @override
  String get cfpResetButton => 'Reset PIN';

  @override
  String get cfpSuccessMessage => 'PIN updated. You can now log in.';

  @override
  String get cfpResendCode => 'Resend code';

  @override
  String get cfpBackToLogin => 'Back to Login';

  @override
  String get seUnableToCheckLocation => 'Unable to check location.';

  @override
  String seUnableToLoadProfile(String error) {
    return 'Unable to load your profile: $error';
  }

  @override
  String get seLocationRequiredTitle => 'Location Required';

  @override
  String get seOpenSettings => 'Open Settings';

  @override
  String get seSignInAgain => 'Sign In Again';

  @override
  String get prMyProfileTitle => 'My Profile';

  @override
  String get prEmployeeCodeLabel => 'Employee Code';

  @override
  String get prEmailLabel => 'Email';

  @override
  String get prPhoneLabel => 'Phone';

  @override
  String get prDeviceBindingLabel => 'Device Binding';

  @override
  String get prBound => 'Bound';

  @override
  String get prNotBoundOwnDevice => 'Not bound (own device)';

  @override
  String get prChangeTransactionPin => 'Change Transaction PIN';

  @override
  String get prContactBackOfficeNote =>
      'To change your username, password, or other details, contact your branch back-office.';

  @override
  String get rtMyRouteTodayTitle => 'My Route — Today';

  @override
  String get rtNoGpsOrCollections =>
      'No GPS pings or collections recorded today.';

  @override
  String rtCollectionLine(String amount) {
    return 'Collection — $amount XAF';
  }

  @override
  String get rtGpsPing => 'GPS ping';

  @override
  String rtTimeLatLonLine(String time, String lat, String lon) {
    return '$time • $lat, $lon';
  }

  @override
  String get asAppTitle => 'MICROFI COLLECT';

  @override
  String get asHomeTab => 'Home';

  @override
  String get asHistoryTab => 'History';

  @override
  String get asOfflineTooltip => 'Offline';

  @override
  String cbNoPhoneOnFile(String branchName) {
    return 'No phone number on file for $branchName.';
  }

  @override
  String get cbUnableToOpenDialer => 'Unable to open the phone dialer.';

  @override
  String get rqShowToClientTitle => 'Show to Client';

  @override
  String rqClientAmountLine(String name, int amount) {
    return '$name — $amount XAF';
  }

  @override
  String get rqScanInstructions =>
      'Have the client scan this from their own app to get their receipt.';

  @override
  String rqRefLine(String ref) {
    return 'Ref: $ref';
  }

  @override
  String get cshMyBookletTitle => 'MY BOOKLET';

  @override
  String get crsScanYourReceiptTitle => 'Scan Your Receipt';

  @override
  String get crsPointCameraInstructions =>
      'Point your camera at the QR your agent is showing you';

  @override
  String get crsCouldNotReadQr => 'Could not read this QR code.';

  @override
  String get crsTryAgain => 'Try Again';

  @override
  String get ltsNotificationTitle => 'MICROFI Collect — Tracking active';

  @override
  String get ltsNotificationText =>
      'Sharing your position with your branch while your session is open.';

  @override
  String get ltsNotificationChannelName => 'Field Tracking';

  @override
  String hsPendingConfirmationBanner(int count) {
    return '$count reconciliation(s) awaiting your confirmation';
  }

  @override
  String get rcTitle => 'Pending Confirmations';

  @override
  String get rcSubtitle =>
      'A cashier counted this cash. Confirm it matches what you collected — until you do, it still counts against your ceiling.';

  @override
  String get rcEmptyState => 'Nothing awaiting your confirmation.';

  @override
  String rcLineSummary(int count, String amount) {
    return '$count collection(s) • $amount XAF';
  }

  @override
  String rcCountedAt(String date, String time) {
    return 'Counted $date at $time';
  }

  @override
  String get rcConfirmButton => 'Confirm';

  @override
  String get rcReviewButton => 'Review';

  @override
  String get rcConfirmDialogTitle => 'Confirm Reconciliation';

  @override
  String rcConfirmDialogMessage(String amount, int count) {
    return 'Confirm the cashier\'s count of $amount XAF across $count collection(s) matches what you actually collected?';
  }

  @override
  String get rcConfirmSuccess => 'Reconciliation confirmed.';

  @override
  String get rcConfirmFailed => 'Failed to confirm this reconciliation.';

  @override
  String get rcLineCollectionsTitle => 'Collections in this Line';

  @override
  String get rcRequestRejectionButton => 'Request Rejection';

  @override
  String get rcRejectionReasonLabel => 'Why should this be rejected?';

  @override
  String get rcRejectionSubmit => 'Submit Request';

  @override
  String get rcRejectionSuccess => 'Rejection request sent to your branch.';

  @override
  String get rcRejectionFailed => 'Failed to submit the rejection request.';

  @override
  String get rcRejectionReasonRequired =>
      'Please explain why this should be rejected.';
}
