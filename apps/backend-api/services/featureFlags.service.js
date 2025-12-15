const logger = require('../logger');
const { listFlags, findFlagByKey, upsertFlag } = require('../repositories/featureFlags.repository');

const FEATURE_KEYS = {
  OFFLINE_MODE: 'offline_mode',
  REGISTER_ROLLOUT_PROFILE: 'register_rollout_profile'
};
const KEY_REGISTER_ROLLOUT_PROFILE = FEATURE_KEYS.REGISTER_ROLLOUT_PROFILE;

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

const REGISTER_PROFILES = ['NORMAL', 'ROLLBACK_A', 'ROLLBACK_B'];
const PROFILE_CACHE_TTL_MS = 2000;
let cachedRegisterProfile = null;
let cachedRegisterProfileFetchedAt = 0;

function normalizeRegisterProfile(value) {
  if (!value) return null;
  if (typeof value === 'string' && REGISTER_PROFILES.includes(value.toUpperCase())) {
    return value.toUpperCase();
  }
  if (
    typeof value === 'object' &&
    typeof value.profile === 'string' &&
    REGISTER_PROFILES.includes(value.profile.toUpperCase())
  ) {
    return value.profile.toUpperCase();
  }
  return null;
}

function buildRegisterProfileSnapshot(flag, fallbackProfile = 'NORMAL') {
  const parsedProfile = normalizeRegisterProfile(flag?.metadata) || fallbackProfile;
  return {
    profile: parsedProfile,
    updatedAt: flag?.updatedAt || null,
    updatedBy: flag?.updatedBy || null
  };
}

async function getRegisterRolloutProfile(opts = {}) {
  const bypassCache = !!opts.bypassCache;
  const now = Date.now();
  if (!bypassCache && cachedRegisterProfile && now - cachedRegisterProfileFetchedAt < PROFILE_CACHE_TTL_MS) {
    return cachedRegisterProfile;
  }

  const flag = await findFlagByKey(KEY_REGISTER_ROLLOUT_PROFILE);
  const meta = flag?.metadata;
  const raw =
    (meta && typeof meta === 'object' ? meta.profile : meta) ??
    flag?.value ??
    flag?.flagValue ??
    flag?.flag_value ??
    flag?.profile ??
    'NORMAL';

  const profile = normalizeRegisterProfile(raw) || 'NORMAL';
  cachedRegisterProfile = profile;
  cachedRegisterProfileFetchedAt = now;
  return profile;
}

async function setRegisterRolloutProfile(profileInput, updatedBy) {
  // 支持旧的对象入参模式
  let profile = profileInput;
  let actorId = updatedBy;
  if (profile && typeof profile === 'object') {
    profile = profile.profile || profile.value || profile.flagValue || profile.flag_value;
    actorId = actorId || profileInput.updatedBy || profileInput.actorId;
  }
  const normalizedProfile = normalizeRegisterProfile(profile);
  if (!normalizedProfile) {
    const error = new Error('Invalid register rollout profile');
    error.code = 'INVALID_PROFILE';
    throw error;
  }
  const updated = await upsertFlag({
    featureKey: KEY_REGISTER_ROLLOUT_PROFILE,
    enabled: true,
    metadata: { profile: normalizedProfile },
    updatedBy: actorId || null
  });
  cachedRegisterProfile = normalizedProfile;
  cachedRegisterProfileFetchedAt = Date.now();
  return buildRegisterProfileSnapshot(updated, normalizedProfile);
}

async function getFeatureFlagSnapshot() {
  const flags = await listFlags();
  const offline = flags.find(flag => flag.featureKey === FEATURE_KEYS.OFFLINE_MODE);
  const registerProfile = flags.find(
    flag => flag.featureKey === FEATURE_KEYS.REGISTER_ROLLOUT_PROFILE
  );
  return {
    offlineMode: offline || defaultFlagSnapshot(FEATURE_KEYS.OFFLINE_MODE),
    registerRolloutProfile: buildRegisterProfileSnapshot(
      registerProfile,
      cachedRegisterProfile || 'NORMAL'
    )
  };
}

module.exports = {
  FEATURE_KEYS,
  getOfflineModeFlag,
  setOfflineModeFlag,
  getFeatureFlagSnapshot,
  getRegisterRolloutProfile,
  setRegisterRolloutProfile
};
