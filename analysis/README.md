### Setup
Make sure [UV](https://docs.astral.sh/uv/getting-started/installation/) is installed.

### Unique state parts
The model state is a combination of induvidual state parts, e.g., `log`, `currentTerm`, etc. `unique_states.py` finds, for each part, all unique values that were found during the search. Function usage: `uv run unique_states.py <input.json> <output.json>`. Example:

```bash
uv run unique_states.py ../modelfuzz-java/output_2/k-path/unique_states.json ./results/k=1/unique_state_parts.json
```

### Create coverage plot
`coverage_plot.py` visualizes the state coverage over time across one or more runs. Each line in the resulting plot represents a separate run. Usage: `uv run coverage_plot.py --input <run1.json> <run1.json> --output <output.pdf> [--labels <label1> <label2> ...] [--save-csv]`. Example:

```bash
uv run coverage_plot.py --input ../modelfuzz-java/output_2/k-path/stats.json ../modelfuzz-java/output_3/k-path/stats.json ../modelfuzz-java/output_4/k-path/stats.json --output ./results/coverage/coverage.pdf --labels k=1 k=2 k=3 --save-csv
```