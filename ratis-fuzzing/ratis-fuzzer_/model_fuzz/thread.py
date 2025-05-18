import logging
import subprocess
from threading import Thread

class ProcessThread(Thread):
    def __init__(self):
        self.error_flag = False
        self.error_log = None
        self.should_stop_immidiately = False

    def run(self, cmd, timeout, shutdown):
            try:
                result = subprocess.run(cmd, shell=True, capture_output=True, text=True, check=True, timeout=timeout)
                logging.debug(result.stdout)
            except subprocess.CalledProcessError as e:
                logging.error('RatisClient CalledProcessError.')
                self.error_flag = True
                logging.error(e.stderr)
                logging.error(e.stdout)
                self.error_log = (e.stderr, e.stdout)
            except subprocess.TimeoutExpired as e:
                logging.error('RatisClient TimeoutExpired.')
                self.error_flag = True
                logging.error(e.stderr)
                logging.error(e.stdout)
                # TODO - Handle timeout
            finally:
                kill_cmd = f'pkill -f "{cmd}"'
                subprocess.run(kill_cmd, shell=True)
                shutdown()
            return