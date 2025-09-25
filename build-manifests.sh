#!/bin/bash

branch=${1:-main}
manifests=$(cat images.txt)

for manifest in $manifests ; do
  aarch64Image=$(echo "${manifest}" | sed "s/:[^:]*$/:${branch}-aarch64/")
  x864Image=$(echo "${manifest}" | sed "s/:[^:]*$/:${branch}-x86_64/")

  echo "Pulling ${aarch64Image}"
  podman pull ${aarch64Image}

  echo "Pulling ${x864Image}"
  podman pull ${x864Image}

  if [[ "${branch}" == "main" ]] ; then
    echo "Creating the manifest ${manifest}"
    podman rmi -f ${manifest} || true
    podman manifest create ${manifest} ${aarch64Image} ${x864Image}

    podman manifest push --all ${manifest}
  else
    stableManifest=$(echo "${manifest}" | sed "s/:[^:]*$/:${branch}/")

    echo "Creating the manifest ${stableManifest}"
    podman rmi -f ${stableManifest} || true
    podman manifest create ${stableManifest} ${aarch64Image} ${x864Image}
    podman manifest push --all ${stableManifest}
  fi

done