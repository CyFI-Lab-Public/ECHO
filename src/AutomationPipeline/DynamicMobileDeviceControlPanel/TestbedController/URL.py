import os, sys, json

from pkg_resources import UnknownExtra
import ControlENV

url_attributes = {
    'url': "",
    'domain': "",
    'alive': False,
    'owner_type': 'unknown',
    'ip_info': {},
    'protocol': ""

}

class URL:
    def __init__(self, url = "", domain = "", alive = False, owner_type = 'unknown', ip_info = {}, protocol = "" ) -> None:
        self.url = url
        self.domain = domain
        self.alive = alive
        self.owner_type = owner_type
        self.ip_info = ip_info
        self.protocol = protocol
        
        pass

    def to_dict(self):
        d = {}
        for k in url_attributes:
            d[k] = self.__getattribute__(k)
        return d