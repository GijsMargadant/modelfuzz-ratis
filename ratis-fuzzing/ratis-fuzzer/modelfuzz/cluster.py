import os
import json
import time
import shutil

from collections import deque
from dataclasses import dataclass
from modelfuzz.network import Network
from modelfuzz.server import RatisServer
from modelfuzz.client import RatisClient
from modelfuzz.fuzzer_type import FuzzerType

@dataclass
class Error:
    name: str
    run_id: int
    fuzzer: str

    returncode: int
    strerr: str
    event_trace: list
    states: list
    schedule: list
    stdout: str

    def __init__(self, name: str, run_id: int, fuzzer: FuzzerType,
                 stdout: str = None, stderr: str = None, returncode: int = None,
                 schedule: list = None, event_trace: list = None, states: list = None):
        self.name = name
        self.run_id = run_id
        self.fuzzer = fuzzer.value
        self.returncode = returncode
        self.strerr = stderr
        self.event_trace = event_trace
        self.states = states
        self.schedule = schedule
        self.stdout = stdout
    
    def log_error(self, log_dir):
        log = self.__dict__
        print(log_dir)
        with open(os.path.join(log_dir, f'{self.fuzzer}_{self.run_id}_{self.name}.json'), 'w') as f:
            json.dump(log, f, indent='\t')
        

