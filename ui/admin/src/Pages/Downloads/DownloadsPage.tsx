import {Button, Column, Grid, Link} from "@carbon/react";
import {Launch} from "@carbon/icons-react";
import React from "react";
import {CLI_DOWNLOADS, CLI_RELEASE_PAGE, CLI_RELEASE_TAG} from "./downloads";
import {DownloadsTable} from "./DownloadsTable";

export const DownloadsPage: React.FC = () => {
  return (
    <div>
      <h1 className="title">CLI Downloads</h1>
      <p className="description">
        Wanaku provides its command line interface (CLI) in multiple packages.
        Some packages are portable Java packages that run on any operating system
        with a compatible Java runtime, while others are self-contained native
        binaries built for a specific platform. Pick the package that matches your
        environment and follow the accompanying instructions to install it.
      </p>
      <div id="page-content">
        <Grid narrow>
          <Column lg={16} md={8} sm={4}>
            <DownloadsTable downloads={CLI_DOWNLOADS} />
          </Column>
          <Column lg={16} md={8} sm={4} style={{marginTop: "1rem"}}>
            <p style={{color: "var(--cds-text-secondary)", marginBottom: "0.5rem"}}>
              Looking for a different platform or an older version? Browse every
              published artifact on the release page.
            </p>
            <Button
              kind="ghost"
              size="sm"
              renderIcon={Launch}
              href={CLI_RELEASE_PAGE}
              target="_blank"
              rel="noopener noreferrer"
              as="a"
            >
              View all packages ({CLI_RELEASE_TAG})
            </Button>
            <p style={{color: "var(--cds-text-secondary)", marginTop: "1rem", fontSize: "0.75rem"}}>
              After downloading, verify the file against the{" "}
              <Link
                href={`${CLI_RELEASE_PAGE.replace("/tag/", "/download/")}/checksums_sha256.txt`}
                target="_blank"
                rel="noopener noreferrer"
              >
                published checksums
              </Link>
              .
            </p>
          </Column>
        </Grid>
      </div>
    </div>
  );
};
