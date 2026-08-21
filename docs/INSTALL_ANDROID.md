# Android Agent 설치 및 등록

## 설치 채널

운영 단말은 Google Play 내부 테스트 또는 비공개 테스트 링크로 설치합니다. GitHub Release APK는 개발 및 장애 복구용으로만 사용합니다.

개발 단계에서는 다음 두 방식 중 하나로 APK를 받습니다.

1. PR 또는 main 빌드의 GitHub Actions에서 payment-check-apk 아티팩트 다운로드
2. v로 시작하는 태그를 배포한 뒤 고정 릴리스 주소에서 다운로드

고정 릴리스 주소:

https://github.com/xogh3198/payment_check/releases/latest/download/payment-check.apk

release-assets.githubusercontent.com으로 시작하는 개별 주소는 만료되므로 공유 링크로 사용하지 않습니다.

## Dashboard 준비

1. 결제 관리의 자동 확인 화면으로 이동합니다.
2. 점포와 단말 이름을 선택해 등록 토큰을 생성합니다.
3. 등록 토큰은 15분 안에 한 번만 사용할 수 있으므로 앱을 설치한 상태에서 생성합니다.

## 단말 등록

1. 앱을 실행하고 데이터 수집 안내를 확인합니다.
2. 개발 검증은 서버 주소에 https://dev-seller-api.marketbite.co.kr를 입력합니다.
3. Dashboard에서 받은 등록 토큰을 입력하고 Agent 등록을 누릅니다.
4. 알림 접근 설정에서 MarketBite 결제 확인을 허용합니다.
5. 배터리 최적화 설정에서 운영 중인 Agent가 중단되지 않도록 설정합니다.
6. 은행 또는 비플페이 샘플 전송 후 Dashboard에서 이벤트를 확인합니다.

개발 서버는 공인 HTTPS 주소를 사용하므로 Android 단말과 서버가 같은 Wi-Fi에 있을 필요가 없습니다. main 운영 배포 이후에는 서버 주소를 https://seller-api.marketbite.co.kr로 변경합니다.

## APK 직접 설치

1. Android에서 APK 링크를 엽니다.
2. 브라우저 또는 파일 앱의 알 수 없는 앱 설치 권한을 이번 설치에만 허용합니다.
3. Play Protect 경고가 표시되면 배포 출처와 서명 파일을 확인한 뒤 개발 단말에서만 설치를 계속합니다.
4. 설치 후 다시 알 수 없는 앱 설치 권한을 해제합니다.

개발 APK는 운영 사용자에게 배포하지 않습니다. 운영 배포 전에는 동일한 서명키로 빌드한 AAB를 Google Play 내부 테스트 트랙에서 먼저 검증합니다.

## 금융 앱 설정

- 국민은행, 하나은행, 농협은행 앱에서 입금 알림을 활성화합니다.
- 수산대전상품권은 판매자 수취 단말에 비플페이 결제 완료 알림이 실제로 표시되는지 먼저 확인합니다.
- 알림 내용 숨김 기능이 결제자명이나 금액을 제거하면 자동 매칭할 수 없습니다.

## 장애 확인

- 등록 필요: Dashboard에서 새 토큰을 발급해 다시 등록
- 설정 필요: 알림 접근 또는 배터리 최적화 설정 확인
- 재전송 대기: 네트워크가 복구되면 WorkManager가 자동 재시도
- 서버 거절: 앱의 HTTP 상태와 Dashboard Agent 폐기 여부 확인
- 파싱 partial: 실제 알림 원문을 기준으로 파서 보완 필요

단말 등록을 해제할 때는 Dashboard에서 Agent를 먼저 폐기하고 앱의 등록 정보를 초기화합니다.

## 로컬 Viewer

디버그 앱에서만 HTTP 연결이 허용됩니다.

1. npm run viewer 실행
2. 서버 주소에 http://노트북-IP:8787 입력
3. 등록 토큰에 local-enrollment-token 입력
4. Agent 등록 및 샘플 전송

단말 브라우저에서 http://노트북-IP:8787/health가 열리지 않으면 같은 Wi-Fi와 방화벽 상태를 확인합니다.
