import logging
import threading
import traceback
import subprocess

class RatisServer:
    def __init__(self, config, timeout, run_id, fuzzer_port, server_client_port, peer_index, peer_addresses, group_id, capture) -> None:
        self.config = config
        self.timeout = timeout
        self.run_id = run_id
        self.fuzzer_port = fuzzer_port
        self.server_client_port = server_client_port
        self.peer_index = peer_index
        self.peer_addresses = peer_addresses
        self.group_id = group_id
        self.thread = None
        self.error_flag = False
        self.error_log = None
        self.capture = capture

        # enable assertions -ea
        self.log4j_config = '-Dlog4j.configuration=file:../ratis-examples/src/main/resources/log4j.properties' 
        self.cmd = self.get_cmd()

    def get_cmd(self, restart=False):
        r = 1 if restart else 0
        cmd = 'java {} -cp {} org.apache.ratis.examples.counter.server.CounterServer {} {} {} {} {} {} {}'.format(
            self.log4j_config,
            self.config.jar_path,
            self.run_id,
            self.fuzzer_port,
            self.server_client_port,
            self.peer_index,
            self.peer_addresses,
            self.group_id,
            r
        )
        return cmd
    
    def start(self, restart=False) -> None:
        def run(cmd, timeout, run_id, capture):
            try:
                result = subprocess.run(cmd, shell=True, capture_output=capture, text=True, check=True, timeout=timeout)
                logging.debug(result.stdout)
            except subprocess.CalledProcessError as e:
                if e.stderr is None:
                    return
                if len(e.stderr) > 0:
                    logging.error(f'CalledProcessError {run_id}.')
                    if not "Connect timed out" in e.stderr:
                        self.error_flag = True
                        self.error_log = (e.stderr, e.stdout)
            except subprocess.TimeoutExpired as e:
                pass
                # logging.error('TimeoutExpired.')
                # self.error_flag = True
                # TODO - Handle timeout
            finally:
                kill_cmd = f'pkill -f "{cmd}"'
                subprocess.run(kill_cmd, shell=True)
            return

        self.cmd = self.get_cmd(restart)
        self.thread = threading.Thread(target=run, args=(self.cmd, self.timeout, self.run_id, self.capture))
        self.thread.start()
        logging.debug(f'RatisServer {self.peer_index} started.\n{self.get_cmd(False)}')

    def shutdown(self) -> bool:
        try:
            kill_cmd = f'pkill -f "{self.cmd}"'
            subprocess.run(kill_cmd, shell=True)
            if self.thread is not None:
                self.thread.join()
        except Exception as e:
            traceback.print_exc()
            return False
        finally:
            return True