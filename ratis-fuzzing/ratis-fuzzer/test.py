import time
import json
import requests
import asyncio
import traceback
import logging
import subprocess
from threading import Thread
from aiohttp import web

class Server(Thread):
    def __init__(self):
        Thread.__init__(self)
        self.process = None
        self.done = False
        self.returncode = 0
        self.wait = True
        self.restart_flg = False
        self.restart_process = False
        self.error_flg = False

    def run(self):
        if not self.wait:
            return
        asyncio.run(self.run_process())

        while self.wait:
            time.sleep(0.1)
            if self.restart_process:
                asyncio.run(self.run_process())
                self.restart_process = False

    async def run_process(self):
        if not self.wait:
            return
        print('Starting process...')
        self.process = await asyncio.create_subprocess_shell('python3 busy_test.py')
        try:
            await asyncio.wait_for(self.process.wait(), 6)
            self.returncode = self.process.returncode
            if self.returncode != 0 and self.returncode != -9:
                self.error_flg = True
                self.close()
            print('Process ended.')
        except asyncio.exceptions.TimeoutError as e:
            print('Timeout!')
            self.returncode = self.process.returncode if self.process.returncode is not None else -1
            self.error_flg = True
            self.close()
        except Exception as e:
            print('Error!')
            traceback.print_exc()
            self.returncode = self.process.returncode
            self.error_flg = True
            self.close()
    
    def kill(self):
        if not self.wait:
            return
        try:
            self.process.kill()
        except ProcessLookupError as e:
            pass
        if not self.error_flg:
            self.returncode = 0
        print('Process killed.')
    
    def stop(self):
        if not self.wait:
            return
        print('Stopping server...')
        self.kill()
        self.restart_flg = True
    
    def restart(self):
        if not self.wait:
            return
        print('Restarting server...')
        self.restart_process = True

    def close(self):
        if not self.wait:
            return
        self.kill()
        self.wait = False
        self.done = True

class Network(Thread):
    def __init__(self, config):
        Thread.__init__(self)
        self.app = web.Application()
        self.app.add_routes([web.post('/replica', self.handle_replica),
                             web.post('/message', self.handle_message),
                             web.post('/event', self.handle_event)])
        self.runner = web.AppRunner(self.app)
        self.site = None
        self.loop = None
    
    def run_server(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        self.loop.run_until_complete(self.runner.setup())
        self.site = web.TCPSite(self.runner, 'localhost', 10000)
        self.loop.run_until_complete(self.site.start())
        self.loop.run_forever()
        asyncio.run(self.runner.cleanup())

    def run(self):
        self.run_server()
        
    def stop(self):
        print('Stopping server.')
        asyncio.run(self.site.stop())
        print('Server stopped, stopping loop.')
        self.loop.call_soon_threadsafe(self.loop.stop)
        print('Loop stopped.')
        self.join()

    async def handle_replica(self, request):
        print('New replica received!')
        return web.Response()

    async def handle_message(self, request):
        return web.Response()

    async def handle_event(self, request):
        return web.Response()
    
class TLC(Thread):
    def __init__(self):
        Thread.__init__(self)
        self.cmd = 'java -jar dist/tla2tools_server.jar -controlled ../tla-benchmarks/Raft/model/RAFT_3_3.tla -config ../tla-benchmarks/Raft/model/RAFT_3_3.cfg -mapperparams "name=raft;port=2023;abstract"'
        self.wd = '../../tlc-controlled-with-benchmarks/tlc-controlled'
        self.process = None

    def run(self):
        asyncio.run(self.run_tlc())

    async def run_tlc(self):
        self.process = await asyncio.create_subprocess_shell(self.cmd, cwd=self.wd, stdout=open('abc.txt', 'w+'))
        await self.process.wait()

    def stop(self):
        self.process.kill()

    def get_states(self, event_trace) -> int:
        trace_to_send = event_trace
        trace_to_send.append({"reset": True})
        # logging.debug("Sending trace to TLC: {}".format(trace_to_send))
        try:
            r = requests.post(f'http://127.0.0.1:2023/execute', json=trace_to_send)
            if r.ok:
                response = r.json()
                # logging.debug("Received response from TLC: {}".format(response))               
                return [{"state": response["states"][i], "key" : response["keys"][i]} for i in range(len(response['states']))]
            else:
                print(f'Received error response from TLC, code {r.status_code}, text: {r.content}')
        except Exception as e:
            print(f'Error received from TLC: {e}')

        return []
    
class DataClass():
    listA = []
    def __init__(self):
        self.listB = []

if __name__ == '__main__':
    # sch = [
    #     {'type': 'Crash',
    #      'node': 0},
    #     {'type': 'Restart',
    #      'node': 0},
    #      {'type': 'Crash',
    #      'node': 0},
    #      {'type':'Restart',
    #      'node': 0}
    # ]
    
    # crashed = []
    # for i, step in enumerate(sch):
    #     if step['type'] == 'Crash':
    #         crashed.append((i, step))
        
    #     if step['type'] == 'Restart':
    #         index = -1
    #         for j in range(len(crashed)):
    #             if step['node'] == crashed[j][1]['node'] and i > crashed[j][0]:
    #                 index = j
    #                 break
                
    #         if index >= 0:
    #             crashed.pop(index)
    
    # print(crashed)
    # assert(len(crashed) == 0)

    

    # t1 = DataClass()
    # t2 = DataClass()

    # t1.listA.append(1)
    # t2.listB.append(2)

    # print(t1.listA)
    # print(t2.listA)
    
    n = Network({})
    n.start()
    print('Network running...')
    print('Main thread sleeping for 3...')
    time.sleep(3)
    print('Main thread registering.')
    requests.post('http://127.0.0.1:10000/replica', data=json.dumps({'a':0}))
    print('Main thread sleeping for 3...')
    time.sleep(3)
    print('Main thread stopping network.')
    n.stop()
    n.join()
    print('Server joined.')

    # server = Server()
    # server.start()
    # print('Main thread sleeping...')
    # time.sleep(1)
    # print('Main thread stopping server...')
    # server.stop()
    # time.sleep(1)
    # print('Main thread restarting server...')
    # server.restart()
    # print('Main thread sleeping...')
    # time.sleep(6)
    # # print('Main thread killing server.')
    # # server.kill()
    # print('Main thread woke up.')
    # server.close()
    # # server.join()
    # print(f'Server returned: {server.returncode}')

    # tlc = TLC()
    # print('MT starting TLC')
    # tlc.start()
    # print('MT sleeping for 10')
    # time.sleep(10)
    # print('Resetting TLC')
    # print(tlc.get_states([]))
    # print('Sleeping again for 5')
    # time.sleep(5)
    # print('Stopping TLC')
    # tlc.stop()
