import React from "react";
import { Column, Grid, Link as CarbonLink, Tag, Tile } from "@carbon/react";
import { Chip, Download, Java } from "@carbon/icons-react";
import {
  CLI_PACKAGE_TYPE_LABELS,
  CLI_PACKAGES,
  CLI_RELEASES_URL,
  type CliPackage,
  type CliPackageType,
} from "../../constants/cli-packages";
import "./CliDownloadsPage.scss";

const PACKAGE_TYPE_ICONS: Record<CliPackageType, React.ComponentType<{ size?: number }>> = {
  native: Chip,
  java: Java,
};

function groupByType(packages: CliPackage[]): Map<CliPackageType, CliPackage[]> {
  const groups = new Map<CliPackageType, CliPackage[]>();
  for (const pkg of packages) {
    const existing = groups.get(pkg.packageType) ?? [];
    existing.push(pkg);
    groups.set(pkg.packageType, existing);
  }
  return groups;
}

export const CliDownloadsPage: React.FC = () => {
  const groups = groupByType(CLI_PACKAGES);

  return (
    <div className="cli-downloads-page">
      <h1 className="title">CLI Downloads</h1>
      <p className="description">
        Download the Wanaku CLI to manage tools, resources, forwards, and namespaces from
        the command line. The CLI works with both the Java-based Classic router and this
        Rust-based router.
      </p>

      <div id="page-content">
        {Array.from(groups.entries()).map(([packageType, packages]) => {
          const Icon = PACKAGE_TYPE_ICONS[packageType];
          return (
            <section className="cli-package-section" key={packageType}>
              <h2 className="cli-package-section-heading">
                {CLI_PACKAGE_TYPE_LABELS[packageType]}
              </h2>
              <Grid className="cli-package-grid" narrow>
                {packages.map((pkg) => (
                  <Column lg={4} md={4} sm={4} key={pkg.id}>
                    <Tile className="cli-package-tile">
                      <div className="cli-package-tile-header">
                        <Icon size={24} />
                        <h3>{pkg.name}</h3>
                      </div>
                      <Tag type="cool-gray">{pkg.platform}</Tag>
                      {pkg.requirement && (
                        <p className="cli-package-requirement">{pkg.requirement}</p>
                      )}
                      <p className="cli-package-description">{pkg.description}</p>
                      <CarbonLink
                        href={pkg.downloadUrl}
                        target="_blank"
                        rel="noreferrer"
                        renderIcon={Download}
                      >
                        Download
                      </CarbonLink>
                    </Tile>
                  </Column>
                ))}
              </Grid>
            </section>
          );
        })}
      </div>

      <p className="cli-downloads-footer">
        Looking for a different platform? Browse all published assets on the{" "}
        <CarbonLink href={CLI_RELEASES_URL} target="_blank" rel="noreferrer">
          releases page
        </CarbonLink>
        .
      </p>
    </div>
  );
};
