localDir="$(readlink -f $0)"
installDir="$(dirname "${localDir}")"/../..

workDir=$(pwd)

cliDir=${installDir}/apps/wanaku-cli/target/quarkus-app/
cliJar=${cliDir}/quarkus-run.jar

if [[ -d build ]] ; then
  rm -rf build
fi

mkdir build && cd build

java -jar ${cliJar} capabilities create resource --name test
if [[ ! -d wanaku-provider-test ]] ; then
  echo "The generated directory is wrong"
  ls -1
  exit 1
else
  cd wanaku-provider-test
fi

mvn -Pdist clean package
if [[ $? -ne 0 ]] ; then
  echo "The code does not compile"
  exit 2
fi

cd "${workDir}"/build

java -jar ${cliJar} capabilities create tool --name test
if [[ ! -d wanaku-tool-service-test ]] ; then
  echo "The generated directory is wrong"
  exit 1
else
  cd wanaku-tool-service-test
fi

mvn -Pdist clean package
if [[ $? -ne 0 ]] ; then
  echo "The code does not compile"
  exit 2
fi

cd "${workDir}"/build

java -jar ${cliJar} capabilities create resource --name test-camel --type camel
if [[ ! -d wanaku-provider-test-camel ]] ; then
  echo "The generated directory is wrong for camel resource"
  ls -1
  exit 1
else
  cd wanaku-provider-test-camel
fi

mvn -Pdist clean package
if [[ $? -ne 0 ]] ; then
  echo "The camel resource code does not compile"
  exit 2
fi

cd "${workDir}"/build

java -jar ${cliJar} capabilities create tool --name test-camel --type camel
if [[ ! -d wanaku-tool-service-test-camel ]] ; then
  echo "The generated directory is wrong for camel tool"
  exit 1
else
  cd wanaku-tool-service-test-camel
fi

mvn -Pdist clean package
if [[ $? -ne 0 ]] ; then
  echo "The camel tool code does not compile"
  exit 2
fi


cd "${workDir}"