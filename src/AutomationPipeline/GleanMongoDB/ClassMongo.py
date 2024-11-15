import os, sys
PROJ_ROOT_FOLDER = os.environ['GLEAN_PATH']
if not PROJ_ROOT_FOLDER in sys.path:
    sys.path.append(PROJ_ROOT_FOLDER)
from ENV import *

import subprocess

import pymongo
import urllib

from .Class import Class

class ClassMongo:
    def __init__(self, test_connection: bool = False) -> None:
            self.mongoClient, self.db, self.collection = self.init_mongo_connection(test_connection)

    def init_mongo_connection(self, test_connection: bool = False):
        self.init_mongo_connection_channel()
        if self.get_my_ip() == MONGO_HOST_IP_ADDR:
            port = MONGO_PORT
        else:
            port = MONGO_LOCAL_PORT
        mongoClient = pymongo.MongoClient(host=MONGO_CONN_IP_ADDR, port=port, username = MONGO_USER, password= MONGO_PWD)
        db = mongoClient[MONGO_DB_NAME]

        if test_connection:
            try:
                print("MongoDB connection test:")
                print("MongoDB version: %s" % db.command("serverStatus")['version'])
                print("MongoDB connection status: %s" % db.command("serverStatus")['ok'])
                print("MongoDB connection test done.")
            except Exception as e:
                print(e)
                exit()
        print("db init success")
        collection = db['malware']
        return mongoClient, db, collection

    def update(self, clz: Class, collection:str = None):
        if collection == None:
            collection = self.collection      
        count = collection.count_documents({"class_hash": clz.class_hash})
        if count == 0:
            _id = collection.insert_one(clz.to_dict())
        else: 
            _id = collection.replace_one({"class_hash": clz.class_hash}, clz.to_dict())
        return _id
    
    def query_and_update(self, q, update, collection = None):
        if collection == None:
            collection = self.collection
        result = collection.update_many(q, {"$set": update})
        return result
    
    def get_record(self, class_hash:str): 
        return self.collection.find_one({'class_hash': class_hash})
    
    def query(self, q, sort_key = None, reverse = False, collection = None):
        if collection == None:
            collection = self.collection
        result = collection.find(q)
        if sort_key != None:
            result.sort(sort_key, pymongo.DESCENDING if reverse else pymongo.ASCENDING)
        return list(result)
    
    def remove(self, q:dict, collection = None):
        if collection == None:
            collection = self.collection
        collection.delete_many(q)
        
    def check_mongo_port_forwarding(self):
        try:  
            cmd = "nc -zv localhost {}".format(MONGO_LOCAL_PORT)
            p = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            out, _ = p.communicate(timeout=5)
            outstr = str(out.decode('utf-8'))
            if "Connection refused" in outstr:
                return False
            elif "succeeded" in outstr:
                print("Coonnection exists")
                return True
        except subprocess.TimeoutExpired:
            print("Timeout")
            return False
        except Exception as e:
            print("Unexpected error")
            return False

        return False


    def setup_mongo_port_forwarding(self):
        try:
            cmd = "ssh -fN -L {}:localhost:{} {}".format(MONGO_LOCAL_PORT, MONGO_PORT, MONGO_REMOTE_CONFIG_NAME)
            p = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            print("ssh port forwarding setup")
        except:
            return False 
        return True
        
    def init_mongo_connection_channel(self):
        if not self.check_mongo_port_forwarding():
            if not self.setup_mongo_port_forwarding():
                print("Failed to setup port forwarding")

    def get_my_ip(self):
        return urllib.request.urlopen('https://api.ipify.org').read().decode('utf8')
