from __future__ import annotations

import warnings
# ignore RuntimeWarning from asyncio
# warnings.filterwarnings('ignore')

import time
import json
import base64
import asyncio
import requests
import traceback

from aiohttp import web
from threading import Thread, Lock


class Message:
    def __init__(self, fr, to, type, data, id=None, params=None) -> None:
        self.fr = fr
        self.to = to
        self.type = type
        self.id = id
        self.data = data
        self.params = params

    def from_str(m) -> Message:
        if 'from' not in m or 'to' not in m or 'type' not in m or 'data' not in m:
            return None
        return Message(m['from'], m['to'], m['type'], m['data'], m['id'] if 'id' in m else None, m['params'] if 'id' in m else None)

    def __str__(self) -> str:
        return f'fr: {self.fr}, to: {self.to}, type: {self.type}, msg: {self.data}, id: {self.id}, params: {self.params}'

        

class Network(Thread):
    def __init__(self, port) -> None:
        Thread.__init__(self)
        # print('Initializing network')
        self.port = port
        self.event_mapper = EventMapper()

        self.app = web.Application()
        self.app.add_routes([web.post('/replica', self.handle_replica),
                             web.post('/message', self.handle_message),
                             web.post('/event', self.handle_event)])
        self.runner = web.AppRunner(self.app)
        self.site = None
        self.loop = None

        self.lock = Lock()
        self.replicas = {}
        self.mailboxes = {}
        self.event_trace = []

    def run(self) -> None:
        # print('Starting network')
        self.run_server()

    def ask_exit(self):
        for task in asyncio.Task.all_tasks():
            task.cancel()

    def run_server(self) -> None:
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        self.loop.run_until_complete(self.runner.setup())
        self.site = web.TCPSite(self.runner, 'localhost', self.port)
        self.loop.run_until_complete(self.site.start())
        self.loop.run_forever()
        # asyncio.run(self.runner.cleanup())
    
    def stop(self) -> None:
        # print('Stopping network')
        asyncio.run(self.site.stop())
        self.loop.call_soon_threadsafe(self.loop.stop)
        # print('Network closed')

    async def handle_replica(self, request) -> web.Response:
        replica = await request.json() #json.loads(request.content)
        # print('HandleReplica: ' , replica)
        if 'id' in replica:
            try:
                self.lock.acquire()
                self.replicas[str(replica['id'])] = replica['addr']
            except Exception as e:
                traceback.print_exc()
            finally:
                self.lock.release()
        return web.Response(body=json.dumps({'message': 'Ok'}))

    async def handle_message(self, request) -> web.Response:
        content = await request.json() # content = json.loads(request.content)
        # print('HandleMessage: ' , content)
        msg = Message.from_str(content)
        if msg == None:
            return
        else:
            try:
                self.lock.acquire()
                key = f'{str(msg.fr)}_{str(msg.to)}'
                if key not in self.mailboxes:
                    self.mailboxes[key] = []
                self.mailboxes[key].append(msg)
            except Exception as e:
                traceback.print_exc()
            finally:
                self.lock.release()
            params = self.event_mapper.get_message_event_params(msg)
            params['node'] = params['from']
            self.add_event({'name': 'SendMessage', 'params': params})

        return web.Response(body=json.dumps({'message': 'Ok'}))

    async def handle_event(self, request) -> web.Response:
        event = await request.json() # event = json.loads(json.loads(request.content))
        event = json.loads(event)
        # print('HandleEvent: ' , event)
        params = self.event_mapper.map_event_params(event)
        e = {'name': event['type'], 'params': params}
        if params != None:
            e['params']['replica'] = event['server_id']
            self.add_event(e)
        return web.Response(body=json.dumps({'message': 'Ok'}))

    def message_exists(self, fr, to) -> bool:
        key = f'{str(fr)}_{str(to)}'
        if key in self.mailboxes:
            return len(self.mailboxes[key]) > 0
        else:
            return False
    
    def schedule_node(self, fr, to, max_msgs, to_crashed) -> int:
        messages = []
        key = f'{str(fr)}_{str(to)}'
        if key not in self.mailboxes.keys():
            return 0
        for i, m in enumerate(self.mailboxes[key]):
            try:
                self.lock.acquire()
                if i < max_msgs and len(self.mailboxes[key]) > 0:
                    messages.append(self.mailboxes[key].pop(0))
            except Exception as e:
                traceback.print_exc()
            finally:
                self.lock.release()
        
        addr = self.replicas[str(to)]
        for m in messages:
            dict_ = m.__dict__
            dict_['from'] = m.fr

            params = self.event_mapper.get_message_event_params(m)
            params['node'] = params['to']
            self.add_event({'name': 'DeliverMessage', 'params': params})
            if not to_crashed:
                try:
                    requests.post(f'http://{addr}', json=json.dumps(dict_))
                except Exception as e:
                    # traceback.print_exc()
                    pass
            else:
                pass
                # print('Network schedule_node: To crashed')
        
        return len(messages)
    
    def send_shutdown(self) -> None:
        # print(f'Sending shutdown to cluster.')
        try:
            self.lock.acquire()
            msg = Message(0, 1, 'shutdown', base64.b64encode('shutting_down'))
            for addr in self.replicas.values():
                requests.post(f'http://{addr}', json=json.dumps(msg.__dict__)) # /message may be required
        except Exception as e:
            traceback.print_exc()
        finally:
            if self.lock.locked():
                self.lock.release()
    

    def add_event(self, e) -> None:
        try:
            self.lock.acquire()
            self.event_trace.append(e)
        finally:
            self.lock.release()
    
    def get_num_replicas(self) -> int:
        return len(self.replicas)
    
    def get_event_trace(self) -> list:
        return self.event_trace
    
    def get_leader_id(self) -> int:
        return self.event_mapper.get_leader_id()


