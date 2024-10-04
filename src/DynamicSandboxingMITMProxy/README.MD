# Malware C&C Simulation MITM Proxy 

This folder contains customzied script for mitm proxy to redirect real & inactive malware C&C server to simulate the remote payload fetching process. 

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


This will redirect all traffic to the C&C server locally and return a payload previous acquired by ECHO.

For reproducing the remediation, additional server for hosting user-notification webpages is required, run 

```
python src/alert_server.py
```


For more information on installing mitmproxy and Flask, please visit the official websites: mitmproxy and Flask. If you encounter any issues, feel free to reach out to us at runze.zhang@gatech.edu.

We would like to express our gratitude to all the authors and developers of these amazing tools for their contributions in helping us build our own solutions.