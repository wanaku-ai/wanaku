import React, {useState} from "react";
import {Button, Column, Grid, Search, Tag, Tile} from "@carbon/react";
import {ChevronDown, ChevronUp, DataBase} from "@carbon/icons-react";
import {ServiceTemplateWizard} from "./ServiceTemplateWizard";
import "./ServiceCatalogPage.scss";

interface ServiceTemplateSummary {
  id: string;
  name: string;
  icon?: string;
  description: string;
  services: string[];
  hasProperties?: boolean;
}

interface ServiceTemplateCardsProps {
  templates: ServiceTemplateSummary[];
  onSearch: (search: string) => void;
  onInstantiateSuccess: (templateName: string) => void;
}

export const ServiceTemplateCards: React.FC<ServiceTemplateCardsProps> = ({
  templates,
  onSearch,
  onInstantiateSuccess,
}) => {
  const [instantiateTarget, setInstantiateTarget] = useState<string | null>(null);
  const [expandedCard, setExpandedCard] = useState<string | null>(null);

  const handleToggleExpand = (name: string) => {
    setExpandedCard(expandedCard === name ? null : name);
  };

  return (
    <>
      <div className="catalog-search">
        <Search
          labelText="Search service templates"
          placeholder="Search service templates..."
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => onSearch(e.target.value)}
          size="lg"
        />
      </div>

      {templates.length === 0 ? (
        <Tile className="catalog-empty-tile">
          <p>No service templates found.</p>
          <p className="catalog-empty-hint">
            Use the CLI to deploy a service template:
          </p>
          <code className="catalog-empty-code">
            wanaku service init --name=mytemplate --services=system1 --template
          </code>
        </Tile>
      ) : (
        <Grid className="catalog-grid">
          {templates.map((template, index) => {
            const isExpanded = expandedCard === template.name;

            return (
              <Column lg={4} md={4} sm={4} key={template.id || `template-${index}`}>
                <Tile className={`catalog-card ${isExpanded ? "catalog-card--expanded" : ""}`}>
                  <div className="catalog-card-header">
                    <div className="catalog-card-title-row">
                      {template.icon && <span className="catalog-card-icon">{template.icon}</span>}
                      <h4 className="catalog-card-name">{template.name}</h4>
                    </div>
                  </div>

                  <p className="catalog-card-description">{template.description}</p>

                  <div className="catalog-card-tags">
                    {template.services?.map((service) => (
                      <Tag key={service} type="purple" size="sm">{service}</Tag>
                    ))}
                    {template.hasProperties && (
                      <Tag type="green" size="sm">Parameterized</Tag>
                    )}
                  </div>

                  <div className="catalog-card-actions">
                    <Button
                      kind="primary"
                      size="sm"
                      renderIcon={DataBase}
                      className="catalog-card-instantiate"
                      onClick={() => setInstantiateTarget(template.name)}
                    >
                      Create Service Catalog
                    </Button>

                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={isExpanded ? ChevronUp : ChevronDown}
                      className="catalog-card-expand"
                      onClick={() => handleToggleExpand(template.name)}
                    >
                      {isExpanded ? "Hide details" : "View details"}
                    </Button>
                  </div>

                  {isExpanded && (
                    <div className="catalog-card-details">
                      <div className="catalog-system">
                        <strong className="catalog-system-name">Template Info</strong>
                        <ul className="catalog-system-files">
                          <li>Systems: {template.services.join(", ")}</li>
                          <li>Has Properties: {template.hasProperties ? "Yes" : "No"}</li>
                        </ul>
                      </div>
                    </div>
                  )}
                </Tile>
              </Column>
            );
          })}
        </Grid>
      )}

      {instantiateTarget && (
        <ServiceTemplateWizard
          templateName={instantiateTarget}
          onClose={() => setInstantiateTarget(null)}
          onSuccess={() => {
            onInstantiateSuccess(instantiateTarget);
            setInstantiateTarget(null);
          }}
        />
      )}
    </>
  );
};
