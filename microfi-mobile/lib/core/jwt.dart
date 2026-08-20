import 'dart:convert';

/// Decodes a JWT's payload for local display only (e.g. "logged in as AGT-001, expires at...").
/// This performs no signature verification — the backend is always the source of truth for
/// whether a token is genuine; the app only ever trusts it insofar as the server accepts it on
/// the next request.
Map<String, dynamic> decodeJwtPayload(String token) {
  final parts = token.split('.');
  if (parts.length != 3) {
    throw const FormatException('Not a valid JWT');
  }
  var payload = parts[1];
  payload = base64Url.normalize(payload);
  final decoded = utf8.decode(base64Url.decode(payload));
  return jsonDecode(decoded) as Map<String, dynamic>;
}
