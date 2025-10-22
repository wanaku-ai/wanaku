# Wanaku Operator

This module contains the Wanaku Operator implementation.


### Running the operator

The operator uses the [Java Operator SDK](https://javaoperatorsdk.io/). This SDK will resolve the
Kubernetes cluster by inspecting the local configuration for the `kubectl` command. This process is
described in more detail in the [Getting Started section of the documentation](https://javaoperatorsdk.io/docs/getting-started/#getting-started).

**NOTE**: Podman Desktop can manage the Kubernetes contexts for you. You can set it using the tray icon or the Dashboard.

With the Kind cluster up and running, the `kubectl` command installed and the Kubernetes context set. Then you can launch the
operator. At this moment, the recommended way is by running the `main` method on `WanakuOperator` class
directly via the IDE or by running `mvn quarkus:dev`.
