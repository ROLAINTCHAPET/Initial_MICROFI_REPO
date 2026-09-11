import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/geofence_math.dart';
import 'package:microfi_mobile/features/home/agent_profile.dart';

void main() {
  // Same square fixture as the server's GeofenceServiceTest (verticesCsv "0,0;0,10;10,10;10,0")
  // and the same probe points, so a local pre-sync check and the server's own re-check can never
  // disagree on this shape.
  final square = [
    GeofenceVertex(lat: 0, lon: 0),
    GeofenceVertex(lat: 0, lon: 10),
    GeofenceVertex(lat: 10, lon: 10),
    GeofenceVertex(lat: 10, lon: 0),
  ];

  group('isInsidePolygon', () {
    test('true for a point inside the polygon — mirrors GeofenceServiceTest.isWithinAssignedGeofenceTrueWhenInsidePolygon', () {
      expect(isInsidePolygon(5, 5, square), isTrue);
    });

    test('false for a point outside the polygon — mirrors GeofenceServiceTest.isWithinAssignedGeofenceFalseWhenOutsidePolygon', () {
      expect(isInsidePolygon(50, 50, square), isFalse);
    });

    test('false for an empty vertex list', () {
      expect(isInsidePolygon(5, 5, []), isFalse);
    });

    test('correctly evaluates a triangle', () {
      final triangle = [
        GeofenceVertex(lat: 0, lon: 0),
        GeofenceVertex(lat: 0, lon: 10),
        GeofenceVertex(lat: 10, lon: 10),
      ];
      expect(isInsidePolygon(3, 7, triangle), isTrue);
      expect(isInsidePolygon(9, 1, triangle), isFalse);
    });
  });
}
