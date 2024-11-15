


class DB_Template:
    def __init__(self, attributes, obj_dict = None, *args, **kwargs):
        self.attributes = attributes
        if obj_dict != None:
            self.from_dict(obj_dict)
        else:
            for k in attributes:
                if k in kwargs.keys():
                    self.__setattr__(k, kwargs[k])
                else:
                    self.__setattr__(k, attributes[k])
            
        # ignore args and unused kwargs for now

    def __eq__(self, __o: object) -> bool:
        if not isinstance(__o, self.__class__):
            return False
        for k in self.attributes:
            if k == 'attributes' or k == '_id':
                continue
            if self.__getattribute__(k) != __o.__getattribute__(k):
                    return False
        return True
        
    def to_dict(self):
        d = {}
        for k in self.attributes:
            d[k] = self.__getattribute__(k)
        return d
    
    def from_dict(self, d):
        for k in self.attributes:
            if k in d.keys():
                self.__setattr__(k, d[k])
            else:
                self.__setattr__(k, self.attributes[k])
                
    def match_query(self, query) -> bool:
        for k in query.keys():
            if self.__getattribute__(k) != query[k]:
                return False
        return True
    
    