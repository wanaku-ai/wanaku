#!/bin/sh

branch=${1:-main}
manifests=$(cat docker-compose.yml | grep image | grep wanaku | cut -d ' ' -f 6)

for manifest in $manifests ; do
  aarch64Image=${manifest/:latest/:${branch}-aarch64}
  x864Image=${manifest/:latest/:${branch}-x86_64}

  echo "Pulling ${aarch64Image}"
  podman pull ${aarch64Image}

  echo "Pulling ${x864Image}"
  podman pull ${x864Image}

  echo "Creating the manifest ${manifest}"
  podman manifest create ${manifest} ${aarch64Image} ${x864Image}
  podman manifest push --all ${manifest}

done