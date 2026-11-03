# NOVA OS Launcher

Kotlin + Jetpack Compose(Material 3)로 만든 독자 디자인 런처입니다.

## 기능
- **홈**: 디지털 시계·날짜, 날씨(Open-Meteo), 검색창, 즐겨찾기, 하단 독, 3개 페이지(홈/위젯/자주 쓰는 앱), 배경화면 변경
- **앱 서랍**: 설치 앱 자동 로드(LauncherApps), 아이콘·이름, 초성 검색, 정렬 4종, 실행, 길게 눌러 즐겨찾기/독/정보/숨기기/삭제
- **제어 센터**: Wi-Fi·블루투스 바로가기, 밝기·볼륨 슬라이더, 화면 회전, 다크 모드, 손전등, 비행기 모드, 배터리 정보
- **설정**: 테마·강조색, 배경화면(프리셋/내 사진), 아이콘 크기, 글자 크기, 애니메이션, 홈 구성
- **추가 앱**: 계산기, 메모, 시계(세계 시계/스톱워치/타이머), 앱 관리, 앱 정보

## 제스처
위로 스와이프 = 앱 서랍 / 아래로 스와이프 = 제어 센터 / 좌우 = 페이지 이동 / 빈 곳 길게 = 빠른 메뉴

## APK 만들기 (직접 코딩 불필요)

### 방법 1. GitHub Actions (추천, 설치 프로그램 필요 없음)
1. GitHub에서 새 저장소를 만든다.
2. 이 폴더의 **모든 파일(숨김 폴더 `.github` 포함)** 을 저장소에 업로드한다. (PC 브라우저에서 드래그&드롭 권장)
3. 저장소의 **Actions → Build NOVA OS APK → Run workflow** (업로드하면 자동 실행됨)
4. 완료 후 실행 결과 하단 **Artifacts → NOVA-OS-Launcher-APK** 를 내려받아 압축을 풀면 `app-debug.apk`.

### 방법 2. Android Studio
폴더를 열고(Open) Gradle 동기화가 끝나면 **Build → Build APK(s)**.
명령줄: JDK 17 + Android SDK 34 + Gradle 8.9 환경에서 `gradle assembleDebug`
(결과: `app/build/outputs/apk/debug/app-debug.apk`)

## 설치 후
APK 설치(출처를 알 수 없는 앱 허용) → 홈 버튼 → **NOVA OS** 선택 → **항상**.
기본 홈 앱 변경: 설정 ▸ 앱 ▸ 기본 앱 ▸ 홈 앱.

## 구조
```
app/src/main/java/com/nova/os/launcher/
  MainActivity.kt / NovaApp.kt / NovaViewModel.kt
  data/      모델, 설정/메모 저장소, 앱 로더, 날씨, 계산 엔진
  system/    Wi-Fi·밝기·회전·손전등 등 시스템 연동
  ui/theme/  NOVA 디자인 토큰(색상, 모션, 배경화면)
  ui/components/ 글래스 카드, 슬라이더, 앱 아이콘 등 공통 컴포넌트
  ui/home, drawer, control, settings, apps, tools/  각 화면
```
