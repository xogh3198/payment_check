import { createHmac, timingSafeEqual } from 'node:crypto';

export function createSignature(secret, timestamp, nonce, rawBody) {
  return createHmac('sha256', secret)
    .update(`${timestamp}.${nonce}.${rawBody}`)
    .digest('hex');
}

export function verifySignature(
  secret,
  timestamp,
  nonce,
  rawBody,
  signature,
  nowSeconds = Date.now() / 1000,
) {
  if (!secret) return { ok: true };
  if (!timestamp || !nonce || !signature) {
    return { ok: false, reason: 'Missing webhook signature headers' };
  }
  if (String(nonce).length < 16 || String(nonce).length > 128) {
    return { ok: false, reason: 'Invalid webhook nonce' };
  }

  const parsedTimestamp = Number(timestamp);
  if (!Number.isFinite(parsedTimestamp) || Math.abs(nowSeconds - parsedTimestamp) > 300) {
    return { ok: false, reason: 'Webhook timestamp is outside the 5 minute window' };
  }

  const expected = Buffer.from(createSignature(secret, timestamp, nonce, rawBody), 'utf8');
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
  for (const field of ['eventId', 'provider', 'paymentMethod', 'eventType', 'currency', 'rawText', 'parseStatus']) {
    if (typeof event[field] !== 'string' || event[field].trim() === '') {
      errors.push(`${field} must be a non-empty string`);
    }
  }
  if (event.amount !== null && event.amount !== undefined) {
    if (!Number.isSafeInteger(event.amount) || event.amount < 1) {
      errors.push('amount must be a positive integer or null');
    }
  }
  if (!['deposit', 'payment_received', 'cancel', 'refund'].includes(event.eventType)) {
    errors.push('eventType is not supported');
  }
  if (!['KB', 'HANA', 'NH', 'BEEPAY'].includes(event.provider)) {
    errors.push('provider is not supported');
  }
  if (!['bank_transfer', 'fishery_voucher'].includes(event.paymentMethod)) {
    errors.push('paymentMethod is not supported');
  }
  if (event.provider === 'BEEPAY' && event.paymentMethod !== 'fishery_voucher') {
    errors.push('BEEPAY must use fishery_voucher');
  }
  if (event.provider !== 'BEEPAY' && event.paymentMethod !== 'bank_transfer') {
    errors.push('bank providers must use bank_transfer');
  }
  return errors;
}
