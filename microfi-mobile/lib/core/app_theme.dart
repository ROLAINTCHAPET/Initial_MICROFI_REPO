import 'package:flutter/material.dart';
import 'design_tokens.dart';

final ThemeData microfiTheme = ThemeData(
  useMaterial3: true,
  brightness: Brightness.light,
  fontFamily: 'Inter',
  colorScheme: ColorScheme.fromSeed(
    seedColor: MicrofiColors.primary,
    brightness: Brightness.light,
    primary: MicrofiColors.primary,
    secondary: MicrofiColors.secondary,
    error: MicrofiColors.error,
    surface: MicrofiColors.surfaceContainerLowest,
  ),
  scaffoldBackgroundColor: Colors.transparent,
  textTheme: const TextTheme(
    headlineMedium: TextStyle(fontWeight: FontWeight.w700, color: MicrofiColors.primary),
    titleMedium: TextStyle(fontWeight: FontWeight.w600, color: MicrofiColors.onSurface),
    bodyMedium: TextStyle(color: MicrofiColors.onSurfaceVariant),
  ),
  inputDecorationTheme: InputDecorationTheme(
    filled: true,
    fillColor: MicrofiColors.surfaceContainerLowest,
    border: OutlineInputBorder(
      borderRadius: BorderRadius.circular(MicrofiRadius.sm),
      borderSide: const BorderSide(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
    ),
    enabledBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(MicrofiRadius.sm),
      borderSide: const BorderSide(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
    ),
    focusedBorder: OutlineInputBorder(
      borderRadius: BorderRadius.circular(MicrofiRadius.sm),
      borderSide: const BorderSide(color: MicrofiColors.primary, width: MicrofiBorders.width),
    ),
  ),
  filledButtonTheme: FilledButtonThemeData(
    style: FilledButton.styleFrom(
      backgroundColor: MicrofiColors.primary,
      foregroundColor: MicrofiColors.onPrimary,
      minimumSize: const Size.fromHeight(48),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
    ),
  ),
  outlinedButtonTheme: OutlinedButtonThemeData(
    style: OutlinedButton.styleFrom(
      foregroundColor: MicrofiColors.primary,
      side: const BorderSide(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
      minimumSize: const Size.fromHeight(48),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
    ),
  ),
  textButtonTheme: TextButtonThemeData(
    style: TextButton.styleFrom(
      foregroundColor: MicrofiColors.primary,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
    ),
  ),
  // Every showDialog(...) in the app (error/success/PIN prompts, confirm dialogs) inherits this —
  // one place to make the whole app's dialogs read as softly rounded/friendly instead of Material's
  // sharp-cornered default, rather than repeating a `shape:` on every AlertDialog call site.
  dialogTheme: DialogThemeData(
    backgroundColor: MicrofiColors.surfaceContainerLowest,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.lg)),
    elevation: 6,
  ),
  cardTheme: CardThemeData(
    color: MicrofiColors.surfaceContainerLowest,
    elevation: 0,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
  ),
  appBarTheme: const AppBarTheme(
    backgroundColor: MicrofiColors.primary,
    foregroundColor: Colors.white,
    elevation: 0,
  ),
  // A consistent fade-forward push/pop on every platform, instead of Android's default Material 3
  // zoom transition (which reads as an abrupt cut on a phone-sized screen) — one place to make
  // every Navigator.push in the app feel more deliberate, rather than adding a custom transition
  // to each of the ~30 individual push call sites.
  pageTransitionsTheme: const PageTransitionsTheme(
    builders: {
      TargetPlatform.android: FadeForwardsPageTransitionsBuilder(),
      TargetPlatform.iOS: FadeForwardsPageTransitionsBuilder(),
    },
  ),
);
