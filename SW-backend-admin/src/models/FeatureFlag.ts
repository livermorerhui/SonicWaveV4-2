export interface FeatureFlag {
  featureKey: string;
  enabled: boolean;
  metadata: Record<string, unknown> | null;
  updatedAt: string | null;
  updatedBy: number | null;
}

export interface FeatureFlagSnapshot {
  offlineMode: FeatureFlag;
}
