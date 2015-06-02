#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-
#
# Last Updated: <2015-May-27 18:38:32>
#

apiproxy=costco-json-schema-validator1
jresourcedir=~/dev/apiproxies/${apiproxy}/apiproxy/resources/java/
outdir=out
srcdir=src/java/com/dinochiesa/jsonschema
sources="${srcdir}/ValidatorCallout.java"
jartarget=JsonSchemaValidatorCallout.jar
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.7.0_67.jdk/Contents/Home

if [ ! -d "${JAVA_HOME}" ]; then
    echo "Must set JAVA_HOME in this script to point to a JDK6."
    echo
    exit 1
fi

function check_and_download() {
  local filename
  local url
  filename=$1
  url=$2
  if [ ! -f "$filename" ]; then
    # download from github
    echo download $filename
    curl -s -o $filename $url
  fi
}

apigeejars=""
if [ ! -d "edge-libs" ]; then
  mkdir edge-libs
fi
if [ ! -d "lib" ]; then
  mkdir lib
fi

cd edge-libs
check_and_download "expressions-1.0.0.jar" https://raw.githubusercontent.com/apigee/api-platform-samples/master/doc-samples/java-cookbook/lib/expressions-1.0.0.jar
check_and_download "message-flow-1.0.0.jar" https://raw.githubusercontent.com/apigee/api-platform-samples/master/doc-samples/java-cookbook/lib/message-flow-1.0.0.jar
                                            
cd ..

# 3rd party jars, must manage these separately later (copy to java resource dir)
cd lib
check_and_download "json-schema-validator-2.2.6.jar" http://repo1.maven.org/maven2/com/github/fge/json-schema-validator/2.2.6/json-schema-validator-2.2.6.jar
check_and_download "json-schema-core-1.2.5.jar" http://repo1.maven.org/maven2/com/github/fge/json-schema-core/1.2.5/json-schema-core-1.2.5.jar
check_and_download "jackson-coreutils-1.8.jar" http://repo1.maven.org/maven2/com/github/fge/jackson-coreutils/1.8/jackson-coreutils-1.8.jar
check_and_download "msg-simple-1.1.jar" http://repo1.maven.org/maven2/com/github/fge/msg-simple/1.1/msg-simple-1.1.jar
check_and_download "jackson-databind-2.2.3.jar" http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.2.3/jackson-databind-2.2.3.jar
check_and_download "jackson-annotations-2.2.3.jar" http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.2.3/jackson-annotations-2.2.3.jar
check_and_download "jackson-core-2.2.3.jar" http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.2.3/jackson-core-2.2.3.jar

cd ..


apigeejars=`find edge-libs -type f -name "*.jar" -maxdepth 1 | tr '\n' ':'`
jars=`find lib -type f -name "*.jar" -maxdepth 1 | tr '\n' ':'`
classpath=${apigeejars}${jars}.

rm -fr ${outdir}
if [ ! -d "${outdir}" ]; then
    mkdir ${outdir}
fi


${JAVA_HOME}/bin/javac -Xlint:unchecked  -d ${outdir} -sourcepath src -classpath ${classpath} ${sources}

rc=$?
if [[ $rc != 0 ]] ; then
    echo
    echo "The compile failed."
    echo
    exit $rc
fi

## Create the jar

cd ${outdir}
if [ -d "../src/resources" ]; then
  cp -r ../src/resources resources
fi
${JAVA_HOME}/bin/jar -cvf ${jartarget} com resources
cd ..

## copy the output jar and dependencies if possible
echo
if [ -d "${jresourcedir}" ]; then
  #echo "Copying the file ${outdir}/${jartarget} to ${jresourcedir}"
  echo
  echo cp ${outdir}/${jartarget} ${jresourcedir}
  cp ${outdir}/${jartarget} ${jresourcedir}
  # copy 3rd-party jars to java resource dir
  find lib -type f -name "*.jar" -maxdepth 1 -exec cp {} ${jresourcedir} \;
else
  echo "You must now copy the file ${outdir}/${jartarget} "
  echo "         and all the files in lib "
  echo "         to ...apiproxy/resources/java "
fi
echo
echo
