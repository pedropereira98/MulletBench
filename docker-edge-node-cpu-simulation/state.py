
class State:
    
    def __init__(self):
        
        self.edge_node_name: str = ""
        
        self.cpu_history: list[tuple[float, float, float]] = []
        
        self.disk_io_history: list[tuple[float, float, float]] = []
        
        self.alternate_history: list[tuple[tuple[float, float, float], int, float, float]] = []
        self.axis_history: list[tuple[float, int, float]] = []
        self.keep_axis_initial_values: tuple[float, float] = (0.0, 0.0)
        self.keep_axis: bool = False
        
state = State()