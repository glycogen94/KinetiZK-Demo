# KinetiZK Android Demo Project Structure

```
KinetiZK-Demo/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/kinetizk/demo/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── SplashActivity.kt
│   │   │   │   ├── KinetiZKHelper.kt
│   │   │   │   ├── SensorCollector.kt
│   │   │   │   └── KinetiZKKeys.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_splash.xml
│   │   │   │   │   └── activity_main.xml
│   │   │   │   ├── values/
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   └── drawable/
│   │   │   │       └── ic_settings.xml
│   │   │   └── AndroidManifest.xml
│   │   └── androidTest/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## Key Files:

1. **MainActivity.kt** - 메인 화면, 터치 감지 및 증명 생성/검증
2. **SplashActivity.kt** - 시작 화면
3. **KinetiZKHelper.kt** - SDK 래퍼 클래스
4. **SensorCollector.kt** - 센서 데이터 수집 관리
5. **KinetiZKKeys.kt** - Base64 인코딩된 키 저장 (production용)