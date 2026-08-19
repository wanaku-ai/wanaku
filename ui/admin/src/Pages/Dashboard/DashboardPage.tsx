import React, {useCallback, useEffect, useState} from "react";
import {
  Button,
  Column,
  Grid,
  Tile,
  ClickableTile,
  InlineNotification,
  SkeletonText,
  SkeletonPlaceholder,
  DataTable,
  TableContainer,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
} from "@carbon/react";
import {
  Activity,
  ArrowRight,
  Document,
  Renew,
  TextAlignJustify,
  Tools,
  Settings,
  Flow,
} from "@carbon/icons-react";
import {useNavigate} from "react-router-dom";
import {useStatistics} from "../../hooks/api/use-statistics";
import {useMetrics} from "../../hooks/api/use-metrics";
import {Links} from "../../router/links.models";
import type {MetricsSnapshot, FilterSnapshot, EvaluatorSnapshot} from "../../models";
interface SystemStatistics {
  toolsCount?: number;
  resourcesCount?: number;
  promptsCount?: number;
  forwardsCount?: number;
  dataStoresCount?: number;
}
import "./DashboardPage.scss";

export const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
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

  const filterHeaders = [
    { key: "name", header: "Filter" },
    { key: "continue", header: "Continue" },
    { key: "reject", header: "Reject" },
    { key: "errors", header: "Errors" },
    { key: "avgDuration", header: "Avg Duration (ms)" },
  ];

  const filterRows = filterEntries.map(([name, f]: [string, FilterSnapshot]) => ({
    id: name,
    name,
    continue: f.requests_continue,
    reject: f.requests_reject,
    errors: f.errors,
    avgDuration: f.duration.avg_ms.toFixed(2),
  }));

  const evaluatorHeaders = [
    { key: "name", header: "Evaluator" },
    { key: "pass", header: "Pass" },
    { key: "block", header: "Block" },
    { key: "warn", header: "Warn" },
    { key: "llmCalls", header: "LLM Calls" },
    { key: "llmAvg", header: "LLM Avg (ms)" },
    { key: "wasmRuns", header: "WASM Runs" },
    { key: "schemaPass", header: "Schema ✓" },
    { key: "schemaFail", header: "Schema ✗" },
  ];

  const evaluatorRows = evaluatorEntries.map(([name, e]: [string, EvaluatorSnapshot]) => ({
    id: name,
    name,
    pass: e.decisions.pass,
    block: e.decisions.block,
    warn: e.decisions.warn,
    llmCalls: e.llm.calls_success + e.llm.calls_failure,
    llmAvg: e.llm.duration.avg_ms.toFixed(0),
    wasmRuns: e.wasm.executions,
    schemaPass: e.schema.validations_pass,
    schemaFail: e.schema.validations_fail,
  }));

  if (isLoading) {
    return (
      <div className="dashboard-page">
        <h1 className="title">Dashboard</h1>
        <p className="description">
          Governed action proxy for AI agents — system overview and operational metrics.
        </p>
        <section className="dashboard-section hero-section">
          <Grid className="hero-grid">
            <Column lg={4} md={4} sm={4}>
              <SkeletonPlaceholder className="hero-tile-skeleton" />
            </Column>
            <Column lg={4} md={4} sm={4}>
              <SkeletonPlaceholder className="hero-tile-skeleton" />
            </Column>
            <Column lg={4} md={4} sm={4}>
              <SkeletonPlaceholder className="hero-tile-skeleton" />
            </Column>
            <Column lg={4} md={4} sm={4}>
              <SkeletonPlaceholder className="hero-tile-skeleton" />
            </Column>
          </Grid>
        </section>
        <section className="dashboard-section">
          <SkeletonText heading={false} lineCount={5} />
        </section>
      </div>
    );
  }

  return (
    <div className="dashboard-page">
      {errorMessage && (
        <InlineNotification
          kind="error"
          title="Error"
          subtitle={errorMessage}
          onCloseButtonClick={() => setErrorMessage(null)}
          lowContrast
          hideCloseButton={false}
        />
      )}
      <div className="dashboard-header">
        <div>
          <h1 className="title">Dashboard</h1>
          <p className="description">
            Governed action proxy for AI agents — system overview and operational metrics.
          </p>
        </div>
        <Button
          kind="ghost"
          size="md"
          renderIcon={Renew}
          onClick={fetchData}
        >
          Refresh
        </Button>
      </div>

      {statistics && (
        <section className="dashboard-section hero-section">
          <Grid className="hero-grid">
            <Column lg={4} md={4} sm={4}>
              <ClickableTile
                className="hero-tile"
                aria-label={`${statistics.toolsCount ?? 0} tools registered — view all`}
                onClick={() => navigate(Links.Tools)}
              >
                <Tools size={32} className="hero-icon" />
                <div className="hero-value">{statistics.toolsCount ?? 0}</div>
                <div className="hero-label">Tools</div>
              </ClickableTile>
            </Column>
            <Column lg={4} md={4} sm={4}>
              <ClickableTile
                className="hero-tile"
                aria-label={`${statistics.resourcesCount ?? 0} resources registered — view all`}
                onClick={() => navigate(Links.Resources)}
              >
                <Document size={32} className="hero-icon" />
                <div className="hero-value">{statistics.resourcesCount ?? 0}</div>
                <div className="hero-label">Resources</div>
              </ClickableTile>
            </Column>
            <Column lg={4} md={4} sm={4}>
              <ClickableTile
                className="hero-tile"
                aria-label={`${statistics.promptsCount ?? 0} prompts registered — view all`}
                onClick={() => navigate(Links.Prompts)}
              >
                <TextAlignJustify size={32} className="hero-icon" />
                <div className="hero-value">{statistics.promptsCount ?? 0}</div>
                <div className="hero-label">Prompts</div>
              </ClickableTile>
            </Column>
            <Column lg={4} md={4} sm={4}>
              <ClickableTile
                className="hero-tile"
                aria-label={`${statistics.forwardsCount ?? 0} forwards registered — view all`}
                onClick={() => navigate(Links.Forwards)}
              >
                <ArrowRight size={32} className="hero-icon" />
                <div className="hero-value">{statistics.forwardsCount ?? 0}</div>
                <div className="hero-label">Forwards</div>
              </ClickableTile>
            </Column>
          </Grid>
        </section>
      )}

      {metrics && (
        <>
          <section className="dashboard-section">
            <div className="summary-row">
              <Tile className="summary-card">
                <Activity size={20} className="card-icon" />
                <div className="card-content">
                  <span className="card-value">{totalFilterRequests}</span>
                  <span className="card-sep">/</span>
                  <span className="card-value error-value">{totalFilterErrors}</span>
                </div>
                <div className="card-label">Requests / Errors</div>
              </Tile>

              {metrics.pipeline && (
                <Tile className="summary-card">
                  <Flow size={20} className="card-icon" />
                  <div className="card-content">
                    <span className="card-value">{metrics.pipeline.trigger_matches}</span>
                    <span className="card-sep">/</span>
                    <span className="card-value">{metrics.pipeline.trigger_misses}</span>
                    <span className="card-sep">/</span>
                    <span className="card-value">{metrics.pipeline.skipped_no_match}</span>
                  </div>
                  <div className="card-label">Matches / Misses / Skipped</div>
                </Tile>
              )}

              {metrics.gauges && (
                <Tile className="summary-card">
                  <Settings size={20} className="card-icon" />
                  <div className="card-content">
                    <span className="card-value">{metrics.gauges.evaluators_loaded}</span>
                    <span className="card-sep">/</span>
                    <span className="card-value">{metrics.gauges.wasm_compiled}</span>
                    <span className="card-sep">/</span>
                    <span className="card-value">{metrics.gauges.namespace_bindings}</span>
                  </div>
                  <div className="card-label">Evaluators / WASM / Namespaces</div>
                </Tile>
              )}
            </div>
          </section>

          {filterEntries.length > 0 && (
            <section className="dashboard-section">
              <DataTable rows={filterRows} headers={filterHeaders}>
                {({
                  rows,
                  headers,
                  getTableProps,
                  getHeaderProps,
                  getRowProps,
                }) => (
                  <TableContainer title="Filter Performance">
                    <Table {...getTableProps()} size="md">
                      <TableHead>
                        <TableRow>
                          {headers.map((header) => (
                            <TableHeader {...getHeaderProps({ header })}>
                              {header.header}
                            </TableHeader>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((row) => (
                          <TableRow {...getRowProps({ row })}>
                            {row.cells.map((cell) => (
                              <TableCell key={cell.id}>{cell.value}</TableCell>
                            ))}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              </DataTable>
            </section>
          )}

          {evaluatorEntries.length > 0 && (
            <section className="dashboard-section">
              <DataTable rows={evaluatorRows} headers={evaluatorHeaders}>
                {({
                  rows,
                  headers,
                  getTableProps,
                  getHeaderProps,
                  getRowProps,
                }) => (
                  <TableContainer title="Evaluator Performance">
                    <Table {...getTableProps()} size="md">
                      <TableHead>
                        <TableRow>
                          {headers.map((header) => (
                            <TableHeader {...getHeaderProps({ header })}>
                              {header.header}
                            </TableHeader>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((row) => (
                          <TableRow {...getRowProps({ row })}>
                            {row.cells.map((cell) => (
                              <TableCell key={cell.id}>{cell.value}</TableCell>
                            ))}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              </DataTable>
            </section>
          )}
        </>
      )}
    </div>
  );
};
