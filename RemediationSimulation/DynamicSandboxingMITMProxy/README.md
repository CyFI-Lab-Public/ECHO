# Malware C&C Simulation MITM Proxy 

This folder contains customzied script for mitm proxy to redirect real & inactive malware C&C server to simulate the remote payload fetching process. Given the remote server of the given test case has been taken down and thus invalidated, we use this to simulate the live C&C server for the test sample. 

Notable, this is not a part of ECHO's pipeline, but we provided for users who want to run the test case with the dynamic sandboxing module. Ideally, for malware samples with live C&C backends, ECHO does not need to setup this MitM proxy and should capture the payload-deployment related vertices when the malware communicates with the live C&C servers and fetches the payload accordingly.   

## Dependency:
Flask (version==2.0.3) 

[mitmproxy](https://mitmproxy.org/) (version==9.0.1) 


## Usage: 

```
mitmweb -s src/mitm.py
```
and 

```
python src/payload_server.py 
```


This will redirect all traffic to the C&C server locally and return a payload previous acquired by ECHO. To test the provided fake youku malware, this server is the only one required to simulate the live C&C backend of the malware.  



For reproducing the remediation (as shown in the end of the demo video), additional server for hosting user-notification webpages is required, run 

```
python src/alert_server.py
```


For more information on installing mitmproxy and Flask, please visit the official websites: mitmproxy and Flask. If you encounter any issues, feel free to reach out to us at runze.zhang@gatech.edu.

We would like to express our gratitude to all the authors and developers of these amazing tools for their contributions in helping us build our own solutions.