# A Quick Note To Check Code For ECHO's Automated Pipeline

This folder contains code to support ECHO's core functionality with the capability of deploying the pipeline at scale to handle malware streams automatically. 

To start the dynamic sandboxing module, a bare-metal machine running Linux 20.04+ which connects the testbed phone is required. The machine will run the control server (Check code in [DynamicMobileDeviceControlPanel](DynamicMobileDeviceControlPanel)), which communicate with the mobile testbed through both WIFI and Android Debugging Bridge (ADB) - that to say, the mobile should conntect to the same WiFi as the Linux machine does. 
The machine should also set up a MITM proxy to intercept the command between the control server with the testbed, see code in [MITMProxy](MitMProxy). We provided an additional implemention of our unpacking module used in ECHO in [Unpacker](DynamicMobileDeviceControlPanel/Unpacker/). Check [Frida](https://frida.re/)'s documentation for more details. 
  

In terms of the data flow modules, we build our pipeline with our in-house cluster. This pipeline enables a distributed message queue system to run our pipeline over multiple VMs. We implemnented a Kafka-based customized message queue in [PipelineExecutionFramework](PipelineExecutionFramework). The customized Consumer (Message Queue Client) is coded in [DataflowModulePipeline](DataflowModulePipeline). To summarize, the message queue enables the users to feed malware streams into the queue and allocate different analysis tasks over worker VMs. See official documentation for more details. Besides, for each task, users should combine the pipeline with the core functional components to run ECHO's pipeline. 

All these pipeline modules sync-up the results through an in-house MongoDB. We implemented our DB template and necessary APIs in [GleanMongoDB](GleanMongoDB), where GLEAN is the previous name of ECHO.





