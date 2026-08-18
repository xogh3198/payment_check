# Payment Check

국민은행, 하나은행, 농협은행 입금 알림을 안드로이드에서 감지하고 구조화된 이벤트로 웹훅 전송하는 독립 검증 프로젝트입니다. TradLab 운영 서비스와 데이터베이스에는 아직 연결하지 않습니다.

## APK 다운로드

안드로이드 테스트 APK는 GitHub Releases에서 내려받을 수 있습니다.

https://github.com/xogh3198/payment_check/releases

설치와 단말 설정 방법은 docs/INSTALL_ANDROID.md를 확인합니다.

main 브랜치에 push하면 GitHub Actions가 테스트와 APK 빌드를 수행합니다. v로 시작하는 태그를 push하면 payment-check.apk가 첨부된 GitHub Release가 자동 생성됩니다.

중요한 경계:

- 은행 계좌 원장을 직접 조회하지 않습니다.
- 안드로이드 알림에서 추출한 입금 후보 데이터입니다.
- 자동 결제 확정이나 자동 환불을 수행하지 않습니다.
- 로컬 HTTP 허용은 PoC 전용입니다.

## 구성

- android: 국민·하나·농협은행 입금 알림 감지, 파싱, 단말 저장, 웹훅 전송 앱
- webhook-viewer: 웹훅 수신, 중복 제거, JSON Lines 저장, 브라우저 조회 서버

## 가장 빠른 검증

요구 사항:

- Node.js 20 이상
- Android Studio와 Android SDK 35
- 실제 단말 테스트 시 노트북과 안드로이드가 같은 네트워크에 연결되어 있어야 함

1. Webhook Viewer 실행

   npm run viewer

2. 브라우저 확인

   http://localhost:8787

3. 안드로이드 없이 샘플 전송

   npm run sample

   은행별 샘플:

   npm run sample:kb
   npm run sample:hana
   npm run sample:nh

4. Android Studio에서 android 폴더를 열고 앱을 실행합니다.

5. 앱에서 알림 접근 설정을 열어 TradLab Deposit Agent PoC를 허용합니다.

6. Webhook URL을 입력하고 샘플 입금 웹훅 전송을 누릅니다.

## Webhook URL

Android Emulator:

http://10.0.2.2:8787/webhook/deposits

실제 안드로이드 단말:

http://노트북의-로컬-IP:8787/webhook/deposits

Mac의 Wi-Fi IP 확인 예시:

ipconfig getifaddr en0

실제 단말에서 연결되지 않으면 다음을 확인합니다.

- 노트북과 단말이 같은 Wi-Fi인지
- macOS 방화벽이 Node 연결을 차단하지 않는지
- Viewer가 0.0.0.0:8787로 실행 중인지
- 모바일 브라우저에서 http://노트북-IP:8787/health가 열리는지

## 공유 시크릿

초기 화면 확인은 시크릿 없이 실행할 수 있습니다. 서명 검증까지 확인하려면 Viewer와 안드로이드 앱에 같은 값을 사용합니다.

Viewer 실행:

WEBHOOK_SECRET=임의의-긴-문자열 npm run viewer

안드로이드 앱의 공유 시크릿 입력란에도 같은 값을 입력합니다. 앱은 요청 시각과 JSON 본문을 HMAC-SHA256으로 서명합니다.

## 실제 은행 알림 확인 순서

1. 테스트할 국민은행, 하나은행 또는 농협은행 계좌의 입출금 알림을 해당 안드로이드에서 받도록 설정합니다.
2. 먼저 실제 수신 문구가 제공된 예시와 같은지 확인합니다.
3. 알림 접근 권한을 허용한 전용 안드로이드에서 입금 알림을 수신합니다.
4. 앱의 최근 감지 내역에서 파싱 결과를 확인합니다.
5. Viewer에서 같은 이벤트가 표시되는지 확인합니다.
6. 실제 문구가 다르면 해당 은행 파서의 패턴을 조정합니다.

## 현재 필터

다음 조건을 만족하는 알림만 처리합니다.

- 본문 또는 제목에 [KB], KB국민, 국민은행 중 하나가 있음
- 또는 본문이나 제목에 [하나은행], [하나], 하나은행, 하나원큐, KEB하나 중 하나가 있음
- 또는 알림 게시 앱이 신·구 하나원큐 패키지임
- 또는 본문이나 제목에 [NH], NH농협, 농협은행, 농축협, 농협, NH스마트뱅킹, NH올원뱅크, 올원뱅크, NH콕뱅크, 콕뱅크 중 하나가 있음
- 또는 알림 게시 앱이 NH스마트뱅킹, NH올원뱅크, NH콕뱅크 패키지임
- 입금 문구가 있음

다른 알림과 출금 알림은 저장하거나 전송하지 않습니다.

Webhook의 bank 값은 국민은행이면 KB, 하나은행이면 HANA, 농협은행이면 NH입니다. Viewer의 은행 컬럼과 필터에서 세 은행을 구분할 수 있습니다.

## 로컬 데이터

- 안드로이드: SharedPreferences에 최근 100건 저장
- Viewer: webhook-viewer/data/events.jsonl에 최근 수신 원본 저장
- Viewer 메모리: 최근 500건 표시

웹훅 eventId가 같으면 Viewer는 성공으로 응답하지만 중복 저장하지 않습니다.

## 운영 전 필수 변경

- HTTP 허용 제거 및 HTTPS 엔드포인트 적용
- 기기별 키 발급과 폐기
- 계좌 전체 번호와 잔액 수집 금지
- 원문 보관 기간과 마스킹 정책 적용
- 입금 후보와 주문 매칭 후 판매자 확인 단계 추가
- 은행 거래내역 또는 공식 API를 통한 사후 대조
- 장애 알림, 기기 heartbeat, 배터리 최적화 예외 안내
- 개인정보 처리방침과 판매자 동의 절차 적용

## 알려진 한계

- 알림이 지연되거나 누락될 수 있습니다.
- 문자 앱이나 은행 알림 문구 형식이 변경되면 파서 수정이 필요합니다.
- 고정 3만 원만으로는 주문을 구분할 수 없어 입금자명에 주문 식별값이 필요합니다.
- iOS에서는 다른 앱의 알림을 같은 방식으로 읽을 수 없습니다.
- Google Play 공개 배포 시 SMS와 알림 데이터 정책 검토가 필요합니다.
