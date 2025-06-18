# /// script
# requires-python = ">=3.13"
# dependencies = [
#     "requests",
# ]
# ///
import argparse
import json
from pathlib import Path
from typing import Dict, List
import requests

def find_multiple_leader_iterations(baseFolder: Path, tlc: str) -> Dict[int, List[str]]:
    multiple_leader_iterations = {}

    for iterationFolder in baseFolder.iterdir():
        if iterationFolder.is_dir():
            eventsFile = iterationFolder / "events.json"
            if eventsFile.exists():
                states = get_model_states(eventsFile, tlc)
                
                if any([state.count("leader") > 1 for state in states]):
                    multiple_leader_iterations[int(iterationFolder.name)] = states
            else:
                print(f"Looked for events file, but could not find any: {eventsFile}")

    return multiple_leader_iterations
            
            
def get_model_states(eventsFile: Path, tlc_addr: str) -> List[str]:
    with open(eventsFile, "r") as f:
            traces = json.load(f)
            
    traces["Events"].append({"Reset": True})
    data = json.dumps(traces["Events"])
    try:
        response = requests.post(f"http://{tlc_addr}/execute", data=data, headers={"Content-Type": "application/json"})
        response.raise_for_status()
    except:
        raise Exception("Unable to post events. Is the TLC server running?")
    
    body = response.json()
    return body["states"]

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Detect iterations with multiple leaders based on TLC simulation.")
    parser.add_argument("--input", "-i", type=Path, required=True, help="Path to the folder containing iteration folders.")
    parser.add_argument("--output", "-o", type=Path, required=True, help="Output path for the JSON file.")
    parser.add_argument("--tlc", "-t", type=str, default="127.0.0.1:2023", help="Address of the TLC server (default: 127.0.0.1:2023)")

    args = parser.parse_args()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    buggyIterations = find_multiple_leader_iterations(args.input, args.tlc)
    with args.output.open("w", encoding="utf-8") as f:
        json.dump(buggyIterations, f, indent=2, sort_keys=True)