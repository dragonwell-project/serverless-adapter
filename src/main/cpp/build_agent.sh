#!/bin/bash
set -x

if [ 0"${JAVA_HOME}" = "0" ]; then
    echo "You must set \$JAVA_HOME first!"
else
    echo "Your JAVA_HOME: [${JAVA_HOME}]"
fi

# https://unix.stackexchange.com/a/101559
FULLPATH=$(readlink -f $(dirname $0))
OUTPUTDIR=$(readlink -f .)/output

# mkdir output
if [ -d "${OUTPUTDIR}" ]; then
  rm -rf ${OUTPUTDIR}
fi
mkdir ${OUTPUTDIR}

# build CDSXMLSRC
SRC=${FULLPATH}
g++ -I${SRC}/include/ -I${SRC}/include/linux ${SRC}/loadClassAgent.cpp ${SRC}/testClassLoaderJVMTI.cpp -fPIC -shared -o ${OUTPUTDIR}/libloadclassagent.so
if [ $? != '0' ]
then
	printf "Failed to generate so file"
	exit 1
fi

echo "Build succeeded!"
