
import logging
import traceback
import asyncio

from threading import Lock
from concurrent.futures import ThreadPoolExecutor

from model_fuzz.cluster import RatisCluster

class WorkerUtil():
    def __init__(self, config):
        self.config = config

    async def create_and_run_cluster(self, config, ports, run_id, base_peer_port, group_id, iteration, capture, mimic=None):
        cluster = RatisCluster(config, ports, run_id, base_peer_port, group_id, capture)
        (trace, event_trace, is_buggy) = await cluster.run_iteration(iteration, mimic)
        return (trace, event_trace, is_buggy)
