# Nebula Client (Minecraft 26.2, Fabric)

Lekki klient klienckiego moda w stylu starego Feathera: HUD z modułami, menu pod **Prawym Shiftem**, edytor pozycji elementów, motywy kolorystyczne i animacje. Bez cheatów, nic nie zmienia ruchu, walki ani tego, co widzisz w świecie.

## Co jest w środku

- **ClickGUI (Prawy Shift)**: panele z kategoriami, przełączniki modułów (LPM), ustawienia (PPM), suwaki, przeciąganie paneli, płynne animacje.
- **Edytor HUD** (przycisk na dole menu): przeciągasz elementy myszką, krawędzie i środek ekranu przyciągają.
- **Moduły HUD**: FPS, CPS (LPM / PPM), Ping, Koordynaty + kierunek, Zegar, Klawisze (WASD, spacja, myszka), Pancerz (wytrzymałość), Efekty mikstur.
- **Wygląd**: 5 motywów (Fiolet, Cyjan, Róż, Zieleń, Pomarańcz), przezroczystość tła, animacje on/off, znak wodny.
- **Config** zapisuje się sam do `.minecraft/config/nebula.json` (włączone moduły, pozycje, ustawienia).

## Wymagania

- JDK **25** (Minecraft 26.x wymaga Javy 25), np. Temurin 25
- IntelliJ IDEA (lub inne IDE z Gradle)
- Fabric Loader 0.19.3+ i Fabric API 0.152.x+ dla 26.2

## Budowanie

**Wariant A (zalecany): oficjalny szablon**
1. Wejdź na https://fabricmc.net/develop/template i wygeneruj projekt dla 26.2.
2. Skopiuj z tego projektu folder `src/` (z `fabric.mod.json` i `assets/`) do wygenerowanego projektu, zastępując jego `src/`.
3. W `build.gradle` / `gradle.properties` zostaw wersje z szablonu (są zawsze aktualne).

**Wariant B: moje pliki Gradle**
1. W tym folderze uruchom `gradle wrapper --gradle-version 9.5.1` (potrzebny zainstalowany Gradle), albo skopiuj `gradlew`, `gradlew.bat` i `gradle/` z szablonu.
2. Jeśli Gradle nie znajdzie Loom `1.17-SNAPSHOT`, podmień `loom_version` na tę z szablonu.

Potem:

```
./gradlew runClient    # odpala Minecrafta z modem do testów
./gradlew build        # gotowy plik: build/libs/nebula-client-0.1.0.jar
```

(Plik `-sources.jar` ignoruj.)

## Instalacja i optymalizacja

Najprościej przez **Modrinth App** albo **Prism Launcher**: utwórz profil Fabric 26.2 i wrzuć do niego:

- `fabric-api` (wymagane)
- `nebula-client-0.1.0.jar`
- mody optymalizacyjne, które mają wersję na 26.2: zwykle **Sodium**, **Lithium**, **FerriteCore**, **ImmediatelyFast**, **EntityCulling**, **ModernFix**

Sprawdź na Modrinth z filtrem „26.2” i Fabric, czy dany mod już jest. Jeśli któregoś brak, pomiń go. Własnej optymalizacji nie piszę celowo, bo te mody robią to lepiej i są sprawdzone.

## Sterowanie

| Akcja | Jak |
|---|---|
| Otwórz / zamknij menu | Prawy Shift (zmiana w Sterowaniu → Nebula Client) |
| Włącz / wyłącz moduł | LPM na module |
| Ustawienia modułu | PPM na module (lub LPM na „Wygląd”) |
| Suwak | Przeciągnij |
| Przesuń panel | Przeciągnij nagłówek |
| Edytor HUD | Przycisk „Edytor HUD” na dole menu |

## Serwery

Wszystkie moduły są tylko wizualne, ale każdy serwer ma własny regulamin i antycheat. Przed wejściem z klientem na **Minestar** (albo inny serwer) sprawdź jego regulamin dotyczący modyfikacji.

## Gdy coś się nie kompiluje

Ten kod powstał bez dostępu do prawdziwego Minecrafta 26.2 (sprawdzony tylko na atrapach klas), więc przy pierwszym buildzie mogą wyskoczyć drobne różnice w nazwach API. Wszystko, co dotyka Minecrafta, jest w kilku miejscach:

- `ui/Gfx.java`: całe rysowanie (fill, text, outline, gradient). Jeśli Mojang zmienił nazwę metody, poprawiasz ją tylko tu.
- `ui/ClickGuiScreen.java`, `ui/HudEditorScreen.java`: sygnatury `extractRenderState`, `mouseClicked`, `mouseDragged`, `mouseReleased`, `keyPressed`.
- `NebulaClient.java`: importy Fabric API (keymapping, HUD) i `Identifier`.
- `module/impl/*`: gettery gracza (pozycja, pancerz, efekty, ping).

Wklej mi błędy z konsoli, a poprawię.

## Struktura

```
src/main/java/dev/nebula/client/
  NebulaClient.java      wejście: klawisz, HUD, zapis configu
  config/                zapis i odczyt nebula.json
  hud/HudManager.java    rysowanie elementów HUD
  module/                Module, HudModule, ModuleManager
  module/impl/           konkretne moduły
  setting/               Bool / Number / Mode
  ui/                    Gfx, Theme, ClickGuiScreen, HudEditorScreen
```

Nowy moduł HUD to jedna klasa: dziedziczysz po `HudModule`, implementujesz `draw(...)` i dopisujesz ją w `ModuleManager`.
