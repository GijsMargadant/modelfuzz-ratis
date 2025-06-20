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

### Find iterations that violate single leader constraint
`multiple_leader.py` finds the iterations that, at some point, has two leaders. Provide it with an intput folder that contains, for each iteration, a folder with the events.json file. The event.json files are examined and sent to the TLC server in order to retrieve abstract model states. If at least one of the states violates the single leader constraint, the states for that iteration are stored in the output file. Usage: `uv run multiple_leader.py --input <iteration_folder> --output <output.json> [--tlc TLC_address]`. Example:

```bash
uv run .\multiple_leader.py -i ..\modelfuzz-java\output_extra\k-path\iterations -o .\results\buggy\buggyIterations.json
```
This creates a a json file with iteration numbers as keys and list of model state represented as strings as values.