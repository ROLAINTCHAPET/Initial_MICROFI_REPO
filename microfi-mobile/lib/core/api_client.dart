import 'dart:convert';
import 'package:http/http.dart' as http;

/// Thrown for any non-2xx response. Mirrors the backend's ApiError shape
/// ({timestamp, path, status, error, message}) so callers can show the real reason.
class ApiException implements Exception {
  final int statusCode;
  final String message;

  ApiException(this.statusCode, this.message);

  @override
  String toString() => message;
}

/// Talks to the MICROFI API Gateway (Kong) the same way the mobile app will in production —
/// no backend-for-frontend layer in between, since there's no server tier on this side to hide
/// the token behind (unlike the Next.js Back-Office, which proxies through a Node server).
class ApiClient {
  // Kong's public port (see kong/kong.yml) — same gateway the Back-Office and this app both go
  // through, so agent auth/rate-limiting/CORS rules are exercised for real, not bypassed.
  static const String baseUrl = 'http://localhost:8000/api/v1';

  final String? token;

  ApiClient({this.token});

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (token != null) 'Authorization': 'Bearer $token',
      };

  Future<Map<String, dynamic>> post(String path, Map<String, dynamic> body) async {
    final response = await http.post(
      Uri.parse('$baseUrl$path'),
      headers: _headers,
      body: jsonEncode(body),
    );
    return _decode(response);
  }

  Future<Map<String, dynamic>> patch(String path, Map<String, dynamic> body) async {
    final response = await http.patch(
      Uri.parse('$baseUrl$path'),
      headers: _headers,
      body: jsonEncode(body),
    );
    return _decode(response);
  }

  /// For endpoints that reply 204 No Content — `patch()` can't be reused here since its return
  /// type expects a decoded JSON object back, and `_decode` returns null for an empty body.
  Future<void> patchNoContent(String path, Map<String, dynamic> body) async {
    final response = await http.patch(
      Uri.parse('$baseUrl$path'),
      headers: _headers,
      body: jsonEncode(body),
    );
    _decode(response);
  }

  /// For endpoints taking/returning a JSON array rather than an object (e.g. batch sync).
  Future<dynamic> postJson(String path, dynamic body) async {
    final response = await http.post(
      Uri.parse('$baseUrl$path'),
      headers: _headers,
      body: jsonEncode(body),
    );
    return _decode(response);
  }

  Future<dynamic> get(String path) async {
    final response = await http.get(Uri.parse('$baseUrl$path'), headers: _headers);
    return _decode(response);
  }

  dynamic _decode(http.Response response) {
    final bodyText = response.body.isEmpty ? null : response.body;
    final decoded = bodyText != null ? jsonDecode(bodyText) : null;

    if (response.statusCode < 200 || response.statusCode >= 300) {
      final message = (decoded is Map && decoded['message'] != null)
          ? decoded['message'] as String
          : (decoded is Map && decoded['error'] != null)
              ? decoded['error'] as String
              : 'Request failed with status ${response.statusCode}';
      throw ApiException(response.statusCode, message);
    }

    return decoded;
  }
}
