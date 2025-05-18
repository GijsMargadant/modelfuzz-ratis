import os
import json
import random
import argparse

from modelfuzz.fuzzer import Fuzzer
from modelfuzz.mutator import MutatorType
from modelfuzz.fuzzer_type import FuzzerType

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()

    # Run parameters
    parser.add_argument('-ct', '--control', type=str) # For replication
    parser.add_argument('-w', '--workers', type=int, default=5)
    parser.add_argument('-to', '--timeout', type=int, default=60)
    parser.add_argument('-jp', '--jar-path', type=str, default='../ratis-examples/target/ratis-examples-2.5.1.jar')
    parser.add_argument('-td', '--tlc-dir', type=str, default='../../tlc-controlled-with-benchmarks/tlc-controlled')

    # Files
    parser.add_argument('-sd', '--save-dir', type=str, default='./output/saved')
    parser.add_argument('-rd', '--result-dir', type=str, default='./output/results')
    parser.add_argument('-ed', '--errors-dir', type=str, default='./output/errors')
    parser.add_argument('-rdd', '--ratis-data-dir', type=str, default='./data')

    # Experiment parameters
    parser.add_argument('-l', '--load', type=str, default=None)
    parser.add_argument('-s', '--seed', type=str, default='delft')
    parser.add_argument('-e', '--experiments', type=int, default=1)
    parser.add_argument('-f','--fuzzers', nargs='+', type=FuzzerType, default=[FuzzerType.MODELFUZZ, FuzzerType.RANDOM, FuzzerType.TRACE])



    # Fuzzer parameters
    parser.add_argument('-i', '--iterations', type=int, default=100)
    parser.add_argument('-n', '--nodes', type=int, default=3)
    parser.add_argument('-cr', '--client-requests', type=int, default=3)
    parser.add_argument('-sp', '--seed-population', type=int, default=20)
    parser.add_argument('-sf', '--seed-frequency', type=int, default=200)
    parser.add_argument('-cq', '--crash-quota', type=int, default=5)
    parser.add_argument('-st', '--steps', type=int, default=500)
    parser.add_argument('-mm', '--max-messages', type=int, default=5)
    parser.add_argument('-mc', '--mutation-count', type=int, default=10)
    parser.add_argument('-mps', '--mutations-per-schedule', type=int, default=5)
    parser.add_argument('-mt','--mutator-type', type=MutatorType, default=MutatorType.ALL)

    parser.add_argument('-bfp', '--base-network-port', type=int, default=7071)
    parser.add_argument('-blp', '--base-listener-port', type=int, default=10000)
    parser.add_argument('-bpp', '--base-node-port', type=int, default=6000)
    parser.add_argument('-btp', '--base-tlc-port', type=int, default=2023)
    
    # parser.add_argument('-rs', '--replica-script', type=str, default='../ratis-examples/target/ratis-examples-2.5.1.jar')
    # parser.add_argument('-se', '--save-every', type=int, default=100)

    return parser.parse_args()

def main() -> None:
    args = parse_args()
    if args.seed_frequency % args.workers != 0:
        print('Seed frequency must be divisible by the number of workers!')
        return
    print('Setting seed')
    random.seed(args.seed.__hash__())
    args.seed = args.seed.__hash__()
    exp_stats = {}
    for i in range(args.experiments):
        print('Starting fuzzer')
        fuzzer = Fuzzer(args)
        stats = fuzzer.run()
        exp_stats[i] = stats
        print('Re-setting seed')
        args.seed += random.randint(0, 1e20)
        random.seed(args.seed)
    
        # TODO - Run statistical tests
        os.makedirs(args.result_dir, exist_ok=True)
        with open(os.path.join(args.result_dir, 'experiment_stats.json'), 'w') as f:
            json.dump(exp_stats, f, indent='\t')
        print(exp_stats)

if __name__ == '__main__':
    main()