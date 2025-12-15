export interface FeatureFlag {
  featureKey: string;
  enabled: boolean;
  metadata: Record<string, unknown> | null;
  updatedAt: string | null;
  updatedBy: number | null;
}

export type RegisterRolloutProfileValue = 'NORMAL' | 'ROLLBACK_A' | 'ROLLBACK_B';

export interface RegisterRolloutProfileFlag {
  profile: RegisterRolloutProfileValue;
  updatedAt: string | null;
  updatedBy: number | null;
}

export interface FeatureFlagSnapshot {
  offlineMode: FeatureFlag;
  registerRolloutProfile: RegisterRolloutProfileFlag;
}
