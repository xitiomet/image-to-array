#!/bin/bash
java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i hope.jpg -s 64x64 -d -a -h
java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i https://openstatic.org/ -b > /dev/null
