import { customFetch } from "../../custom-fetch";

const LAUNCHER_PATH = "/api/v1/capabilities/launcher";

export const launchCapability = async (
  catalogName: string,
  systemName: string,
  options?: RequestInit
) => {
  return customFetch<any>(
    `${LAUNCHER_PATH}/${catalogName}/${systemName}/launch`,
    { method: 'POST', ...options }
  );
};

export const stopCapability = async (
  catalogName: string,
  systemName: string,
  options?: RequestInit
) => {
  return customFetch<any>(
    `${LAUNCHER_PATH}/${catalogName}/${systemName}/stop`,
    { method: 'POST', ...options }
  );
};

export const getCapabilityStatus = async (
  catalogName: string,
  systemName: string,
  options?: RequestInit
) => {
  return customFetch<any>(
    `${LAUNCHER_PATH}/${catalogName}/${systemName}/status`,
    { method: 'GET', ...options }
  );
};

export const getAllLauncherStatuses = async (
  options?: RequestInit
) => {
  return customFetch<any>(
    `${LAUNCHER_PATH}/status`,
    { method: 'GET', ...options }
  );
};
