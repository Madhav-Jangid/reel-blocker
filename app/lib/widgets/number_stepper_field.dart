import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/app_theme.dart';

/// A labeled numeric input: pill-shaped -/+ step controls either side of a
/// text field you can also type an exact value into directly. Used in place
/// of a [Slider] for settings where the exact number matters
/// (minutes/hours/days) and a slider's drag precision isn't a good fit.
class NumberStepperField extends StatefulWidget {
  final String label;
  final String? helperText;
  final double value;
  final double min;
  final double max;
  final double step;
  final String suffix;
  final int decimals;
  final ValueChanged<double> onChanged;

  const NumberStepperField({
    super.key,
    required this.label,
    required this.value,
    required this.min,
    required this.max,
    required this.step,
    required this.suffix,
    required this.onChanged,
    this.decimals = 0,
    this.helperText,
  });

  @override
  State<NumberStepperField> createState() => _NumberStepperFieldState();
}

class _NumberStepperFieldState extends State<NumberStepperField> {
  late final TextEditingController _controller;
  final _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: _format(widget.value));
    _focusNode.addListener(() {
      if (!_focusNode.hasFocus) _commitText();
    });
  }

  @override
  void didUpdateWidget(NumberStepperField oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Only overwrite the field while the user isn't actively editing it -
    // otherwise an external value change (e.g. a +/- tap) would yank the
    // cursor out from under mid-typing.
    if (!_focusNode.hasFocus && oldWidget.value != widget.value) {
      _controller.text = _format(widget.value);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  String _format(double v) => widget.decimals == 0 ? v.round().toString() : v.toStringAsFixed(widget.decimals);

  double _clamp(double v) => v.clamp(widget.min, widget.max);

  void _step(double delta) {
    final next = _clamp(widget.value + delta);
    _controller.text = _format(next);
    widget.onChanged(next);
  }

  void _commitText() {
    final parsed = double.tryParse(_controller.text);
    if (parsed == null) {
      _controller.text = _format(widget.value);
      return;
    }
    final clamped = _clamp(parsed);
    _controller.text = _format(clamped);
    widget.onChanged(clamped);
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(widget.label, style: Theme.of(context).textTheme.titleSmall),
        if (widget.helperText != null) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(widget.helperText!, style: Theme.of(context).textTheme.bodySmall),
        ],
        const SizedBox(height: AppSpacing.s),
        Row(
          children: [
            _StepButton(
              icon: Icons.remove,
              onPressed: widget.value <= widget.min ? null : () => _step(-widget.step),
            ),
            const SizedBox(width: AppSpacing.s),
            Expanded(
              child: Container(
                height: 48,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: scheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(AppRadius.control),
                ),
                child: TextField(
                  controller: _controller,
                  focusNode: _focusNode,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.titleMedium,
                  keyboardType: TextInputType.numberWithOptions(decimal: widget.decimals > 0),
                  inputFormatters: widget.decimals > 0
                      ? [FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d*'))]
                      : [FilteringTextInputFormatter.digitsOnly],
                  onSubmitted: (_) => _commitText(),
                  decoration: InputDecoration(
                    filled: false,
                    border: InputBorder.none,
                    isDense: true,
                    suffixText: widget.suffix,
                    suffixStyle: Theme.of(context).textTheme.bodyMedium,
                  ),
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.s),
            _StepButton(
              icon: Icons.add,
              onPressed: widget.value >= widget.max ? null : () => _step(widget.step),
            ),
          ],
        ),
      ],
    );
  }
}

class _StepButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback? onPressed;

  const _StepButton({required this.icon, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final enabled = onPressed != null;
    return Material(
      color: enabled ? scheme.primaryContainer : scheme.surfaceContainerHighest,
      shape: const CircleBorder(),
      child: InkWell(
        onTap: onPressed,
        customBorder: const CircleBorder(),
        child: SizedBox(
          width: 48,
          height: 48,
          child: Icon(
            icon,
            size: 20,
            color: enabled ? scheme.onPrimaryContainer : scheme.onSurfaceVariant.withValues(alpha: 0.4),
          ),
        ),
      ),
    );
  }
}
