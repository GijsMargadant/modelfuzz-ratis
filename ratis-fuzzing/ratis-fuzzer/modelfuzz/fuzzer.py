
import os
import time
import random
import traceback
import concurrent.futures

from itertools import cycle
from modelfuzz.cluster import Error
from modelfuzz.cluster import Cluster
from modelfuzz.guider import GuiderFactory
from modelfuzz.fuzzer_type import FuzzerType
from modelfuzz.mutator import MutatorFactory

class Fuzzer():
    def __init__(self, params) -> None:
        self.params = params
        self.sch_pool = []
        self.stats = {}

        self.mutator = MutatorFactory.get_mutator(self.params.mutator_type, self.params)

        self.group_ids = cycle([
            '02511d47-d67c-49a3-9011-abb3109a44c1',
            '02511d47-d67c-49a3-9011-abb3109a44c2',
            '02511d47-d67c-49a3-9011-abb3109a44c3',
            '02511d47-d67c-49a3-9011-abb3109a44c4',
            '02511d47-d67c-49a3-9011-abb3109a44c5',
            '02511d47-d67c-49a3-9011-abb3109a44c6',
            '02511d47-d67c-49a3-9011-abb3109a44c7',
            '02511d47-d67c-49a3-9011-abb3109a44c8',
            '02511d47-d67c-49a3-9011-abb3109a44c9',
            '02511d47-d67c-49a3-9011-abb3109a44ca',
            '02511d47-d67c-49a3-9011-abb3109a44cb',
            '02511d47-d67c-49a3-9011-abb3109a44cc',
            '02511d47-d67c-49a3-9011-abb3109a44cd',
            '02511d47-d67c-49a3-9011-abb3109a44ce',
            '02511d47-d67c-49a3-9011-abb3109a44cf'
        ])

