localDir="$(readlink -f $0)"
installDir="$(dirname "${localDir}")"/../..

workDir=$(pwd)

cliDir=${installDir}/cli/target/quarkus-app/
cliJar=${cliDir}/quarkus-run.jar

if [[ -d build ]] ; then
  rm -r build
fi

mkdir build && cd build

java -jar ${cliJar} services create resource --name test
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

java -jar ${cliJar} services create tool --name test
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


cd "${workDir}"