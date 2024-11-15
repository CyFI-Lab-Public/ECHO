import json, os, sys
PROJ_ROOT_FOLDER = os.environ['GLEAN_PATH']
if not PROJ_ROOT_FOLDER in sys.path:
    sys.path.append(PROJ_ROOT_FOLDER)
from ENV import *


from GleanMongoDB.DB_Template import DB_Template

class_attributes = {
    '_id': '', # mongo id
    'class_name': '', # class name
    'class_hash': '', # class hash
    'methods': [], # list of methods,
    'fields': [], # list of fields
    'from_unpacker': False, # if this class is from unpacker
}


class Class(DB_Template):
    def __init__(self, class_dict = None, *args, **kwargs):
        super().__init__(class_attributes, class_dict, *args, **kwargs)
        
