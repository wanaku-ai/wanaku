export interface InstallationConfig {
  type: 'native' | 'cic' | 'code-execution';
  executablePath: string;
  environmentVariables?: Record<string, string>;
  serviceCatalog?: string;
  serviceCatalogSystem?: string;
  engineType?: string;
  language?: string;
}

export interface ProcessStatus {
  running: boolean;
  port: number;
  startedAt: string | null;
  exitCode: number | null;
}
