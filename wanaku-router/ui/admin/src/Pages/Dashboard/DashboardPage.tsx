import React, { useCallback, useEffect, useState } from "react";
import {
  Button,
  Grid,
  Column,
  Tile,
  ToastNotification,
} from "@carbon/react";
import { Renew } from "@carbon/icons-react";
import { useStatistics } from "../../hooks/api/use-statistics";
import { SystemStatistics, CapabilityStatistics } from "../../models";
import "./DashboardPage.scss";

export const DashboardPage: React.FC = () => {
  const [statistics, setStatistics] = useState<SystemStatistics | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { getStatistics } = useStatistics();

  const fetchStatistics = useCallback(async () => {
    setIsLoading(true);
    return getStatistics().then((result) => {
      if (result.status !== 200 || !result.data.data) {
        setErrorMessage("Failed to fetch statistics. Please try again later.");
        setStatistics(null);
      } else {
        setStatistics(result.data.data);
      }
      setIsLoading(false);
    }).catch(() => {
      setErrorMessage("Failed to fetch statistics. Please try again later.");
      setStatistics(null);
      setIsLoading(false);
    });
  }, [getStatistics]);

  useEffect(() => {
    fetchStatistics();
  }, [fetchStatistics]);

  useEffect(() => {
    if (errorMessage) {
      const timer = setTimeout(() => {
        setErrorMessage(null);
      }, 10_000);

      return () => {
        clearTimeout(timer);
      };
    }
  }, [errorMessage]);

  if (isLoading) return <div>Loading...</div>;

  return (
    <div className="dashboard-page">
      {errorMessage && (
        <ToastNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          timeout={10000}
          style={{ float: "right" }}
        />
      )}
      <h1 className="title">Dashboard</h1>
      <p className="description">
        System overview showing counts for registered entities and capability
        status.
      </p>
      <div className="dashboard-actions">
        <Button
          kind="ghost"
          size="sm"
          renderIcon={Renew}
          onClick={fetchStatistics}
        >
          Refresh
        </Button>
      </div>
      {statistics && (
        <Grid className="stats-grid">
          <Column lg={4} md={4} sm={4}>
            <Tile className="stat-tile">
              <div className="stat-value">{statistics.toolsCount ?? 0}</div>
              <div className="stat-label">Tools</div>
            </Tile>
          </Column>
          <Column lg={4} md={4} sm={4}>
            <Tile className="stat-tile">
              <div className="stat-value">
                {statistics.resourcesCount ?? 0}
              </div>
              <div className="stat-label">Resources</div>
            </Tile>
          </Column>
          <Column lg={4} md={4} sm={4}>
            <Tile className="stat-tile">
              <div className="stat-value">{statistics.promptsCount ?? 0}</div>
              <div className="stat-label">Prompts</div>
            </Tile>
          </Column>
          <Column lg={4} md={4} sm={4}>
            <Tile className="stat-tile">
              <div className="stat-value">
                {statistics.forwardsCount ?? 0}
              </div>
              <div className="stat-label">Forwards</div>
            </Tile>
          </Column>
          <Column lg={4} md={4} sm={4}>
            <Tile className="stat-tile">
              <div className="stat-value">
                {statistics.dataStoresCount ?? 0}
              </div>
              <div className="stat-label">Data Stores</div>
            </Tile>
          </Column>
          <Column lg={8} md={4} sm={4}>
            <Tile className="capability-tile">
              <div className="capability-title">Tool Capabilities</div>
              <CapabilityDetails stats={statistics.toolCapabilities} />
            </Tile>
          </Column>
          <Column lg={8} md={4} sm={4}>
            <Tile className="capability-tile">
              <div className="capability-title">Resource Capabilities</div>
              <CapabilityDetails stats={statistics.resourceCapabilities} />
            </Tile>
          </Column>
        </Grid>
      )}
    </div>
  );
};

interface CapabilityDetailsProps {
  stats?: CapabilityStatistics;
}

const CapabilityDetails: React.FC<CapabilityDetailsProps> = ({ stats }) => {
  return (
    <div className="capability-details">
      <div className="capability-stat">
        <div className="capability-value">{stats?.total ?? 0}</div>
        <div className="capability-label">Total</div>
      </div>
      <div className="capability-stat">
        <div className="capability-value">{stats?.active ?? 0}</div>
        <div className="capability-label">Active</div>
      </div>
      <div className="capability-stat">
        <div className="capability-value">{stats?.inactive ?? 0}</div>
        <div className="capability-label">Inactive</div>
      </div>
    </div>
  );
};
