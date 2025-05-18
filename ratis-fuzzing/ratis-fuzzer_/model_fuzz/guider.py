import json
import pickle
import logging
import requests

from enum import Enum

class GuiderType(Enum):
    EMPTY='empty'
    TLC='tlc'
    RANDOM='random'
    TRACE='trace'

class EmptyGuider:
    def __init__(self, tlc_addr) -> None:
        self.tlc_addr = tlc_addr
        self.states = {}
    
    def check_new_state(self, trace, event_trace, name, record = False) -> int:
        return 1

    def coverage(self):
        return 1

    def save_states(self, dir_):
        pass
        
    def load_states(self, dir_):
        pass

    def reset(self):
        self.states = {}

from hashlib import sha256
class TLCGuider:
    def __init__(self, tlc_addr) -> None:
        self.tlc_addr = tlc_addr
        self.states = {}
    
    def check_new_state(self, trace, event_trace, name, record = False) -> int:
        trace_to_send = event_trace
        trace_to_send.append({"reset": True})
        logging.debug("Sending trace to TLC: {}".format(trace_to_send))
        try:
            r = requests.post("http://"+self.tlc_addr+"/execute", json=trace_to_send)
            if r.ok:
                response = r.json()
                logging.debug("Received response from TLC: {}".format(response))               
                new_states = 0
                for i in range(len(response["states"])):
                    tlc_state = {"state": response["states"][i], "key" : response["keys"][i]}
                    if tlc_state["key"] not in self.states:
                        self.states[tlc_state["key"]] = tlc_state
                        new_states += 1
                return new_states
            else:
                logging.info("Received error response from TLC, code: {}, text: {}".format(r.status_code, r.content))
        except Exception as e:
            logging.error("Error received from TLC: {}".format(e))

        return 0
    
    def coverage(self):
        return len(self.states.keys())

    def save_states(self, dir_):
        with open(dir_, 'wb') as f:
            pickle.dump(self.states, f)
        
    def load_states(self, dir_):
        with open(dir_, 'rb') as f:
            self.states = pickle.load(f)

    def reset(self):
        self.states = {}

def create_event_graph(event_trace):
    cur_event = {}
    nodes = {}

    for e in event_trace:
        try:
            if 'reset' in e.keys():
                continue
            node = e["params"]["node"]
            node_ = {"name": e["name"], "params": e["params"], "node": node}
            if node in cur_event:
                node_["prev"] = cur_event[node]["id"]
            id = sha256(json.dumps(node_, sort_keys=True).encode('utf-8')).hexdigest()
            node_["id"] = id

            cur_event[node] = node_
            nodes[id] = node_
        except:
            logging.error(f'Event cannot be added to the trace: {e}')
        finally:
            continue
    
    return nodes

class TraceGuider(TLCGuider):
    def __init__(self, tlc_addr) -> None:
        super(TraceGuider, self).__init__(tlc_addr)
        self.traces = {}

    def check_new_state(self, trace, event_trace, name, record=False) -> int:
        super().check_new_state(trace, event_trace, name, record)

        new = 0
        event_graph = create_event_graph(event_trace)
        event_graph_id = sha256(json.dumps(event_graph, sort_keys=True).encode('utf-8')).hexdigest()

        if event_graph_id not in self.traces:
            self.traces[event_graph_id] = True
            new = 1
        
        return new
    
    def reset(self):
        self.traces = {}
        return super().reset()

    def save_states(self, dir_):
        with open(dir_, 'wb') as f:
            pickle.dump((self.traces, self.states), f)
        
    def load_states(self, dir_):
        with open(dir_, 'rb') as f:
            self.traces, self.states = pickle.load(f)