# Liquid Glass Launcher (Jetpack Compose, Android 15)

Build: otevřete složku v Android Studiu (Ladybug+), JDK 17, Gradle 8.11+. minSdk 29, target/compileSdk 35.
Po instalaci nastavte jako výchozí domovskou aplikaci. Pro plnou funkci:
- Oznámení: Nastavení → Přístup k oznámením → povolit launcher (panel nabídne tlačítko).
- Jas / rotace: povolit „Úpravy systémových nastavení" (otevře se při prvním použití).
- Nerušit: povolit přístup k zásadám Nerušit.

Poznámky:
- Wi-Fi, Bluetooth, Letadlo otevírají systémový panel (Android 10+ zakazuje běžným aplikacím je přepínat).
- Průhledné sklo používá okno s wallpaperem; blur pozadí u panelů = Window.setBackgroundBlurRadius (API 31+).
- Specular okraje: AGSL shader (API 33+), starší zařízení mají gradientový fallback.
- Architektura je inspirovaná Lawnchair/Launcher3 (AppWidgetHost, LauncherApps, mřížka, long-press v intercept fázi), ale kód je původní; Lawnchair je GPLv3.
- Projekt nebyl v tomto prostředí kompilován (bez sítě a Android SDK) – první build může vyžadovat drobné opravy.
