currentDir=$(pwd)
cliJar=${currentDir}/cli/target/quarkus-app/quarkus-run.jar

cd "${currentDir}"/cli/target

java -jar ${cliJar} services create resource --name test
if [[ ! -d wanaku-provider-test ]] ; then
  echo "The generated directory is wrong"
  ls -1
  exit 1
else
  cd wanaku-provider-test
fi

mvn clean package
if [[ $? -eq 0 ]] ; then
  echo "The code does not compile"
  exit 2
fi

cd "${currentDir}"/cli/target

java -jar ${cliJar} services create tool --name test
if [[ ! -d wanaku-tool-service-test ]] ; then
  echo "The generated directory is wrong"
  exit 1
else
  cd wanaku-tool-service-test
fi

mvn clean package
if [[ $? -eq 0 ]] ; then
  echo "The code does not compile"
  exit 2
fi

cd "$currentDir"