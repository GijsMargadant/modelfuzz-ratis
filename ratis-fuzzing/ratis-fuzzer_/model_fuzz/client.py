import logging
import threading
import traceback
import subprocess

class RatisClient:
    def __init__(self, config, timeout, request, elle_file, peer_addresses, group_id) -> None:
        self.config = config
        self.timeout = timeout
        self.request = request
        self.elle_file = elle_file
        self.peer_addresses = peer_addresses
        self.group_id = group_id
        self.thread = None
        self.error_flag = False
        self.error_log = None

        self.log4j_config = '-Dlog4j.configuration=file:../ratis-examples/src/main/resources/log4j.properties'
        self.cmd = 'java {} -cp {} org.apache.ratis.examples.counter.client.CounterClient {} {} {}'.format(
            self.log4j_config,
            self.config.jar_path,
            self.request,
            self.peer_addresses,
            self.group_id
        )
    
    def start(self) -> None:
        def run(cmd, timeout):
            try:
                result = subprocess.run(cmd, shell=True, capture_output=True, text=True, check=True, timeout=timeout)
                logging.debug(result.stdout)
            except subprocess.CalledProcessError as e:
                if len(e.stderr) > 0:
                    logging.error('RatisClient CalledProcessError.')
                    self.error_flag = True
                    self.error_log = (e.stderr, e.stdout)
            except subprocess.TimeoutExpired as e:
                pass
                # logging.error('RatisClient TimeoutExpired.')
                # self.error_flag = True
                # TODO - Handle timeout
            finally:
                kill_cmd = f'pkill -f "{cmd}"'
                subprocess.run(kill_cmd, shell=True)
            return

        self.thread = threading.Thread(target=run, args=(self.cmd, self.timeout))
        self.thread.start()
        logging.debug(f'RatisClient started.\n{self.cmd}')

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