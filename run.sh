#!/bin/bash
mvn exec:java -Dexec.mainClass="org.openstatic.ImageArrayTool" -Dexec.args="$*"
