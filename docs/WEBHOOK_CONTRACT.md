# Webhook 계약

## Endpoint

POST /webhook/deposits

Content-Type: application/json

선택적 서명 헤더:

- X-Deposit-Agent-Id
- X-Deposit-Agent-Timestamp
- X-Deposit-Agent-Signature

서명 원문은 timestamp + 점 + 요청 JSON 원문이며 HMAC-SHA256 hex 형식을 사용합니다.

## Payload

주요 필드:

- eventId: 중복 방지 식별자
- deviceId: Agent 설치 기기 식별자
- source: android_notification
- bank: KB, HANA 또는 NH
- packageName: 알림을 게시한 앱 패키지
- receivedAt: Agent가 감지한 시각
- transactionAt: 알림 본문에서 해석한 거래 시각
- accountMasked: 마스킹된 계좌 번호
- depositorName: 입금자명
- amount: 원 단위 정수
- direction: DEPOSIT
- parseStatus: PARSED 또는 PARTIAL
- rawText: 파싱에 사용한 알림 원문
- isAuthoritative: 항상 false

## Response

- 202: 신규 이벤트 저장
- 200: 이미 저장된 eventId
- 401: 서명 누락 또는 불일치
- 422: 필수 필드 또는 데이터 형식 오류

## 향후 TradLab 연결 시 권장 상태

- detected: 알림 감지
- matched: 주문 후보와 자동 매칭
- ambiguous: 여러 주문과 일치해 확인 필요
- confirmed: 판매자가 입금 확인
- rejected: 잘못된 매칭
- reconciled: 공식 거래내역과 사후 대조 완료

알림 수신만으로 confirmed 또는 reconciled 상태를 만들지 않습니다.
