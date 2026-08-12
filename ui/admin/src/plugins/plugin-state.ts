import type { NavItem } from "../contexts/NavigationContext";
import { CORE_NAV_ITEMS } from "../navigation/core-nav-items";

let _navItems: NavItem[] = [...CORE_NAV_ITEMS];

export function setInitialNavItems(items: NavItem[]) {
  _navItems = items;
}

export function getInitialNavItems(): NavItem[] {
  return _navItems;
}
