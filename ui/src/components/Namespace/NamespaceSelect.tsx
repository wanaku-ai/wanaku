
import { Select, SelectItem } from '@carbon/react';
import { useContext, useEffect, useState } from 'react';
import { listNamespaces } from '../../hooks/api/use-namespaces';
import { NamespaceContext } from '../../contexts/NamespaceContext';
import { Namespace } from '../../models';

export const NamespaceSelect = () => {
  const [namespaces, setNamespaces] = useState<Namespace[]>([]);
  const namespaceContext = useContext(NamespaceContext);

  useEffect(() => {
    listNamespaces().then(result => {
      setNamespaces(result.data.data);
    });
  }, []);

  const handleNamespaceChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    namespaceContext?.setSelectedNamespace(event.target.value);
  };

  return (
    <div>
      <Select inline id="namespace-select" labelText="Namespace" onChange={handleNamespaceChange}>
        <SelectItem
          text="Default"
          value={null}
        />
        {namespaces.map(namespace => (
          <SelectItem key={namespace.id} value={namespace.id} text={namespace.path} />
        ))}
      </Select>
    </div>
  );
};
