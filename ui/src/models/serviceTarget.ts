/**
 * Generated by orval v7.6.0 🍺
 * Do not edit manually.
 * wanaku-router API
 * OpenAPI spec version: 0.0.8-SNAPSHOT
 */
import type { ServiceType } from "./serviceType";

export interface ServiceTarget {
  id?: string;
  service?: string;
  host?: string;
  port?: number;
  serviceType?: ServiceType;
}
