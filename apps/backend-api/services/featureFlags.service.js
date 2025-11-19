const logger = require('../logger');
const { listFlags, findFlagByKey, upsertFlag } = require('../repositories/featureFlags.repository');

const FEATURE_KEYS = {
  OFFLINE_MODE: 'offline_mode'
};

const defaultFlagSnapshot = key => ({
  featureKey: key,
  enabled: false,
  metadata: null,
  updatedAt: null,
  updatedBy: null
});

async function ensureFlag(featureKey) {
  const existing = await findFlagByKey(featureKey);
  if (existing) {
    return existing;
  }
  logger.info(`[FeatureFlags] Creating default flag for ${featureKey}`);
  return upsertFlag({
    featureKey,
    enabled: false,
    metadata: null,
    updatedBy: null
  });
}

async function getOfflineModeFlag() {
  return ensureFlag(FEATURE_KEYS.OFFLINE_MODE);
}

async function setOfflineModeFlag({ enabled, actorId }) {
  return upsertFlag({
    featureKey: FEATURE_KEYS.OFFLINE_MODE,
    enabled,
    metadata: null,
    updatedBy: actorId || null
  });
}

async function getFeatureFlagSnapshot() {
  const flags = await listFlags();
  const offline = flags.find(flag => flag.featureKey === FEATURE_KEYS.OFFLINE_MODE);
  return {
    offlineMode: offline || defaultFlagSnapshot(FEATURE_KEYS.OFFLINE_MODE)
  };
}

module.exports = {
  FEATURE_KEYS,
  getOfflineModeFlag,
  setOfflineModeFlag,
  getFeatureFlagSnapshot
};
