import 'package:flutter/material.dart';

/// A lightweight "fade + slide up" entrance for content that pops into an already-built screen —
/// a banner appearing after a background poll, a freshly-loaded card — so it reads as arriving
/// deliberately instead of snapping into existence mid-frame. Runs once, on this widget's own
/// first build; wrap the exact widget that should animate in (e.g. inside an `if` in a list), not
/// a container around content that's always present.
class FadeSlideIn extends StatefulWidget {
  final Widget child;
  final Duration duration;
  final double offset;

  const FadeSlideIn({super.key, required this.child, this.duration = const Duration(milliseconds: 350), this.offset = 14});

  @override
  State<FadeSlideIn> createState() => _FadeSlideInState();
}

class _FadeSlideInState extends State<FadeSlideIn> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(vsync: this, duration: widget.duration)..forward();
  late final Animation<double> _curve = CurvedAnimation(parent: _controller, curve: Curves.easeOutCubic);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _curve,
      child: widget.child,
      builder: (context, child) => Opacity(
        opacity: _curve.value,
        child: Transform.translate(offset: Offset(0, (1 - _curve.value) * widget.offset), child: child),
      ),
    );
  }
}

/// A small bouncy scale-in, meant for a success/checkmark badge right after it appears —
/// Curves.elasticOut gives a subtle "pop" that reads as a positive confirmation landing, rather
/// than just more content rendering.
class ScaleIn extends StatefulWidget {
  final Widget child;
  final Duration duration;

  const ScaleIn({super.key, required this.child, this.duration = const Duration(milliseconds: 500)});

  @override
  State<ScaleIn> createState() => _ScaleInState();
}

class _ScaleInState extends State<ScaleIn> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(vsync: this, duration: widget.duration)..forward();
  late final Animation<double> _scale = CurvedAnimation(parent: _controller, curve: Curves.elasticOut);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ScaleTransition(scale: _scale, child: widget.child);
  }
}
