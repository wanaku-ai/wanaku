
import React, { createContext, useState, ReactNode } from 'react';

interface NamespaceContextType {
  selectedNamespace: string | null;
  setSelectedNamespace: (namespace: string | null) => void;
}

export const NamespaceContext = createContext<NamespaceContextType | undefined>(undefined);

export const NamespaceProvider = ({ children }: { children: ReactNode }) => {
  const [selectedNamespace, setSelectedNamespace] = useState<string | null>('default');

  return (
    <NamespaceContext.Provider value={{ selectedNamespace, setSelectedNamespace }}>
      {children}
    </NamespaceContext.Provider>
  );
};
