#!/bin/bash
mvn clean
mvn package
echo Generating Executable... ita
cat src/stub.sh target/image-to-array-1.0-SNAPSHOT.jar > ./ita
chmod +x ita
