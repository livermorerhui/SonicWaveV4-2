const logger = require('../logger');

const codes = new Map();

function buildKey(mobile, scene) {
  return `${mobile}|${scene}`;
}

async function sendCode(mobile, scene) {
  if (!mobile || typeof mobile !== 'string' || mobile.trim().length === 0) {
    throw new Error('Invalid mobile');
  }

  const trimmedMobile = mobile.trim();
  if (trimmedMobile.length > 32) {
    throw new Error('Invalid mobile');
  }

  const code = String(Math.floor(Math.random() * 1000000)).padStart(6, '0');
  const expiresAt = Date.now() + 5 * 60 * 1000;
  const key = buildKey(trimmedMobile, scene);

  codes.set(key, { code, expiresAt });
  logger.info('[smsCode.local] Generated code', { mobile: trimmedMobile, scene, code });

  return { ok: true };
}

async function verifyCode(mobile, scene, code) {
  const key = buildKey(mobile, scene);
  const entry = codes.get(key);

  if (!entry || Date.now() > entry.expiresAt) {
    codes.delete(key);
    return { ok: false, reason: 'EXPIRED' };
  }

  if (entry.code !== code) {
    return { ok: false, reason: 'MISMATCH' };
  }

  codes.delete(key);
  return { ok: true };
}

module.exports = { sendCode, verifyCode };
