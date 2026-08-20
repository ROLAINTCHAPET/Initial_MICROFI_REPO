import '../../core/api_client.dart';

class RoutePoint {
  final double lat;
  final double lon;
  final DateTime recordedAt;

  RoutePoint({required this.lat, required this.lon, required this.recordedAt});

  factory RoutePoint.fromJson(Map<String, dynamic> json) => RoutePoint(
        lat: (json['lat'] as num).toDouble(),
        lon: (json['lon'] as num).toDouble(),
        recordedAt: DateTime.parse(json['recordedAt'] as String),
      );
}

class RouteTransaction {
  final String collectionId;
  final double lat;
  final double lon;
  final int amountXaf;
  final DateTime collectedAt;

  RouteTransaction({required this.collectionId, required this.lat, required this.lon, required this.amountXaf, required this.collectedAt});

  factory RouteTransaction.fromJson(Map<String, dynamic> json) => RouteTransaction(
        collectionId: json['collectionId'] as String,
        lat: (json['lat'] as num).toDouble(),
        lon: (json['lon'] as num).toDouble(),
        amountXaf: (json['amountXaf'] as num).toInt(),
        collectedAt: DateTime.parse(json['collectedAt'] as String),
      );
}

class AgentRoute {
  final List<RoutePoint> points;
  final List<RouteTransaction> transactions;

  AgentRoute({required this.points, required this.transactions});

  factory AgentRoute.fromJson(Map<String, dynamic> json) => AgentRoute(
        points: (json['points'] as List<dynamic>).map((e) => RoutePoint.fromJson(e as Map<String, dynamic>)).toList(),
        transactions: (json['transactions'] as List<dynamic>).map((e) => RouteTransaction.fromJson(e as Map<String, dynamic>)).toList(),
      );
}

/// UC-11 — Historical Route Visualization (GET /agents/me/route). Self-scoped: always the caller's
/// own trail, defaults to today server-side when no date is given.
class RouteRepository {
  final String token;

  RouteRepository(this.token);

  Future<AgentRoute> fetchToday() async {
    final client = ApiClient(token: token);
    final json = await client.get('/agents/me/route') as Map<String, dynamic>;
    return AgentRoute.fromJson(json);
  }
}
