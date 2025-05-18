from enum import Enum

class FuzzerType(Enum):
    MODELFUZZ='modelfuzz'
    RANDOM='random'
    TRACE='trace'