class EventMapper:
    def __init__(self) -> None:
        self.request_map = {}
        self.request_ctr = 1
        self.leader_id = -1

    def get_message_event_params(self, msg) -> dict:
        if msg is None:
            return {}
        
        params = {
            'from': int(msg.fr),
            'to': int(msg.to),
            'term': int(msg.params['term']),
            'entries': [],
            'commit': 0
        }
        if msg.type == 'append_entries_request':
            params['type'] = 'MsgApp'
            params['log_term'] = msg.params['prev_log_term']
            params['entries'] = [{'Term': msg.params['entries'][key]['term'], 'Data': str(self.get_request_number(msg.params['entries'][key]['data']))} for key in msg.params['entries'].keys() if msg.params['entries'][key]['data'] != '']
            params['index'] = int(msg.params['prev_log_idx'])
            params['commit'] = int(msg.params['leader_commit'])
            params['reject'] = False
        elif msg.type == 'append_entries_reply':
            params['type'] = 'MsgAppResp'
            params['log_term'] = 0
            params['index'] = int(msg.params['current_idx'])
            params['reject'] = msg.params['success'] == 0
        elif msg.type == 'request_vote_request':
            params['type'] = 'MsgVote'
            params['log_term'] = msg.params['last_log_term']
            params['index'] = msg.params['last_log_idx']
            params['reject'] = False
        elif msg.type == 'request_vote_reply':
            params['type'] = 'MsgVoteResp'
            params['log_term'] = 0
            params['index'] = 0
            params['reject'] = int(msg.params['reject']) == 0
        else:
            params = {}
        return params

    def map_event_params(self, event) -> dict:
        # if event['type'] == 'ShutdownReady':
        #     self.cluster_shutdown_ready = True
        #     return None
        # if event['type'] == 'LogUpdate':
        #     self.log_index = int(event['log_index'])
        #     if self.log_index < 0:
        #         self.negative_log_index = True
        #     print(f'Log index updated: {self.log_index}')
        #     return None
        if event['type'] == 'ClientRequest':
            self.request_ctr += 1
            return {
                'leader': int(event['leader']),
                'request': self.request_ctr-1
            }
        elif event['type'] == 'BecomeLeader':
            # if self.leader_id != -1:
            #     if not self.timeout:
            #         self.multiple_leaders = True
            # self.timeout = False
            # self.leader_id = event['node']
            # print(f'Leader elected: {self.leader_id}')
            self.leader_id = int(event['node'])
            return {
                'node': int(event['node']),
                'term': int(event['term'])
            }
        elif event['type'] == 'Timeout':
            self.leader_id = -1
            return {
                'node': int(event['node'])
            }
        elif event['type'] == 'MembershipChange':
            return {
                'action': event['action'],
                'node': int(event['node'])
            }
        elif event['type'] == 'UpdateSnapshot':
            return {
                'node': int(event['server_id']),
                'snapshot_index': int(event['snapshot_index'])
            }
        elif event['type'] == 'AdvanceCommitIndex':
            return {
                'i': int(event['server_id']),
                'node': int(event['server_id'])
            }
        else:
            return None

    
    def get_request_number(self, data) -> int:
        if data not in self.request_map:
            self.request_map[data] = self.request_ctr
            self.request_ctr += 1
            return self.request_map[data]
        return self.request_map[data]

    def get_leader_id(self) -> int:
        return self.leader_id