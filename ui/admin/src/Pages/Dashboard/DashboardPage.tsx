import React, {useCallback, useEffect, useState} from "react";
import {Button, Column, Grid, Tile, ToastNotification,} from "@carbon/react";
import {
  Activity,
  ArrowRight,
  ChartBar,
  Document,
  Renew,
  TextAlignJustify,
  Tools,
  WarningAlt,
} from "@carbon/icons-react";
import {useStatistics} from "../../hooks/api/use-statistics";
import {useMetrics} from "../../hooks/api/use-metrics";
import type {MetricsSnapshot} from "../../models";
interface SystemStatistics {
  toolsCount?: number;
  resourcesCount?: number;
  promptsCount?: number;
  forwardsCount?: number;
  dataStoresCount?: number;
}
import "./DashboardPage.scss";

export const DashboardPage: React.FC = () => {
  const [statistics, setStatistics] = useState<SystemStatistics | null>(null);
  const [metrics, setMetrics] = useState<MetricsSnapshot | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const { getStatistics } = useStatistics();
  const { getMetrics } = useMetrics();

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    try {
      const [statsResult, metricsResult] = await Promise.all([
        getStatistics(),
        getMetrics(),
      ]);

      if (statsResult.status !== 200 || !statsResult.data) {
        setErrorMessage("Failed to fetch statistics.");
        setStatistics(null);
      } else {
        setStatistics(statsResult.data);
      }

      if (metricsResult.status === 200 && metricsResult.data) {
        setMetrics(metricsResult.data as MetricsSnapshot);
      } else {
        setMetrics(null);
      }
    } catch {
      setErrorMessage("Failed to fetch data. Please try again later.");
      setStatistics(null);
      setMetrics(null);
    } finally {
      setIsLoading(false);
    }
  }, [getStatistics, getMetrics]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

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

  const filterEntries = metrics
    ? Object.entries(metrics.filters).sort(([a], [b]) => a.localeCompare(b))
    : [];

  const totalFilterRequests = filterEntries.reduce(
    (sum, [, f]) => sum + f.requests_continue + f.requests_reject + f.requests_other,
    0,
  );

  const totalFilterErrors = filterEntries.reduce(
    (sum, [, f]) => sum + f.errors,
    0,
  );

  const evaluatorEntries = metrics
    ? Object.entries(metrics.evaluators).sort(([a], [b]) => a.localeCompare(b))
    : [];

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
        System overview showing counts for registered entities.
      </p>
      <div className="dashboard-actions">
        <Button
          kind="ghost"
          size="sm"
          renderIcon={Renew}
          onClick={fetchData}
        >
          Refresh
        </Button>
      </div>
      {statistics && (
        <section className="dashboard-section">
          <h3 className="section-heading">Overview</h3>
          <Grid className="stats-grid">
            <Column lg={4} md={4} sm={4}>
              <Tile className="stat-tile">
                <Tools size={24} className="stat-icon" />
                <div className="stat-value">{statistics.toolsCount ?? 0}</div>
                <div className="stat-label">Tools</div>
              </Tile>
            </Column>
            <Column lg={4} md={4} sm={4}>
              <Tile className="stat-tile">
                <Document size={24} className="stat-icon" />
                <div className="stat-value">
                  {statistics.resourcesCount ?? 0}
                </div>
                <div className="stat-label">Resources</div>
              </Tile>
            </Column>
            <Column lg={4} md={4} sm={4}>
              <Tile className="stat-tile">
                <TextAlignJustify size={24} className="stat-icon" />
                <div className="stat-value">{statistics.promptsCount ?? 0}</div>
                <div className="stat-label">Prompts</div>
              </Tile>
            </Column>
            <Column lg={4} md={4} sm={4}>
              <Tile className="stat-tile">
                <ArrowRight size={24} className="stat-icon" />
                <div className="stat-value">
                  {statistics.forwardsCount ?? 0}
                </div>
                <div className="stat-label">Forwards</div>
              </Tile>
            </Column>
          </Grid>
        </section>
      )}

      {metrics && (
        <>
          <section className="dashboard-section">
            <h3 className="section-heading">Filter Activity</h3>
            <Grid className="stats-grid">
              <Column lg={4} md={4} sm={4}>
                <Tile className="stat-tile">
                  <Activity size={24} className="stat-icon" />
                  <div className="stat-value">{totalFilterRequests}</div>
                  <div className="stat-label">Total Requests</div>
                </Tile>
              </Column>
              <Column lg={4} md={4} sm={4}>
                <Tile className="stat-tile">
                  <WarningAlt size={24} className="stat-icon" />
                  <div className="stat-value">{totalFilterErrors}</div>
                  <div className="stat-label">Errors</div>
                </Tile>
              </Column>
              <Column lg={4} md={4} sm={4}>
                <Tile className="stat-tile">
                  <ChartBar size={24} className="stat-icon" />
                  <div className="stat-value">{filterEntries.length}</div>
                  <div className="stat-label">Active Filters</div>
                </Tile>
              </Column>
            </Grid>

            {filterEntries.length > 0 && (
              <div className="metrics-table">
                <table className="cds--data-table">
                  <thead>
                    <tr>
                      <th>Filter</th>
                      <th>Continue</th>
                      <th>Reject</th>
                      <th>Errors</th>
                      <th>Avg Duration</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filterEntries.map(([name, f]) => (
                      <tr key={name}>
                        <td>{name}</td>
                        <td>{f.requests_continue}</td>
                        <td>{f.requests_reject}</td>
                        <td>{f.errors}</td>
                        <td>{f.duration.avg_ms.toFixed(2)} ms</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {evaluatorEntries.length > 0 && (
            <section className="dashboard-section">
              <h3 className="section-heading">Evaluator Activity</h3>
              <div className="metrics-table">
                <table className="cds--data-table">
                  <thead>
                    <tr>
                      <th>Evaluator</th>
                      <th>Pass</th>
                      <th>Block</th>
                      <th>Warn</th>
                      <th>LLM Calls</th>
                      <th>LLM Avg</th>
                      <th>WASM Runs</th>
                    </tr>
                  </thead>
                  <tbody>
                    {evaluatorEntries.map(([name, e]) => (
                      <tr key={name}>
                        <td>{name}</td>
                        <td>{e.decisions.pass}</td>
                        <td>{e.decisions.block}</td>
                        <td>{e.decisions.warn}</td>
                        <td>{e.llm.calls_success + e.llm.calls_failure}</td>
                        <td>{e.llm.duration.avg_ms.toFixed(0)} ms</td>
                        <td>{e.wasm.executions}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {metrics.gauges && (
            <section className="dashboard-section">
              <h3 className="section-heading">Configuration</h3>
              <Grid className="stats-grid">
                <Column lg={4} md={4} sm={4}>
                  <Tile className="stat-tile">
                    <div className="stat-value">{metrics.gauges.evaluators_loaded}</div>
                    <div className="stat-label">Evaluators Loaded</div>
                  </Tile>
                </Column>
                <Column lg={4} md={4} sm={4}>
                  <Tile className="stat-tile">
                    <div className="stat-value">{metrics.gauges.wasm_compiled}</div>
                    <div className="stat-label">WASM Compiled</div>
                  </Tile>
                </Column>
                <Column lg={4} md={4} sm={4}>
                  <Tile className="stat-tile">
                    <div className="stat-value">{metrics.gauges.namespace_bindings}</div>
                    <div className="stat-label">Namespace Bindings</div>
                  </Tile>
                </Column>
              </Grid>
            </section>
          )}
        </>
      )}
    </div>
  );
};
