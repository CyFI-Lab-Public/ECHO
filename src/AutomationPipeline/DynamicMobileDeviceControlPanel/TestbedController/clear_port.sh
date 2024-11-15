#!/bin/bash
echo "clear port for $1";
res=`netstat -tnlp | grep $1 | sed -n '1p' `;
IFS=' ' read -ra arr <<< "$res"
IFS='/' read -ra seg <<< "${arr[6]}"
pid=${seg[0]}
kill -9 $pid






