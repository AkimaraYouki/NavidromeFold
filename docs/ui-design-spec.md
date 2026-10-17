> 초기 UI 설계 자료입니다. 현재 구현 현황은 [README](../README.md)와 [빌드 상태](../BUILD-STATUS.md)를 참고하세요. 아래의 미구현 표기는 작성 당시 기준입니다.

# NaviFold UI 리디자인 명세 및 Media3 알림 진단 지침

본 문서는 Android 네이티브(Java View 기반) Navidrome 클라이언트 **NaviFold**의 화면 구조 개편, 폴더블 반응형 레이아웃(접힌 화면·펼친 화면), 수치화된 디자인 토큰(dp/sp), 상호작용 상태 정의, 그리고 시스템 미디어 알림 미표시 문제의 기술 진단을 위해 작성된 구현 명세서입니다. 구현 담당 에이전트는 본 문서의 수치와 완료 조건(DoD)을 기준으로 작업을 진행하십시오.

---

## 0. 검토 전제 및 추가로 확보해야 할 프로젝트 자료

### 0.1 자료 접근 범위 고지
- 현재 검토 환경에는 로컬 경로로 언급된 소스 코드 아카이브(`outputs/NavidromeFold.zip`)와 실제 휴대폰 캡처 화면이 첨부되어 있지 않습니다.
- 따라서 본 문서는 **보지 않은 실제 화면이나 코드를 열람한 것처럼 가정하지 않으며**, 프롬프트에 명시된 확정 사실과 Android View·[Media3](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.android.com%2Fmedia%2Fmedia3%2Fsession%2Fbackground-playback) 아키텍처에 기반한 구조적 추정을 엄격히 구분하여 기술합니다.
- 공개 확인되지 않은 미출시 One UI 버전 사양을 공식 규격처럼 인용하지 않으며, 검증된 [Samsung One UI 대형 화면·폴더블 디자인 가이드](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.samsung.com%2Fone-ui%2Flargescreen-and-foldable%2Fintro.html), [Samsung One UI 기본 레이아웃 원칙](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.samsung.com%2Fone-ui%2Flayout%2Fbasic.html), [Android 공식 반응형 뷰 가이드](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.android.com%2Fdevelop%2Fui%2Fviews%2Flayout%2Fbuild-responsive-navigation)를 기준으로 수치를 정의합니다.

### 0.2 코드 수정 착수 전 확인해야 할 파일 및 화면 목록
구현 담당 에이전트는 작업을 시작할 때 프로젝트 트리에서 아래 파일과 화면 캡처를 먼저 확인하십시오.

