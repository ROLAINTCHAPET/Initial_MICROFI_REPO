import 'package:flutter/material.dart';

/// Mirrors the Back-Office's DESIGN.md palette/shape tokens exactly (same values as
/// microfi-backoffice/src/app/globals.css's @theme block) so the two clients read as one system.
class MicrofiColors {
  static const primary = Color(0xFF000F22);
  static const onPrimary = Color(0xFFFFFFFF);
  static const primaryContainer = Color(0xFF0A2540);
  static const primaryFixedDim = Color(0xFFB0C8EB);

  static const secondary = Color(0xFF006B59);
  static const onSecondary = Color(0xFFFFFFFF);
  static const secondaryFixed = Color(0xFF55FCD8);
  static const secondaryContainer = Color(0xFF55FCD8);
  static const onSecondaryContainer = Color(0xFF00725F);
  static const onSecondaryFixedVariant = Color(0xFF005142);

  static const tertiaryFixed = Color(0xFFFFDEA8);
  static const tertiaryFixedDim = Color(0xFFFFBA20);
  static const onTertiaryFixedVariant = Color(0xFF5E4200);

  static const error = Color(0xFFBA1A1A);
  static const onError = Color(0xFFFFFFFF);
  static const errorContainer = Color(0xFFFFDAD6);
  static const onErrorContainer = Color(0xFF93000A);

  static const background = Color(0xFFF7FAFD);
  static const surfaceContainerLowest = Color(0xFFFFFFFF);
  static const surfaceContainerLow = Color(0xFFF1F4F7);
  static const surfaceContainer = Color(0xFFEBEEF1);
  static const surfaceContainerHigh = Color(0xFFE5E8EB);
  static const surfaceContainerHighest = Color(0xFFE0E3E6);
  static const outline = Color(0xFF74777E);
  static const outlineVariant = Color(0xFFC4C6CE);
  static const onSurface = Color(0xFF181C1E);
  static const onSurfaceVariant = Color(0xFF43474D);
}

class MicrofiRadius {
  static const sm = 6.0;
  static const md = 12.0;
  static const full = 999.0;
}

/// Compact spacing/type scale tuned for real phone widths (~360-414 logical px) — the app was
/// originally verified only at a 900px test viewport, which read as comfortably sized there but
/// oversized and cramped on an actual device.
class MicrofiSpacing {
  static const page = 14.0;
  static const card = 12.0;
  static const gap = 8.0;
  static const gapLg = 12.0;
}

/// DESIGN.md: 2px stroke on focused/active surfaces, borders thickened app-wide per explicit
/// Back-Office feedback this session ("thicken all borders").
class MicrofiBorders {
  static const width = 2.0;
}
