import React, {useState} from "react";
import {Button, Column, Grid, Modal, Search, Tag, Tile} from "@carbon/react";
import {ChevronDown, ChevronUp, TrashCan} from "@carbon/icons-react";
import "./ServiceCatalogPage.scss";

interface ServiceCatalogSystem {
  name: string;
  routesFile: string;
  rulesFile: string;
  dependenciesFile?: string;
}

interface ServiceCatalogSummary {
  id: string;
  name: string;
  icon?: string;
  description: string;
  services: string[];
}

interface ServiceCatalogDetail {
  id: string;
  name: string;
  icon?: string;
  description: string;
  services: ServiceCatalogSystem[];
}

interface ServiceCatalogCardsProps {
  catalogs: ServiceCatalogSummary[];
  onDelete: (name: string) => void;
  onSearch: (search: string) => void;
  getDetail: (name: string) => Promise<ServiceCatalogDetail | null>;
}

export const ServiceCatalogCards: React.FC<ServiceCatalogCardsProps> = ({
  catalogs,
  onDelete,
  onSearch,
  getDetail,
}) => {
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [expandedCard, setExpandedCard] = useState<string | null>(null);
  const [expandedDetails, setExpandedDetails] = useState<Record<string, ServiceCatalogDetail>>({});

  const handleToggleExpand = async (name: string) => {
    if (expandedCard === name) {
      setExpandedCard(null);
      return;
    }
    setExpandedCard(name);
    if (!expandedDetails[name]) {
      const detail = await getDetail(name);
      if (detail) {
        setExpandedDetails((prev) => ({ ...prev, [name]: detail }));
      }
    }
  };

  return (
    <>
      <div className="catalog-search">
        <Search
          labelText="Search service catalogs"
          placeholder="Search service catalogs..."
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => onSearch(e.target.value)}
          size="lg"
        />
      </div>

      {catalogs.length === 0 ? (
        <Tile className="catalog-empty-tile">
          <p>No service catalogs found.</p>
          <p className="catalog-empty-hint">
            Use the CLI to deploy a service catalog:
          </p>
          <code className="catalog-empty-code">
            wanaku service init --name=myservice --services=system1
          </code>
        </Tile>
      ) : (
        <Grid className="catalog-grid">
          {catalogs.map((catalog, index) => {
            const isExpanded = expandedCard === catalog.name;
            const detail = expandedDetails[catalog.name];

            return (
              <Column lg={4} md={4} sm={4} key={catalog.id || `catalog-${index}`}>
                <Tile className={`catalog-card ${isExpanded ? "catalog-card--expanded" : ""}`}>
                  <div className="catalog-card-header">
                    <div className="catalog-card-title-row">
                      {catalog.icon && <span className="catalog-card-icon">{catalog.icon}</span>}
                      <h4 className="catalog-card-name">{catalog.name}</h4>
                    </div>
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={TrashCan}
                      hasIconOnly
                      iconDescription="Delete"
                      className="catalog-card-delete"
                      onClick={(e: React.MouseEvent) => {
                        e.stopPropagation();
                        setDeleteTarget(catalog.name);
                      }}
                    />
                  </div>

                  <p className="catalog-card-description">{catalog.description}</p>

                  <div className="catalog-card-tags">
                    {catalog.services?.map((service) => (
                      <Tag key={service} type="blue" size="sm">{service}</Tag>
                    ))}
                  </div>

                  <Button
                    kind="ghost"
                    size="sm"
                    renderIcon={isExpanded ? ChevronUp : ChevronDown}
                    className="catalog-card-expand"
                    onClick={() => handleToggleExpand(catalog.name)}
                  >
                    {isExpanded ? "Hide details" : "View details"}
                  </Button>

                  {isExpanded && (
                    <div className="catalog-card-details">
                      {detail ? (
                        detail.services.map((system) => (
                          <div key={system.name} className="catalog-system">
                            <strong className="catalog-system-name">{system.name}</strong>
                            <ul className="catalog-system-files">
                              <li>Routes: <code>{system.routesFile}</code></li>
                              <li>Rules: <code>{system.rulesFile}</code></li>
                              {system.dependenciesFile && (
                                <li>Dependencies: <code>{system.dependenciesFile}</code></li>
                              )}
                            </ul>
                          </div>
                        ))
                      ) : (
                        <p className="catalog-loading">Loading system details...</p>
                      )}
                    </div>
                  )}
                </Tile>
              </Column>
            );
          })}
        </Grid>
      )}

      {deleteTarget && (
        <Modal
          open={true}
          modalHeading="Delete Service Catalog"
          primaryButtonText="Delete"
          secondaryButtonText="Cancel"
          danger
          onRequestClose={() => setDeleteTarget(null)}
          onRequestSubmit={() => {
            onDelete(deleteTarget);
            setDeleteTarget(null);
          }}
        >
          <p>Are you sure you want to delete the service catalog <strong>{deleteTarget}</strong>? This action cannot be undone.</p>
        </Modal>
      )}
    </>
  );
};
