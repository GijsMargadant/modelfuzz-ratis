# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "pandas",
#     "seaborn",
# ]
# ///
import matplotlib
matplotlib.use("Agg")

import json
import argparse
from pathlib import Path
from typing import Dict, List
import pandas as pd
import seaborn as sns


def main(inputFiles: List[str], outputFile: str, labels: List[str] | None = None, save_csv: bool = False):
    inputs = verify_paths(inputFiles)
    labels = verify_labels(labels, len(inputFiles))
    
    coverage_data: Dict[str, List[int]] = {}
    for i, input in enumerate(inputs):
        with open(input, "r") as f:
            data = json.load(f)
        coverage_data[labels[i]] = data["Coverages"]
    
    outputPath = Path(outputFile)
    outputPath.parent.mkdir(parents=True, exist_ok=True)
    
    df = pd.DataFrame(coverage_data)
    df = df.reset_index().rename(columns={"index": "iteration"})
    if save_csv:
        file = outputPath.with_suffix(".csv")
        df.to_csv(file, index=False)
    df = df.melt(id_vars="iteration", var_name="label", value_name="coverage")
    
    plt = sns.lineplot(data=df, x="iteration", y="coverage", hue="label", palette="colorblind")
    plt.set(title="State coverage", xlabel="Iteration", ylabel="#States")
    plt.get_figure().savefig(outputPath)


def verify_paths(inputFiles: List[str]) -> List[Path]:
    paths = []
    for path in map(Path, inputFiles):
        if not path.exists():
            raise ValueError(f"Input file does not exist: {path}")
        paths.append(path)
    return paths


def verify_labels(labels: List[str] | None, numInputs: int) -> List[str]:
    if not labels:
        return [n for n in range(numInputs)]
    elif len(labels) == numInputs:
        return labels
    else:
        raise ValueError(f"Number of labels must be equal to number of inputs: {len(labels)} vs {numInputs}")
        
        
if __name__ == "__main__":
    # inputFiles = [
    #     "../modelfuzz-java/output_2/k-path/stats.json",
    #     "../modelfuzz-java/output_3/k-path/stats.json",
    #     "../modelfuzz-java/output_4/k-path/stats.json",
    # ]
    # outputFile = "./results/coverage/coverage.pdf"
    # labels = [
    #     "k=1",
    #     "k=2",
    #     "k=3",
    # ]
    # main(inputFiles, outputFile, labels=labels, save_csv=True)
    
    parser = argparse.ArgumentParser(description="Plot state coverage from multiple JSON files.")
    
    parser.add_argument(
        "--input", "-i", 
        nargs="+", 
        required=True, 
        help="List of input JSON files containing 'Coverages' arrays."
    )
    parser.add_argument(
        "--output", "-o", 
        required=True, 
        help="Path to the output PDF file."
    )
    parser.add_argument(
        "--labels", "-l", 
        nargs="+", 
        help="Optional labels corresponding to each input file. Must match the number of inputs."
    )
    parser.add_argument(
        "--save-csv", 
        action="store_true", 
        help="Save the raw coverage data to a CSV file alongside the plot."
    )
    
    args = parser.parse_args()
    
    main(args.input, args.output, labels=args.labels, save_csv=args.save_csv)
