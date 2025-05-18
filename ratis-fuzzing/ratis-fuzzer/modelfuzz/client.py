import logging
import threading
import traceback
import subprocess

import time
import asyncio
import traceback
from threading import Thread

class RatisClient(Thread):
    def __init__(self, config) -> None:
        Thread.__init__(self)
        self.config = config
        self.process = None
        self.returncode = 0
        self.done = False
        self.error_flg = False

        self.stdout = self.config['stdout']
        self.stderr = self.config['stderr']

        self.log4j_config = '-Dlog4j.configuration=file:../ratis-examples/src/main/resources/log4j.properties'
        self.cmd = 'java {} -cp {} org.apache.ratis.examples.counter.client.CounterClient {} {} {}'.format(
            self.log4j_config,
            self.config['jar_path'],
            self.config['request'],
            self.config['peer_addresses'],
            self.config['group_id']
        )
    
    def run(self) -> None:
        asyncio.run(self.run_client())
        if not self.done:
            self.close()

    
    async def run_client(self,) -> None:
        self.process = await asyncio.create_subprocess_shell(self.cmd, stdout=self.stdout, stderr=self.stderr)
        try:
            await asyncio.wait_for(self.process.wait(), self.config['timeout'] + 10)
            self.returncode = self.process.returncode
            if self.returncode != 0 and self.returncode != -9:
                self.error_flg = True
                self.close()
        except asyncio.exceptions.TimeoutError as t:
            print(f'Timeout on client: {self.config["run_id"]}-{self.config["request"]}')
            traceback.print_exc()
            self.returncode = self.process.returncode if self.process.returncode is not None else -1
            self.error_flg = True
            self.close()
        except Exception as e:
            print(f'Error on client: {self.config["run_id"]}-{self.config["request"]}')
            traceback.print_exc()
            self.returncode = self.process.returncode if self.process.returncode is not None else -1
            self.error_flg = True
            self.close()
        # print(f'Ratis server subprocess: {self.process}')
            
    def kill(self) -> None:
        try:
            self.process.kill()
        except ProcessLookupError as e:
            pass
        if not self.error_flg:
            self.returncode=0
    
    def close(self) -> None:
        # print('Client close')
        self.kill()
        self.done = True