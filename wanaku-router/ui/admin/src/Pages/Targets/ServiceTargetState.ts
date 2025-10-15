import { ActivityRecord, ServiceTarget } from "../../models";

export interface ServiceTargetState extends ServiceTarget, ActivityRecord {
    reason?: string
}