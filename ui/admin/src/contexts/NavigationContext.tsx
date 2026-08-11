import { createContext, useContext, useState, useCallback, type ReactNode } from "react";

export interface NavItem {
  id: string;
  label: string;
  route: string;
  icon?: string;
  section?: string;
  order?: number;
  source: "core" | string;
}

export interface Disposable {
  dispose(): void;
}

interface NavigationContextValue {
  items: NavItem[];
  add(item: Omit<NavItem, "source">, source: string): Disposable;
}

const NavigationContext = createContext<NavigationContextValue | null>(null);

export function NavigationProvider({ children, initialItems }: { children: ReactNode; initialItems: NavItem[] }) {
  const [items, setItems] = useState<NavItem[]>(initialItems);

  const add = useCallback((item: Omit<NavItem, "source">, source: string): Disposable => {
    const fullItem: NavItem = { ...item, source };
    setItems(prev => [...prev, fullItem]);

    return {
      dispose: () => {
        setItems(prev => prev.filter(i => i.id !== fullItem.id));
      },
    };
  }, []);

  const sortedItems = [...items].sort((a, b) => (a.order || 0) - (b.order || 0));

  return (
    <NavigationContext.Provider value={{ items: sortedItems, add }}>
      {children}
    </NavigationContext.Provider>
  );
}

export function useNavigation(): NavigationContextValue {
  const context = useContext(NavigationContext);
  if (!context) {
    throw new Error("useNavigation must be used within a NavigationProvider");
  }
  return context;
}
