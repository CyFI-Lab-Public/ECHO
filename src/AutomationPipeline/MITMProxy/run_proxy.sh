#!/bin/bash
echo "start proxy on port 8080"
mitmdump -s /home/cyfi/workplace/ProjDropperChain/MITMProxy/mitm_script.py >  /dev/null 2>&1

