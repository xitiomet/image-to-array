#!/bin/bash
mvn clean package
java -jar target/image-to-array-1.0-SNAPSHOT.jar $*
