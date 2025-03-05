import {
  Column,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@carbon/react";
import { FunctionComponent, useState, useEffect } from "react";
import { getApiV1ResourcesList } from '../../api/wanaku-router-api';
import { ResourceReference } from "../../models";

export const ResourcesPage: FunctionComponent = () => {
    const [fetchedData,  setFetchedData] = useState<any[] | null>(null);
    const [isLoading, setIsLoading] = useState(true);
  
    useEffect(() => {
      const fetchData = async () => {
        try {
          const response = await getApiV1ResourcesList();
          setFetchedData(response.data);
        } catch (error) {
          console.error("Error fetching data:", error);
        } finally {
          setIsLoading(false);
        }
      };
  
      fetchData();
    }, []);
    if (isLoading) return <div>Loading...</div>;

    const data = fetchedData;
  
    const headers = ["Name", "Location", "Type", "Description"];
    let resourcesList = <></>;

    if (data) {
        resourcesList = (
        <Grid>
          <Column lg={12} md={8} sm={4}>
            <div
              style={{
                display: "flex",
                justifyContent: "flex-end",
                alignItems: "center",
              }}
            >
            </div>
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
                {data.map((row: ResourceReference) => (
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
            Resources are a fundamental primitive in MCP that allow servers to expose data and content to LLM clients
            </p>
            {resourcesList}
        </div>
    )
}