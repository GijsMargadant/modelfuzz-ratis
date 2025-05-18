import time
import asyncio
import traceback
from threading import Thread

class RatisServer(Thread):
    def __init__(self, config) -> None:
        Thread.__init__(self)
        # print('Initializing server')
        self.config = config
        self.process = None
        self.returncode = 0
        self.done = False
        self.restart_flg=False
        self.restart_process = False
        self.wait = True
        self.error_flg = False

        self.stdout = self.config['stdout']
        self.stderr = self.config['stderr']

        self.log4j_config = '-Dlog4j.configuration=file:../ratis-examples/src/main/resources/log4j.properties' 
        self.cmd = self.get_cmd()

    def get_cmd(self) -> str:
        if not self.wait:
            return
        r = 1 if self.restart_flg else 0
        # enable assertions -ea
        cmd = 'java {} -cp {} org.apache.ratis.examples.counter.server.CounterServer {} {} {} {} {} {} {}'.format(
            self.log4j_config,
            self.config['jar_path'],
            self.config['run_id'],
            self.config['fuzzer_port'],
            self.config['listener_port'],
            self.config['peer_index'],
            self.config['peer_addresses'],
            self.config['group_id'],
            r
        )
        return cmd
    
    def run(self) -> None:
        # print('Running server')
        if not self.wait:
            return
        asyncio.run(self.run_server())

        while self.wait:
            time.sleep(0.1)
            if self.restart_process:
                self.restart_process = False
                asyncio.run(self.run_server())

    
    async def run_server(self) -> None:
        if not self.wait:
            return
        
        if self.restart_flg:
            self.cmd = self.get_cmd()
            self.restart_flg = False
        else:
            self.cmd

        self.process = await asyncio.create_subprocess_shell(self.cmd, stdout=self.stdout, stderr=self.stderr)
        try:
            await asyncio.wait_for(self.process.wait(), self.config['timeout']+10) # CancellationException, TimeoutException
            self.returncode = self.process.returncode
            if self.returncode != 0 and self.returncode != -9:
                self.error_flg = True
        except asyncio.exceptions.TimeoutError as t:
            print(f'Timeout on: {self.config["run_id"]}-{self.config["peer_index"]}')
            traceback.print_exc()
            self.returncode = self.process.returncode if self.process.returncode is not None else -1
            self.error_flg = True
        except Exception as e:
            print(f'Error on: {self.config["run_id"]}-{self.config["peer_index"]}')
            traceback.print_exc()
            self.returncode = self.process.returncode if self.process.returncode is not None else -1
            self.error_flg = True
        finally: 
            self.close()
        # print(f'Ratis server subprocess: {self.process}')
            
    def kill(self) -> None:
        if not self.wait:
            return
        try:
            self.process.kill()
        except ProcessLookupError as e:
            pass
        if not self.error_flg:
            self.returncode=0
    
    def crash(self) -> None :
        if not self.wait:
            return
        self.kill()
        self.restart_flg = True
    
    def restart(self) -> None:
        if not self.wait:
            return
        if not self.restart_flg:
            return
        self.restart_process = True
    
    def close(self) -> None:
        # print('Server close')
        if not self.wait:
            return
        self.kill()
        self.wait = False
        self.done = True