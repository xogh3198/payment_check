# Payment Agent API 계약

## 등록

POST /api/v1/payment-agents/enroll

요청:

    {
      "enrollmentToken": "one-time-token",
      "installationId": "stable-installation-id",
      "appVersion": "1.0.0"
    }

응답의 agentSecret은 등록 시 한 번만 반환되며 Android Keystore로 암호화해 저장합니다.

## 서명

이벤트와 heartbeat 요청에는 다음 헤더를 포함합니다.

- X-Deposit-Agent-Id
- X-Deposit-Agent-Timestamp
- X-Deposit-Agent-Nonce
- X-Deposit-Agent-Signature

서명 원문:

    timestamp + "." + nonce + "." + rawJsonBody

서명 알고리즘은 agentSecret을 키로 사용하는 HMAC-SHA256 hex입니다. timestamp 허용 오차는 기본 5분입니다.

## 이벤트

POST /api/v1/payment-agents/events

주요 필드:

- eventId: 알림 중복 방지 ID
- provider: KB, HANA, NH, BEEPAY
- paymentMethod: bank_transfer, fishery_voucher
- eventType: deposit, payment_received, cancel, refund
- amount: 원 단위 정수
- transactionAt: ISO 8601 거래 시각
- payerName: 송금 구매자명 또는 상품권 결제자명
- accountMasked: 마스킹된 수취 계좌
- packageName, notificationTitle, rawText, postedAt
- parseStatus: parsed, partial

BEEPAY는 fishery_voucher만 사용할 수 있으며 은행 제공자는 bank_transfer만 사용합니다.

## Heartbeat

POST /api/v1/payment-agents/heartbeat

요청:

    {
      "notificationAccessEnabled": true,
      "batteryOptimizationIgnored": true,
      "appVersion": "1.0.0"
    }

WorkManager가 15분마다 전송하며 Dashboard에서 단말 장애 판단에 사용합니다.
