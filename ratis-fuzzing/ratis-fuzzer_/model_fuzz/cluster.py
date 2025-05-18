import os
import time
import pickle
import random
import shutil
import asyncio
import logging
import threading
import traceback
import subprocess

from model_fuzz.network import Network
from model_fuzz.server import RatisServer
from model_fuzz.client import RatisClient

class RatisCluster:
    lock = threading.Lock()

    def __init__(self, config, ports, run_id, base_peer_port, group_id, capture) -> None:
        self.config = config
        self.fuzzer_port = ports[0]
        self.server_client_ports = ports[1:]
        self.run_id = run_id
        self.base_peer_port = base_peer_port
        self.group_id = group_id
        self.network_error = False
        self.capture = capture

        try:
            self.network = Network(self.config, ("127.0.0.1", self.fuzzer_port))
        except:
            self.network_error = True
        finally:
            pass
        self.timeout = 60
        self.client_request_counter = 1
        self.error_flag = False
        self.error_logs = []

        self.servers = {}
        self.peer_addresses = ','.join([f'127.0.0.1:{self.base_peer_port+i}' for i in range(self.config.nodes)])
        for i in range(1, self.config.nodes+1, 1):
            self.servers[i] = RatisServer(
                self.config,
                self.timeout,
                self.run_id,
                self.fuzzer_port,
                self.server_client_ports[i-1],
                i,
                self.peer_addresses,
                self.group_id, self.capture)
        self.clients = []

        # os.makedirs(f'dump/{self.run_id}', exist_ok=True)
        self.elle_file = f'dump/{self.run_id}/elle.edn'
        self.elle_cmd = f'java -jar ../../elle-cli/target/elle-cli-0.1.7-standalone.jar --model counter {self.elle_file}'

        logging.debug(f'RatisCluster {self.run_id} created.')
    
    def shutdown(self):
        self.network.shutdown()

        if os.path.exists(f'./data/{self.run_id}'):
            shutil.rmtree(f'./data/{self.run_id}')

        # if os.path.exists(f'./dump/{self.run_id}'):
        #     shutil.rmtree(f'./dump/{self.run_id}')

        logging.info(f'Cluster {self.run_id} shutdown.')

    def start(self):
        logging.debug('RatisCluster starting.')
        self.network.run()
        self.start_servers()

    def start_servers(self):
       for server in self.servers.values():
           server.start()

    def end_process(self):
        for server in self.servers.values():
           server.shutdown()
        
        for client in self.clients:
            client.shutdown()
    
    async def run_iteration(self, iteration, mimic=None):
        if self.network_error:
            return None, None, None
        # TODO - Update
        logging.info(f'Starting iteration {iteration}')
        trace = []
        crashed = set()

        crash_points = {}
        start_points = {}
        schedule = []
        client_requests = []
        if mimic is None:
            node_ids = [i for i in range(1,self.config.nodes+1)]
            for c in random.sample(range(0, self.config.horizon, 2), self.config.crash_quota):
                node_id = random.choice(node_ids)
                crash_points[c] = node_id
                s = random.choice(range(c, self.config.horizon))
                start_points[s] = node_id

            client_requests = random.sample(range(self.config.horizon), self.config.test_harness)
            for choice in random.choices(node_ids, k=self.config.horizon):
                # to = random.choice([node for node in node_ids if node != choice])
                max_messages = random.randint(0, self.config.max_messages_to_schedule)
                schedule.append((choice, max_messages))
        else:
            schedule = [(1, random.randint(0, self.config.max_messages_to_schedule)) for i in range(self.config.horizon)]
            for ch in mimic:
                if ch["type"] == "Crash":
                    crash_points[ch["step"]] = ch["node"]
                elif ch["type"] == "Start":
                    start_points[ch["step"]] = ch["node"]
                elif ch["type"] == "ClientRequest":
                    client_requests.append(ch["step"])
                elif ch["type"] == "Schedule":
                    if ch['step'] > len(schedule):
                        continue
                    schedule[ch["step"]] = (ch["node"], ch["max_messages"])

        logging.debug("Starting cluster")
        self.start()
        # await asyncio.sleep(2)
        while self.network.check_replicas():
            # print('Waiting replicas')
            if self.check_error_flag():
                break
            await asyncio.sleep(1e-3)
        
        while not self.network.first_message:
            if self.check_error_flag():
                break
            await asyncio.sleep(1e-3)

        event_trace = []
        try:
            for i in range(self.config.horizon):
                if i > len(schedule):
                    break
                logging.debug("Taking step {}".format(i))
                if self.check_error_flag():
                    break
                if i in start_points and start_points[i] in crashed:
                    node_id = start_points[i]
                    logging.debug(f"Starting crashed node {node_id}")
                    self.servers[node_id].start(True)
                    trace.append({"type": "Start", "node": node_id, "step": i})
                    self.network.add_event({"name": "Add", "params": {"i": node_id, "node": node_id}})
                    crashed.remove(node_id)
                
                if i in crash_points:
                    node_id = crash_points[i]
                    logging.debug(f"Crashing node {node_id}")
                    if node_id not in crashed:
                        # self.network.send_crash(node_id)
                        self.servers[node_id].shutdown()
                    crashed.add(node_id)
                    trace.append({"type": "Crash", "node": node_id, "step": i})
                    self.network.add_event({"name": "Remove", "params": {"i": node_id, "node": node_id}})
                
                # key = f'{schedule[i][0]}_{schedule[i][1]}'
                key = f'{schedule[i][0]}'
                # logging.info(f'Scheduling node {key}.')
                logging.debug('---------------------------------------------------------------------')
                if not self.network.check_mailboxes(key):
                    logging.debug('Waiting for message.')
                    await asyncio.sleep(1e-1)
                else:
                    logging.debug('Message already arrived, did not wait.')
                if schedule[i][0] not in crashed:
                    self.network.schedule_replica(schedule[i][0], schedule[i][1])
                    trace.append({"type": "Schedule", "node": schedule[i][0], "step": i, "max_messages": schedule[i][1]})
                else:
                    logging.debug('Node crashed, did not schedule.')
                logging.debug('---------------------------------------------------------------------')

                if i in client_requests:
                    try:
                        logging.debug(f"Executing client request {i}")                        
                        client = RatisClient(self.config,
                                             self.timeout,
                                             self.client_request_counter,
                                             self.elle_file,
                                             self.peer_addresses,
                                             self.group_id)
                        client.start()
                        self.client_request_counter += 1
                        self.clients.append(client)
                        trace.append({"type": "ClientRequest", "step": i, "node": self.network.leader_id})
                        self.network.add_event({"name": 'ClientRequest', "params": {"leader": self.network.leader_id, "request": self.client_request_counter-1, "node": 1}})
                    except Exception as e:
                        logging.error('Client error')
                        traceback.print_exc()
        except Exception as e:
            logging.error(f'run_iteration exception: {e}')
            try:
                traceback.print_exc()
            except:
                logging.error('Cannot print exception')
        finally:
            try:
                logging.debug("Shutting down cluster")
                self.network.send_shutdown()
            except Exception:
                logging.error('Error on shutdown!')

        event_trace = self.network.get_event_trace()
        end_process_timeout = time.time() + 5e-3
        while True:
            if self.network.cluster_shutdown_ready or time.time() > end_process_timeout:
                break
            await asyncio.sleep(1e-3)

        self.end_process()

        # err, err_log = await self.run_elle(self.elle_cmd, self.elle_file)
        # if err:
        #     if err_log is not None:
        #         _, stdout = err_log
        #         if len(stdout) > 0:
        #             self.error_flag = True
        #             self.error_logs.append(err_log)
        self.check_errors(iteration, event_trace, trace)

        self.shutdown()
        return (trace, event_trace, self.error_flag)

    
    # async def run_elle(self, elle_cmd, elle_file):
    #     err = False
    #     err_log = None
    #     try:
    #         proc = await asyncio.create_subprocess_shell(elle_cmd,
    #                                                             stdout=asyncio.subprocess.PIPE,
    #                                                             stderr=asyncio.subprocess.PIPE)# subprocess.run(elle_cmd, shell=True, capture_output=True, text=True, check=True)
    #         stdout, stderr = await proc.communicate()
    #         if 'true' not in stdout.decode():
    #             logging.error('Elle check error.')
    #             err = True
    #             with open(elle_file, 'r') as f:
    #                 elle_log = f.readlines()
    #             err_log = (f'Elle linearizability fail.\n\n{elle_log}', stdout.decode())
    #     except Exception as e:
    #         traceback.print_exc()
    #         return True, None
    #     finally:
    #         return err, err_log
    
    def check_error_flag(self):
        try:
            for server in self.servers.values():
                if server.error_flag:
                    self.error_flag = True
            
            for client in self.clients:
                if client.error_flag:
                    self.error_flag = True
        except Exception as e:
            traceback.print_exc()
        return self.error_flag
    
    def check_errors(self, iteration, event_trace, trace):
        try:
            for server in self.servers.values():
                if server.error_flag:
                    self.error_flag = True
                    self.error_logs.append(server.error_log)
            
            for client in self.clients:
                if client.error_flag:
                    self.error_flag = True
                    self.error_logs.append(client.error_log)
            
            log_index = self.network.log_index

            if log_index is not None:
                if log_index < 0:
                    self.error_flag = True
                    err = 'Negative LogIndex!'
                    self.error_logs.append((err, ''))

            if self.error_flag:
                stdout_ = []
                stderr_ = []
                for i in range(len(self.error_logs)):
                    if self.error_logs is None or self.error_logs[i] is None:
                        err, out = ('', '')
                    else:
                        err, out = self.error_logs[i]
                    stdout_.append(out)
                    stderr_.append(err)

                stdout = '\n\n--------------------------------------------------------------------------------\n\n'.join(stdout_)
                stderr = '\n\n--------------------------------------------------------------------------------\n\n'.join(stderr_)

                path = os.path.join(self.config.error_path, f'{self.config.exp_name}_{iteration}')
                os.makedirs(path, exist_ok=True)
                with open(os.path.join(path, 'stderr.log'), 'w+') as f:
                    f.writelines(stderr)
                with open(os.path.join(path, 'stdout.log'), 'w+') as f:
                    f.writelines(stdout)
                with open(os.path.join(path, 'event_trace.log'), 'w+') as f:
                    for trace_ in event_trace:
                        f.write(f'{str(trace_)}\n')
                with open(os.path.join(path, 'trace.pkl'), 'wb') as f:
                    pickle.dump(trace, f)
        except Exception as e:
            traceback.print_exc()
        

