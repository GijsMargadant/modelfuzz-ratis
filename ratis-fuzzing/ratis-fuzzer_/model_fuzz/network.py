from http import HTTPStatus
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlsplit, parse_qs
from threading import Lock, Thread
import requests
import json
import base64
import traceback
import logging

class Request:
    def __init__(self, method, path, headers, query=None, content=None) -> None:
        self.path = path
        self.headers = headers
        self.query = query
        self.content = content
        self.method = method

class Response:
    def __init__(self, status_code, content) -> None:
        self.status_code = status_code
        self.headers = {}
        self.content = content

    def set_header(self, key, value):
        self.headers[key] = value

    def set_content_json(self):
        self.headers["Content-Type"] = "application/json"

    def json(status_code, content):
        r = Response(status_code, content)
        r.set_content_json()
        return r


class _ServerHandler(BaseHTTPRequestHandler):
    def __init__(self, *args, handler = None, **kwargs) -> None:
        self.handler = handler
        super().__init__(*args, **kwargs)

    def respond(self, response: Response):
        if response is None:
            self.send_error(HTTPStatus.INTERNAL_SERVER_ERROR, "Failed to generate a response")

        if response.status_code >= HTTPStatus.BAD_REQUEST:
            for key in response.headers:
                self.send_header(key, response.headers[key])
            self.send_error(response.status_code, response.content)
        else:
            self.send_response(response.status_code)
            for key in response.headers:
                self.send_header(key, response.headers[key])

            content = None
            if response.content is not None and len(response.content) > 0:
                content = response.content.encode("UTF-8", "replace")
            
            if content is None:
                self.send_header("Content-Length", 0)
                self.end_headers()
            else:
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)

            return
        
    def log_message(self, format, *args):
        return

    def do_GET(self):
        (_, _, path, query, _) = urlsplit(self.path)
        parsed_qs = parse_qs(query)
        request = Request("GET", path, self.headers, parsed_qs)
        response = self.handler.handle(request)
        self.respond(response)

    def do_POST(self):
        (_, _, path, query, _) = urlsplit(self.path)
        parsed_qs = parse_qs(query)
        length = self.headers.get('content-length')
        content = ""
        if int(length) > 0:
            content = self.rfile.read(int(length))
        
        request = Request("POST", path, self.headers, parsed_qs, content)
        response = self.handler.handle(request)
        self.respond(response)


class Router:
    def __init__(self) -> None:
        self.handlers = {}
    
    def add_route(self, path, handler):
        self.handlers[path] = handler

    def handle(self, request):
        if request.path in self.handlers:
            response = self.handlers[request.path](request)
            return response
        return Response(HTTPStatus.NOT_FOUND, "Path does not exist")
    

class Server(ThreadingHTTPServer):
    def __init__(self, 
                 server_address, 
                 handler: Router, 
                 bind_and_activate: bool = True) -> None:
        self.handler = handler
        super().__init__(server_address, _ServerHandler, bind_and_activate)

    def finish_request(self, request, client_address) -> None:
        if self.RequestHandlerClass == _ServerHandler:
            return self.RequestHandlerClass(request, client_address, self, handler=self.handler)
        return super().finish_request(request, client_address)
    

class Message:
    def __init__(self, fr, to, type, data, id=None, params=None) -> None:
        self.fr = fr
        self.to = to
        self.type = type
        self.id = id
        self.data = data
        self.params = params

    def from_str(m):
        if "from" not in m or "to" not in m or "type" not in m or "data" not in m:
            return None
        return Message(m["from"], m["to"], m["type"], m["data"], m["id"] if "id" in m else None, m["params"] if "id" in m else None)

    def __str__(self) -> str:
        return f'fr: {self.fr}, to: {self.to}, type: {self.type}, msg: {self.data}, id: {self.id}, params: {self.params}'


