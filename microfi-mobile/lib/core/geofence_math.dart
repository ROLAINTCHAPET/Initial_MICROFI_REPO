import '../features/home/agent_profile.dart';

/// Standard ray-casting point-in-polygon test — byte-for-byte the same algorithm as the server's
/// GeofenceService#isInsidePolygon (treats lat/lon as planar x/y, adequate at market/district
/// scale), so a local pre-sync check and the server's own re-check can never disagree.
bool isInsidePolygon(double lat, double lon, List<GeofenceVertex> vertices) {
  bool inside = false;
  final n = vertices.length;
  for (int i = 0, j = n - 1; i < n; j = i++) {
    final latI = vertices[i].lat, lonI = vertices[i].lon;
    final latJ = vertices[j].lat, lonJ = vertices[j].lon;
    final edgeCrossesRay = ((lonI > lon) != (lonJ > lon)) && (lat < (latJ - latI) * (lon - lonI) / (lonJ - lonI) + latI);
    if (edgeCrossesRay) {
      inside = !inside;
    }
  }
  return inside;
}
