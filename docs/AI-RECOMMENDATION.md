# v0.7.7 구현 업데이트
자동 큐 보충, 감상 기록 기반 자동 넘기기, SQLite 태그/취향 DB, 장르·레이블·가수·앨범 검색, 선택적 MusicBrainz 보완을 구현했습니다. 상세 기준과 검증은 ../BUILD-STATUS.md를 확인하세요. 아래는 이전 조사 자료이며 Gorse/ListenBrainz 연결은 구현하지 않았습니다.

# 구현 현황 업데이트 (2026-10-17)
사용자는 ChatGPT 플랜 로그인을 선택했습니다. v0.7.6에 OAuth PKCE 로그인, 계정 모델 조회, 후보 40곡 기반 추천 결과 확인 및 대기열 추가를 구현했습니다. 실제 계정 로그인과 추천은 사용자 검증이 필요합니다. 아래의 Gorse·ListenBrainz 비교 및 자동 넘기기/큐 보충 설계는 후속 검토 자료이며 구현 완료를 의미하지 않습니다.

# AI 자동 넘기기·추천 재생 설계안

2026-10-17 공식 자료 확인. 이번 0.7.6에는 UI 수정과 SONG만 구현했으며 AI 서버·계정 연동·추론 호출은 하지 않았습니다.

## 추천 방향
무료 기반 추천을 먼저 만들고, 필요할 때만 GPT/Claude로 후보의 순서를 정하는 구성을 권합니다. 추천 데이터는 기본적으로 보유한 Navidrome 라이브러리의 곡 ID·앨범·아티스트·장르·즐겨찾기·수동 스킵·완주 기록입니다. 음원 파일을 LLM으로 보내지 않습니다.

| 방식 | 용도 | 비용·조건 |
| --- | --- | --- |
| Gorse 자체 호스팅 | 청취 피드백과 곡 메타데이터로 내 라이브러리 추천 | Apache 2 오픈소스, REST API. 고전 추천기를 사용하면 LLM 토큰 비용 없음. PC 자원·DB 운영은 필요 |
| ListenBrainz | 청취 기록 기반 음악 추천 API | recording MBID를 반환. Navidrome 곡 ID와 대응 필요. 계정에 추천이 아직 생성되지 않으면 결과가 없을 수 있음 |
| ChatGPT 플랜 사용 연동 | 로컬/오픈소스 앱에서 로그인 후 후보 재정렬 | 공식 Sign in with ChatGPT의 별도 권한 흐름. 사용 계정의 지원 모델·권한·한도를 확인해야 함. 무료 플랜 이용 가능 여부와 무제한 사용을 보장하지 않음 |
| 일반 OpenAI API 키 | 작은 모델로 후보 재정렬 | API 토큰 과금, 실제 비용은 모델·입출력·추론 토큰에 따라 달라짐 |
| Claude API | 후보 재정렬 | 현재 Max/Team의 API 크레딧 연동 경로 있음. Max 5x $100, Max 20x $200 월 크레딧. Free/Pro는 이 혜택 대상이 아님. 크레딧 신청·Console 계정 연결 필요 |

Gorse는 음악 전용 데이터가 자동 제공되는 서비스가 아닙니다. 곡을 item으로, 재생 반응을 feedback으로 넣고 추천 결과를 받아야 합니다. 한 사용자와 청취 기록이 적은 초기에는 취향 학습이 충분하지 않으므로 장르·아티스트 등 내용 기반 후보와 최근 중복 방지가 필요합니다. ListenBrainz 결과도 보유 곡과 교집합을 구한 뒤 사용해야 실제 재생할 수 있습니다.

