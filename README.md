# Earpiece Radar

폰 지자기 센서로 초소형 마그네틱 이어피스(네오디뮴 자석)를 탐지할 수 있는지 검증하는 **실험·측정용** Android 앱.
출시용이 아니라 거리별 Δ(µT)를 기록하고 CSV 로 뽑는 도구다.

## 빌드

```bash
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # 순수 Kotlin 신호 처리 단위 테스트
```

Kotlin 2.0 / Compose(Material3) / minSdk 26 / targetSdk 35. DI·차트 라이브러리 없음.

## 구조

```
com.dogdduddy.earpieceradar
├─ signal/      Android 의존성 없는 순수 Kotlin (단위 테스트 대상)
│   MagSample, BaselineCalibrator(B0, σ), Metrics(Δ, z), AcRmsEstimator,
│   SampleRateEstimator, Detector(없음/의심/탐지 + 디바운스), TrialStats, MetricsEngine
├─ sensor/      MagneticFieldSource 인터페이스 (2단계 BLE 소스용) + AndroidMagneticFieldSource
├─ core/        SignalHub: 센서 flow 공유, 지표 계산, 기준값 보관
├─ data/        Settings(SharedPreferences), Trial 모델, TrialRepository(내부 파일), CsvExporter(FileProvider)
└─ ui/          monitor / record / results / locate / settings, components/LineChart(Canvas)
```

## 지표 정의

| 지표 | 정의 |
|---|---|
| B0 | 캘리브레이션 구간(기본 3초) 벡터 평균 |
| σ | 벡터 편차 RMS = √(σx²+σy²+σz²). 자석 없을 때 Δ 의 대표값이 σ 근처가 되도록 정의 |
| Δ | \|B − B0\| 벡터 차의 크기(µT). 크기끼리 빼지 않아 방향 변화도 잡힘 |
| z | Δ / σ |
| AC RMS | 0.5초 롤링 윈도우에서 이동평균을 뺀(high-pass) 벡터 편차의 RMS. 넥루프 교류 자기장용 |
| 탐지 | (Δ ≥ Δthr **그리고** z ≥ zthr) 또는 AC ≥ ACthr → 탐지, 하나만 넘으면 의심. 연속 N 샘플 디바운스 |

기본 임계값: Δ 3 µT, z 5, AC 1 µT, 디바운스 5 샘플. 설정 탭에서 조절.

## 센서

- `TYPE_MAGNETIC_FIELD_UNCALIBRATED` 우선, 없으면 `TYPE_MAGNETIC_FIELD` 폴백. 모니터 탭 하단에 어느 것을 쓰는지 표시.
- `SENSOR_DELAY_FASTEST` + `HIGH_SAMPLING_RATE_SENSORS` 권한. 실측 Hz 는 `event.timestamp` 기준으로 계산.
- 측정 중 화면 꺼짐 방지, 햅틱 비활성(진동 모터 자석 간섭 방지).

## 내일 측정 절차 (자석 도착 후)

1. **위치 탭**: 영점 → 자석을 폰 위에서 천천히 움직여 최대 Δ 가 튀는 지점(센서 위치)을 찾고 테이프로 표시.
2. **기록 탭**: 자석 종류·거리·방향·위치 선택. "기준값 새로 잡기" 켠 상태로 시작하면
   영점 3초(자석 멀리) → 카운트다운 3초(자석 놓기) → 녹화 5초.
3. 같은 조건을 **"같은 설정으로 다시"** 로 3회 이상 반복. 거리 1/2/3/5/10 cm 를 자석별로.
4. **대조군**: 자석 "없음" 으로 같은 절차를 몇 번 기록해 노이즈 바닥을 잡는다.
5. **결과 탭**: 자석별 거리 vs 평균Δ 그래프(log Y). r⁻³ 참고선과 평행하면 쌍극자 감쇠로 설명됨.
6. 요약 CSV / raw CSV 를 공유 시트로 내보내기.

## 저장 포맷

- `files/trials/trials.json` — 트라이얼 요약(JSON 배열)
- `files/trials/raw/<id>.csv` — `trial_id,idx,timestamp_ns,t_rel_ms,x_ut,y_ut,z_ut,mag_ut,accuracy,delta_ut,z_score`
- 요약 CSV 컬럼: 조건, B0, σ, 샘플 수, Hz, 평균Δ, 최대Δ, Δ표준편차, 평균 z, AC RMS, 평균 |B|

## 범위 밖

스토어 배포, 서버, 로그인, BLE 스캔(2단계: `MagneticFieldSource` 인터페이스만 분리해 둠).

## APK 다운로드

푸시마다 GitHub Actions 가 디버그 APK 를 빌드해 브랜치별 프리릴리스 `apk-<branch>` 에 올린다.
폰 브라우저에서 아래 주소로 바로 받을 수 있다(최신 커밋으로 덮어씀).

- https://github.com/dogdduddy/earpiece-radar/releases
- 고정 URL 예: `https://github.com/dogdduddy/earpiece-radar/releases/download/apk-claude-beautiful-knuth-1l9t2t/earpiece-radar-debug.apk`

디버그 서명이라 설치 시 "출처를 알 수 없는 앱" 허용이 필요하다.
