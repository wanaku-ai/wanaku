#!/bin/bash

branch=${1:-main}
registry=${2:-quay.io/wanaku}
images=$(cat images.txt)

for image in $images ; do
  fullImage="${registry}/${image}"
  aarch64Image=$(echo "${fullImage}" | sed "s/:[^:]*$/:${branch}-aarch64/")
  x864Image=$(echo "${fullImage}" | sed "s/:[^:]*$/:${branch}-x86_64/")

  echo "Pulling ${aarch64Image}"
  podman pull ${aarch64Image}

  echo "Pulling ${x864Image}"
  podman pull ${x864Image}

  if [[ "${branch}" == "main" ]] ; then
    echo "Creating the manifest ${fullImage}"
    podman rmi -f ${fullImage} || true
    podman manifest create ${fullImage} ${aarch64Image} ${x864Image}

    podman manifest push --all ${fullImage}
  else
    stableManifest=$(echo "${fullImage}" | sed "s/:[^:]*$/:${branch}/")

    echo "Creating the manifest ${stableManifest}"
    podman rmi -f ${stableManifest} || true
    podman manifest create ${stableManifest} ${aarch64Image} ${x864Image}
    podman manifest push --all ${stableManifest}
  fi

done