## 토큰을 적게 쓰는 처리
1. PC의 로컬 추천기가 라이브러리에서 30~60개 후보를 먼저 선택합니다.
2. 요청에는 짧은 로컬 ID, 아티스트·장르·연도 등 필요한 태그, 최근 청취 요약만 보냅니다. 전체 라이브러리·전체 대화·음원은 보내지 않습니다.
3. LLM은 후보 안에서 10~20개 ID의 순서만 JSON으로 반환하도록 요청합니다. 설명 문장은 요구하지 않습니다.
4. 앱은 받은 ID가 실제 후보인지 검사하고 중복을 제거해 대기열로 캐시합니다. 다음 곡마다 호출하지 않습니다.
5. 추천 대기열이 줄어들 때만 다음 묶음을 받습니다. 통신/쿼터 문제에는 로컬 추천 또는 기존 큐로 이어집니다.

목표 예시는 한 번에 입력 약 1,500~3,000토큰, 출력 약 80~200토큰이며 실제 측정치가 아닙니다. 메타데이터와 모델에 따라 달라집니다. 공식 표의 gpt-6-luna 표준 짧은 컨텍스트 요율(입력 $0.10/1M, 출력 $0.50/1M)을 예로 들면 입력 2,000·출력 100토큰은 약 $0.00025/회입니다. 추가 추론·도구·서버 비용을 제외한 계산이며 특정 모델 품질이나 계정 이용 가능성을 보장하지 않습니다.

ChatGPT 플랜 OAuth 연동은 일반 API 키 경로와 다릅니다. 공식 프리뷰는 Responses에 store=false, stream=true를 요구하며 max_output_tokens 등 일부 필드는 허용하지 않습니다. 따라서 API 키 경로의 파라미터를 그대로 복사하지 않고, 플랜 경로는 짧은 반환 형식 요청·묶음 호출 횟수 제한·사용량 관찰로 설계해야 합니다. 계정의 모델 목록을 조회한 후 이용 가능한 모델을 선택합니다. 웹 로그인 쿠키나 비공식 backend-api를 사용하지 않습니다.

## 자동 넘기기와 학습
- 추천 자동 재생과 현재 곡 자동 스킵은 각각 꺼짐/켜짐을 설정합니다. 기본은 꺼짐입니다.
- 사용자가 누른 다음 곡, 자동 스킵, 탐색, 네트워크 오류를 구분합니다. 앱이 스스로 넘긴 곡을 다시 부정 학습 데이터로 넣지 않습니다.
- 수동 스킵은 약한 신호, 즐겨찾기는 강한 신호로 봅니다. 청취 비율은 실제 재생한 시간으로 계산합니다.
- 곡당 반복 스킵 금지, 연속 자동 스킵 제한, 이미 재생 중인 곡을 뒤늦게 도착한 추천으로 바꾸지 않는 규칙이 필요합니다.
- 텍스트 모델은 음원을 듣지 않습니다. 태그만으로 BPM·음색·에너지를 알아냈다고 표시하지 않습니다. 실제 소리 기반 추천은 별도 로컬 오디오 특징 추출 단계가 필요합니다.
- 일반 앨범 재생 큐는 건드리지 않습니다. 별도의 추천 모드에서만 적용하며 앨범 순서를 지키는 모드도 분리합니다.

## 설치 및 계정 경계
추천 서버는 Navidrome PC 옆의 별도 서비스로 두고 Tailscale로 접근하는 방향입니다. 서버 설치, 외부 계정 가입, API 키 저장, 음악 청취 기록 외부 제출은 아직 하지 않았습니다. API 비밀 키는 APK에 내장하지 않고 서버에 둡니다. 플랜 OAuth는 공식 등록·사용자 동의와 안전한 토큰 저장/갱신을 별도로 구현해야 합니다.

## 공식 참고
- [Gorse](https://gorse.io/) · [소스와 REST 예시](https://github.com/gorse-io/gorse)
- [ListenBrainz recommendation API](https://listenbrainz.readthedocs.io/en/latest/users/api/recommendation.html)
- [OpenAI ChatGPT plan usage](https://developers.openai.com/siwc/token-sharing-open-source)
- [OpenAI 프리뷰 제한](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)
- [OpenAI 모델 목록·추론](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)
- [OpenAI API 요금](https://developers.openai.com/api/docs/pricing)
- [Claude Max/Team API 크레딧](https://platform.claude.com/docs/en/about-claude/api-credits-for-subscribers)
