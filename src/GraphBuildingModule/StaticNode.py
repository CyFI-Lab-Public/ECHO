


class StaticNode:
    
    
    def __init__(self, node_dict, line_number) -> None:
        self.line_number = line_number
        if 'methodName' in node_dict:
            self.method = node_dict['methodName']
            if '<init>' in self.method:
                first_seg = self.method.split(':')[0].replace('<','')            
                self.method = self.method.replace('void', first_seg)                
    
        if 'stack' in node_dict:
            original_stack = node_dict['stack']
            self.stack = []
            for l in original_stack:
                l = l.replace('<', '').replace('>', '')
                segs = l.split(' ')
                new_l = segs[0] + segs[2].split('(')[0]
                self.stack.append(new_l)    
        self.matched_dynamic_nodes = set()        
    
    def __eq__(self, value: object) -> bool:
        if isinstance(value, StaticNode):
            return self.method == value.method and self.stack == value.stack and self.line_number == value.line_number
        return False
    

    def __hash__(self) -> int:
        return hash(self.method) + sum([hash(s) for s in self.stack]) + hash(self.line_number)
    
    
