import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // platformType: IU = IntelliJ IDEA Ultimate (WebStorm JS plugin 포함)
        // 순수 WebStorm은 "WS"이지만 로컬 개발 편의상 IU 사용
        intellijIdeaUltimate(providers.gradleProperty("platformVersion").get())
        bundledPlugin("JavaScript") // JS/TS PSI 지원
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }

    // OkHttp: 실제 LLM API 호출 시 사용 (Mock 단계에선 미사용)
    // TODO: 실제 API 연동 시 주석 해제
    // implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "243.*"
        }
    }
    signing {
        // TODO: 실제 배포 시 인증서 경로 설정
    }
    publishing {
        // TODO: JetBrains Marketplace 토큰 설정
    }
}