| 구분 | 대상 경로 / 자료명 | 확인 목적 |
| :--- | :--- | :--- |
| 매니페스트 | `app/src/main/AndroidManifest.xml` | `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 권한 및 `MediaSessionService` 인텐트 필터·서비스 타입 선언 확인 |
| 빌드 설정 | `app/build.gradle` (또는 `build.gradle.kts`) | `compileSdk`, `targetSdk`, `androidx.media3:*`, `androidx.window:window`, `com.google.android.material:material`, `androidx.slidingpanelayout:slidingpanelayout` 버전 확인 |
| 레이아웃 리소스 | `app/src/main/res/layout/*.xml` 및 `layout-sw600dp/*.xml` | 현재 메인 화면, 앨범 그리드 아이템, 미니 플레이어, 현재 곡 화면의 View 계층과 하드코딩된 크기(`dp`/`sp`) 확인 |
| 리소스 정의 | `res/values/dimens.xml`, `colors.xml`, `themes.xml`, `font/` | Poppins 폰트 참조 위치, 색상 대비, 다크 테마 구성, 아이콘 리소스 형태(`.png` vs `.xml`) 확인 |
| 재생 서비스 | `PlaybackService.java` (또는 `MediaSessionService` 상속 클래스) | `onGetSession()`, `MediaNotification.Provider` 설정, `ExoPlayer` 인스턴스 생명주기 및 포그라운드 승격 로직 확인 |
| 화면 캡처 | 접힌 화면(커버 디스플레이) / 펼친 화면(메인 디스플레이) / 앱 정보 > 알림 설정 화면 | 버튼·패널 비대 현상 실측, 상단 컨트롤 노출 여부 확인, 시스템 알림 설정의 ‘모두 거부’ 및 채널 생성 여부 대조 |

---

## 1. 현재 UI의 문제점 (중요도순 정리)

프롬프트로 전달된 **확인된 사실(Fact)**과 이를 통해 도출한 **구조적 추정(Technical Inference)**을 구분하여 우선순위순으로 정리했습니다.

| 우선순위 | 문제 영역 | 확인된 사실 (프롬프트 근거) | 구조적 원인 추정 (검증 필요) | 사용자 경험 및 완성도에 미치는 영향 |
| :--- | :--- | :--- | :--- | :--- |
| **P0 (치명적)** | 메인 화면 내 컨트롤 과밀 및 설정 노출 | 버튼과 패널이 크기만 크고 정돈되어 있지 않으며, 메인 화면에 정렬·커버 크기·세부 재생 설정을 나열하지 말아야 함 | 상단 툴바 아래에 정렬 칩, 커버 크기 토글, 재생 옵션 패널 등이 고정 영역(`LinearLayout`/`CardView`)으로 배치되어 실제 앨범 목록의 스크롤 뷰포트를 잠식하고 있을 가능성이 높음 | 접힌 화면에서 한 번에 보이는 앨범 수가 급감하고, 음악 감상보다 제어판 같은 인상을 줌 |
| **P0 (치명적)** | 폴더블(접힘·펼침) 화면 구조 미분리 | Galaxy Fold 시리즈의 접힌 세로 화면과 펼친 넓은 화면을 모두 지원해야 하나 현재 버튼·패널만 비대함 | `sw600dp` 분기 레이아웃이나 `SlidingPaneLayout` 없이 단일 스마트폰 레이아웃을 펼친 화면에 그대로 폭만 늘려(`match_parent`) 렌더링하고 있을 가능성이 높음 | 펼친 화면에서 카드·행·버튼이 과도하게 옆으로 늘어나고 한 손 조작 영역이 화면 중앙·상단으로 흩어짐 |
| **P1 (높음)** | 타이포그래피 불일치 (Poppins + 기기 한글 폴백) | [Sonixd](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fjeffvli%2Fsonixd%3Fref%3Ddanmackinlay.name)에서 가져온 Poppins 폰트를 사용 중이며, Poppins에 없는 한글은 기기 글꼴로 표시됨 | 영문 기하학 산세리프인 Poppins는 x-height와 상하 어센더/디센더 여백이 커서, 한글 시스템 글꼴과 한 줄에 섞일 때 베이스라인이 어긋나고 줄 높이가 과도하게 커짐 | 한글 앨범명·곡명 가독성이 떨어지고, 리스트 행 높이와 텍스트 수직 중앙 정렬이 깨져 보임 |
| **P1 (높음)** | 아이콘 자산 파편화 (PNG + Font Awesome 혼용) | [Sonixd](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fjeffvli%2Fsonixd%3Fref%3Ddanmackinlay.name)의 재생·일시정지·이전·다음 PNG와 Font Awesome 탐색 아이콘을 혼용해 선 굵기·해상도·형태가 일관되지 않음 | 데스크톱용 저해상도 PNG와 웹용 Font Awesome 글리프가 섞여 획 굵기(Stroke), 끝 모양(Cap), 내부 여백(Optical padding)이 제각각이며, `ImageButton`에 `scaleType="fitXY"` 등이 적용되어 아이콘이 비대해졌을 가능성이 있음 | 앱 전체가 하나의 시스템으로 보이지 않고 임시 프로토타입처럼 보이는 핵심 원인 |
| **P1 (높음)** | 재생 계층(미니 플레이어 ↔ 현재 곡 화면) 및 한 손 조작 동선 미흡 | 재생 화면이 전체 디자인과 정돈되어 있지 않으며, 주요 조작을 한 손으로 쓰기 쉽게 재배치해야 함 | 미니 플레이어와 현재 곡 화면의 시각적 연결(`BottomSheetBehavior` 등)이 약하거나, 현재 곡 화면 상단/중앙에 조작 버튼이 분산되어 엄지 도달 범위를 벗어났을 가능성이 있음 | 접힌 화면의 긴 세로 비율(약 21:9)과 펼친 화면의 넓은 폭에서 한 손으로 곡을 넘기거나 탐색하기 불편함 |
| **P2 (보통)** | 미구현 기능(AI 추천·자동 스킵)의 UI 노출 위험 | AI 추천·자동 스킵은 향후 기능이며 아직 작동하지 않으나, 작동하는 기능처럼 보이면 안 됨 | 화면 내 버튼, 탭, 또는 토글 스위치로 미구현 기능이 노출되어 클릭 시 무반응이거나 더미 상태로 남아 있을 가능성이 있음 | 사용자에게 앱이 고장 났다는 인상을 주며 핵심 기능(앨범·폴더 탐색)의 집중도를 떨어뜨림 |

---

## 2. 일관된 디자인 방향 및 자산 정비 지침

### 2.1 핵심 디자인 원칙: “One UI의 인체공학적 구조 + Sonixd의 고밀도 앨범 탐색”
1. **상단 감상 영역(Viewing Area)과 하단 조작 영역(Interaction Area)의 분리**: [Samsung One UI 기본 레이아웃 원칙](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.samsung.com%2Fone-ui%2Flayout%2Fbasic.html)에 따라 화면 상단 35~40%는 앨범 커버와 타이틀 등 시각 정보 확인에 집중하고, 탭 전환·미니 플레이어·주요 재생 컨트롤·선택 팝업은 엄지손가락이 닿는 화면 하단 60% 이내에 배치합니다.
2. **장식 배제 및 앨범 아트워크 중심(Content-First)**: 불필요한 외곽선(`stroke`), 중첩된 카드 배경, 과도한 그림자(`elevation`), 설명용 보조 문구를 제거합니다. 배경은 깊은 무채색 다크 서피스(`#121214`)로 통일하여 앨범 커버 자체가 시각적 주인공이 되도록 합니다.
3. **고밀도 연속 탐색**: 데스크톱 [Subsonic 클라이언트 Sonixd](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fjeffvli%2Fsonixd%3Fref%3Ddanmackinlay.name)의 장점인 **빠른 앨범 그리드 스캔과 상세 메타데이터 가시성**을 모바일·폴더블 뷰포트에 맞게 재해석합니다. 메인 화면에는 필터·정렬 패널을 두지 않고, 상단 앱바의 우측에 최소한의 액션 아이콘만 남깁니다.

### 2.2 현재 자산 유지·대체 판정표
삼성 전용 폰트(One UI Sans 등)나 비공개 리소스는 외부 앱에서 임의로 번들링할 수 없으므로, Android 표준 시스템 리소스와 검증된 오픈소스 자산만 사용합니다.

| 현재 자산 | 판정 | 대체 자산 및 구현 방식 | 결정 이유 |
| :--- | :--- | :--- | :--- |
| **Sonixd Poppins 폰트** | **교체 (전면 제거)** | 시스템 기본 산세리프(`sans-serif`, `sans-serif-medium`) 기본 적용 + 숫자/시간 표시에만 `android:fontFeatureSettings="tnum"`(고정폭 숫자) 적용. (선택 사항: 한·영 통합 폰트 번들링이 필요할 경우 오픈소스 [Pretendard](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Forioncactus%2Fpretendard%2Fblob%2Fmain%2Fpackages%2Fpretendard%2FREADME.md) 단일 패밀리만 사용) | Poppins는 한글 글리프가 없어 한·영 혼용 시 베이스라인 불일치와 행간 비대화를 유발함. 시스템 기본 산세리프를 쓰면 Galaxy 기기의 사용자 글꼴 및 큰 글자 크기(`sp`) 설정과 완벽히 호환됨 |
| **Sonixd 재생·일시정지·이전·다음 PNG** | **교체 (전면 제거)** | [Material Symbols Rounded](https://www.google.com/search?q=https%3A%2F%2Fdevelopers.google.com%2Ffonts%2Fdocs%2Fmaterial_symbols) 기반 Android `VectorDrawable` (`res/drawable/*.xml`, Filled 스타일, Optical Size 24dp/36dp) | 래스터 PNG는 폴더블 고밀도 화면 및 버튼 크기 변형 시 흐려지며 다크/라이트 틴트 제어가 어렵고 여백 기준이 불투명함 |
| **Font Awesome 탐색 아이콘** | **교체 (전면 제거)** | [Material Symbols Rounded](https://www.google.com/search?q=https%3A%2F%2Fdevelopers.google.com%2Ffonts%2Fdocs%2Fmaterial_symbols) 기반 Android `VectorDrawable` (Outlined/Rounded, Weight 400, 선 굵기 `2dp` 통일) | Font Awesome은 Material 아이콘과 획 굵기·곡률·시각적 무게중심이 달라 혼용 시 UI가 이질적으로 보임 |
| **Sonixd 기본 표지(Placeholder) 이미지** | **교체** | `res/drawable/bg_album_placeholder.xml` (`#242428` 단색 사각형 배경 + 중앙 `32dp` 음표 벡터 아이콘 `#6E6E77`) | 비트맵 디코딩 메모리를 절약하고, 커버 크기를 ‘작게/보통/크게’ 또는 상세 화면으로 바꿔도 깨지지 않는 일관된 다크 서피스 톤을 유지함 |
| **Media3 ExoPlayer & MediaSessionService** | **유지 (구현 보완)** | [AndroidX Media3](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.android.com%2Fmedia%2Fmedia3%2Fsession%2Fbackground-playback) `ExoPlayer` + `MediaSessionService` 아키텍처 유지, 알림 채널·권한·포그라운드 승격 로직 보완 | Android 표준 미디어 세션 및 폴더블 화면 회전/접힘 상태 유지에 가장 적합한 구조임 |

---

## 3. 화면별 구체적인 구성안 (접힌 상태 vs 펼친 상태)

### 3.1 화면 폭 분기 기준 (Breakpoint)
[Android 대형 화면 반응형 내비게이션 가이드](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.android.com%2Fdevelop%2Fui%2Fviews%2Flayout%2Fbuild-responsive-navigation) 및 [Samsung One UI 대형 화면 가이드](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.samsung.com%2Fone-ui%2Flargescreen-and-foldable%2Fintro.html)에 따라 창 너비(`sw` / Window Width) 기준으로 두 가지 레이아웃 상태를 운영합니다.
- **접힌 상태 (Compact Width, `< 600dp`)**: 단일 패인(Single-pane) 구조 + 하단 `BottomNavigationView`(`56dp`) + 그 위에 고정된 하단 미니 플레이어(`64dp`).
- **펼친 상태 (Expanded Width, `>= 600dp`)**: 좌측 고정 `NavigationRailView`(`72dp` 너비, 아이콘+라벨을 화면 중앙~하단 엄지 영역에 배치) + 본문 영역(목록 단독 또는 `SlidingPaneLayout` 기반 목록·상세 2단 분할) + 하단 전체 폭(네비게이션 레일 우측 영역) 미니 플레이어(`64dp`).

---

### 3.2 화면 1: 앨범 목록 (Main Album Grid)
- **핵심 규칙**:
  - 메인 화면 상단에 정렬 바, 커버 크기 슬라이더, 재생 설정 칩을 **절대 배치하지 않습니다** (요구사항 1).
  - 상단 앱바(`56dp`)에는 좌측 현재 탭 제목(`앨범`)과 우측 **검색 아이콘(`ic_search`)**만 배치합니다. 정렬 기준(앨범명, 앨범 아티스트, 연도, 최근 추가순)과 커버 크기(작게/보통/크게) 변경은 전부 **설정 탭**의 선택 팝업에서 제어합니다.
  - 페이지 번호나 ‘다음 페이지’ 버튼 없이 `RecyclerView`의 `OnScrollListener`가 하단 끝에서 6행 이전에 도달하면 [Navidrome Subsonic API](https://www.google.com/search?q=https%3A%2F%2Fwww.navidrome.org%2Fdocs%2Fdevelopers%2Fsubsonic-api%2F) `getAlbumList2`(`offset`, `size=60`)를 자동 호출하여 끝까지 연속 스크롤합니다 (요구사항 2).
  - 각 앨범 그리드 셀은 **[1:1 정사각형 커버] + [앨범 제목 1줄(또는 최대 2줄)] + [앨범 아티스트 1줄]**만 표시하며, 카드 배경 박스를 제거하고 커버 모서리(`8dp` 반경)만 둥글게 처리합니다.

#### 앨범 목록 와이어프레임 (접힌 상태 `< 600dp` vs 펼친 상태 `>= 600dp`)
```text
[접힌 상태 (Compact, < 600dp) - 기본 3열 (보통)]     [펼친 상태 (Expanded, >= 600dp) - 기본 5열 (보통)]
+----------------------------------------+     +------+------------------------------------------------+
| 앨범                              [🔍] |     |      | 앨범                                      [🔍] |
+----------------------------------------+     |      +------------------------------------------------+
| +----------+ +----------+ +----------+ |     |      | +--------+ +--------+ +--------+ +--------+ +--------+
| |          | |          | |          | |     | [앨범]| | Cover  | | Cover  | | Cover  | | Cover  | | Cover  |
| |  Cover   | |  Cover   | |  Cover   | |     |      | +--------+ +--------+ +--------+ +--------+ +--------+
| |   1:1    | |   1:1    | |   1:1    | |     | [폴더]| 앨범 제목  앨범 제목  앨범 제목  앨범 제목  앨범 제목
| +----------+ +----------+ +----------+ |     |      | 아티스트   아티스트   아티스트   아티스트   아티스트
| 앨범 제목 1   앨범 제목 2   앨범 제목 3  |     | [설정]|                                                |
| 아티스트 A    아티스트 B    아티스트 C   |     |      | +--------+ +--------+ +--------+ +--------+ +--------+
|                                        |     |      | | Cover  | | Cover  | | Cover  | | Cover  | | Cover  |
| +----------+ +----------+ +----------+ |     |      | +--------+ +--------+ +--------+ +--------+ +--------+
| |  Cover   | |  Cover   | |  Cover   | |     |      | 앨범 제목  앨범 제목  앨범 제목  앨범 제목  앨범 제목
| ... (끝까지 연속 스크롤, 페이지 버튼 없음) |     |      | 아티스트   아티스트   아티스트   아티스트   아티스트
+----------------------------------------+     |      +------------------------------------------------+
| [img] 현재 재생 곡 제목 - 아티스트 [▶][⏭]|     |      | [img] 현재 재생 곡 제목 · 아티스트    [⏮][▶][⏭][≡]|
+----------------------------------------+     +------+------------------------------------------------+
|   [앨범]        [폴더]        [설정]   |     (좌측 NavigationRail 72dp, 하단 버튼 중앙~하단 정렬)
+----------------------------------------+
```

---

### 3.3 화면 2: 폴더 탐색 (Folder Browser)
- **핵심 규칙**:
  - 상단 앱바 아래에 현재 경로를 보여주는 가로 스크롤 브레드크럼(`HorizontalScrollView` 내부 텍스트 칩: `루트 > 클래식 > 바흐`, 높이 `36dp`)을 두어 상위 폴더로 즉시 이동할 수 있게 합니다.
  - 현재 폴더 안에 음원 파일이 존재할 경우, 리스트 최상단에 과장된 배너 대신 컴팩트한 액션 바 행(`높이 48dp`: `[▶ 이 폴더 전체 재생]` 텍스트 버튼 + `[셔플 재생]` 보조 버튼)만 노출합니다.
  - 하위 폴더 행(폴더 아이콘 + 폴더명 + 하위 항목 수)을 먼저 표시하고, 그 아래에 오디오 파일 행(트랙 번호 또는 파일 아이콘 + 제목/파일명 + 아티스트·길이 + 우측 `[⋮]` 더보기 버튼)을 연속 스크롤로 나열합니다.
  - 펼친 상태(`>= 600dp`)에서는 좌측 패인(너비 38%, 최대 `320dp`)에 상위·하위 폴더 트리를, 우측 패인(너비 62%)에 선택된 폴더의 하위 항목 및 음원 목록을 2단(`SlidingPaneLayout`)으로 표시합니다.

#### 폴더 탐색 와이어프레임
```text
[접힌 상태 (Compact)]                           [펼친 상태 (Expanded - 2단 분할)]
+----------------------------------------+     +------+--------------------+---------------------------+
| 폴더                              [🔍] |     |      | 폴더 트리          | /Music/OST/Interstellar   |
+----------------------------------------+     |      +--------------------+---------------------------+
| 루트 > Pop > 2020s                     |     | [앨범]| 📁 Pop             | [▶ 폴더 전체 재생] [셔플] |
+----------------------------------------+     |      | 📁 Rock            +---------------------------+
| [▶ 폴더 전체 재생]         [🔀 셔플]   |     | [폴더]| 📂 OST (선택됨)    | 01. Cornfield Chase  02:06|
+----------------------------------------+     |      |   └ Interstellar   |     Hans Zimmer       [⋮] |
| 📁 CD 1                        12곡  > |     | [설정]|   └ Inception      | 02. Stay             06:52|
| 📁 CD 2                        10곡  > |     |      | 📁 Jazz            |     Hans Zimmer       [⋮] |
| 🎵 01. Track Title A          03:42 [⋮]|     |      |                    | 03. Mountains        03:39|
|    Artist Name · FLAC                  |     +------+--------------------+---------------------------+
| 🎵 02. Track Title B          04:15 [⋮]|     |      | [img] 현재 재생 곡 제목 · 아티스트    [⏮][▶][⏭][≡]|
+----------------------------------------+     +------+------------------------------------------------+
```

---

### 3.4 화면 3: 앨범 상세와 트랙 목록 (Album Detail & Tracklist)
- **핵심 규칙**:
  - **접힌 상태 (Compact)**: 상단에 과도하게 큰 전체 폭 커버를 두면 트랙 목록이 화면 아래로 밀려나므로, 상단 헤더는 **좌측 썸네일 커버(`112dp × 112dp`, 반경 `10dp`) + 우측 메타데이터 요약(앨범명 최대 2줄, 앨범 아티스트 1줄, 발매 연도 · 총 곡 수 · 총 재생시간 · 포맷 배지 `FLAC`)**의 가로 배치로 압축합니다. 그 바로 아래에 `[▶ 전체 재생]`(주요 강조 버튼)과 `[🔀 셔플]`(보조 표면 버튼)을 나란히 배치하여 한 손 엄지로 즉시 누를 수 있게 합니다.
  - **펼친 상태 (Expanded)**: 좌측 고정 패널(`280dp~320dp`)에 앨범 커버(`200dp × 200dp`), 앨범 메타데이터, `[▶ 전체 재생]` / `[🔀 셔플]` 버튼을 고정 배치하고, 우측 패널 전체를 트랙 목록 `RecyclerView`에 할당하여 스크롤 시에도 좌측 앨범 정보가 유지되게 합니다.
  - **트랙 행 구성**: `[트랙 번호(32dp 고정폭, 우측 정렬)] + [곡 제목(1~2줄) / 아티스트명(앨범 아티스트와 다를 때만 표시)] + [재생 시간(44dp 고정폭)] + [더보기 ⋮(48dp 터치 영역)]`. 현재 재생 중인 트랙은 트랙 번호 자리에 강조 색상(`#6C8CFF`) 재생 인디케이터 아이콘을 표시하고 곡 제목 색상을 강조 색상으로 변경합니다.

#### 앨범 상세 와이어프레임
```text
[접힌 상태 (Compact)]                           [펼친 상태 (Expanded)]
+----------------------------------------+     +------+-------------------+----------------------------+
| [←] 앨범 상세                     [⋮]  |     |      | [←]               | 트랙 목록 (12곡 · 54분)    |
+----------------------------------------+     |      | +---------------+ |----------------------------|
| +---------+  앨범 타이틀 (최대 2줄)    |     | [앨범]| |               | | 01  곡 제목 A    03:45 [⋮] |
| |  Cover  |  앨범 아티스트             |     |      | |  Cover 200dp  | | 02  곡 제목 B    04:12 [⋮] |
| |  112dp  |  2023 · 12곡 · 54분 · FLAC |     | [폴더]| |               | | ▶   곡 제목 C    05:01 [⋮] |
| +---------+                            |     |      | +---------------+ |     (현재 재생 중 강조)    |
| [   ▶ 전체 재생   ]  [   🔀 셔플   ]   |     | [설정]| 앨범 타이틀       | 04  곡 제목 D    03:20 [⋮] |
+----------------------------------------+     |      | 앨범 아티스트     | 05  곡 제목 E    04:50 [⋮] |
| 01  첫 번째 트랙 제목        03:45 [⋮] |     |      | 2023 · FLAC       | 06  곡 제목 F    03:18 [⋮] |
| 02  두 번째 트랙 제목        04:12 [⋮] |     |      | [▶ 전체] [🔀 셔플]| 07  곡 제목 G    06:04 [⋮] |
| ▶   세 번째 트랙 (재생 중)   05:01 [⋮] |     +------+-------------------+----------------------------+
| 04  네 번째 트랙 제목        03:20 [⋮] |     |      | [img] 세 번째 트랙 · 아티스트         [⏮][⏸][⏭][≡]|
+----------------------------------------+     +------+------------------------------------------------+
```

---

### 3.5 화면 4: 하단 미니 플레이어 (Mini Player Bar)
- **핵심 규칙**:
  - 높이 **`64dp` 고정**, 상단에 `2dp` 두께의 얇은 진행률 바(`LinearProgressIndicator`, 터치 드래그 비활성·시각 표시 전용)를 부착합니다.
  - 본문(좌측~중앙) 영역 전체를 클릭 가능한 단일 컨테이너(`android:clickable="true"`, `foreground="?attr/selectableItemBackground"`)로 묶어 **어디를 누르든 현재 곡 화면(Now Playing)이 열리도록** 합니다 (요구사항 4).
  - **접힌 상태 구성**: `[좌측 패딩 12dp] + [앨범 커버 44dp×44dp (반경 6dp)] + [간격 12dp] + [곡 제목 1줄 + 아티스트 1줄 (가변 폭)] + [재생/일시정지 버튼 48dp×48dp] + [다음 곡 버튼 48dp×48dp] + [우측 패딩 4dp]`. (접힌 화면에서는 제목 가독성 확보를 위해 이전 곡 버튼을 생략하고 재생/일시정지·다음 곡 2개만 노출합니다.)
  - **펼친 상태 구성**: 가로 폭이 충분하므로 우측 컨트롤에 `[이전 곡 48dp] + [재생/일시정지 48dp] + [다음 곡 48dp] + [재생 대기열 열기 48dp]` 4개 버튼을 배치합니다.

---

### 3.6 화면 5: 현재 곡 재생 화면 (Now Playing Screen)
- **핵심 규칙**:
  - 하단 미니 플레이어를 탭하거나 위로 스와이프하면 `BottomSheetBehavior`(`STATE_EXPANDED`)로 전체 화면으로 올라오며, 상단 핸들 바(`36dp × 4dp`) 또는 좌측 상단 닫기(`[∨]`) 버튼, 시스템 뒤로가기 제스처로 닫힙니다.
  - **필수 표시 정보 (요구사항 5)**:
    1. 앨범 커버
    2. 곡 제목 (긴 제목은 최대 2줄까지 표시 후 말줄임 또는 단일 행 마키 선택 가능 — 기본 2줄 고정으로 시선 흔들림 방지)
    3. 아티스트 (탭 시 해당 아티스트의 앨범 필터로 이동 가능)
    4. 앨범명 및 발매 **연도** (`앨범명 · 2021` 형식)
    5. **상세 메타데이터 배지(Technical Metadata Pill)**: 커버와 시크바 사이에 별도 팝업 없이 즉시 읽을 수 있는 캡슐형 정보 행(`FLAC · 16bit / 44.1kHz · 945 kbps · 디스크 1 트랙 03`)을 표시합니다.
  - **한 손 조작 배치 (요구사항 6)**:
    - **접힌 상태**: 화면 상단 50%는 커버 + 상세 메타데이터 배지(Viewing Area), 화면 하단 50%는 곡 제목·아티스트·앨범·연도 + 시크바 + 주요 재생 버튼(`셔플`, `이전 곡`, `재생/일시정지(64dp 원형)`, `다음 곡`, `반복`) + 하단 보조 바(`상세 태그 정보 모달`, `재생 대기열 열기`)로 구성하여 엄지 이동 거리를 최소화합니다.
    - **펼친 상태**: 좌측 45% 영역에 앨범 커버와 기술 메타데이터 배지를 중앙 정렬하고, **우측 55% 영역의 하단부**에 곡 정보·시크바·재생 컨트롤·하단 대기열 버튼을 모아두어 기기를 오른손(또는 왼손)으로 파지한 상태에서 손가락을 뻗지 않고 조작할 수 있게 합니다.
  - **미구현 기능 배제 (요구사항 8)**: 현재 곡 화면에 ‘AI 유사곡 추천’, ‘스마트 스킵’ 같은 미구현 아이콘을 **절대 배치하지 않습니다**.

#### 현재 곡 재생 화면 와이어프레임
```text
[접힌 상태 (Compact)]                           [펼친 상태 (Expanded)]
+----------------------------------------+     +-------------------------------------------------------+
| [∨]            현재 재생 중        [⋮] |     | [∨]                   현재 재생 중                [⋮] |
+----------------------------------------+     +--------------------------+----------------------------+
|                                        |     |                          |                            |
|       +------------------------+       |     |   +------------------+   |  곡 제목 (최대 2줄까지     |
|       |                        |       |     |   |                  |   |  줄바꿈 허용 및 가독성 확보)|
|       |     Album Artwork      |       |     |   |  Album Artwork   |   |  아티스트 이름             |
|       |     (최대 280x280dp)   |       |     |   |   (260x260dp)    |   |  앨범 제목 · 2022          |
|       |                        |       |     |   |                  |   |                            |
|       +------------------------+       |     |   +------------------+   |  [FLAC · 24/96kHz · 1411k] |
|    [FLAC · 16bit/44.1kHz · 912kbps]    |     |                          |                            |
|                                        |     |                          |  01:24 =======●----- 04:12 |
| 곡 제목 (최대 2줄 표시, 한글 가독성)   |     |                          |                            |
| 아티스트 이름                          |     |                          |   [🔀]  [⏮] (⏸) [⏭]  [🔁] |
| 앨범 제목 · 2022                       |     |                          |                            |
|                                        |     |                          |  [ⓘ 파일/태그 상세] [≡ 대기열]|
| 01:24 ============●------------ 04:12  |     +--------------------------+----------------------------+
|                                        |
|   [🔀]    [⏮]    ( ⏸ )    [⏭]    [🔁] |
|                                        |
|      [ⓘ 태그 상세]      [≡ 재생 대기열]|
+----------------------------------------+
```

---

### 3.7 화면 6: 재생 대기열 (Playing Queue)
- **핵심 규칙**:
  - 현재 곡 화면 하단의 `[≡ 재생 대기열]` 버튼 또는 펼친 화면 미니 플레이어의 `[≡]` 버튼을 누르면 열립니다.
  - **접힌 상태**: 현재 곡 화면 위에 올라오는 모달 바텀시트(`BottomSheetDialogFragment`, 화면 높이의 85% 확장)로 표시하며, 상단에 `재생 대기열 (현재 3 / 전체 18곡)` 헤더와 `[대기열 비우기]` 텍스트 버튼을 배치합니다.
  - **펼친 상태**: 현재 곡 화면 우측 패인 하단 탭을 전환하거나 우측 사이드 시트(`360dp` 폭)로 즉시 표시하여 커버를 가리지 않고 대기열을 편집할 수 있게 합니다.
  - 각 대기열 행은 `[드래그 핸들 ≡ (48dp 터치)] + [소형 썸네일 40dp] + [곡 제목 / 아티스트] + [삭제 × (48dp 터치)]`로 구성하고, `ItemTouchHelper`로 위아래 순서 변경을 지원합니다. 열릴 때 항상 **현재 재생 중인 트랙 위치로 자동 스크롤**됩니다.

---

### 3.8 화면 7: 설정과 선택 팝업 (Settings & Selection Dialogs)
- **핵심 규칙 (요구사항 1 & 3 & 8)**:
  - 메인 화면에서 제거한 모든 보기·정렬·재생 옵션은 **설정 탭**에 카테고리별 리스트 행(`높이 60dp`: 좌측 설정 항목명 + 하단 현재 선택된 값 요약 텍스트 `#A0A0AB`)으로 정리합니다.
  - 각 설정 행을 탭하면 페이지 이동 없이 **`MaterialAlertDialogBuilder` 기반 단일 선택 라디오 팝업(`setSingleChoiceItems`)**이 열리며, 항목을 선택하는 즉시 다이얼로그가 닫히고 설정이 반영됩니다.
  - **필수 설정 항목 및 선택 팝업 옵션**:
    1. **앨범 정렬 기준**: `앨범 제목 (가나다/ABC순)` / `앨범 아티스트순` / `발매 연도 (최신순)` / `발매 연도 (오래된순)` / `최근 추가된 순` / `무작위`
    2. **앨범 커버 크기**: `작게 (한 화면에 더 많은 앨범 표시)` / `보통 (권장)` / `크게 (아트워크 강조)`
    3. **스트리밍 음질 (Tailscale / 외부 네트워크)**: `원본 음질 (FLAC/원본 유지)` / `높음 (320 kbps)` / `보통 (192 kbps)`
    4. **긴 제목 표시 방식**: `2줄까지 표시 (권장)` / `1줄 자동 흐르기(마키)` / `1줄 말줄임(...)`
    5. **서버 및 Tailscale 연결 정보**: 연결된 Navidrome URL, 연결 상태 배지(`연결됨` / `오프라인`), `[연결 테스트]` 버튼
  - **미구현 기능 격리**: AI 추천·자동 스킵 기능은 설정 화면에서도 제거하는 것을 기본으로 하되, 기획상 자리 표시가 반드시 필요하다면 설정 최하단 `향후 지원 예정` 그룹에 `android:enabled="false"`, 투명도 `0.38`로 비활성화하고 부제목에 `"준비 중인 기능입니다 (현재 버전 미지원)"`이라고 명시하여 작동 가능한 토글처럼 보이지 않게 합니다.

#### 설정 및 선택 팝업 와이어프레임
```text
[설정 화면]                                     [선택 팝업 (MaterialAlertDialog 단일 선택)]
+----------------------------------------+     +----------------------------------+
| 설정                                   |     | 앨범 커버 크기                   |
+----------------------------------------+     +----------------------------------+
| [화면 및 탐색]                         |     | ( ) 작게 (접힘 4열 / 펼침 6열)   |
| 앨범 정렬 기준                         |     | (●) 보통 (접힘 3열 / 펼침 5열)   |
| 앨범 아티스트순                      > |     | ( ) 크게 (접힘 2열 / 펼침 4열)   |
|----------------------------------------|     +----------------------------------+
| 앨범 커버 크기                         |     |                           [취소] |
| 보통 (접힘 3열 / 펼침 5열)           > |     +----------------------------------+
|----------------------------------------|
| 긴 제목 표시 방식                      |
| 2줄까지 표시                         > |
|                                        |
| [재생 및 서버]                         |
| 스트리밍 음질                          |
| 원본 음질 (무손실 유지)              > |
|----------------------------------------|
| Navidrome 서버 상태 (Tailscale)        |
| http://100.x.y.z:4533 · 연결됨  [점검] |
+----------------------------------------+
```

---

## 4. 구현 가능한 디자인 수치 (Design Tokens & Metrics)

구현 담당 에이전트는 아래 수치를 `res/values/colors.xml`, `res/values/dimens.xml`, `res/values-sw600dp/dimens.xml`, `res/values/styles.xml`에 그대로 정의하여 하드코딩된 픽셀/dp 값을 전면 교체하십시오.

### 4.1 색상 시스템 (Color Tokens - Dark First)
음악 앱 특성상 앨범 커버의 색감을 해치지 않는 저채도 다크 테마를 기본으로 하며, 모든 텍스트는 WCAG AA(4.5:1) 이상의 명암비를 충족합니다.

| 토큰 이름 (`colors.xml`) | Hex 코드 | 용도 및 적용 대상 | 배경 대비 명암비 |
| :--- | :--- | :--- | :--- |
| `color_bg_primary` | `#121214` | 기본 화면 배경 (앨범 목록, 폴더, 설정, 앨범 상세 본문) | 기준 배경 |
| `color_surface_elevated` | `#1C1C20` | 하단 미니 플레이어, 상단 앱바 스크롤 시 배경, 설정 그룹 카드 | 1.25:1 (표면 구분) |
| `color_surface_sheet` | `#222227` | 현재 곡 재생 화면(바텀시트), 재생 대기열 시트, 선택 팝업 배경 | 1.45:1 |
| `color_surface_variant` | `#2C2C34` | 커버 플레이스홀더 배경, 브레드크럼 칩, 보조 버튼(`[셔플]`) 배경, 구분선(`#2A2A30`) | - |
| `color_text_primary` | `#F4F4F6` | 곡 제목, 앨범 제목, 폴더명, 설정 본문 타이틀 | **16.2:1 (AAA)** |
| `color_text_secondary` | `#A2A2AE` | 아티스트명, 앨범 연도, 트랙 길이, 설정 선택값 요약 | **7.1:1 (AAA)** |
| `color_text_tertiary` | `#6E6E7A` | 비활성 상태, 트랙 번호(미재생 시), 기술 메타데이터 보조 구분자 | **4.5:1 (AA)** |
| `color_accent_primary` | `#6C8CFF` | 주요 재생 버튼 배경, 현재 재생 중 트랙 타이틀/아이콘, 시크바 활성 트랙, 선택된 탭 | **6.4:1 (AA)** |
| `color_on_accent` | `#0E1324` | `color_accent_primary` 버튼 내부의 아이콘/텍스트 색상 | **7.2:1 (AAA)** |
| `color_status_error` | `#FF6B6B` | Tailscale/Navidrome 서버 연결 실패 배너, 오류 아이콘 | **6.1:1 (AA)** |

---

### 4.2 타이포그래피 스케일 (`sp` 단위 - 시스템 글꼴 크기 연동)
- 모든 글자 크기는 반드시 **`sp` 단위**로 지정하여 사용자의 시스템 글자 크기 설정을 따릅니다.
- 한글 가독성을 위해 줄 간격(`android:lineSpacingExtra` 또는 `lineHeight`)을 글자 크기의 **1.35~1.4배**로 확보하고, `android:includeFontPadding="false"` 대신 한글 상하 잘림을 막기 위해 `android:includeFontPadding="true"`(기본값)를 유지하되 상하 패딩을 대칭으로 설계합니다.

| 스타일 명칭 | 크기 (`sp`) | 굵기 (`fontFamily` / `textStyle`) | 행간 (`lineHeight`) | 최대 줄 수 (`maxLines`) | 적용 위치 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `Text.NaviFold.ScreenTitle` | `20sp` | `sans-serif-medium` (500) | `28sp` | 1 (`ellipsize="end"`) | 상단 앱바 화면 제목 (`앨범`, `폴더`, `설정`) |
| `Text.NaviFold.NowPlayingTitle` | `20sp` (접힘) / `22sp` (펼침) | `sans-serif-medium` (600/Bold) | `28sp` / `30sp` | **2** (`ellipsize="end"`) | 현재 곡 재생 화면의 곡 제목 |
| `Text.NaviFold.AlbumDetailTitle` | `18sp` | `sans-serif-medium` (600/Bold) | `25sp` | 2 (`ellipsize="end"`) | 앨범 상세 헤더의 앨범 제목 |
| `Text.NaviFold.ListPrimary` | `15sp` | `sans-serif-medium` (500) | `21sp` | 1~2 (설정에 따름) | 트랙 목록 곡 제목, 폴더명, 설정 항목명, 미니 플레이어 곡 제목 |
| `Text.NaviFold.GridTitle` | `13sp` (작게 설정 시 `12sp`) | `sans-serif-medium` (500) | `18sp` | 1 (`ellipsize="end"`) | 앨범 그리드 셀 하단 앨범 제목 |
| `Text.NaviFold.Secondary` | `13sp` | `sans-serif` (400) | `18sp` | 1 (`ellipsize="end"`) | 아티스트명, 미니 플레이어 아티스트, 설정 현재 선택값 |
| `Text.NaviFold.CaptionMono` | `12sp` | `sans-serif` (400, `tnum`) | `16sp` | 1 | 재생 시간(`03:42`), 트랙 번호, 기술 메타데이터 배지(`FLAC · 945kbps`) |

---

### 4.3 커버 크기 프리셋 및 그리드 간격 (요구사항 3 충족)
설정 화면의 **‘앨범 커버 크기’** 선택 팝업(`작게 / 보통 / 크게`)과 화면 상태(`접힘 < 600dp / 펼침 >= 600dp`)에 따라 `GridLayoutManager`의 열 개수(`spanCount`)와 간격을 동적으로 계산합니다. 커버 크기를 **‘작게’**로 바꾸면 열 개수가 늘어나 한 화면에 즉시 더 많은 앨범이 표시됩니다.

| 커버 크기 설정값 | 접힌 상태 (`< 600dp`) 열 수 (`spanCount`) | 접힌 상태 1화면 노출 앨범 수 (예상) | 펼친 상태 (`>= 600dp`) 열 수 (`spanCount`) | 펼친 상태 1화면 노출 앨범 수 (예상) | 그리드 셀 간격 (`itemSpacing`) | 화면 좌우 외곽 여백 (`screenPadding`) | 커버 모서리 반경 (`cornerRadius`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **작게 (Small)** | **4열** (셀 폭 약 `78dp`) | **16~20개** | **6열** (셀 폭 약 `104dp`) | **24~30개** | `8dp` | `12dp` | `6dp` |
| **보통 (Medium, 기본)** | **3열** (셀 폭 약 `106dp`) | **9~12개** | **5열** (셀 폭 약 `126dp`) | **15~20개** | `12dp` | `16dp` | `8dp` |
| **크게 (Large)** | **2열** (셀 폭 약 `164dp`) | **4~6개** | **4열** (셀 폭 약 `160dp`) | **8~12개** | `16dp` | `16dp` | `10dp` |

- **구현 규칙**: 커버 `ImageView`는 `ConstraintLayout` 내부에서 `app:layout_constraintDimensionRatio="1:1"`, `android:layout_width="0dp"`, `android:layout_height="0dp"`, `android:scaleType="centerCrop"`으로 고정하여 어떤 열 개수에서도 정사각형 비율이 깨지지 않게 합니다.

---

### 4.4 여백, 행 높이, 모서리 반경, 아이콘·터치 영역 명세

| 구성 요소 | 시각적 크기 (Visual Size) | 최소 터치 영역 (Touch Target) | 내부 여백 / 간격 | 모서리 반경 (`cornerRadius`) | 비고 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **상단 앱바 (Toolbar)** | 높이 `56dp` | 아이콘 버튼 `48dp × 48dp` | 좌우 패딩 `16dp` | `0dp` | 불필요한 2단 서브 툴바 금지 |
| **하단 내비게이션 바 (접힘)** | 높이 `56dp` | 탭당 폭 균등 분할 × 높이 `56dp` | 아이콘 `24dp`, 라벨 `12sp` | `0dp` | 펼친 화면에서는 `GONE` 처리 후 좌측 `NavigationRailView`(`72dp` 폭)로 전환 |
| **하단 미니 플레이어** | 높이 `64dp` (상단 진행바 `2dp` 포함) | 본문 전체 클릭 / 우측 각 버튼 `48dp × 48dp` | 좌측 패딩 `12dp`, 커버 `44dp × 44dp`, 커버-텍스트 간격 `12dp` | 상단 모서리 `12dp` (또는 평면 `0dp` + 상단 `2dp` 진행바) | `RecyclerView` 하단에는 반드시 `120dp`(접힘) / `64dp`(펼침) `clipToPadding="false"` 패딩을 부여해 마지막 행이 가려지지 않게 함 |
| **트랙 / 폴더 / 대기열 리스트 행** | 최소 높이 (`minHeight`) `56dp` (2줄 제목 시 자동 확장) | 행 전체 클릭 / 우측 `[⋮]` 버튼 `48dp × 48dp` | 좌우 패딩 `16dp`, 상하 패딩 `8dp` | `0dp` (클릭 리플만 적용) | 고정 `layout_height="56dp"` 금지 → 반드시 `android:layout_height="wrap_content"` + `android:minHeight="56dp"`로 큰 글자 크기 대응 |
| **설정 리스트 행** | 최소 높이 (`minHeight`) `60dp` | 행 전체 클릭 | 좌우 패딩 `20dp`, 상하 패딩 `10dp` | 섹션 그룹 배경 반경 `16dp` | One UI 스타일의 라운드 그룹 서피스(`#1C1C20`) 적용 |
| **일반 탐색·더보기 아이콘** | **`24dp × 24dp`** | **`48dp × 48dp`** (`padding="12dp"`) | 버튼 간 최소 간격 `4dp` | 리플 반경 `24dp` | 아이콘 이미지 자체를 `48dp`로 키우지 말고, `24dp` 벡터에 `12dp` 패딩을 주어 버튼 비대 현상을 해결할 것 |
| **현재 곡 화면 주요 재생 버튼 (재생/일시정지)** | 원형 배경 **`64dp × 64dp`**, 내부 아이콘 **`32dp × 32dp`** | **`64dp × 64dp`** | 좌우 이전/다음 버튼과 `20dp` 간격 | **`32dp` (완전한 원형)** | `color_accent_primary`(`#6C8CFF`) 배경 + `#0E1324` 아이콘 |
| **현재 곡 화면 이전/다음 곡 버튼** | 아이콘 **`28dp × 28dp`** | **`52dp × 52dp`** (`padding="12dp"`) | 셔플/반복 버튼과 `12dp` 간격 | 리플 반경 `26dp` | 배경 없는 투명 원형 리플 버튼 |
| **현재 곡 화면 보조 버튼 (셔플/반복/대기열)** | 아이콘 **`22dp × 22dp`** | **`48dp × 48dp`** (`padding="13dp"`) | - | 리플 반경 `24dp` | 비활성 시 `#6E6E7A`, 활성 시 `#6C8CFF` 틴트 |

---

## 5. 상호작용과 상태 (Interaction & State Matrix)

### 5.1 미니 플레이어 ↔ 현재 곡 화면 열기·닫기
- **구조**: 메인 `CoordinatorLayout` 내부에 본문 프래그먼트 컨테이너와 함께 `NowPlayingSheetLayout`을 배치하고 `BottomSheetBehavior`를 연결하거나, 별도 전체 화면 모달 프래그먼트로 전환합니다 (`BottomSheetBehavior` 권장).
- **열기**: 하단 미니 플레이어의 커버·제목 영역을 탭하거나 위로 드래그하면 `BottomSheetBehavior.STATE_EXPANDED`로 전환됩니다. 전환 진행률(`slideOffset`: `0.0` → `1.0`)에 따라 미니 플레이어 헤더는 `alpha`가 `1.0` → `0.0`으로 페이드아웃되고(`slideOffset > 0.25`에서 `INVISIBLE`), 전체 재생 화면 본문은 `0.0` → `1.0`으로 페이드인됩니다.
- **닫기**: 상단 `[∨]` 버튼 탭, 시트 상단 영역 아래로 스와이프, 또는 시스템 뒤로가기(`OnBackPressedCallback` 등록: 시트가 `STATE_EXPANDED`일 때 `STATE_COLLAPSED`로 내림)로 닫힙니다.

### 5.2 긴 제목 및 한글 가독성 처리 (요구사항 7)
- 클래식 곡명이나 리마스터 표기(`(2023 Remastered Deluxe Edition)`), 한·영 혼합 긴 제목이 잘려서 구분 불가능해지는 문제를 막기 위해 위치별 규칙을 통일합니다.
  - **현재 곡 화면 & 앨범 상세 헤더**: 기본적으로 `maxLines="2"`, `ellipsize="end"`를 적용하고, 한글 어절 단위 줄바꿈이 자연스럽도록 Android API 33+에서 `android:lineBreakWordStyle="phrase"`(가능한 경우) 또는 기본 줄바꿈을 적용합니다.
  - **트랙 목록 행**: 설정 화면의 ‘긴 제목 표시 방식’ 기본값인 **‘2줄까지 표시’**(`maxLines="2"`, `ellipsize="end"`)를 적용합니다. 행 높이를 `wrap_content` + `minHeight="56dp"`로 두어 2줄이 되어도 아티스트명과 겹치지 않습니다.
  - **미니 플레이어**: 공간이 `64dp`로 고정되어 있으므로 `maxLines="1"`로 두되, 설정에서 마키를 선택한 경우에만 `ellipsize="marquee"`, `marqueeRepeatLimit="marquee_forever"`를 적용하고, 그렇지 않으면 `ellipsize="end"`를 적용합니다.

### 5.3 예외 상태 및 재생 상태별 UI 동작

| 상태 구분 | 발생 조건 | 시각적 표시 및 레이아웃 처리 | 사용자 조작 (Action) |
| :--- | :--- | :--- | :--- |
| **커버 없음 (No Cover Art)** | 태그에 내장 커버가 없거나 `getCoverArt` 응답이 비어 있을 때 | `res/drawable/bg_album_placeholder.xml`(`#242428` 배경 + 중앙 `#6E6E7A` 음표 벡터) 즉시 표시. 깨진 이미지 아이콘 노출 금지 | 일반 앨범과 동일하게 클릭·재생 가능 |
| **초기 및 추가 로딩 (Loading)** | 첫 앨범 목록 로드 또는 무한 스크롤 다음 페이지 요청 중 | 첫 로드는 화면 중앙에 `36dp` 원형 `CircularProgressIndicator` 표시. 무한 스크롤 추가 로드는 `RecyclerView` 최하단 푸터 뷰(`48dp` 높이)에만 소형 스피너 표시 | 스크롤 방해 없음, 중복 API 호출 방지 락(`isLoading`) 유지 |
| **빈 목록 (Empty State)** | 폴더 내 음원이 없거나 검색 결과가 0건일 때 | 중앙에 `48dp` 폴더/검색 벡터 아이콘(`#6E6E7A`) + 1줄 안내 문구(`"표시할 앨범이 없습니다"` 또는 `"일치하는 검색 결과가 없습니다"`) | 검색어 초기화 또는 상위 폴더 이동 버튼 제공 |
| **Tailscale / 서버 연결 실패** | Tailscale VPN 미연결, Navidrome 서버 오프라인, 타임아웃 발생 | 전체 화면을 백지로 비우지 않고, 상단에 고정 상태 배너(`#2C1B1E` 배경 + `#FF6B6B` 아이콘 + `"서버에 연결할 수 없습니다. Tailscale 연결 상태를 확인하세요."` + `[재시도]` 버튼) 표시. 캐시된 목록이 있으면 하단에 유지 | `[재시도]` 탭 시 즉시 `ping` 및 목록 재요청, `[설정]` 탭 시 서버 설정으로 이동 |
| **재생 / 일시정지 / 버퍼링** | `Player.STATE_BUFFERING`, `isPlaying` 상태 변화 | **중요**: 버퍼링 중에도 재생/일시정지 버튼(`64dp`)의 외곽 크기는 절대 변하지 않아야 함. `STATE_BUFFERING`일 때는 재생 버튼 외곽 둘레에 `2dp` 두께의 회전 `CircularProgressIndicator`를 겹쳐 표시하거나 시크바에 불확정(indeterminate) 애니메이션을 표시 | 버퍼링 중에도 탭하면 일시정지(`pause()`) 명령이 즉시 전달되어야 함 |

### 5.4 폴드 전환(접힘 ↔ 펼침) 시 재생 및 스크롤 위치 유지
Galaxy Fold 8을 접거나 펼칠 때 화면 크기(`smallestScreenSize`, `screenSize`, `screenLayout`)가 변경됩니다.
1. **절대 금지**: `AndroidManifest.xml`에서 `android:configChanges="screenSize|smallestScreenSize|screenLayout"`를 선언해 액티비티 재생성을 막아버리면 `res/layout-sw600dp/` 폴더블 전용 레이아웃으로 자동 전환되지 않습니다.
2. **권장 구현**:
   - 액티비티/프래그먼트가 정상적으로 재생성되어 접힌 레이아웃(`< 600dp`)과 펼친 레이아웃(`>= 600dp`) 리소스를 자동으로 로드하게 합니다.
   - **재생 상태 유지**: 재생 엔진(`ExoPlayer`)은 UI와 분리된 `MediaSessionService`에서 동작하므로 화면이 접히거나 펼쳐져도 오디오가 1ms도 끊기지 않습니다. 액티비티는 `onStart()`에서 `MediaController.Builder(context, sessionToken).buildAsync()`로 재바인딩하여 현재 재생 위치와 메타데이터를 즉시 동기화합니다.
   - **스크롤 위치 및 데이터 유지**: 이미 불러온 앨범 목록 리스트와 현재 `offset`은 `ViewModel`에 보관하여 폴드 전환 시 네트워크를 다시 호출하지 않습니다. `GridLayoutManager`의 첫 번째 가시 아이템 인덱스(`findFirstVisibleItemPosition()`)를 `ViewModel` 또는 `onSaveInstanceState`에 저장한 뒤, 열 개수(`spanCount`)가 변경된 새 `GridLayoutManager`에서도 동일한 앨범 아이템 인덱스로 `scrollToPositionWithOffset(firstVisibleItemPosition, 0)`을 호출해 보고 있던 앨범 위치를 유지합니다.
   - **현재 곡 화면 열림 상태 유지**: 현재 곡 바텀시트의 열림 여부(`isNowPlayingExpanded`)와 선택 팝업 상태를 `ViewModel`에 저장하여, 현재 곡 화면을 보다가 기기를 펼쳐도 닫히지 않고 펼친 상태의 2단 현재 곡 화면으로 즉시 이어지게 합니다.

### 5.5 접근성 (TalkBack, 큰 글자 크기, 명암 대비)
- **TalkBack (`contentDescription`)**: 모든 `ImageButton`에 상태 기반 설명을 동적으로 부여합니다.
  - 재생/일시정지 버튼: 재생 중일 때 `"일시정지"`, 일시정지 중일 때 `"재생"`
  - 미니 플레이어 컨테이너: `"현재 재생 중: {곡 제목}, {아티스트}. 두 번 탭하여 재생 화면 열기"`
  - 앨범 그리드 셀: `"{앨범 제목}, {앨범 아티스트}, {연도}"` (커버 `ImageView`는 `importantForAccessibility="no"`로 설정하고 부모 아이템 뷰에서 한 번에 읽도록 병합)
- **큰 시스템 글자 크기 (`fontScale >= 1.3`)**:
  - 모든 리스트 행과 헤더 컨테이너에 고정 높이(`layout_height="56dp"`)를 쓰지 않고 `layout_height="wrap_content"` + `minHeight`를 사용합니다.
  - 앨범 그리드 하단 텍스트 영역도 고정 높이를 피하고, 글자 크기가 커질 때 커버 아래 텍스트 영역이 자연스럽게 늘어나도록 설정합니다.

---

## 6. 시스템 미디어 알림 미표시 및 ‘모두 거부’ 문제 기술 진단 지침

> **핵심 구분**:
> - **앱 내부 재생 화면(미니 플레이어 / 현재 곡 화면)**: 앱 프로세스 내부의 `Activity`/`Fragment`가 `MediaController`를 통해 `MediaSession`의 상태를 구독하여 그리는 **앱 자체 View UI**입니다.
> - **알림 패널의 시스템 미디어 컨트롤(Quick Settings Media Player / 잠금화면 컨트롤)**: Android 시스템 UI(`SystemUI`)가 앱의 `MediaSessionService`가 발행한 `MediaStyle` 포그라운드 알림과 `MediaSession` 토큰을 읽어 시스템 알림 드로어 및 빠른 설정 패널에 렌더링하는 **OS 수준 미디어 카드**입니다.
> - 따라서 앱 내부 재생 화면이 작동하더라도 권한·채널·서비스 설정이 누락되면 시스템 미디어 컨트롤은 전혀 나타나지 않습니다.

### 6.1 “권한만 켜면 해결된다”고 단정하면 안 되는 이유
앱 알림 설정 화면에 **‘모두 거부(All blocked)’**로 표시되고 알림 패널에 미디어 플레이어가 안 뜨는 현상은 단순한 사용자 미허용 외에도 다음 **5가지 복합 원인** 중 하나 이상이 겹쳤을 때 발생합니다.
1. `AndroidManifest.xml`에 `android.permission.POST_NOTIFICATIONS` 선언 자체가 누락되어, Android 13(API 33)+ 기기(Galaxy Fold 8 포함)가 앱의 알림 토글을 기본 차단 상태로 고정한 경우(이 경우 시스템 설정에서 토글 자체가 비활성화되어 사용자가 켤 수도 없음).
2. 앱이 알림 채널(`NotificationChannel`)을 한 번도 생성하지 않았거나, `MediaSessionService`가 `onGetSession()`에서 유효한 `MediaSession`을 반환하지 못해 시스템이 미디어 세션을 인식하지 못한 경우.
3. Android 14(API 34)+ 필수 요건인 `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 권한 및 `<service android:foregroundServiceType="mediaPlayback">` 선언이 누락되어 포그라운드 서비스 승격(`startForeground`)이 실패하거나 무시된 경우.
4. 참고로 [AndroidX Media3 백그라운드 재생 가이드](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.android.com%2Fmedia%2Fmedia3%2Fsession%2Fbackground-playback)에 따르면, Android 13(API 33)+에서는 `MediaSessionService`가 유효한 `MediaSession`과 활성 `Player`(재생 중 또는 준비 완료)를 연결하고 포그라운드 서비스로 정상 진입하면 설령 런타임 `POST_NOTIFICATIONS`가 거부되어도 OS 미디어 컨트롤 영역에 세션이 노출될 수 있는 예외 경로가 있으나, **매니페스트 선언 누락, `DefaultMediaNotificationProvider` 미작동, 또는 서비스 바인딩 방식 오류(`bindService`만 호출하고 재생 시작 시 서비스가 포그라운드로 시작되지 않음)**가 있으면 알림과 미디어 카드 모두 나타나지 않습니다.

### 6.2 개발자 확인용 6단계 기술 진단 체크리스트

구현 담당 에이전트는 UI 작업과 별도의 수정 작업(Track B)으로 아래 6개 항목을 순서대로 점검하고 수정하십시오.

#### 점검 1: `AndroidManifest.xml` 권한 및 서비스 선언 확인
아래 3가지 권한과 `<service>` 속성이 정확히 선언되어 있는지 확인하십시오. 하나라도 빠지면 Galaxy Fold(Android 13/14/15+)에서 포그라운드 미디어 알림이 차단되거나 크래시가 발생합니다.

```xml
<!-- 1. 필수 권한 3종 선언 확인 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<application ...>
    <!-- 2. MediaSessionService 선언 확인 -->
    <service
        android:name=".playback.PlaybackService"
        android:exported="true"
        android:foregroundServiceType="mediaPlayback">
        <intent-filter>
            <action android:name="androidx.media3.session.MediaSessionService" />
            <!-- 하위 호환이 필요한 경우 MediaBrowserService 액션 병기 가능 -->
            <action android:name="android.media.browse.MediaBrowserService" />
        </intent-filter>
    </service>
</application>
```

#### 점검 2: Android 13(API 33)+ 런타임 `POST_NOTIFICATIONS` 권한 요청 흐름 확인
- `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` 환경에서 `ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)`를 확인하고, 미허용 시 `ActivityResultContracts.RequestPermission()`으로 최초 1회(또는 첫 곡 재생 시점) 런타임 권한 팝업을 호출하는 코드가 있는지 확인하십시오.
- 만약 사용자가 ‘다시 묻지 않음’ 상태로 거부했다면, 설정 화면의 `서버 및 재생` 섹션에 `"미디어 알림 권한이 꺼져 있습니다 [권한 설정 열기]"` 안내 행을 노출하고 `Settings.ACTION_APP_NOTIFICATION_SETTINGS` 인텐트로 바로 이동할 수 있게 연결하십시오.

#### 점검 3: 알림 채널(`NotificationChannel`) 및 `MediaNotification.Provider` 설정 확인
- [Media3 MediaSessionService](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.android.com%2Fmedia%2Fmedia3%2Fsession%2Fbackground-playback)는 기본적으로 `DefaultMediaNotificationProvider`를 사용하여 `default_channel_id` 채널을 자동 생성합니다.
- 만약 개발 코드에서 `setMediaNotificationProvider(...)`를 커스텀 구현했으면서 정작 `NotificationManager.createNotificationChannel(...)`을 호출하지 않았다면 알림이 영구 드롭됩니다.
- `PlaybackService.onCreate()`에서 아래와 같이 `DefaultMediaNotificationProvider`를 명시적으로 등록하고 소형 단색 벡터 아이콘(알파 채널만 있는 흰색 단색 아이콘)을 지정하십시오. (일반 컬러 PNG를 알림 `smallIcon`으로 넣으면 흰색 네모로 깨지거나 표시가 거부될 수 있습니다.)

```java
DefaultMediaNotificationProvider notificationProvider =
    new DefaultMediaNotificationProvider.Builder(this)
        .setChannelId("navifold_playback_channel")
        .setChannelName(R.string.notification_channel_playback)
        .build();
notificationProvider.setSmallIcon(R.drawable.ic_stat_music_note); // 반드시 단색 알파 벡터
setMediaNotificationProvider(notificationProvider);
```

#### 점검 4: `MediaSessionService.onGetSession()` 및 `MediaController` 연결 방식 확인
- **흔한 버그 패턴**: 액티비티에서 `ExoPlayer`를 직접 생성해 재생하거나, `PlaybackService`에 로컬 `Binder`로만 연결하여 재생하면 시스템 `MediaSessionService` 생명주기가 트리거되지 않습니다.
- **필수 확인**:
  1. `ExoPlayer`와 `MediaSession` 인스턴스는 반드시 `PlaybackService.onCreate()` 내부에서 생성되어야 합니다.
  2. `PlaybackService.onGetSession(MediaSession.ControllerInfo controllerInfo)`가 `null`이 아닌 `mediaSession` 인스턴스를 반환하는지 확인하십시오.
  3. 액티비티/프래그먼트는 `new SessionToken(context, new ComponentName(context, PlaybackService.class))`를 사용해 `MediaController.Builder(context, sessionToken).buildAsync()`로 연결해야 합니다. 이렇게 연결된 `MediaController`를 통해 `controller.setMediaItems(...)`, `controller.prepare()`, `controller.play()`를 호출해야 Media3가 자동으로 `startForegroundService()`를 호출하고 시스템 미디어 알림을 게시합니다.

#### 점검 5: `MediaMetadata` 필수 필드 주입 여부 확인
- Navidrome 트랙을 `MediaItem`으로 변환할 때 `MediaMetadata.Builder`에 `setTitle(...)`, `setArtist(...)`, `setAlbumTitle(...)`, `setArtworkUri(...)`, `setIsPlayable(true)`, `setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)`이 채워져 있는지 확인하십시오. 메타데이터가 비어 있으면 일부 Android 버전 및 One UI 미디어 패널에서 빈 카드로 무시될 수 있습니다.
- Tailscale 내부 IP(`http://100.x.y.z:4533/rest/getCoverArt...`)에서 커버 이미지를 불러올 때 SystemUI 프로세스가 직접 해당 URL에 접근하지 못할 경우를 대비해, 커스텀 `BitmapLoader`(`CacheDataSource` 또는 앱의 HTTP 클라이언트를 공유하는 `DataSourceBitmapLoader`)를 `MediaSession.Builder.setBitmapLoader(...)`에 연결했는지 확인하십시오.

#### 점검 6: 기기 및 ADB 로그캣 검증 명령
수정 후 실제 휴대폰을 연결하여 아래 명령으로 시스템 등록 상태를 검증하십시오.
- `adb shell dumpsys media_session`: `PlaybackService`의 세션이 `active=true` 상태이며 `PlaybackState`가 `STATE_PLAYING(3)`인지 확인
- `adb shell dumpsys notification --noredact`: `navifold_playback_channel` 채널 생성 여부 및 `MediaStyle` 알림 게시 여부 확인

---

## 7. 개발 순서와 검수 기준 (Prioritized Roadmap & Definition of Done)

디자인 개편 작업(**Track A**)과 시스템 미디어 알림 복구 작업(**Track B**)을 분리하여 충돌 없이 순차 적용하십시오.

### 7.1 Track B: 시스템 미디어 알림 및 세션 복구 (최우선 독립 작업)
| 단계 | 작업 항목 | 수정 대상 파일 (예시) | 완료 및 검수 기준 (Definition of Done) |
| :--- | :--- | :--- | :--- |
| **B-1** | 매니페스트 권한·서비스 타입 선언 및 런타임 알림 권한 요청 추가 | `AndroidManifest.xml`, `MainActivity.java` | 앱 정보 > 알림 설정 진입 시 ‘모두 거부’가 해제 가능하며 `navifold_playback_channel` 알림 카테고리가 노출됨 |
| **B-2** | `PlaybackService`의 `MediaSession`·`DefaultMediaNotificationProvider`·`MediaController` 표준화 | `PlaybackService.java`, 플레이어 연동 클래스 | 곡 재생 시작 후 홈 화면으로 나가도 음악이 끊기지 않고, One UI 상단 알림 패널 및 잠금화면 미디어 컨트롤에 곡 제목·아티스트·커버·시크바·재생/다음 버튼이 정상 표시 및 작동함 |

### 7.2 Track A: UI 구조 및 디자인 시스템 개편 (단계별 순차 작업)
| 단계 | 작업 항목 | 수정 대상 | 완료 및 검수 기준 (Definition of Done) |
| :--- | :--- | :--- | :--- |
| **A-1 (기반 정비)** | 폰트·색상·치수 토큰 정리 및 아이콘 전면 교체 | `res/values/colors.xml`, `dimens.xml`, `styles.xml`, `res/drawable/ic_*.xml` | 1) 프로젝트 내 Poppins `.ttf` 참조가 0건이고 한글·영문 텍스트 베이스라인이 일치함<br>2) Sonixd PNG 및 Font Awesome 참조가 0건이고 모든 아이콘이 `24dp`(재생 메인 `32dp`) `VectorDrawable`로 교체됨<br>3) 모든 아이콘 버튼의 터치 영역이 최소 `48dp × 48dp` 이상임 |
| **A-2 (폴더블 골격)** | 접힘(`< 600dp`) / 펼침(`>= 600dp`) 반응형 내비게이션 및 미니 플레이어 고정 구조 구현 | `res/layout/activity_main.xml`, `res/layout-sw600dp/activity_main.xml` | 1) 접힌 화면에서는 하단 `BottomNavigationView`(`56dp`), 펼친 화면에서는 좌측 `NavigationRailView`(`72dp`)가 표시됨<br>2) 미니 플레이어(`64dp`) 탭 시 현재 곡 화면이 열리고, 폴드 접힘↔펼침 전환 시 재생 끊김 없이 스크롤 위치(동일 앨범 인덱스)가 유지됨 |
| **A-3 (메인 탐색)** | 앨범 목록 연속 스크롤·동적 그리드(`spanCount`) 및 폴더 탐색·앨범 상세 개편 | `AlbumListFragment.java`, `FolderFragment.java`, `AlbumDetailFragment.java` | 1) 메인 화면에 정렬·커버 크기·재생 설정 컨트롤이 0개 노출됨<br>2) 페이지 전환 버튼 없이 끝까지 연속 스크롤됨<br>3) 설정에서 커버 크기를 ‘작게/보통/크게’로 변경 시 접힌 화면 열 수가 `4열 / 3열 / 2열`, 펼친 화면 열 수가 `6열 / 5열 / 4열`로 즉시 변경되어 ‘작게’에서 더 많은 앨범이 보임 |
| **A-4 (재생·설정)** | 현재 곡 화면(한 손 조작 + 기술 메타데이터), 재생 대기열, 설정 선택 팝업 구현 및 미구현 기능 제거 | `NowPlayingFragment.java`, `QueueBottomSheet.java`, `SettingsFragment.java` | 1) 현재 곡 화면에 커버, 제목(2줄 지원), 아티스트, 앨범, 연도, 상세 메타데이터(`FLAC · kbps · kHz`)가 모두 표시되고 재생 버튼이 하단 엄지 영역에 위치함<br>2) 모든 설정 항목이 `MaterialAlertDialogBuilder` 단일 선택 팝업으로 동작함<br>3) 작동하지 않는 AI 추천·자동 스킵 버튼이 메인/재생 화면에 일절 노출되지 않음<br>4) 시스템 글자 크기 최대 확대 시 텍스트 잘림이나 뷰 겹침이 0건임 |
