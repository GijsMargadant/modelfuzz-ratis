import os
import time
import pickle
import random
import logging
import asyncio
import traceback

from itertools import cycle
from model_fuzz.workers import WorkerUtil

class Fuzzer:
    def __init__(self, load, capture, config = {}) -> None:
        self.config = config
        self.guider = self.config.guider
        self.mutator = self.config.mutator
        self.sch_queue = []
        self.worker_util = WorkerUtil(self.config)
        self.stats = {
            "coverage" : [0],
            "random_traces": 0,
            "mutated_traces": 0,
            "bug_iterations" : []
        }
        self.prev_iters = 0
        self.capture = capture

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

        if load:
            self.load()
    
    def reset(self):
        self.guider.reset()
        # TODO - Worker manager
        self.sch_queue = []
        self.stats = {
            "coverage" : [0],
            "random_schedules": 0,
            "mutated_schedules": 0,
            "bug_iterations" : []
        }
    
    def save(self, iters, random_state):
        path = self.config.save_dir
        os.makedirs(path, exist_ok=True)
        self.guider.save_states(os.path.join(path, f'{self.config.exp_name}_states.pkl'))
        with open(os.path.join(path, f'{self.config.exp_name}_schedules.pkl'), 'wb') as f:
            pickle.dump(self.sch_queue, f)
        
        with open(os.path.join(path, f'{self.config.exp_name}_stats.pkl'), 'wb') as f:
            pickle.dump(self.stats, f)
        
        with open(os.path.join(path, f'{self.config.exp_name}_iters.pkl'), 'wb') as f:
            pickle.dump(iters, f)
        
        with open(os.path.join(path, f'{self.config.exp_name}_random_state.pkl'), 'wb') as f:
            pickle.dump(random_state, f)

    def load(self):
        path = self.config.save_dir
        if os.path.exists(os.path.join(path, f'{self.config.exp_name}_states.pkl')):
            self.guider.load_states(os.path.join(path, f'{self.config.exp_name}_states.pkl'))
        if os.path.exists(os.path.join(path, f'{self.config.exp_name}_schedules.pkl')):
            with open(os.path.join(path, f'{self.config.exp_name}_schedules.pkl'), 'rb') as f:
                self.sch_queue = pickle.load(f)
        if os.path.exists(os.path.join(path, f'{self.config.exp_name}_stats.pkl')):
            with open(os.path.join(path, f'{self.config.exp_name}_stats.pkl'), 'rb') as f:
                self.stats = pickle.load(f)
        if os.path.exists(os.path.join(path, f'{self.config.exp_name}_iters.pkl')):
            with open(os.path.join(path, f'{self.config.exp_name}_iters.pkl'), 'rb') as f:
                self.prev_iters = pickle.load(f)
        
        if os.path.exists(os.path.join(path, f'{self.config.exp_name}_random_state.pkl')):
            with open(os.path.join(path, f'{self.config.exp_name}_random_state.pkl'), 'rb') as f:
                random.setstate(pickle.load(f))

    # def run_controlled(self):
    #     # TODO - Clusterify
    #     file = self.args.control
    #     logging.info(file)
    #     with open(file, 'rb') as f:
    #         trace = pickle.load(f)
    #     self.run_iteration(0, trace, controlled=True)

    def seed(self):
        logging.info("Seeding...")
        self.sch_queue = []
        for i in range(self.config.seed_population):
            crash_points = {}
            start_points = {}
            schedule = []
            node_ids = list(range(1, self.config.nodes+1))
            for c in random.sample(range(0, self.config.horizon, 2), self.config.crash_quota):
                node_id = random.choice(node_ids)
                crash_points[c] = node_id
                s = random.choice(range(c, self.config.horizon))
                start_points[s] = node_id

            client_requests = random.sample(range(self.config.horizon), self.config.test_harness)
            for choice in random.choices(node_ids, k=self.config.horizon):
                max_messages = random.randint(0, self.config.max_messages_to_schedule)
                schedule.append((choice, max_messages))

            crashed = set()
            trace = []
            for j in range(self.config.horizon):
                if j in start_points and start_points[j] in crashed:
                    node_id = start_points[j]
                    trace.append({"type": "Start", "node": node_id, "step": j})
                    crashed.remove(node_id)
                if j in crash_points:
                    node_id = crash_points[j]
                    trace.append({"type": "Crash", "node": node_id, "step": j})
                    crashed.add(node_id)
                if j in client_requests:
                    trace.append({"type": "ClientRequest", "step": j})

                trace.append({"type": "Schedule", "node": schedule[j][0], "step": j, "max_messages": schedule[j][1]})

            self.sch_queue.append(([e for e in trace], 0))
        logging.info("Finished seeding")
    
    def generate_ports(self, i):
        ports = []
        ports.append(self.config.base_network_port + i)
        for j in range(self.config.nodes):
            ports.append(self.config.base_server_client_port + ((i * self.config.nodes) + j))
        return ports

    
    def generate_coros(self, iterations, mimics, run_ids, capture):
        coros = []
        for i in range(self.config.num_workers):
            ports = self.generate_ports(i)
            coros.append(self.worker_util.create_and_run_cluster(self.config,
                                                                 ports,
                                                                 run_ids[i],
                                                                 self.config.base_peer_port + (i * self.config.nodes),
                                                                 next(self.group_ids),
                                                                 iterations[i],
                                                                 capture,
                                                                 mimics[i]))

        return coros

    async def run(self):
        try:
            logging.info("Starting fuzzer loop")
            random_ =  'random' in self.config.exp_name
            start = time.time()
            iters = 0
            for i in range(0, self.config.iterations, self.config.num_workers):
                iters = i + self.prev_iters
                # if iter_count >= self.config.iterations:
                #     return True
                if iters != 0 and i % self.config.save_every == 0:
                    self.save(iters, random.getstate())
                
                if iters % self.config.seed_frequency == 0 and not random_:
                    self.seed()

                logging.info(f'##### Starting fuzzer iterations {iters}-{iters+self.config.num_workers-1} #####')
                mimics = []
                for j in range(self.config.num_workers):
                    to_mimic = None
                    if len(self.sch_queue) > 0:
                        to_mimic = self.sch_queue.pop(0)[0]
                    if to_mimic is None:
                        self.stats["random_traces"] += 1
                    else:
                        self.stats["mutated_traces"] += 1
                    mimics.append(to_mimic)
                # TODO - Worker run
                # results = loop.run_until_complete(asyncio.wait(self.generate_coros(range(iters, iters+self.config.num_workers, 1), mimics),
                #                                             return_when=asyncio.ALL_COMPLETED))
                run_ids = [iters+j for j in range(self.config.num_workers)]
                results = await asyncio.gather(*self.generate_coros(range(iters, iters+self.config.num_workers, 1), mimics, run_ids, self.capture))
                k = 0
                # await asyncio.sleep(1)
                for trace, event_trace, is_buggy in results:
                    if trace is None:
                        continue
                    if is_buggy:
                        self.stats['bug_iterations'].append(iters+k)

                    new_states = self.guider.check_new_state(trace, event_trace, str(iters+k), record=False)
                    logging.info(f'New states: {new_states}')
                    logging.info(f'Total states: {self.guider.coverage()}')
                    self.stats["coverage"].append(self.guider.coverage())

                    if new_states > 0 and not random_:
                        for j in range(new_states * self.config.mutations_per_trace):
                            try:
                                mutated_sch = self.mutator.mutate(trace, self.config)
                                if mutated_sch is not None:
                                    self.sch_queue.append((mutated_sch, new_states))
                            except Exception as e:
                                logging.error(f"Error mutating {iters+k}")
                                traceback.print_exc()
                            finally:
                                pass
                    k += 1
                # self.sch_queue.sort(key=lambda x: x[1])

            self.stats["runtime"] = time.time() - start
            logging.info(self.stats)
            self.save(self.config.iterations + self.prev_iters, random.getstate())
        except Exception as e:
            traceback.print_exc()

        return True