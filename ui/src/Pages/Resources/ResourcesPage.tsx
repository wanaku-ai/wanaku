import {
  Column,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@carbon/react";
import React, { useState, useEffect } from "react";
import { ResourceReference } from "../../models";
import { useResources } from "../../hooks/api/use-resources";

export const ResourcesPage: React.FC = () => {
  const [fetchedData, setFetchedData] = useState<ResourceReference[]>(
    []
  );
  const [isLoading, setIsLoading] = useState(true);
  const { listResources } = useResources();

  useEffect(() => {
    listResources().then((result) => {
      setFetchedData(result.data.data!);
      setIsLoading(false);
    });
  }, [listResources]);

  if (isLoading) return <div>Loading...</div>;

  const headers = ["Name", "Location", "Type", "Description"];
  let resourcesList = <></>;

  if (fetchedData) {
    resourcesList = (
      <Grid>
        <Column lg={12} md={8} sm={4}>
          <div
            style={{
              display: "flex",
              justifyContent: "flex-end",
              alignItems: "center",
            }}
          ></div>
          <Table aria-label="Tools table">
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader id={header} key={header}>
                    {header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {fetchedData.map((row: ResourceReference) => (
                <TableRow key={row.name}>
                  <TableCell>{row.name}</TableCell>
                  <TableCell>{row.location}</TableCell>
                  <TableCell>{row.type}</TableCell>
                  <TableCell>{row.description}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Column>
      </Grid>
    );
  }

  return (
    <div>
      <h1 className="title">Resources</h1>
      <p className="description">
        Resources are a fundamental primitive in MCP that allow servers to
        expose data and content to LLM clients
      </p>
      <div style={{ background: "#161616", paddingTop: "2rem" }}>
        {resourcesList}
      </div>
    </div>
  );
};
