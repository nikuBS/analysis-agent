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
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }

    // OpenAI API 호출용
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "241"
            untilBuild = "253.*"
        }
    }
    signing {
        // TODO: 실제 배포 시 인증서 경로 설정
    }
    publishing {
        // TODO: JetBrains Marketplace 토큰 설정
    }
}

tasks.runIde {
    // 샌드박스 IDE JVM 힙 설정
    jvmArgs("-Xmx2048m", "-Xms512m")
    // macOS에서 IDE 창이 포커스 되도록
    systemProperty("idea.no.launcher", "true")
}

// MVP 단계에서 불필요한 태스크 스킵 (빌드 속도 향상)
tasks.buildSearchableOptions {
    enabled = false
}
