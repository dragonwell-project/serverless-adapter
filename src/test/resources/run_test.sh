#!/bin/bash -v

set -x

FULLPATH=$(realpath $(dirname $0))
OUTPUTDIR=$(realpath .)/output

if [ ! -d ${OUTPUTDIR} ]; then
  echo "Didn't find the output dir in the project root. You'd run \"build_agent.sh\" first."
  exit 1
fi

AGENT=$(realpath .)/target/serverless-adapter-0.1-jar-with-dependencies.jar

export LD_LIBRARY_PATH=${OUTPUTDIR}:${LD_LIBRARY_PATH}


CLASS4CDS=com.alibaba.jvm.cds.classlist.ClassListTransformer
LST=hello2.lst
JSA=hello2.jsa
LST_AFTER=hello.lst
JAR="-jar ${FULLPATH}/spring-helloworld.jar"
OP="-DstartupBenchmark -XX:+UnlockExperimentalVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+EagerAppCDS -XX:+AppCDSLegacyVerisonSupport -Dserver.port=12623"
OP="${OP} -agentlib:loadclassagent -Xbootclasspath/a:${AGENT}"
rm -rf ${LST} ${JSA} ${LST_AFTER} tmp

${JAVA_HOME}/bin/java ${OP} -Xshare:off -XX:DumpLoadedClassList=${LST} ${JAR}
${JAVA_HOME}/bin/java --add-exports java.base/jdk.internal.misc=ALL-UNNAMED -cp ${AGENT} ${CLASS4CDS} ${LST} ${LST_AFTER}
${JAVA_HOME}/bin/java ${OP} -Xshare:dump -XX:SharedClassListFile=${LST_AFTER} -XX:SharedArchiveFile=${JSA} ${JAR}
${JAVA_HOME}/bin/java ${OP} -Xshare:on -Dcom.alibaba.cds.listPath=${LST_AFTER} -Dcom.alibaba.appcds.debug=false -XX:SharedArchiveFile=${JSA} ${JAR}

