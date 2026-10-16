import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

/// Circular "budget remaining" gauge - the dashboard's hero visual, replacing
/// a flat LinearProgressIndicator. Color shifts with severity: plenty of
/// budget left reads as the brand primary color, under 25% reads amber,
/// exhausted reads the theme's error color.
class BudgetGauge extends StatelessWidget {
  final double fractionRemaining;
  final String bigLabel;
  final String smallLabel;
  final double size;

  const BudgetGauge({
    super.key,
    required this.fractionRemaining,
    required this.bigLabel,
    required this.smallLabel,
    this.size = 180,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final fraction = fractionRemaining.clamp(0.0, 1.0);
    final ringColor = fraction <= 0
        ? scheme.error
        : fraction < 0.25
            ? AppSeverity.warning
            : scheme.primary;

    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          CustomPaint(
            size: Size(size, size),
            painter: _GaugePainter(
              fraction: fraction,
              trackColor: scheme.surfaceContainerHighest,
              ringColor: ringColor,
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                bigLabel,
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(color: ringColor),
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(
                smallLabel,
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _GaugePainter extends CustomPainter {
  final double fraction;
  final Color trackColor;
  final Color ringColor;

  _GaugePainter({required this.fraction, required this.trackColor, required this.ringColor});

  @override
  void paint(Canvas canvas, Size size) {
    const strokeWidth = 14.0;
    final rect = Offset.zero & size;
    final center = rect.center;
    final radius = (math.min(size.width, size.height) - strokeWidth) / 2;

    final trackPaint = Paint()
      ..color = trackColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;

    final ringPaint = Paint()
      ..color = ringColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;

    canvas.drawCircle(center, radius, trackPaint);

    const startAngle = -math.pi / 2;
    final sweepAngle = 2 * math.pi * fraction;
    if (sweepAngle > 0) {
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        startAngle,
        sweepAngle,
        false,
        ringPaint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _GaugePainter oldDelegate) =>
      oldDelegate.fraction != fraction || oldDelegate.ringColor != ringColor || oldDelegate.trackColor != trackColor;
}
