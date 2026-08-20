import assert from 'node:assert/strict';
import test from 'node:test';
import { createSignature, validateDepositEvent, verifySignature } from '../lib.mjs';

const validEvent = {
  eventId: 'event-1',
  provider: 'KB',
  paymentMethod: 'bank_transfer',
  eventType: 'deposit',
  currency: 'KRW',
  rawText: 'KB deposit notification',
  parseStatus: 'parsed',
  amount: 30000,
};

test('valid bank transfer event passes validation', () => {
  assert.deepEqual(validateDepositEvent(validEvent), []);
});

test('BEEPAY fishery voucher event passes validation', () => {
  assert.deepEqual(validateDepositEvent({
    ...validEvent,
    provider: 'BEEPAY',
    paymentMethod: 'fishery_voucher',
    eventType: 'payment_received',
  }), []);
});

test('provider and payment method mismatch is rejected', () => {
  const errors = validateDepositEvent({ ...validEvent, provider: 'BEEPAY' });
  assert.ok(errors.includes('BEEPAY must use fishery_voucher'));
});

test('unsupported provider is rejected', () => {
  const errors = validateDepositEvent({ ...validEvent, provider: 'UNKNOWN' });
  assert.ok(errors.includes('provider is not supported'));
});

test('signature is accepted only for the matching nonce and body', () => {
  const secret = 'poc-secret';
  const timestamp = '1000';
  const nonce = '0123456789abcdef';
  const body = JSON.stringify(validEvent);
  const signature = createSignature(secret, timestamp, nonce, body);
  assert.deepEqual(verifySignature(secret, timestamp, nonce, body, signature, 1000), { ok: true });
  assert.equal(verifySignature(secret, timestamp, `${nonce}x`, body, signature, 1000).ok, false);
  assert.equal(verifySignature(secret, timestamp, nonce, `${body}x`, signature, 1000).ok, false);
});

test('stale signature timestamp is rejected', () => {
  const secret = 'poc-secret';
  const nonce = '0123456789abcdef';
  const body = JSON.stringify(validEvent);
  const signature = createSignature(secret, '1000', nonce, body);
  assert.equal(verifySignature(secret, '1000', nonce, body, signature, 1401).ok, false);
});
