// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for French (`fr`).
class AppLocalizationsFr extends AppLocalizations {
  AppLocalizationsFr([String locale = 'fr']) : super(locale);

  @override
  String get appName => 'Microfi';

  @override
  String get roleSelectPrompt => 'Qui se connecte ?';

  @override
  String get roleFieldAgentTitle => 'Agent de terrain';

  @override
  String get roleFieldAgentSubtitle =>
      'Encaisser, gérer les clients et le compte séquestre';

  @override
  String get roleClientTitle => 'Client';

  @override
  String get roleClientSubtitle => 'Consulter mon livret d\'épargne numérique';

  @override
  String get languagePickerTitle => 'Langue';

  @override
  String get languageEnglish => 'Anglais';

  @override
  String get languageFrench => 'Français';

  @override
  String get commonOk => 'OK';

  @override
  String get commonCancel => 'Annuler';

  @override
  String get commonConfirm => 'Confirmer';

  @override
  String get commonRetry => 'Réessayer';

  @override
  String get commonSave => 'Enregistrer';

  @override
  String get commonClose => 'Fermer';

  @override
  String get commonContactBranch => 'Contacter l\'agence';

  @override
  String get commonDone => 'Terminé';

  @override
  String get commonSignOut => 'Déconnexion';

  @override
  String get errorIncorrectCredentials =>
      'Identifiants de connexion incorrects.';

  @override
  String get errorRequestTimeout =>
      'La requête a pris trop de temps. Veuillez réessayer.';

  @override
  String get errorUnableToReachServer =>
      'Impossible de joindre le serveur. Vérifiez votre connexion.';

  @override
  String get errorSomethingWentWrong =>
      'Une erreur s\'est produite. Veuillez réessayer.';

  @override
  String get errorDialogTitle => 'Un problème est survenu';

  @override
  String get dialogEnterPinTitle => 'Saisissez votre code confidentiel';

  @override
  String get escrowCeilingReachedTitle => 'Plafond du compte séquestre atteint';

  @override
  String get offlineModeTitle => 'Mode hors ligne';

