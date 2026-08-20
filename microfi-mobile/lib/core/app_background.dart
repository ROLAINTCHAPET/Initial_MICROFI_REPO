import 'package:flutter/material.dart';
import 'design_tokens.dart';

/// Tiled CEMAC geometric pattern background — see Graphical Design/Background_Design.
///
/// The source tile's contrast is baked into the asset itself (a levels remap of its actual
/// 223-255 pixel range up to a visible 150-255 — done once with PIL, not a runtime shader).
/// A runtime ColorFilter was tried first but proved unreliable: calibrated around a 128 midpoint,
/// it was numerically extreme for this image's real ~32-value range and rendered differently
/// across environments (invisible in one, a solid dark wash in another). Editing the file once
/// and verifying the result directly removes that platform-dependent guesswork entirely.
class AppBackground extends StatelessWidget {
  final Widget child;

  const AppBackground({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: MicrofiColors.background,
        image: DecorationImage(
          image: AssetImage('assets/backgrounds/pattern.jpg'),
          repeat: ImageRepeat.repeat,
        ),
      ),
      child: child,
    );
  }
}
