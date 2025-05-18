import os
import time
import random
import pickle
import asyncio
import logging
import argparse
import traceback

from pandas import read_csv
from types import SimpleNamespace

from model_fuzz.fuzzer import Fuzzer
from model_fuzz.guider import GuiderType
from model_fuzz.guider import TLCGuider, TraceGuider, EmptyGuider
from model_fuzz.mutator import SwapMutator, SwapCrashStepsMutator, SwapCrashNodesMutator, CombinedMutator, MaxMessagesMutator, DefaultMutator



def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('-v', '--verbose', action='store_true')
    parser.add_argument('-c', '--config', type=str, default='random_state_config.csv')
    parser.add_argument('-l', '--load', action='store_true')
    parser.add_argument('-cp', '--capture', action='store_true')
    parser.add_argument('-ct', '--control', type=str)
    parser.add_argument('-s', '--seed', type=int, default=123456)

    parser.add_argument('-i', '--iterations', type=int, default=10000)
    parser.add_argument('-n', '--nodes', type=int, default=3)

    parser.add_argument('-cq', '--crash-quota', int, default=1)
    parser.add_argument('-bnp', '--base-network-port', type=int, default=7071)
    parser.add_argument('-bscp', '--base-server-client-port', type=int, default=10000)
    parser.add_argument('-bsp', '--base-peer-port', type=int, default=6000)
    parser.add_argument('-btp', '--base-tlc-port', type=int, default=2023)
    parser.add_argument('-h', '--horizon', type=int, default=100)
    parser.add_argument('-mpt', '--mutations-per-trace', type=int, default=5)
    parser.add_argument('-sp', '--seed-population', type=int, default=20)
    parser.add_argument('-sf', '--seed-frequency', type=int, default=200)
    parser.add_argument('-jp', '--jar-path', type=str, default='../ratis-examples/target/ratis-examples-2.5.1.jar')
    parser.add_argument('-mm', '--max-messages', type=int, default=20)
    parser.add_argument('-w', '--workers', type=int, default=5)
    parser.add_argument('-se', '--save-every', type=int, default=100)
    parser.add_argument('-ep', '--error-path', type=str, default=)
    parser.add_argument('-sp', '--snapshot-path', type=str, default=)
    parser.add_argument('-sd', '--save-dir', type=str, default=)


    return parser.parse_args()

def validate_config(config):
        new_config = SimpleNamespace()
        
        mutators = [SwapMutator(), SwapCrashNodesMutator(), SwapCrashStepsMutator()]
        if "mutator" not in config:
            new_config.mutator = CombinedMutator(mutators)
        else:
            if config["mutator"] == "all":
                new_config.mutator = CombinedMutator(mutators)
            else:
                new_config.mutator = DefaultMutator()
            
        
        if "base_network_port" not in config:
            new_config.base_network_port = 7071
        else:
            new_config.base_network_port = config["base_network_port"]
        
        if "base_server_client_port" not in config:
            new_config.base_server_client_port = 10000
        else:
            new_config.base_server_client_port = config["base_server_client_port"]
            
        if "base_peer_port" not in config:
            new_config.base_peer_port = 6000
        else:
            new_config.base_peer_port = config["base_peer_port"]

        if "iterations" not in config:
            new_config.iterations = 10000
        else:
            new_config.iterations = config["iterations"]

        if "horizon" not in config:
            new_config.horizon = 100
        else:
            new_config.horizon = config["horizon"]

        if "nodes" not in config:
            new_config.nodes = 3
        else:
            new_config.nodes = config["nodes"]
        
        if "crash_quota" not in config:
            new_config.crash_quota = 1
        else:
            new_config.crash_quota = config["crash_quota"]

        if "mutations_per_trace" not in config:
            new_config.mutations_per_trace = 5
        else:
            new_config.mutations_per_trace = config["mutations_per_trace"]
        
        if "seed_population" not in config:
            new_config.seed_population = 20
        else:
            new_config.seed_population = config["seed_population"]

        new_config.seed_frequency = 200
        if "seed_frequency" in config:
            new_config.seed_frequency = config["seed_frequency"]
        
        if "test_harness" not in config:
            new_config.test_harness = 3
        else:
            new_config.test_harness = config["test_harness"]

        base_tlc_port = 2023
        if "base_tlc_port" in config:
            base_tlc_port = config["base_tlc_port"]
        
        if 'guider' in config:
            if config['guider'] == 'trace':
                new_config.guider = GuiderType.TRACE # TraceGuider(tlc_addr)
            elif config['guider'] == 'tlc':
                new_config.guider = GuiderType.TLC # TLCGuider(tlc_addr)
            elif config['guider'] == 'empty':
                new_config.guider = GuiderType.EMPTY # EmptyGuider(tlc_addr)
            else:
                new_config.guider = GuiderType.TLC # TLCGuider(tlc_addr)
        else:
            new_config.guider = GuiderType.TLC # TLCGuider(tlc_addr)


        if 'exp_name' not in config:
            new_config.exp_name = 'random'
        else:
            new_config.exp_name = config['exp_name']
        
        if 'jar_path' not in config:
            new_config.jar_path = '../ratis-examples/target/ratis-examples-2.5.1.jar'
        else:
            new_config.jar_path = config['jar_path']

        if 'error_path' not in config:
            new_config.error_path = 'output/errors'
        else:
            new_config.error_path = config['error_path']

        new_config.snapshots_path = "/tmp/ratis"
        if "snapshots_path" in config:
            new_config.snapshots_path = config["snapshots_path"]
        
        if "max_message_to_schedule" not in config:
            new_config.max_messages_to_schedule = 20
            
        save_dir = 'output/saved'
        if "save_dir" in config:
            new_config.save_dir = config["save_dir"]
        else:
            new_config.save_dir = save_dir
        
        if 'save_every' not in config:
            new_config.save_every = 10
        else:
            new_config.save_every = config['save_every']

        if 'num_workers' not in config:
            new_config.num_workers = 10
        else:
            new_config.num_workers = config['num_workers']

        return new_config

def main():
    args = parse_args()
    if not args.verbose:
        logging.disable()
    else:
        logging.basicConfig(level=logging.INFO)
    
    experiment_config = read_csv(args.config, index_col=False)
    random_seed = time.time_ns()
    random.seed(random_seed)
    os.makedirs('output/saved', exist_ok=True)
    with open('output/saved/random_seed.pkl', 'wb') as f:
        pickle.dump(random_seed, f)
    
    load = args.load
    capture = args.capture
    for index, row in experiment_config.iterrows():
        config = validate_config(row.to_dict())
        fuzzer = Fuzzer(load, capture, config)
        # if args.control is not None:
        #     fuzzer.run_controlled()
        #     break
        try:
            asyncio.run(fuzzer.run())
        except:
            pass
        finally:
            load = True

if __name__ == '__main__':
    try:
        main() # random 447
    except Exception as e:
        traceback.print_exc()
