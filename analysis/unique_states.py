import json
from pathlib import Path
from typing import Dict, Set
import argparse


def main(inputPath: str, outputPath: str):
    inputFile = Path(inputPath)
    if not inputFile.exists():
        raise ValueError(f"Input file does not exist: {inputFile}")

    stateParts: Dict[str, Set[str]] = {}

    with open(inputFile, "r") as f:
        data = json.load(f)

    for iterData in data["iterations"]:
        if not iterData["states"]: continue
        for state in iterData["states"]:
            parts = split_state_parts(state["Repr"])
            for part in parts:
                key, value = map(str.strip, part.split("="))
                if key in stateParts:
                    stateParts[key].add(value)
                else:
                    stateParts[key] = {value}

    outputFilePath = Path(outputPath)
    outputFilePath.parent.mkdir(parents=True, exist_ok=True)
    with open(outputPath, "w") as outputFile:
        serializable = {k: list(v) for k, v in stateParts.items()}
        json.dump(serializable, outputFile, indent=2)


def split_state_parts(s: str) -> list[str]:
    result = []
    current = []
    depth_square = depth_curly = 0

    for char in s:
        if char == ',' and depth_square == depth_curly == 0:
            result.append(''.join(current).strip())
            current = []
        else:
            current.append(char)
            if char == '[':
                depth_square += 1
            elif char == ']':
                depth_square -= 1
            elif char == '{':
                depth_curly += 1
            elif char == '}':
                depth_curly -= 1

    if current:
        result.append(''.join(current).strip())

    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Process states from a JSON file.")
    parser.add_argument("inputPath", help="Path to the input JSON file")
    parser.add_argument("outputPath", help="Path to save the output JSON file")
    args = parser.parse_args()
    
    main(args.inputPath, args.outputPath)