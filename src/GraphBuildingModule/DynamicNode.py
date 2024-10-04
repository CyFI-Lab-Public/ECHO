def chash(item):
        if type(item) == list:
            return sum([hash(i) for i in item])
        else:
            return hash(item)
        
class DynamicNode:
    
    def __init__(self, node_dict: dict, line_number: int) -> None:
        self.line_number = line_number
        # Set placeholders for empty fields
        self.method = node_dict.get('method', "")
        self.method = self.method.replace('[B', 'byte[]').replace('[I', 'int[]').replace('[C', 'char[]').replace('[S', 'short[]').replace('[J', 'long[]').replace('[F', 'float[]').replace('[D', 'double[]').replace('[Z', 'boolean[]')
        
        self.packageName = node_dict.get('packageName', "")
        self.type = node_dict.get('type', "")
        self.url = node_dict.get('url', "")
        if 'file://' in self.url:
            self.filePath = self.url.replace('file://', '')    
        else:
            self.filePath = node_dict.get('filePath', "")
            
        if self.filePath != "":
            self.filePath = self.filePath.replace('/data/data/', '/data/user/0/')
        
        self.loadClassName = node_dict.get('loadClassName', "")
        self.loadMethodName = node_dict.get('loadMethodName', "")
        self.isStatic = node_dict.get('isStatic', 0)  # Default to 0 if not provided
        self.interfaceNames = node_dict.get('interfaceNames', [])
        self.desFilePath = node_dict.get('desFilePath', "").replace('/data/data/', '/data/user/0/')
        
        self.stack = node_dict.get('stack', [])
        if '<init>' in self.method:
            segs = self.stack[0].split(':')
            self.stack[0] = segs[0] + ":" + 'init'
            
            first_seg = self.method.split(':')[0].replace('<','')            
            self.method = self.method.replace('void', first_seg)
        
        
        self.matched_static_vertex = None
        

    def __eq__(self, value: object) -> bool:
        if isinstance(value, DynamicNode):
            return (self.method == value.method and 
                    self.packageName == value.packageName and
                    self.type == value.type and 
                    self.url == value.url and 
                    self.filePath == value.filePath and 
                    self.loadClassName == value.loadClassName and 
                    self.loadMethodName == value.loadMethodName and
                    self.isStatic == value.isStatic and 
                    self.interfaceNames == value.interfaceNames and 
                    self.desFilePath == value.desFilePath and 
                    self.stack == value.stack and 
                    self.line_number == value.line_number)
        return False

    def __hash__(self) -> int:
        return (chash(self.method) + chash(self.packageName) + chash(self.type) + 
                chash(self.url) + chash(self.filePath) + chash(self.loadClassName) + 
                chash(self.loadMethodName) + chash(self.isStatic) + chash(self.interfaceNames) + 
                chash(self.desFilePath) + chash(self.stack) + chash(self.line_number))

    
    def to_dict(self):
        res = {}
        
        if self.method != "":
            res['method'] = self.method
        if self.packageName != "":
            res['packageName'] = self.packageName
        if self.type != "":
            res['type'] = self.type
        if self.url != "":
            res['url'] = self.url
        if self.filePath != "":
            res['filePath'] = self.filePath
        if self.loadClassName != "":
            res['loadClassName'] = self.loadClassName
        if self.loadMethodName != "":
            res['loadMethodName'] = self.loadMethodName
        if self.isStatic != 0:
            res['isStatic'] = self.isStatic
        if self.interfaceNames != []:
            res['interfaceNames'] = self.interfaceNames
        if self.desFilePath != "":
            res['desFilePath'] = self.desFilePath
        if self.stack != []:
            res['stack'] = self.stack
        if self.line_number != "":
            res['line_number'] = self.line_number
        res['hash'] = hash(self)
        
        return res
                