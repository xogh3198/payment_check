# Payment Check

MarketBite 판매자 단말에서 국민은행, 하나은행, 농협은행 입금 알림과 비플페이 수산상품권 결제 알림을 감지해 Seller API로 전달하는 Android Agent입니다.

## 처리 범위

- 은행 입금: KB, HANA, NH를 bank_transfer로 전송
- 수산상품권: 비플페이를 BEEPAY, fishery_voucher로 전송
- 핵심 데이터: 결제자명, 금액, 거래 시각
- 보조 데이터: 마스킹 계좌, 알림 앱 패키지, 제목, 원문, 알림 게시 시각
- 취소와 환불 알림: 자동 확정하지 않고 관리자 검토 이벤트로 저장
- 동일 알림: eventId로 중복 전송과 중복 확정 방지

Agent는 계좌 비밀번호를 저장하거나 은행 계좌 원장을 조회하지 않습니다. 알림이 실제로 수신된 이후 결제를 확인하며, 송금이나 카드 결제를 실행하지 않습니다.

## 운영 등록

1. 회사 Dashboard에서 점포를 선택하고 Agent 등록 토큰을 발급합니다.
2. Android 앱에 Seller API 주소와 15분 유효 등록 토큰을 입력합니다.
3. 앱은 설치 ID를 등록하고 기기별 Agent ID와 서명 키를 한 번만 수신합니다.
4. 서명 키와 최근 이벤트는 Android Keystore 기반 AES-GCM으로 암호화해 저장합니다.
5. 알림 접근 권한과 백그라운드 실행 상태는 15분마다 Seller API에 보고합니다.

운영 기본 서버:

https://seller-api.marketbite.co.kr

## 신뢰 경계

알림 데이터는 입금 후보입니다. 서버는 활성 Agent의 서명을 확인한 뒤 점포, 결제수단, 금액, 결제자명, 거래시각이 하나의 대기 결제와 정확히 일치할 때만 자동 확정합니다.

- 후보 없음: unmatched
- 후보 여러 건: review_required
- 파싱 불완전: invalid
- 정확히 한 건: matched
- 취소 또는 환불 알림: review_required

예약금 매칭은 주문을 pre_authorized로 전환합니다. 최종 추가금 매칭은 준비 완료 주문을 payment_confirmed로 전환합니다.

## 로컬 검증

요구 사항:

- Node.js 20 이상
- Android Studio와 Android SDK 35

Viewer 실행:

    npm run viewer

브라우저:

http://localhost:8787

디버그 앱 설정:

- 서버 주소: http://노트북의-로컬-IP:8787
- 등록 토큰: local-enrollment-token

실제 Android 단말과 노트북은 같은 네트워크를 사용해야 합니다. 운영 release 앱은 HTTPS만 허용합니다.

## 검증 명령

    npm test
    cd android
    ./gradlew testDebugUnitTest assembleDebug

## 배포

main 브랜치와 PR에서는 테스트 및 디버그 APK를 생성합니다. v로 시작하는 태그는 GitHub Actions의 서명 시크릿을 사용해 설치용 APK와 Play Console용 AAB를 생성합니다.

필요한 GitHub Actions Secrets:

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

Google Play 배포 절차와 정책 준비는 docs/PLAY_STORE_RELEASE.md를 따릅니다.

## 알려진 한계

- Android 알림이 꺼지거나 지연되면 결제 확인도 지연됩니다.
- 은행과 비플페이 알림 형식이 바뀌면 파서 업데이트가 필요합니다.
- 비플페이 알림에 결제자명이 없으면 자동 매칭하지 않고 관리자 검토 대상으로 남습니다.
- Agent는 판매자 수취 단말에 설치해야 합니다. 구매자 단말의 결제 완료 알림을 수집하는 구조가 아닙니다.
- iOS는 다른 앱의 알림을 읽는 공개 API를 제공하지 않아 같은 방식으로 구현할 수 없습니다.
