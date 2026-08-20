import 'package:geolocator/geolocator.dart';

class LocationUnavailable implements Exception {
  final String message;
  LocationUnavailable(this.message);

  @override
  String toString() => message;
}

/// BR-05/FR-12: a collection is rejected server-side without a GPS fix, so the app must capture
/// one before it even lets the agent submit — matches the mandatory-GPS rule in
/// CollectionRequest/CollectionService exactly.
Future<Position> captureCurrentLocation() async {
  final serviceEnabled = await Geolocator.isLocationServiceEnabled();
  if (!serviceEnabled) {
    throw LocationUnavailable('Location services are turned off on this device.');
  }

  LocationPermission permission = await Geolocator.checkPermission();
  if (permission == LocationPermission.denied) {
    permission = await Geolocator.requestPermission();
  }
  if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
    throw LocationUnavailable('Location permission was denied. Enable it to record a collection.');
  }

  return Geolocator.getCurrentPosition(
    locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
  );
}
