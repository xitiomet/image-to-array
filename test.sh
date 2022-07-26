#!/bin/bash
#java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i hope.jpg -s 64x64 -d -a -h
#java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i https://openstatic.org/ -b > /dev/null
#java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i https://en.m.wikipedia.org/wiki/Gear -b > /dev/null

#java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i lossless.webp -o lossless.png
#java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i yoda.jpg -o yoda.webp
#java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i noise.png -o noise.jpeg
#java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i cat.png -o cat.gif

java -Djava.awt.headless=true -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/ -jar target/image-to-array-1.0-SNAPSHOT.jar -i arrows.png -o .ico -o .icns
