require('dotenv').config();

const HUMEDS_BASE_URL =
  process.env.HUMEDS_BASE_URL || 'https://health.humeds.com.cn:9443';

const HUMEDS_TIMEOUT_MS = Number(process.env.HUMEDS_TIMEOUT_MS || 10000);
const DEFAULT_REGION_CODE = process.env.HUMEDS_REGION_CODE || '86';

module.exports = {
  baseUrl: HUMEDS_BASE_URL.replace(/\/+$/, ''),
  timeoutMs: HUMEDS_TIMEOUT_MS,
  defaultRegionCode: DEFAULT_REGION_CODE,
};
