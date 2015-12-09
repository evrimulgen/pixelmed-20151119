#!/bin/sh
#
# To always send to a particular DICOM Storage SCP the contents of a particular drive or folder, edit the following variables:

host=localhost
port=11112
calledaet=STORESCP
callingaet=IMPORTER
driveorpath=/Volumes/UNTITLED

echo "Reading from drive or path ${driveorpath} and sending to ${host} ${port} ${calledaet}"
java -Xms128m -Xmx512m -cp "./pixelmed.jar:./lib/additional/commons-compress-1.9.jar:./lib/additional/commons-codec-1.3.jar" com.pixelmed.network.NetworkMediaImporter "${host}" "${port}" "${calledaet}" "${callingaet}" "${driveorpath}"

