import type { NavItem } from "../contexts/NavigationContext";
import { Links } from "../router/links.models";

export const CORE_NAV_ITEMS: NavItem[] = [
  { id: "home", label: "Home", route: Links.Home, source: "core", order: 0 },
  { id: "tools", label: "Tools", route: Links.Tools, source: "core", order: 10 },
  { id: "resources", label: "Resources", route: Links.Resources, source: "core", order: 20 },
  { id: "prompts", label: "Prompts", route: Links.Prompts, source: "core", order: 30 },
  { id: "llmchat", label: "LLMChat", route: Links.LLMChat, source: "core", section: "Developer", order: 40 },
  { id: "tool-calls", label: "Tool Call Debugger", route: Links.ToolCalls, source: "core", section: "Developer", order: 41 },
  { id: "capabilities", label: "Capabilities", route: Links.Capabilities, source: "core", order: 50 },
  { id: "namespaces", label: "Namespaces", route: Links.Namespaces, source: "core", order: 60 },
  { id: "forwards", label: "Forwards", route: Links.Forwards, source: "core", order: 70 },
  { id: "evaluators", label: "Evaluators", route: Links.Evaluators, source: "core", order: 80 },
  { id: "plugins", label: "Plugins", route: Links.Plugins, source: "core", order: 85 },
];
