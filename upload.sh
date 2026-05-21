#!/bin/bash
echo -n "Uploading files to openstatic.org..."
{
    scp target/image-to-array-*.jar openstatic.org:openstatic.org/projects/imagetoarray/
    scp target/image-to-array-*.deb openstatic.org:openstatic.org/projects/imagetoarray/
    scp target/ita-setup.exe openstatic.org:openstatic.org/projects/imagetoarray/
    scp README.md openstatic.org:openstatic.org/projects/imagetoarray/
} &> /dev/null
echo " Done."