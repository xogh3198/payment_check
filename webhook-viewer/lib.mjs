import { createHmac, timingSafeEqual } from 'node:crypto';

export function createSignature(secret, timestamp, rawBody) {
  return createHmac('sha256', secret)
    .update(`${timestamp}.${rawBody}`)
    .digest('hex');
}

export function verifySignature(secret, timestamp, rawBody, signature, nowSeconds = Date.now() / 1000) {
  if (!secret) return { ok: true };
  if (!timestamp || !signature) {
    return { ok: false, reason: 'Missing webhook signature headers' };
  }

  const parsedTimestamp = Number(timestamp);
  if (!Number.isFinite(parsedTimestamp) || Math.abs(nowSeconds - parsedTimestamp) > 300) {
    return { ok: false, reason: 'Webhook timestamp is outside the 5 minute window' };
  }

  const expected = Buffer.from(createSignature(secret, timestamp, rawBody), 'utf8');
  const actual = Buffer.from(String(signature), 'utf8');
  if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) {
    return { ok: false, reason: 'Invalid webhook signature' };
  }
  return { ok: true };
}

export function validateDepositEvent(event) {
  if (!event || typeof event !== 'object' || Array.isArray(event)) {
    return ['Body must be a JSON object'];
  }

  const errors = [];
  for (const field of ['eventId', 'deviceId', 'source', 'bank', 'receivedAt', 'direction', 'parseStatus']) {
    if (typeof event[field] !== 'string' || event[field].trim() === '') {
      errors.push(`${field} must be a non-empty string`);
    }
  }
  if (event.amount !== null && event.amount !== undefined) {
    if (!Number.isSafeInteger(event.amount) || event.amount < 0) {
      errors.push('amount must be a non-negative integer or null');
    }
  }
  if (event.direction !== undefined && event.direction !== 'DEPOSIT') {
    errors.push('direction must be DEPOSIT for this PoC');
  }
  if (event.bank !== undefined && !['KB', 'HANA', 'NH'].includes(event.bank)) {
    errors.push('bank must be KB, HANA, or NH for this PoC');
  }
  if (event.isAuthoritative !== false) {
    errors.push('isAuthoritative must be false for notification-derived data');
  }
  return errors;
}