#     Task was destroyed but it is pending!
# task: <Task pending name='Task-31' coro=<RequestHandler.start() running at /Users/berkay/Library/Python/3.9/lib/python/site-packages/aiohttp/web_protocol.py:505> wait_for=<Future pending cb=[<TaskWakeupMethWrapper object at 0x103a53880>()]>>
# Task was destroyed but it is pending!
# task: <Task pending name='Task-32' coro=<RequestHandler.start() running at /Users/berkay/Library/Python/3.9/lib/python/site-packages/aiohttp/web_protocol.py:505> wait_for=<Future pending cb=[<TaskWakeupMethWrapper object at 0x103a53e20>()]>>

    def run(self) -> dict:
        for fuzzer in self.params.fuzzers:
            self.stats[fuzzer.value] = {
                'coverage': [],
                'random_schedules': 0,
                'mutated_schedules': 0,
                'bugs': [],
                'runtime': time.time()
            }

            print('Instantiating ', fuzzer.value)
            guider = GuiderFactory.get_guider(fuzzer)

            for i in range(0, self.params.iterations, self.params.workers):
                if self.params.workers > 1:
                    print(f'Iterations {i+1}-{i + self.params.workers}')
                else:
                    print(f'Iteration {i}')
                if i % self.params.seed_frequency == 0:
                    self.sch_pool.clear()
                    self.generate_schedules(self.params.seed_population)

                if len(self.sch_pool) < self.params.workers:
                    self.generate_schedules(self.params.workers - len(self.sch_pool))

                mutated_count, random_count, run_configs = self.get_configs(fuzzer, i)
                self.stats[fuzzer.value]['mutated_schedules'] += mutated_count
                self.stats[fuzzer.value]['random_schedules'] += random_count
                results = self.run_batch(run_configs)
                for j, (schedule, event_trace, errors) in enumerate(results):
                    # Add new states
                    new_states = guider.add_and_get_new_states(event_trace)
                    # print('New states: ',  new_states)
                    # Check if erroneous
                    if len(errors) > 0:
                        self.stats[fuzzer.value]['bugs'].append((fuzzer, i+j))
                        os.makedirs(os.path.join(self.params.errors_dir, f'{fuzzer.value}_{i+j}'), exist_ok=True)
                        for error in errors:
                            error.states = guider.get_states(event_trace)
                            error.log_error(os.path.join(self.params.errors_dir, f'{fuzzer.value}_{i+j}'))
                        print(f'{fuzzer.name} found error(s) at iteration: {i+j}')
                    else:
                        if new_states > 0 and fuzzer != FuzzerType.RANDOM:
                            for _ in range(self.params.mutations_per_schedule * new_states):
                                new_sch = self.mutator.mutate(schedule)
                                self.sch_pool.append((True, new_sch))
                    
                    self.stats[fuzzer.value]['coverage'].append(guider.get_coverage())

            self.stats[fuzzer.value]['runtime'] = time.time() - self.stats[fuzzer.value]['runtime']
            print(self.stats)
            self.sch_pool.clear()
        return self.stats


    def get_configs(self, fuzzer, iteration) -> tuple[int, int, list]:
        # print('Generating configs')
        run_configs = []
        random_count = 0
        mutated_count = 0
        for i in range(self.params.workers):
            is_mutated, sch = self.sch_pool.pop(0)
            if is_mutated:
                mutated_count += 1
            else:
                random_count += 1

            run_configs.append({'run_id': i + iteration,
                                'fuzzer': fuzzer,
                                'group_id': next(self.group_ids),
                                'node_ports': [self.params.base_node_port + (self.params.nodes * i) + j for j in range(self.params.nodes)],
                                'listener_ports': [self.params.base_listener_port + (self.params.nodes * i) + j for j in range(self.params.nodes)],
                                'fuzzer_port': self.params.base_network_port + i,
                                'schedule': sch})
        return (mutated_count, random_count, run_configs)

    def run_batch(self, run_configs) -> list[tuple[list, tuple[list, list, list[Error]]]]:
        # print('Running batch')
        results = None
        with concurrent.futures.ProcessPoolExecutor(max_workers=self.params.workers) as executor:
            try:
                results = list(executor.map(self.run_instance, run_configs))
            except Exception as e:
                traceback.print_exc()
            finally:
                pass
        return results


    def run_instance(self, run_config) -> tuple:
        # print('Running instance')
        cluster = Cluster(self.params, run_config)
        return cluster.run()

    def generate_schedules(self, num=1) -> None:
        # print(f'Generating schedules: {num}')
        schedules = []
        nodes = [i+1 for i in range(self.params.nodes)]
        for j in range(num):
            schedule = []
            # Add 'Schedule' type steps
            for i in range(self.params.steps):
                node = random.sample(nodes, 1)[0]
                to = random.sample([n for n in nodes if n != node], 1)[0]
                max_msgs = random.randint(1, self.params.max_messages)
                step = {
                    'type': 'Schedule',
                    'node': node,
                    'to': to,
                    'max_msgs': max_msgs
                }
                schedule.append(step)

            # Add 'Crash' type steps
            crash_steps = []
            for i in range(self.params.crash_quota):
                index = random.randint(0, len(schedule))
                node = random.sample(nodes, 1)[0]
                step = {
                    'type': 'Crash',
                    'node': node,
                    'crash_id': i
                }
                crash_steps.append((index, node))
                schedule.insert(index, step)


            # Add 'Restart' type steps
            for i, (crash_index, node) in enumerate(crash_steps):
                index = random.randint(crash_index+1, len(schedule))
                step = {
                    'type': 'Restart',
                    'node': node,
                    'crash_id': i
                }
                schedule.insert(index, step)
            
            crashed = []
            for i, step in enumerate(schedule):
                if step['type'] == 'Crash':
                    crashed.append((i, step))
                
                if step['type'] == 'Restart':
                    index = -1
                    for k in range(len(crashed)):
                        if step['node'] == crashed[k][1]['node'] and i > crashed[k][0]:
                            index = k
                            break
                        
                    if index >= 0:
                        crashed.pop(index)

            # assert(len(crashed) == 0)

            # Add 'ClientRequest' type steps
            for i in range(self.params.client_requests):
                index = random.randint(0, len(schedule))
                node = random.sample(nodes, 1)[0]
                step = {
                    'type': 'ClientRequest',
                    'node': 0
                }
                schedule.insert(index, step)
            schedules.append((False, schedule))
        self.sch_pool.extend(schedules)