  @override
  String offlineCollectionsPendingSync(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count encaissements en attente de synchronisation',
      one: '1 encaissement en attente de synchronisation',
    );
    return '$_temp0';
  }

  @override
  String get syncNowButton => 'Synchroniser maintenant';

  @override
  String get locationErrorServicesDisabled =>
      'Les services de localisation sont désactivés sur cet appareil.';

  @override
  String get locationErrorPermissionDenied =>
      'L\'autorisation de localisation a été refusée. Activez-la pour enregistrer un encaissement.';

  @override
  String get printerErrorNoPrinterSelected =>
      'Aucune imprimante thermique sélectionnée pour le moment.';

  @override
  String get printerErrorBluetoothUnavailable =>
      'Activez le Bluetooth et accordez l\'autorisation pour imprimer.';

  @override
  String printerErrorConnectionFailed(String printerName) {
    return 'Impossible de se connecter à $printerName.';
  }

  @override
  String get printerErrorDidNotConfirm =>
      'L\'imprimante n\'a pas confirmé l\'impression.';

  @override
  String get qrErrorNotMicrofiReceipt =>
      'Ce code QR n\'est pas un reçu MICROFI.';

  @override
  String get qrErrorMissingData =>
      'Ce code QR ne contient pas toutes les données de reçu attendues.';

  @override
  String get qrErrorTampered =>
      'Ce code QR a été altéré ou n\'est pas un reçu MICROFI authentique.';

  @override
  String get errorUnableToReachServerShort =>
      'Impossible de joindre le serveur.';

  @override
  String get commonTotal => 'Total';

  @override
  String amountXaf(String amount) {
    return '$amount XAF';
  }

  @override
  String get csSearchHint => 'Nom, téléphone ou numéro de membre';

  @override
  String get csNoClientsRegistered => 'Aucun client enregistré pour le moment.';

  @override
  String get csNoClientsFound => 'Aucun client trouvé pour cette recherche.';

  @override
  String csClientSubtitle(String memberNo, String phone) {
    return '$memberNo • $phone';
  }

  @override
  String get csCollectCashTitle => 'Encaisser';

  @override
  String get csCapturingGps => 'Capture de la position GPS…';

  @override
  String csLocationCaptured(int accuracy) {
    return 'Position capturée (±$accuracy m)';
  }

  @override
  String get csLocationUnavailable => 'Position indisponible';

  @override
  String get csDenominationBreakdown => 'Détail des coupures';

  @override
  String get csCalculatedTotal => 'TOTAL CALCULÉ';

  @override
  String csEscrowExceedMessage(String projected, String ceiling, String over) {
    return 'Cet encaissement vous ferait atteindre $projected / $ceiling XAF — soit $over XAF au-delà de votre plafond journalier.';
  }

  @override
  String get csVerifyAndContinue => 'Vérifier et continuer';

  @override
  String get csConfirmCollectionTitle => 'Confirmer l\'encaissement';

  @override
  String csDenominationLine(int denom, int count) {
    return '$denom XAF × $count';
  }

  @override
  String csLocationLine(String lat, String lon, int accuracy) {
    return 'Position : $lat, $lon (±$accuracy m)';
  }

  @override
  String get csEnterPinToConfirm =>
      'Saisissez votre code confidentiel pour confirmer';

  @override
  String get csRecordCollection => 'Enregistrer l\'encaissement';

  @override
  String get csAlreadyRecorded => 'Déjà enregistré';

  @override
  String get csCollectionValidated => 'Encaissement validé';

  @override
  String csTransactionIdWithLocation(String id, String location) {
    return 'N° de transaction : $id • $location';
  }

  @override
  String csTransactionId(String id) {
    return 'N° de transaction : $id';
  }

  @override
  String get csAmountCollected => 'Montant encaissé';

  @override
  String get csReceiptPrinted => 'Reçu imprimé';

  @override
  String get csPrinting => 'Impression…';

  @override
  String get csPrintReceipt => 'Imprimer le reçu';

  @override
  String get csReceiptDownloaded => 'Reçu téléchargé';

  @override
  String get csDownloading => 'Téléchargement…';

  @override
  String get csDownloadReceipt => 'Télécharger le reçu';

  @override
  String get csShowQrForClient => 'Afficher le QR pour le client';

  @override
  String get csSavedOffline => 'Enregistré hors ligne';

  @override
  String csQueuedOfflineMessage(String amount, String clientName) {
    return '$amount XAF pour $clientName est en attente. La synchronisation se fera automatiquement dès que vous serez de nouveau en ligne.';
  }

  @override
  String get csSelectThermalPrinter => 'Sélectionner une imprimante thermique';

  @override
  String csStepLabel(int number) {
    return 'Étape $number';
  }

  @override
  String get csTargetClientLabel => 'CLIENT CIBLE';

  @override
  String get csDailyCeilingImpact => 'Impact sur le plafond journalier';

  @override
  String get csPinRequiredError =>
      'Saisissez votre code confidentiel pour confirmer cet encaissement.';

  @override
  String csTooManyPinAttempts(int minutes) {
    return 'Trop de tentatives de code confidentiel incorrectes. Réessayez dans $minutes min, ou connectez-vous à Internet.';
  }

  @override
  String get csIncorrectPin => 'Code confidentiel incorrect.';

  @override
  String csCeilingExceedOfflineMessage(String projected, String ceiling) {
    return 'Cela vous ferait atteindre $projected / $ceiling XAF, au-delà de votre plafond journalier (d\'après votre dernière connexion — reconnectez-vous pour une vérification à jour, ou contactez votre agence).';
  }

  @override
  String get csCouldNotPrintReceipt => 'Impossible d\'imprimer le reçu.';

  @override
  String csReceiptSavedCouldNotOpen(String error) {
    return 'Reçu enregistré, mais impossible de l\'ouvrir : $error';
  }

  @override
  String get csCouldNotDownloadReceipt => 'Impossible de télécharger le reçu.';

  @override
  String get csBluetoothPermissionNeeded =>
      'Activez le Bluetooth et accordez l\'autorisation, puis réessayez.';

  @override
  String get csNoPairedPrinterFound =>
      'Aucune imprimante thermique appairée trouvée — appairez-en une dans les paramètres Bluetooth de votre téléphone.';

  @override
  String csCeilingProgressLine(int projected, int ceiling) {
    return '$projected / $ceiling XAF';
  }

  @override
  String get csGenericClient => 'client';

  @override
  String get hsCollectionHistoryTitle => 'Historique des encaissements';

  @override
  String get hsQuickActionHistory => 'Historique';

  @override
  String get hsSosSentMessage =>
      'Alerte SOS envoyée — votre agence a été alertée.';

  @override
  String get hsSosSendFailedMessage =>
      'Impossible d\'envoyer l\'alerte SOS — vérifiez votre connexion et réessayez.';

  @override
  String get hsSosAcknowledgedMessage =>
      'Votre alerte SOS a été prise en compte par votre agence.';

  @override
  String get hsNewCollection => 'Nouvel encaissement';

  @override
  String get hsMyRoute => 'Mon itinéraire';

  @override
  String get hsSponsorClientActivation =>
      'Parrainer l\'activation d\'un client';

  @override
  String get hsRecentCollections => 'Encaissements récents';

  @override
  String get hsSeeAll => 'Tout voir';

  @override
  String get hsNoCollectionsRecorded =>
      'Aucun encaissement enregistré pour le moment.';

  @override
  String get hsTodaysCollections => 'ENCAISSEMENTS DU JOUR';

  @override
  String get hsCeilingLabel => 'Plafond';

  @override
  String hsCapacityReachedDepositSoon(int percent) {
    return '$percent % de la capacité atteinte. Déposez bientôt.';
  }

  @override
  String hsCapacityReachedSafe(int percent) {
    return '$percent % de la capacité atteinte. Vous pouvez continuer.';
  }

  @override
  String get hsUnknownClient => 'Client inconnu';

  @override
  String hsTimeCashLine(String time) {
    return '$time • Espèces';
  }

  @override
  String hsAmountCollectedPlus(String amount) {
    return '+$amount XAF';
  }

  @override
  String get hsStatusActive => 'En ligne';

  @override
  String get hsStatusSuspended => 'Suspendu';

  @override
  String get hsSosPendingBanner =>
      'Alerte SOS en attente — en attente de prise en compte par votre agence.';

  @override
  String get chEnterPinConfirmPayment =>
      'Saisissez votre code confidentiel pour confirmer que vous avez payé les frais d\'activation à votre agent.';

  @override
  String get chBookletActivatedTitle => 'Livret activé';

  @override
  String get chPaymentConfirmedTitle => 'Paiement confirmé';

  @override
  String get chBookletActiveMessage =>
      'Votre livret est maintenant actif — votre paiement et le parrainage de votre agent sont tous deux confirmés.';

  @override
  String get chPaymentConfirmedWaitingMessage =>
      'Paiement confirmé de votre côté. En attente que votre agent parraine votre activation.';

  @override
  String get chCouldNotConfirmPaymentTitle =>
      'Impossible de confirmer le paiement';

  @override
  String get chContributionHistoryTitle => 'Historique des versements';

  @override
  String get chRenewalNeeded => 'Renouvellement nécessaire';

  @override
  String get chActivationPending => 'Activation en attente';

  @override
  String get chConfirmOncePaidMessage =>
      'Une fois que votre agent a reçu vos frais d\'activation en espèces, confirmez-le ici avec votre code confidentiel.';

  @override
  String get chConfirmActivationPaymentButton =>
      'Confirmer le paiement d\'activation';

  @override
  String get chJustCollected => 'Encaissement récent';

  @override
  String get chRecordedReflectedMessage =>
      'Enregistré par MICROFI — reflété dans votre solde officiel en fin de journée.';

  @override
  String get chScanReceiptFromAgent => 'Scanner le reçu de l\'agent';

  @override
  String get chHowToTopUpTitle => 'Comment recharger';

  @override
  String get chHowToTopUpMessage =>
      'Remettez les espèces à un agent de terrain qui vous rend visite, ou rendez-vous directement à votre agence. Chaque dépôt apparaît ici une fois enregistré.';

  @override
  String get chTopUpInfo => 'Infos recharge';

  @override
  String get chRecentContributions => 'Versements récents';

  @override
  String get chViewAll => 'Tout voir';

  @override
  String get chNoContributionsRecorded =>
      'Aucun versement enregistré pour le moment.';

  @override
  String get chStatusActive => 'ACTIF';

  @override
  String get chStatusExpired => 'EXPIRÉ';

  @override
  String get chStatusNotActivated => 'NON ACTIVÉ';

  @override
  String get rpTagline => 'COLLECTE D\'ESPÈCES NUMÉRIQUE · CEMAC';

  @override
  String get rpBranchLabel => 'Agence';

  @override
  String get rpDateLabel => 'Date';

  @override
  String get rpAgentLabel => 'Agent';

  @override
  String get rpClientIdLabel => 'N° CLIENT';

  @override
  String get rpClientNameLabel => 'NOM DU CLIENT';

  @override
  String get rpAmountCollectedLabel => 'MONTANT ENCAISSÉ';

  @override
  String get rpCashPill => 'ESPÈCES';

  @override
  String get rpRecordedPill => '✓ ENREGISTRÉ';

  @override
  String get rpDenominationBreakdownLabel => 'DÉTAIL DES COUPURES (XAF)';

  @override
  String get rpUniqueRefLabel => 'RÉFÉRENCE UNIQUE';

  @override
  String get rpElectronicReceiptProofNote =>
      'Ce reçu électronique constitue une preuve de dépôt même en cas de panne réseau temporaire, conformément à FR-09 / BR-Notif-01.';

  @override
  String get rpThankYouTrust => 'MERCI POUR VOTRE CONFIANCE';

  @override
  String get saTitle => 'Parrainer l\'activation';

  @override
  String get saSponsorActivationQuestion => 'Parrainer l\'activation ?';

  @override
  String saConfirmReceivedFee(String name) {
    return 'Confirmez que vous avez reçu les frais d\'activation de $name en espèces.';
  }

  @override
  String saClientFullyActivated(String name) {
    return '$name est maintenant pleinement activé(e) — le parrainage et le paiement sont tous deux confirmés.';
  }

  @override
  String saSponsorshipRecorded(String name) {
    return 'Parrainage enregistré pour $name. En attente de la confirmation de son propre paiement.';
  }

  @override
  String get saClientActivatedTitle => 'Client activé';

  @override
  String get saSponsorshipRecordedTitle => 'Parrainage enregistré';

  @override
  String get saSponsorshipFailedTitle => 'Échec du parrainage';

  @override
  String get saNoClientsAwaiting =>
      'Aucun client n\'est actuellement en attente d\'activation.';

  @override
  String get saAwaitingPayment => 'En attente de paiement';

  @override
  String get saSponsorButton => 'Parrainer';

  @override
  String get wsWalletUnavailable => 'Portefeuille indisponible.';

  @override
  String get wsWalletTitle => 'Portefeuille';

  @override
  String get wsEscrowWalletBalance => 'Solde du compte séquestre';

  @override
  String wsBaseCeiling(String amount) {
    return 'Plafond de base : $amount XAF';
  }

  @override
  String get wsEffectiveCeilingTitle => 'Plafond effectif';

  @override
  String get wsEffectiveCeilingToday => 'Plafond effectif (aujourd\'hui)';

  @override
  String get wsTodaysCollectionsLabel => 'Encaissements du jour';

  @override
  String get wsRemainingToday => 'Restant aujourd\'hui';

  @override
  String get wsActiveWaiver => 'Dérogation active';

  @override
  String get wsTopUpAdminNote =>
      'Les recharges du portefeuille sont administrées par un caissier d\'agence — cet écran est en lecture seule.';

  @override
  String get histTotalCollectedThisMonth => 'Total encaissé ce mois-ci';

  @override
  String histDateTimeCashLine(String date, String time) {
    return '$date • $time • Espèces';
  }

  @override
  String get crvYourReceiptTitle => 'Votre reçu';

  @override
  String get crvVerifiedDepositReceipt => 'Reçu de dépôt vérifié';

  @override
  String get crvClientLabel => 'Client';

  @override
  String get crvMemberNoLabel => 'N° de membre';

  @override
  String get crvReferenceLabel => 'Référence';

  @override
  String crvDenominationLine(String denom, int count) {
    return '$denom XAF × $count';
  }

  @override
  String get commonRequiredField => 'Champ requis';

  @override
  String get caActivationFailedTitle => 'Échec de l\'activation';

  @override
  String get caActivateMyBookletTitle => 'Activer mon livret';

  @override
  String get caCredentialsSetTitle => 'Identifiants définis';

  @override
  String get caBackToLogin => 'Retour à la connexion';

  @override
  String get caIntroMessage =>
      'Saisissez votre numéro de compte MFI (le même numéro déjà fourni par votre agence) et choisissez un identifiant de connexion et un code confidentiel. Ensuite, demandez à votre agent de parrainer votre activation, puis confirmez vous-même le paiement pour recevoir votre livret.';

  @override
  String get caMfiIdentifierLabel => 'Votre numéro de compte MFI';

  @override
  String caRegisteredWithMfi(String mfiName) {
    return 'Vous êtes maintenant enregistré auprès de $mfiName.';
  }

  @override
  String get caChooseLoginLabel => 'Choisir un identifiant de connexion';

  @override
  String get caChoosePinLabel => 'Choisir un code confidentiel';

  @override
  String get caPinDigitsError => '4 à 6 chiffres';

  @override
  String get caSetCredentialsButton => 'Définir mes identifiants';

  @override
  String get chTotalContributionsThisMonth => 'Total des versements ce mois-ci';

  @override
  String chDateTimeReferenceLine(String date, String time, String reference) {
    return '$date, $time • $reference';
  }

  @override
  String get cwMyAccountTitle => 'Mon compte';

  @override
  String get cwBookletTokenTitle => 'Jeton du livret';

  @override
  String get cwStatusLabel => 'Statut';

  @override
  String get cwExpiresLabel => 'Expire le';

  @override
  String get cwRequestWithdrawalComingSoon =>
      'Demande de retrait — Bientôt disponible';

  @override
  String get cwWithdrawalNotAvailableNote =>
      'Les demandes de retrait ne sont pas encore disponibles dans l\'application — rendez-vous à votre agence pour retirer des fonds.';

  @override
  String get clClientLoginTitle => 'Connexion client';

  @override
  String get clMyBooklet => 'Mon livret';

  @override
  String get clDigitalSavingsBooklet => 'Votre livret d\'épargne numérique';

  @override
  String get clLoginLabel => 'Identifiant';

  @override
  String get clPinLabel => 'Code confidentiel';

  @override
  String get clPinMinDigitsError => '4 chiffres minimum';

  @override
  String get clSignInButton => 'Se connecter';

  @override
  String get clFirstTimeActivate => 'Première visite ? Activez mon livret';

  @override
  String get psPinMismatchError =>
      'Le nouveau code confidentiel et sa confirmation ne correspondent pas.';

  @override
  String get psPinUpdatedMessage => 'Votre code confidentiel a été mis à jour.';

  @override
  String get psChangePinTitle => 'Modifier le code confidentiel';

  @override
  String get psSetYourPinTitle =>
      'Définissez votre code confidentiel de transaction';

  @override
  String get psSetYourPinIntro =>
      'Votre agence vous a attribué un code confidentiel initial. Remplacez-le par un code que vous seul(e) connaissez avant de pouvoir enregistrer un encaissement.';

  @override
  String get psStartingPinLabel =>
      'Code confidentiel initial (fourni par votre agence)';

  @override
  String get psCurrentPinLabel => 'Code confidentiel actuel';

  @override
  String get psNewPinLabel => 'Nouveau code confidentiel';

  @override
  String get psNewPinHelperText =>
      'Pas le même chiffre répété ni une suite simple (ex. 1234)';

  @override
  String get psPinLengthError => '4 à 10 chiffres';

  @override
  String get psConfirmNewPinLabel => 'Confirmer le nouveau code confidentiel';

  @override
  String get psSetPinButton => 'Définir le code confidentiel';

  @override
  String get lgMicrofiAgent => 'Microfi Agent';

  @override
  String get lgFieldCollectionCashDesk => 'Encaissement terrain et caisse';

  @override
  String get lgUsernameLabel => 'Nom d\'utilisateur';

  @override
  String get lgPasswordLabel => 'Mot de passe';

  @override
  String get lgSecureAccessOnly =>
      'Accès sécurisé réservé aux agents de terrain.';

  @override
  String get lgForgotPasswordLink => 'Mot de passe oublié ?';

  @override
  String get fpTitle => 'Mot de passe oublié';

  @override
  String get fpRequestSubtitle =>
      'Entrez votre nom d\'utilisateur. Nous enverrons un code de vérification par SMS à votre téléphone enregistré.';

  @override
  String get fpUsernameLabel => 'Nom d\'utilisateur';

  @override
  String get fpSendCodeButton => 'Envoyer le code';

  @override
  String get fpCodeSentMessage =>
      'Si ce nom d\'utilisateur existe, un code a été envoyé par SMS.';

  @override
  String get fpConfirmSubtitle =>
      'Entrez le code reçu et choisissez un nouveau mot de passe.';

  @override
  String get fpOtpLabel => 'Code de vérification';

  @override
  String get fpNewPasswordLabel => 'Nouveau mot de passe';

  @override
  String get fpConfirmPasswordLabel => 'Confirmer le nouveau mot de passe';

  @override
  String get fpPasswordTooShort =>
      'Le mot de passe doit contenir au moins 8 caractères';

  @override
  String get fpPasswordMismatch => 'Les mots de passe ne correspondent pas';

  @override
  String get fpResetButton => 'Réinitialiser le mot de passe';

  @override
  String get fpSuccessMessage =>
      'Mot de passe mis à jour. Vous pouvez maintenant vous connecter.';

  @override
  String get fpResendCode => 'Renvoyer le code';

  @override
  String get fpBackToLogin => 'Retour à la connexion';

  @override
  String get seUnableToCheckLocation => 'Impossible de vérifier la position.';

  @override
  String seUnableToLoadProfile(String error) {
    return 'Impossible de charger votre profil : $error';
  }

  @override
  String get seLocationRequiredTitle => 'Position requise';

  @override
  String get seOpenSettings => 'Ouvrir les paramètres';

  @override
  String get seSignInAgain => 'Se reconnecter';

  @override
  String get prMyProfileTitle => 'Mon profil';

  @override
  String get prEmployeeCodeLabel => 'Matricule';

  @override
  String get prEmailLabel => 'E-mail';

  @override
  String get prPhoneLabel => 'Téléphone';

  @override
  String get prDeviceBindingLabel => 'Appareil associé';

  @override
  String get prBound => 'Associé';

  @override
  String get prNotBoundOwnDevice => 'Non associé (appareil personnel)';

  @override
  String get prChangeTransactionPin =>
      'Modifier le code confidentiel de transaction';

  @override
  String get prContactBackOfficeNote =>
      'Pour modifier votre nom d\'utilisateur, votre mot de passe ou d\'autres informations, contactez le back-office de votre agence.';

  @override
  String get rtMyRouteTodayTitle => 'Mon itinéraire — Aujourd\'hui';

  @override
  String get rtNoGpsOrCollections =>
      'Aucun point GPS ni encaissement enregistré aujourd\'hui.';

  @override
  String rtCollectionLine(String amount) {
    return 'Encaissement — $amount XAF';
  }

  @override
  String get rtGpsPing => 'Point GPS';

  @override
  String rtTimeLatLonLine(String time, String lat, String lon) {
    return '$time • $lat, $lon';
  }

  @override
  String get asAppTitle => 'MICROFI COLLECT';

  @override
  String get asHomeTab => 'Accueil';

  @override
  String get asHistoryTab => 'Historique';

  @override
  String get asOfflineTooltip => 'Hors ligne';

  @override
  String cbNoPhoneOnFile(String branchName) {
    return 'Aucun numéro de téléphone enregistré pour $branchName.';
  }

  @override
  String get cbUnableToOpenDialer =>
      'Impossible d\'ouvrir le composeur téléphonique.';

  @override
  String get rqShowToClientTitle => 'Montrer au client';

  @override
  String rqClientAmountLine(String name, int amount) {
    return '$name — $amount XAF';
  }

  @override
  String get rqScanInstructions =>
      'Demandez au client de scanner ce code depuis sa propre application pour obtenir son reçu.';

  @override
  String rqRefLine(String ref) {
    return 'Réf. : $ref';
  }

  @override
  String get cshMyBookletTitle => 'MON LIVRET';

  @override
  String get crsScanYourReceiptTitle => 'Scannez votre reçu';

  @override
  String get crsPointCameraInstructions =>
      'Pointez votre appareil photo vers le code QR que votre agent vous présente';

  @override
  String get crsCouldNotReadQr => 'Impossible de lire ce code QR.';

  @override
  String get crsTryAgain => 'Réessayer';

  @override
  String get ltsNotificationTitle => 'MICROFI Collect — Suivi actif';

  @override
  String get ltsNotificationText =>
      'Votre position est partagée avec votre agence tant que votre session est ouverte.';

  @override
  String get ltsNotificationChannelName => 'Suivi de terrain';
}