class Cluster():
    def __init__(self, params, config) -> None:
        # print('Initializing cluster')
        self.params = params
        self.config = config

        self.schedule: list = self.config['schedule']
        self.executed_schedule = []
        self.event_trace = []
        self.error = None

        self.network = Network(self.config['fuzzer_port'])

        self.servers: list[RatisServer] = []
        self.peer_addresses = ','.join([f'127.0.0.1:{self.config["node_ports"][i]}' for i in range(self.params.nodes)])

        self.tmp_dir = f'./tmp/{self.config["fuzzer"].value}_{self.config["run_id"]}'
        os.makedirs(self.tmp_dir, exist_ok=True)
        self.stdouts = [open(os.path.join(self.tmp_dir, f'stdout_{i+1}.log'), mode='w+') for i in range(self.params.nodes)]
        self.stderrs = [open(os.path.join(self.tmp_dir, f'stderr_{i+1}.log'), mode='w+') for i in range(self.params.nodes)]

        for i in range(self.params.nodes):
            server_config = {
                'jar_path': self.params.jar_path,
                'run_id': self.config['run_id'],
                'fuzzer_port': self.config['fuzzer_port'],
                'listener_port': self.config['listener_ports'][i],
                'peer_index': i+1,
                'peer_addresses': self.peer_addresses,
                'group_id': self.config['group_id'],
                'timeout': self.params.timeout,
                'stdout': self.stdouts[i],
                'stderr': self.stderrs[i]
            }
            self.servers.append(RatisServer(server_config))
        
        self.clients: list[RatisClient] = []
        self.client_request = 1
        

    def cluster_init(self) -> None:
        # print('Starting cluster')
        self.network.start()
        for i, server in enumerate(self.servers):
            server.start()

    def cluster_stop(self) -> None:
        # print('Stopping cluster')
        self.network.stop()
        self.network.join()

        for server in self.servers:
            server.close()
            server.join()
        
        for client in self.clients:
            client.close()
            client.join()

        shutil.rmtree(self.tmp_dir)
        ratis_data_path = os.path.join(self.params.ratis_data_dir, f'{self.config["run_id"]}')
        if os.path.exists(ratis_data_path):
            shutil.rmtree(ratis_data_path)
        
    def run(self) -> tuple[list, list, list[Error]]:
        self.cluster_init()
        # print('Waiting for server registers')
        timeout = time.time() + self.params.timeout 
        while self.network.get_num_replicas() != self.params.nodes:
            if time.time() > timeout:
                print(f'Timeout at cluster {self.config["run_id"]} while waiting for nodes to register!')
                return (None, (None, None, Error('NodeRegisterTimeout', self.config['run_id'], self.config['fuzzer'])))
            time.sleep(0.01)

        steps = self.schedule
        crashed = set()
        errors = None
        # print('Running cluster while loop')
        timeout = time.time() + self.params.timeout
        for i in range(len(steps)):
            if time.time() > timeout:
                break

            is_error, errors = self.check_error()
            if is_error:
                break

            step = steps[i]
            if step['type'] == 'Crash':
                node = step['node']
                if node not in crashed:
                    self.servers[node-1].crash()
                    crashed.add(node)
                    self.network.add_event({"name": "Remove", "params": {"i": node, "node": node}})
            elif step['type'] == 'Restart':
                node = step['node']
                if node in crashed:
                    self.servers[node-1].restart()
                    self.network.add_event({"name": "Add", "params": {"i": node, "node": node}})
                    crashed.remove(node)
            elif step['type'] == 'ClientRequest':
                leader_id = self.network.get_leader_id()
                if leader_id > 0 and leader_id not in crashed:
                    client_config = {
                        'jar_path': self.params.jar_path,
                        'request': self.client_request,
                        'peer_addresses': self.peer_addresses,
                        'group_id': self.config['group_id'],
                        'timeout': self.params.timeout,
                        'run_id': self.config['run_id'],
                        'stdout': open(os.path.join(self.tmp_dir, f'client_stdout_{self.client_request}.log'), mode='w+'),
                        'stderr': open(os.path.join(self.tmp_dir, f'client_stderr_{self.client_request}.log'), mode='w+')
                    }
                    client = RatisClient(client_config)
                    self.clients.append(client)
                    client.start()
                    self.network.add_event({"name": 'ClientRequest', "params": {"leader": leader_id, "request": self.client_request, "node": 0}})
            elif step['type'] == 'Schedule':
                node = step['node']
                to = step['to']
                max_msgs = step['max_msgs']
                    
                if node not in crashed:
                    scheduled_msgs = self.network.schedule_node(node, to, max_msgs, to in crashed)
                    # step['max_msgs'] = scheduled_msgs 
            else:
                pass

            self.executed_schedule.append(step)
            
            time.sleep(3e-2)

        _, errors = self.check_error()

        # print('Cluster stop')
        self.cluster_stop()

        self.event_trace = self.network.get_event_trace()
        return (self.executed_schedule, self.event_trace, errors)



    # Round-Robin Version
    # def run(self) -> tuple[list, list, list[Error]]:
    #     self.cluster_init()
    #     # print('Waiting for server registers')
    #     timeout = time.time() + self.params.timeout 
    #     while self.network.get_num_replicas() != self.params.nodes:
    #         if time.time() > timeout:
    #             print(f'Timeout at cluster {self.config["run_id"]} while waiting for nodes to register!')
    #             return (None, (None, None, Error('NodeRegisterTimeout', self.config['run_id'], self.config['fuzzer'])))
    #         time.sleep(0.01)

    #     steps = deque(self.schedule)
    #     crashed = set()
    #     errors = None
    #     # print('Running cluster while loop')
    #     timeout = time.time() + self.params.timeout
    #     while len(steps) > 0:
    #         if time.time() > timeout:
    #             break

    #         is_error, errors = self.check_error()
    #         if is_error:
    #             break

    #         step = steps[0]
    #         scheduled = False
    #         if step['type'] == 'Crash':
    #             node = step['node']
    #             if node not in crashed:
    #                 self.servers[node-1].crash()
    #                 crashed.add(node)
    #                 self.network.add_event({"name": "Remove", "params": {"i": node, "node": node}})
    #                 scheduled = True
    #         elif step['type'] == 'Restart':
    #             node = step['node']
    #             if node in crashed:
    #                 self.servers[node-1].restart()
    #                 self.network.add_event({"name": "Add", "params": {"i": node, "node": node}})
    #                 crashed.remove(node)
    #                 scheduled = True
    #         elif step['type'] == 'ClientRequest':
    #             leader_id = self.network.get_leader_id()
    #             if leader_id > 0 and leader_id not in crashed:
    #                 client_config = {
    #                     'jar_path': self.params.jar_path,
    #                     'request': self.client_request,
    #                     'peer_addresses': self.peer_addresses,
    #                     'group_id': self.config['group_id'],
    #                     'timeout': self.params.timeout,
    #                     'run_id': self.config['run_id'],
    #                     'stdout': open(os.path.join(self.tmp_dir, f'client_stdout_{self.client_request}.log'), mode='w+'),
    #                     'stderr': open(os.path.join(self.tmp_dir, f'client_stderr_{self.client_request}.log'), mode='w+')
    #                 }
    #                 client = RatisClient(client_config)
    #                 self.clients.append(client)
    #                 client.start()
    #                 self.network.add_event({"name": 'ClientRequest', "params": {"leader": leader_id, "request": self.client_request, "node": 0}})
    #                 scheduled = True
    #         elif step['type'] == 'Schedule':
    #             node = step['node']
    #             to = step['to']
    #             max_msgs = step['max_msgs']
    #             can_schedule = self.network.message_exists(node, to)
    #             if not can_schedule:
    #                 time.sleep(1e-2)
    #                 can_schedule = self.network.message_exists(node, to)
                    
    #             if can_schedule and node not in crashed:
    #                 scheduled_msgs = self.network.schedule_node(node, to, max_msgs, to in crashed)
    #                 step['max_msgs'] = scheduled_msgs 
    #                 scheduled = True
    #         else:
    #             pass

    #         if scheduled:
    #             self.executed_schedule.append(step)
    #             steps.popleft()
    #         else:
    #             steps.rotate(-1)
            
    #         # time.sleep(3e-2)

    #     _, errors = self.check_error()

    #     # print('Cluster stop')
    #     self.cluster_stop()

    #     self.event_trace = self.network.get_event_trace()
    #     return (self.executed_schedule, self.event_trace, errors)

    
    def check_error(self) -> tuple[bool, list[Error]]:
        error = False
        errors = []
        for i, server in enumerate(self.servers):
            if server.error_flg:
                error = True
                stdout = server.stdout.readlines()
                stderr = server.stderr.readlines()
                returncode = server.returncode
                self.event_trace = self.network.get_event_trace()
                # TODO - Exception extraction from stderr
                name = f'ServerException_{i}'
                if returncode < 0:
                    name = f'NegativeServerReturnCode_{i}'
                self.event_trace = self.network.get_event_trace()
                errors.append(Error(name, self.config['run_id'], self.config['fuzzer'], stdout, stderr,
                                    returncode, self.executed_schedule, self.event_trace))
        
        for i, client in enumerate(self.clients):
            if client.error_flg:
                error = True
                stdout = client.stdout.readlines()
                stderr = client.stderr.readlines()
                returncode = client.returncode
                self.event_trace = self.network.get_event_trace()
                # TODO - Exception extraction from stderr
                name = f'ClientException_{i}'
                if returncode < 0:
                    name = f'NegativeClientReturnCode_{i}'
                self.event_trace = self.network.get_event_trace()
                errors.append(Error(name, self.config['run_id'], self.config['fuzzer'], stdout, stderr,
                                    returncode, self.executed_schedule, self.event_trace))
        
        return (error, errors)
            


        
        