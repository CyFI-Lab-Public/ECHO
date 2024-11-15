import subprocess


PRODUCTION_TOPICS = ['UNPACK', 'FD_CLASS_SCAN', "FD_JSI", "FD_DCL","DYNAMIC_JSI_DCL_VALID", "TEMPLATE"]

def check_exist_topics() -> list:
    p = subprocess.run('$KAFKA_PATH/bin/kafka-topics.sh --list --bootstrap-server localhost:9092', stdout=subprocess.PIPE, shell=True)
    output = p.stdout
    topics = output.decode('utf-8').strip().split('\n')
        # p.communicate() 
    return topics    
    
def create_topic(topic_name, partitions=20):
    p = subprocess.run('$KAFKA_PATH/bin/kafka-topics.sh --create --bootstrap-server localhost:9092 --partitions {} --topic {}'.format(partitions, topic_name), stdout=subprocess.PIPE, shell=True)
    output = p.stdout.decode('utf-8').strip()
    print(output)
    
def delete_topics(topics = []):
    for topic_name in topics:    
        p = subprocess.run('$KAFKA_PATH/bin/kafka-topics.sh --delete --bootstrap-server localhost:9092  --topic {}'.format(topic_name), stdout=subprocess.PIPE, shell=True)
        output = p.stdout.decode('utf-8').strip()
        print(output + ' deleted' + topic_name)
        
def delete_all_topics():
    topics = check_exist_topics()
    delete_topics(topics)
    print('All topics deleted, topic exists: ')
    print(check_exist_topics())

def create_if_not_exists(topic_name, partitions=20):
    exist_topics = check_exist_topics()
    if topic_name not in exist_topics:
        return create_topic(topic_name, partitions)        
    else:
        return True



if __name__ == '__main__':
    #create_if_not_exists('FD_CLASS_SCAN')    
    # delete_topics(PRODUCTION_TOPICS)
    print(check_exist_topics())
    for topic in PRODUCTION_TOPICS:
        create_if_not_exists(topic)
    print(check_exist_topics())

# $KAFKA_PATH/bin/kafka-topics.sh --create --topic $1 --bootstrap-server localhost:9092
