import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A labeled numeric input: -/+ step buttons either side of a text field you
/// can also type an exact value into directly. Used in place of a [Slider]
/// for settings where the exact number matters (minutes/hours/days) and a
/// slider's drag precision isn't a good fit.
class NumberStepperField extends StatefulWidget {
  final String label;
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
    return Row(
      children: [
        Expanded(child: Text(widget.label, style: Theme.of(context).textTheme.titleMedium)),
        IconButton(
          icon: const Icon(Icons.remove_circle_outline),
          onPressed: widget.value <= widget.min ? null : () => _step(-widget.step),
        ),
        SizedBox(
          width: 110,
          child: TextField(
            controller: _controller,
            focusNode: _focusNode,
            textAlign: TextAlign.center,
            keyboardType: TextInputType.numberWithOptions(decimal: widget.decimals > 0),
            inputFormatters: widget.decimals > 0
                ? [FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d*'))]
                : [FilteringTextInputFormatter.digitsOnly],
            onSubmitted: (_) => _commitText(),
            decoration: InputDecoration(
              isDense: true,
              suffixText: widget.suffix,
              border: const OutlineInputBorder(),
            ),
          ),
        ),
        IconButton(
          icon: const Icon(Icons.add_circle_outline),
          onPressed: widget.value >= widget.max ? null : () => _step(widget.step),
        ),
      ],
    );
  }
}
