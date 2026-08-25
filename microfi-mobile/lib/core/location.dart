import 'package:geolocator/geolocator.dart';
import '../l10n/app_localizations.dart';

enum LocationUnavailableReason { servicesDisabled, permissionDenied }

class LocationUnavailable implements Exception {
  final LocationUnavailableReason reason;
  LocationUnavailable(this.reason);

  /// Localized message for [reason] — call sites have a BuildContext (this is only ever thrown
  /// from a location-capture flow driven by a widget), so the string is resolved at catch time.
  String message(AppLocalizations l10n) => switch (reason) {
        LocationUnavailableReason.servicesDisabled => l10n.locationErrorServicesDisabled,
        LocationUnavailableReason.permissionDenied => l10n.locationErrorPermissionDenied,
      };

  @override
  String toString() => reason.toString();
}

/// BR-05/FR-12: a collection is rejected server-side without a GPS fix, so the app must capture
/// one before it even lets the agent submit — matches the mandatory-GPS rule in
/// CollectionRequest/CollectionService exactly.
Future<Position> captureCurrentLocation() async {
  final serviceEnabled = await Geolocator.isLocationServiceEnabled();
  if (!serviceEnabled) {
    throw LocationUnavailable(LocationUnavailableReason.servicesDisabled);
  }

  LocationPermission permission = await Geolocator.checkPermission();
  if (permission == LocationPermission.denied) {
    permission = await Geolocator.requestPermission();
  }
  if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
    throw LocationUnavailable(LocationUnavailableReason.permissionDenied);
  }

  return Geolocator.getCurrentPosition(
    locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
  );
}
