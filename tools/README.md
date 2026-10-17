# 로컬 Navidrome 테스트 서버

Python 3.10 이상과 `ffmpeg`가 필요합니다. Python 외부 패키지는 사용하지 않습니다.

```bash
python3 tools/mock_navidrome.py
```

`127.0.0.1:18765`에서 실행합니다. Android 에뮬레이터에서는 `http://10.0.2.2:18765`로 연결하세요. 실제 기기는 다음처럼 USB 포트를 연결한 뒤 앱에 `http://127.0.0.1:18765`를 입력합니다.

```bash
adb reverse tcp:18765 tcp:18765
```

사용자 이름과 비밀번호는 모두 `demo`입니다. 합성 테스트 계정이며 실제 계정을 입력하지 마세요.

앨범 235개·곡 705개, 무늬가 있는 생성 표지, 120초 무음 WAV와 192/320 kbps MP3를 제공합니다. MP3는 최초 실행 시 `ffmpeg`로 `/tmp/navifold-fixture-cbr*.mp3`에 생성합니다. 변환 스트림은 길이 헤더·탐색 인덱스를 제공하지 않아 재생 시간 처리도 확인할 수 있습니다.

검색·페이지 오프셋·토큰 인증·앨범 정렬·즐겨찾기·플레이리스트의 제한된 응답을 구현합니다. 실제 Navidrome 전체 API와 동일하지 않으며 종료하면 메모리의 변경 내용은 사라집니다. `Ctrl+C`로 종료합니다.

QA 전용 receiver를 사용하는 검증은 `assembleQa`로 별도 패키지를 빌드하세요. 일반 debug/release에는 QA receiver가 포함되지 않습니다. QA APK는 일상용으로 배포하지 마세요.

검증 후 USB 포트 연결을 해제합니다.

```bash
adb reverse --remove tcp:18765
```
