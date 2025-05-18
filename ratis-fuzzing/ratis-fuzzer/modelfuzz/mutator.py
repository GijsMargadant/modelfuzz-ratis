import random

from enum import Enum

class MutatorType(Enum):
    ALL='all'
    SWAP_NODES='swap_nodes'
    SWAP_CRASH_NODES='swap_crash_nodes'
    SWAP_CRASH_STEPS='swap_crash_steps'
    SWAP_MAX_MESSAGES='swap_max_messages'

class MutatorFactory:
    @staticmethod
    def get_mutator(type: MutatorType, params):
        if type == MutatorType.ALL:
            return CombinedMutator(params)
        elif type == MutatorType.SWAP_NODES:
            return SwapNodesMutator(params)
        elif type == MutatorType.SWAP_CRASH_NODES:
            return SwapCrashNodesMutator(params)
        elif type == MutatorType.SWAP_CRASH_STEPS:
            return SwapCrashStepsMutator(params)
        elif type == MutatorType.SWAP_MAX_MESSAGES:
            return SwapMaxMessagesMutator(params)
        else:
            return CombinedMutator(params)

class Mutator:
    def __init__(self, params) -> None:
        self.params = params

    def mutate(self, schedule) -> list[dict]:
        pass

class SwapNodesMutator(Mutator):
    def __init__(self, params) -> None:
        super().__init__(params)

    def mutate(self, schedule) -> list[dict]:
        for _ in range(self.params.mutation_count):
            first_idx = random.choice(list(range(self.params.steps)))
            second_idx = random.choice([i for i in range(self.params.steps) if i != first_idx])

            first_step = -1
            second_step = -1
            ctr = 0
            for i, step in enumerate(schedule):
                if step['type'] == 'Schedule':
                    if ctr == first_idx:
                        first_step = i
                    elif ctr == second_idx:
                        second_step = i
                    ctr += 1
            
            # assert(first_step >= 0 and second_step >= 0)
            schedule[first_step], schedule[second_step] = schedule[second_step], schedule[first_step]
        return schedule

class SwapCrashNodesMutator(Mutator):
    def __init__(self, params) -> None:
        super().__init__(params)
    
    def mutate(self, schedule) -> list[dict]:
        for _ in range(self.params.mutation_count):
            if self.params.crash_quota == 1:
                for step in schedule:
                    if step['type'] == 'Crash':
                        step['node'] = random.choice([i for i in range(1, self.params.nodes+1, 1) if i != step['node']])
            else:
                first_idx = random.choice(list(range(self.params.crash_quota)))
                second_idx = random.choice([i for i in range(self.params.crash_quota) if i != first_idx])

                first_crash = -1
                second_crash = -1
                for i, step in enumerate(schedule):
                    if step['type'] == 'Crash':
                        if step['crash_id'] == first_idx:
                            first_crash = i
                        elif step['crash_id'] == second_idx:
                            second_crash = i
                
                # assert(first_crash >= 0 and second_crash >= 0)
                schedule[first_crash], schedule[second_crash] = schedule[second_crash], schedule[first_crash]
                
                # Repair restart order to ensure each crash has a restart
                first_restart = -1
                second_restart = -1
                for i, step in enumerate(schedule):
                    if step['type'] == 'Restart':
                        if step['crash_id'] == first_idx:
                            first_restart = i
                        elif step['crash_id'] == second_idx:
                            second_restart = i
                
                # assert(first_restart > 0 and second_restart > 0)
                schedule[first_restart], schedule[second_restart] = schedule[second_restart], schedule[first_restart]
        return schedule
    
class SwapCrashStepsMutator(Mutator):
    def __init__(self, params) -> None:
        super().__init__(params)
    
    def mutate(self, schedule) -> list[dict]:
        for _ in range(self.params.mutation_count):
            idx = random.choice(list(range(self.params.crash_quota)))

            crash_step = -1
            for i, step in enumerate(schedule):
                if step['type'] == 'Crash':
                    if step['crash_id'] == idx:
                        crash_step = i
            
            # assert(crash_step >= 0)
            step = schedule.pop(crash_step)
            schedule.insert(random.choice(list(range(len(schedule)+1))), step)

            # Repair restart order to ensure each crash has a restart
            restart_step = -1
            for i, step in enumerate(schedule):
                if step['type'] == 'Restart':
                    if step['crash_id'] == idx:
                        restart_step = i
            
            # assert(restart_step > 0)
            step = schedule.pop(restart_step)
            if crash_step < len(schedule):
                schedule.insert(random.choice([i for i in range(len(schedule)+1) if i > crash_step]), step) 
            else:
                schedule.append(step)
        return schedule
    
class SwapMaxMessagesMutator(Mutator):
    def __init__(self, params) -> None:
        super().__init__(params)

    def mutate(self, schedule) -> list[dict]:
        for _ in range(self.params.mutation_count):
            first_idx = random.choice(list(range(self.params.steps)))
            second_idx = random.choice([i for i in range(self.params.steps) if i != first_idx])

            first_step = 0
            second_step = 0
            ctr = 0
            for i, step in enumerate(schedule):
                if step['type'] == 'Schedule':
                    if ctr == first_idx:
                        first_step = i
                    elif ctr == second_idx:
                        second_step = i
                    ctr += 1
            
            # assert(first_step >= 0 and second_step >= 0)
            if 'max_msgs' in schedule[first_step].keys() and 'max_msgs' in schedule[second_step].keys():
                schedule[first_step]['max_msgs'], schedule[second_step]['max_msgs'] = schedule[second_step]['max_msgs'], schedule[first_step]['max_msgs']
        return schedule

class CombinedMutator(Mutator):
    def __init__(self, params) -> None:
        super().__init__(params)
        self.mutators: list[Mutator] = [SwapNodesMutator(self.params), 
                         SwapCrashNodesMutator(self.params), 
                         SwapCrashStepsMutator(self.params), 
                         SwapMaxMessagesMutator(self.params)]
    
    def mutate(self, schedule) -> list[dict]:
        for mutator in self.mutators:
            try:
                schedule = mutator.mutate(schedule)
            finally:
                schedule = schedule
        return schedule
                
                
    
        

        

