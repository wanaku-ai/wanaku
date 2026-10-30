export const enum Links {
  Home = '/', // Dashboard
  Tools = '/tools',
  Resources = '/resources',
  Prompts = '/prompts',

  Namespaces = '/namespaces',
  Forwards = '/forwards',
  A2aAgents = '/a2a-agents',
  Bindings = '/bindings',
  Evaluators = '/evaluators',
  Audit = '/audit',
  ActionPolicies = '/action-policies',
  PolicySimulation = '/policy-simulation',
  Plugins = '/plugins-admin',
  PluginCatalog = '/plugin-catalog',
  Downloads = '/downloads',
}

export const enum ExternalLinks {
  Home = 'https://wanaku.ai',
  GitHub = 'https://github.com/wanaku-ai/wanaku',
  // oauth2-proxy clears its session cookie, then sends the user back to the admin UI login.
  Logout = '/oauth2/sign_out?rd=%2Fadmin%2F',
}
