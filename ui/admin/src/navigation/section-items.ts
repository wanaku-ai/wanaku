import type { NavItem } from '../contexts/NavigationContext';

/** Items of a header menu section, sorted alphabetically by label. */
export function sectionItems(items: NavItem[], section: string): NavItem[] {
  return items
    .filter((item) => item.section === section)
    .sort((a, b) => a.label.localeCompare(b.label));
}