class Network:
    def __init__(self, config, addr) -> None:
        self.addr = addr
        self.lock = Lock()
        self.event_lock = Lock()
        self.config = config
        
        router = Router()
        router.add_route("/replica", self._handle_replica)
        router.add_route("/message", self._handle_message)
        router.add_route("/event", self._handle_event)

        self.client_request_counter = 0
        self.mailboxes = {}
        self.replicas = {}
        self.request_map = {}
        self.request_ctr = 1
        self.event_trace = []
        self.leader_id = -1
        self.timeout = False
        self.multiple_leaders = False
        self.cluster_shutdown_ready = False
        self.first_message = False

        self.log_index = None
        self.negative_log_index = False

        self.server = Server(addr, router)
        self.server_thread = Thread(target=self.server.serve_forever)
        

    def run(self):
        self.server_thread.start()
        logging.debug('Network started.')
    
    def shutdown(self):
        self.server.shutdown()
        self.server_thread.join()
        self.server.server_close()
        logging.debug('Network shutdown.')
    
    def check_mailboxes(self, key):
        result = False
        try:
            self.lock.acquire()
            if key in self.mailboxes.keys():
                if len(self.mailboxes[key]) > 0:
                    result = True
        finally:
            if self.lock.locked():
                self.lock.release()
        return result
    
    def check_replicas(self):
        result = False
        try:
            self.lock.acquire()
            if len(self.replicas) < self.config.nodes:
                return True
        finally:
            if self.lock.locked():
                self.lock.release()
        return result

    def _get_request_number(self, data):
        if data not in self.request_map:
            self.request_map[data] = self.request_ctr
            self.request_ctr += 1
            return self.request_map[data]
        return self.request_map[data]
    
    def _handle_replica(self, request: Request) -> Response:
        replica = json.loads(request.content)
        logging.debug(f'Replica {replica["id"]} received.')
        if "id" in replica:
            try:
                self.lock.acquire()
                self.replicas[str(replica["id"])] = replica
            finally:
                if self.lock.locked():
                    self.lock.release()

        return Response.json(HTTPStatus.OK, json.dumps({"message": "Ok"}))

    def _get_message_event_params(self, msg):
        if msg.type == "append_entries_request":
            return {
                "type": "MsgApp",
                "term": msg.params["term"],
                "from": int(msg.fr),
                "to": int(msg.to),
                "log_term": msg.params["prev_log_term"], 
                "entries": [{"Term": msg.params['entries'][key]["term"], "Data": str(self._get_request_number(msg.params['entries'][key]["data"]))} for key in msg.params["entries"].keys() if msg.params['entries'][key]["data"] != ""],
                "index": msg.params["prev_log_idx"],
                "commit": msg.params["leader_commit"],
                "reject": False,
            }
        elif msg.type == "append_entries_reply":
            return {
                "type": "MsgAppResp",
                "term": msg.params["term"],
                "from": int(msg.fr),
                "to": int(msg.to),
                "log_term": 0, 
                "entries": [],
                "index": msg.params["current_idx"],
                "commit": 0,
                "reject": msg.params["success"] == 0,
            }
        elif msg.type == "request_vote_request":
            return {
                "type": "MsgVote",
                "term": msg.params["term"],
                "from": int(msg.fr),
                "to": int(msg.to),
                "log_term": msg.params["last_log_term"],
                "entries": [],
                "index": msg.params["last_log_idx"],
                "commit": 0,
                "reject": False,
            }
        elif msg.type == "request_vote_reply":
            return {
                "type": "MsgVoteResp",
                "term": msg.params["term"],
                "from": int(msg.fr),
                "to": int(msg.to),
                "log_term": 0,
                "entries": [],
                "index": 0,
                "commit": 0,
                "reject": msg.params["vote_granted"] == 0,
            }
        return {}


    def _handle_message(self, request: Request) -> Response:
        # if self.paused:
        #     return Response.json(HTTPStatus.NOT_FOUND, json.dumps({"message": "Ok"}))
        content = json.loads(request.content)
        # content['data'] = json.loads(base64.b64decode(content["data"]).decode('utf-8'))
        msg = Message.from_str(content)
        if msg == None:
            return
        logging.debug(f'Message received of type {msg.type} from {msg.fr} to {msg.to}.')
        logging.debug(f'Message content: \n {content}')
        if msg is not None:
            if msg.type == 'config_query':
                return Response.json(HTTPStatus.OK, json.dumps({'nodes': self.config.nodes}))
            try:
                self.lock.acquire()
                key = str(msg.fr)
                if key not in self.mailboxes:
                    self.mailboxes[key] = []
                self.mailboxes[key].append(msg)
            finally:
                if self.lock.locked():
                    self.lock.release()
            # msg.params = json.loads(msg.params)
            params = self._get_message_event_params(msg)
            params["node"] = params["from"]
            self.add_event({"name": "SendMessage", "params": params})
        self.first_message = True

        return Response.json(HTTPStatus.OK, json.dumps({"message": "Ok"}))
    
    def _handle_event(self, request: Request) -> Response:
        event = json.loads(json.loads(request.content))
        logging.debug("Received event: {} {}".format(event, type(event)))
        try:
            params = self._map_event_params(event)
            e = {"name": event["type"], "params": params}
            if params != None:
                e["params"]["replica"] = event["server_id"]
                self.lock.acquire()
                self.event_trace.append(e)
        finally:
            if self.lock.locked():
                self.lock.release()

        return Response.json(HTTPStatus.OK, json.dumps({"message": "Ok"}))
    
    
    def _map_event_params(self, event):
        if event["type"] == "ShutdownReady":
            self.cluster_shutdown_ready = True
            return None
        if event['type'] == 'LogUpdate':
            self.log_index = int(event['log_index'])
            if self.log_index < 0:
                self.negative_log_index = True
            logging.debug(f'Log index updated: {self.log_index}')
            return None
        elif event["type"] == "ClientRequest":
            self.client_request_counter += 1
            return {
                "leader": int(event["leader"]),
                "request": self.client_request_counter-1
            }
        elif event["type"] == "BecomeLeader":
            if self.leader_id != -1:
                if not self.timeout:
                    self.multiple_leaders = True
            self.timeout = False
            self.leader_id = event["node"]
            logging.debug(f'Leader elected: {self.leader_id}')
            return {
                "node": int(event["node"]),
                "term": int(event["term"])
            }
        elif event["type"] == "Timeout":
            self.timeout = True
            return {
                "node": int(event["node"])
            }
        elif event["type"] == "MembershipChange":
            return {
                "action": event["action"],
                "node": int(event["node"])
            }
        elif event["type"] == "UpdateSnapshot":
            return {
                "node": int(event["server_id"]),
                "snapshot_index": int(event["snapshot_index"])
            }
        elif event["type"] == "AdvanceCommitIndex":
            return {
                "i": int(event["server_id"])
            }
        else:
            return None
    
    def get_replicas(self):
        replicas = []
        try:
            self.lock.acquire()
            replicas = list(self.replicas.items())
        finally:
            if self.lock.locked():
                    self.lock.release()
        return replicas
    
    def get_event_trace(self):
        event_trace = []
        try:
            self.lock.acquire()
            for e in self.event_trace:
                event_trace.append(e)
        finally:
            if self.lock.locked():
                self.lock.release()
        return event_trace
    
    def add_event(self, e):
        try:
            self.lock.acquire()
            self.event_trace.append(e)
        finally:
            if self.lock.locked():
                self.lock.release()

    def schedule_replica(self, replica, max_messages):
        messages_to_deliver = []
        # key = f'{replica}_{replica2}'
        key = str(replica)
        logging.debug(f'Scheduling replica: {replica}, max_messages: {max_messages}')
        logging.debug(self.mailboxes)
        try:
            self.lock.acquire()
            if key in self.mailboxes and len(self.mailboxes[key]) > 0:
                for (i,m) in enumerate(self.mailboxes[key]):
                    if i < max_messages:
                        messages_to_deliver.append(m)
                if len(self.mailboxes[key]) > max_messages:
                    self.mailboxes[key] = self.mailboxes[key][max_messages:]
                else:
                    self.mailboxes[key] = []
        finally:
            if self.lock.locked():
                self.lock.release()
        logging.debug(f'Messages to deliver: {len(messages_to_deliver)}')
        for next_msg in messages_to_deliver:
            dict_ = next_msg.__dict__
            dict_["from"] = next_msg.fr
            msg_s = json.dumps(dict_)
            logging.debug("Sending message {} from {} to {}, with replica {}".format(next_msg.type, next_msg.fr, next_msg.to, replica))
            logging.debug(msg_s)
            params = self._get_message_event_params(next_msg)
            params["node"] = params["to"]
            self.add_event({"name": "DeliverMessage", "params": params})
            try:
                addr = self.replicas[str(next_msg.to)]['addr']
                logging.debug(addr)
                requests.post("http://"+addr, json=json.dumps(dict_))
            except:
                logging.debug(f'Error while sending message, addr: {addr}')
    
    def send_crash(self, replica):
        logging.debug(f'Sending crash to {replica}')
        try:
            # addr = self.replicas[replica]["addr"]
            msg = Message(0, replica, "crash", replica)
            addr = self.replicas[replica]['addr']
            requests.post("http://"+addr+"/message", json=json.dumps(msg.__dict__))
        except:
            traceback.print_exc()
        finally:
            pass

    
    def send_shutdown(self):
        logging.debug(f'Sending shutdown to cluster.')
        try:
            self.lock.acquire()
            msg = Message(0, 1, "shutdown", base64.b64encode('shutting_down'))
            for replica in self.replicas.keys():
                addr = self.replicas[replica]['addr']
                requests.post("http://"+addr+"/message", json=json.dumps(msg.__dict__))
        except Exception:
            pass
        finally:
            if self.lock.locked():
                self.lock.release()

    def clear_mailboxes(self):
        try:
            self.lock.acquire()
            for key in self.mailboxes:
                self.mailboxes[key] = []
            self.event_trace = []
        finally:
            if self.lock.locked():
                self.lock.release()