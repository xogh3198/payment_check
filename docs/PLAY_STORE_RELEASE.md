# Google Play 배포 준비

## 권장 출시 순서

1. Play Console 앱 생성
2. 앱 서명 키와 별도 업로드 키 생성
3. GitHub Actions Secrets에 업로드 키 등록
4. 내부 테스트 AAB 배포
5. 실제 판매자 단말에서 은행별·비플페이 알림 검증
6. 비공개 테스트와 정책 검토
7. 운영 단말에 단계적 배포

금융 알림을 수집하는 특성상 초기에는 공개 배포보다 내부 또는 비공개 테스트가 적합합니다.

## 업로드 키 생성 예시

    keytool -genkeypair -v \
      -keystore payment-check-upload.jks \
      -alias payment-check-upload \
      -keyalg RSA -keysize 2048 -validity 10000

keystore 파일을 base64로 변환한 값을 ANDROID_KEYSTORE_BASE64에 등록합니다. 원본 keystore와 비밀번호는 회사 비밀 관리 저장소에 별도 보관합니다.

## Play Console 데이터 보안 신고

앱이 수집하는 데이터:

- 결제자명
- 결제 금액과 시각
- 마스킹 계좌
- 금융 앱 패키지와 알림 제목
- 지원되는 금융 알림 원문
- 설치 식별자와 Agent 상태

처리 원칙:

- 전송 중 HTTPS 암호화
- 단말의 서명 키와 이벤트 AES-GCM 암호화
- 서버 원문 암호화 저장
- 제3자 광고 또는 분석 SDK 미사용
- 계좌 비밀번호와 전체 계좌번호 미수집
- 관리자 역할 기반 접근과 감사 로그 적용

## 심사 자료

- 알림 접근 권한을 요청하기 전에 표시되는 앱 내 데이터 수집 안내
- 공개 접근 가능한 개인정보처리방침 URL
- 권한이 핵심 기능에 필요한 이유를 보여주는 심사 영상
- 테스트 계정과 Agent 등록 토큰 발급 절차
- 데이터 삭제 및 Agent 폐기 절차

비플페이 Android 패키지는 com.bizplay.bizzeropay입니다. 실제 판매자 단말에 표시되는 알림 문구가 파서의 샘플과 다르면 공개 범위를 늘리기 전에 파서를 먼저 보완합니다.
