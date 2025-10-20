
class State:
    
    def __init__(self):
        
        self.cpu_history: list[tuple[float, float, float]] = []
        
        self.disk_io_history: list[tuple[float, float, float]] = []
        
        self.alternate_history: list[tuple[float, int, float, float]] = []
        self.axis_history: list[tuple[float, int, float]] = []
        self.keep_axis: bool = False
        
state = State()