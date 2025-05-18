import json
import requests

from hashlib import sha256
from threading import Thread
from modelfuzz.fuzzer_type import FuzzerType

class GuiderFactory():
    @staticmethod
    def get_guider(guider_type):
        if guider_type == FuzzerType.MODELFUZZ:
            return TLCGuider()
        elif guider_type == FuzzerType.RANDOM:
            return TLCGuider()
        elif guider_type == FuzzerType.TRACE:
            return TraceGuider()
        else:
            return None

class Guider():
    def __init__(self) -> None:
        pass

    def get_states(self, event_trace) -> list[dict]:
        return []
    
    def add_and_get_new_states(self, event_trace) -> int:
        return 0
    
    def get_coverage(self) -> int:
        return 0
        
class TLCGuider(Guider):
    def __init__(self):
        self.states = {}

    def get_states(self, event_trace) -> list[dict]:
        trace_to_send = event_trace
        trace_to_send.append({"reset": True})
        try:
            r = requests.post(f'http://127.0.0.1:2023/execute', json=trace_to_send)
            if r.ok:
                response = r.json() 
                return [{"state": response['states'][i], 'key' : response['keys'][i]} for i in range(len(response['states']))]              
            else:
                print(f'Received error response from TLC, code {r.status_code}, text: {r.content}')
        except Exception as e:
            print(f'Error received from TLC: {e}')
        return []
    
    def add_and_get_new_states(self, event_trace) -> int:
        states = self.get_states(event_trace)
        new_states = 0
        for tla_state in states:
            if tla_state['key'] not in self.states:
                self.states[tla_state['key']] = tla_state
                new_states += 1
        return new_states

    def get_coverage(self) -> int:
        return len(self.states)
    
# TODO - Check event keys    
class TraceGuider(Guider):
    def __init__(self) -> None:
        self.traces = {}
        self.tlc_guider = TLCGuider()
    
    def get_states(self, event_trace):
        return self.tlc_guider.get_states(event_trace)

    def add_and_get_new_states(self, event_trace) -> int:
        new = 0
        self.tlc_guider.add_and_get_new_states(event_trace)
        event_graph = self.create_event_graph(event_trace)
        event_graph_id = sha256(json.dumps(event_graph, sort_keys=True).encode('utf-8')).hexdigest()

        if event_graph_id not in self.traces:
            self.traces[event_graph_id] = True
            new = 1

        return new

    def create_event_graph(self, event_trace) -> dict:
        cur_event = {}
        nodes = {}

        for e in event_trace:
            try:
                if 'reset' in e.keys():
                    continue
                node = e['params']['node']
                node_ = {'name': e['name'], 'params': e['params'], 'node': node}
                if node in cur_event:
                    node_['prev'] = cur_event[node]['id']
                id = sha256(json.dumps(node_, sort_keys=True).encode('utf-8')).hexdigest()
                node_['id'] = id

                cur_event[node] = node_
                nodes[id] = node_
            except:
                print(f'Event cannot be added to the trace: {e}')
                # logging.error(f'Event cannot be added to the trace: {e}')
            finally:
                continue
        
        return nodes
    
    def get_coverage(self) -> int:
        return self.tlc_guider.get_coverage()
    