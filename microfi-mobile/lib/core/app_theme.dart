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
  appBarTheme: const AppBarTheme(
    backgroundColor: MicrofiColors.primary,
    foregroundColor: Colors.white,
    elevation: 0,
  ),
);
