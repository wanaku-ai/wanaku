apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuCapability
metadata:
  name: wanaku-dev-{{catalogName}}-capability
spec:
  auth:
    authServer: <auth-server-address>
    authProxy: "auto"
  secrets:
    oidcCredentialsSecret: <credentials-secret>
  routerRef: <router-name>
  capabilities: