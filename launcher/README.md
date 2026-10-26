# Nebula Launcher

Własny launcher Minecrafta (Windows): wybór wersji, kilka instalacji z własną ilością RAM, konta Microsoft, motywy (ciemny, jasny, szary, neonowy) i gotowa paczka modów z **Nebula Client**.

## Pobieranie

Gotowy plik `NebulaLauncher-0.1.0.exe` jest w [wydaniu nebula-latest](https://github.com/bumbarski/nebula_client/releases/tag/nebula-latest). To wersja przenośna: nie trzeba nic instalować, wystarczy uruchomić. Windows SmartScreen może ostrzec, bo plik nie jest podpisany certyfikatem. „Więcej informacji → Uruchom mimo to”.

Ten sam plik budują się automatycznie przy każdej zmianie w repo (zakładka Actions).

## Jak używać

1. **Konta** → dodaj konto Microsoft (kod w przeglądarce). Wymaga jednorazowej konfiguracji, patrz [Logowanie Microsoft](#logowanie-microsoft).
2. **Graj** → wybierz instalację i kliknij GRAJ. Pierwszy start pobiera Javę, biblioteki i pliki gry, więc trwa kilka minut.
3. **Instalacje** → zmiana wersji Minecrafta, pamięci RAM (np. 8 GB), listy modów, ścieżki Javy i argumentów JVM.
4. **Ustawienia** → motyw i domyślna pamięć dla nowych instalacji.

### Paczka modów

Domyślna instalacja „Nebula (26.2)” ma: **Fabric API**, **Sodium**, **Lithium**, **FerriteCore**, **In-Game Account Switcher** i **Nebula Client** (menu pod Prawym Shiftem). Opcjonalnie: ImmediatelyFast, Entity Culling. Mody są pobierane z Modrinth w najnowszej wersji zgodnej z wybraną wersją gry, a launcher zmienia tylko pliki, które sam dodał, więc Twoje własne mody w folderze `mods` zostają. Mod, który nie ma jeszcze wersji na dany Minecraft, jest pomijany z ostrzeżeniem (przy 26.2 brakuje ModernFix).

## Logowanie Microsoft

Mojang wpuszcza do logowania tylko aplikacje, które zatwierdził, więc **launcher potrzebuje własnego Client ID**. To jednorazowa konfiguracja:

1. Wejdź na [portal.azure.com](https://portal.azure.com) → **Microsoft Entra ID** → **App registrations** → **New registration**.
2. Nazwa dowolna (np. „Nebula Launcher”). W „Supported account types” wybierz **Personal Microsoft accounts only**. Pole „Redirect URI” zostaw puste.
3. Wejdź w **Authentication** → **Advanced settings** → **Allow public client flows** ustaw na **Yes** → Save.
4. Skopiuj **Application (client) ID** ze strony Overview (to nie jest sekret).
5. Złóż do Mojang wniosek o dopuszczenie aplikacji (formularz z nazwą aplikacji, Client ID, Tenant ID i krótkim uzasadnieniem). Aktualny adres formularza sprawdź w dokumentacji Minecrafta, bo może się zmieniać. Zatwierdzenie trwa zwykle około tygodnia. Do tego czasu logowanie kończy się błędem „Invalid app registration”.
6. Po zatwierdzeniu wklej Client ID w launcherze (**Konta** lub **Ustawienia**) i dodaj konto.

Do czasu zatwierdzenia launcher **nie uruchomi gry**, bo gra wymaga zalogowanego konta. W tym czasie możesz grać z tymi samymi modami przez Modrinth App albo Prism Launcher.

Tokeny kont są zapisywane zaszyfrowane (Windows DPAPI przez Electron `safeStorage`) i nigdy nie trafiają do dziennika gry.

## Budowanie ze źródeł

```
cd launcher
npm install
npm start          # uruchamia launcher w Electronie
npm test           # testy rdzenia i procesu głównego (bez internetu)
npm run smoke      # test na prawdziwych serwerach Mojang/Fabric/Modrinth + próbny start gry
npm run dist       # buduje .exe (na Windows) do katalogu dist/
```

## Co jest przetestowane, a co nie

- Rdzeń (wersje, biblioteki, Fabric, Modrinth, pobieranie, logowanie na atrapach), proces główny z atrapą Electrona i interfejs w Chromium: automatyczne testy.
- Pobieranie wersji 26.2, Fabrica 0.20.1, Javy 25 od Mojang i **start gry z Fabricem** na prawdziwych serwerach: `npm run smoke` w CI (gra startuje i kończy się dopiero na braku ekranu).
- **Nieprzetestowane:** otwarcie okna Electrona na prawdziwym Windowsie, logowanie Microsoft (czeka na zatwierdzenie Client ID) i pełna rozgrywka z menu Nebuli.
