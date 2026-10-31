import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { NavItem } from '../contexts/NavigationContext';
import { sectionItems } from './section-items';

const item = (id: string, label: string, order: number, section?: string): NavItem => ({
  id,
  label,
  route: `/${id}`,
  order,
  section,
  source: 'core',
});

test('sorts the items of a section by label, not by order', () => {
  const items = [
    item('plugins', 'Installed Plugins', 1, 'Admin'),
    item('tools', 'Tools', 2),
    item('audit', 'Audit', 3, 'Admin'),
    item('downloads', 'CLI Downloads', 4, 'Developer'),
    item('bindings', 'Credential Bindings', 5, 'Admin'),
  ];

  assert.deepEqual(
    sectionItems(items, 'Admin').map((entry) => entry.label),
    ['Audit', 'Credential Bindings', 'Installed Plugins'],
  );
});
