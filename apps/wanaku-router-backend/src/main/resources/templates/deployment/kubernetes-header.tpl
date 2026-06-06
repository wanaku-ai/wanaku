apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: wanaku-dev-{{catalogName}}-capability
spec:
  {{authOptions}}routerRef: <router-name>
  capabilities:
