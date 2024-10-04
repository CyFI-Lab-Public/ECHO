from mitmproxy import ctx
from mitmproxy import flowfilter
import mitmproxy

class Myaddon: 
    def __init__(self, domain):
        self.intercept = domain

    def request(self, flow):
        if flow.request.host == self.intercept:
            flow.intercept()
            print("traffic to %s is intercepted" % self.intercept)
            flow.request.host = 'localhost'
            flow.request.port = 1551
            flow.request.path = '/original'

            # print(flow.request.query)
            # flow.request.query['whatever parameter you want to change'] = value
            flow.resume()
        #    print(flow.response)


addons = [Myaddon("sdk1.itracker.cn")]
#
