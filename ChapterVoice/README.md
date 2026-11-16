# ChapterVoice

En Android-app som läser upp webbsidor med talsyntes (TTS) och automatiskt går
vidare till nästa kapitel. Gjord för webbnoveller.

## Funktioner

- Inbyggd webbläsare: gå till ett kapitel och tryck **Läs upp den här sidan**.
- Plockar ut själva kapiteltexten (tar bort menyer, reklam, kommentarer m.m.).
- **Nästa kapitel automatiskt**, på något av tre sätt:
  - *Auto* (standard): följer sidans "Nästa"-länk, annars ökar sista numret i adressen.
  - *Bara länk*: följer bara "Nästa"-länken (`rel="next"`, "Next", "Nästa", "下一章", `.btn-next` …).
  - *Bara adress*: `…/chapter-41` → `…/chapter-42` (behåller inledande nollor, `009` → `010`).
- Hämtar nästa kapitel i förväg, så det blir ingen paus mellan kapitlen.
- Stoppar när kapitlet inte finns (t.ex. HTTP 404) eller när sidan skickar tillbaka till samma sida.
- Spelar i bakgrunden med skärmen avstängd (förgrundstjänst), med kontroller i notisen,
  på låsskärmen och via hörlurarnas knappar. Pausar när hörlurarna kopplas ur eller när ett samtal kommer.
- Inställningar: hastighet, tonhöjd, läsa upp rubriken, insomningsstopp efter N kapitel,
  genväg till systemets röst- och språkval.
- Dela en länk från vilken webbläsare som helst till appen för att öppna den.
- Första kapitlet läses från sidan som webbläsaren visar. Följande kapitel hämtas med
  webbläsarens cookies och user agent, vilket fungerar på de flesta sidor med Cloudflare-skydd.

## Bygga

Kräver JDK 17+ och Android SDK (plattform 35).

```bash
cd ChapterVoice
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # enhetstester för textutvinning och nästa-kapitel-logik
```

Du kan också öppna mappen `ChapterVoice` i Android Studio.

## Struktur

| Fil | Ansvar |
| --- | --- |
| `core/ContentExtractor.kt` | Hittar kapiteltexten på en sida |
| `core/NextChapterResolver.kt` | Hittar adressen till nästa kapitel (länk eller +1 i adressen) |
| `core/Chapter.kt` | Hämtar och tolkar ett kapitel (Jsoup) |
| `core/TextChunker.kt` | Delar upp text i bitar som TTS-motorn klarar |
| `service/ReaderService.kt` | Uppläsning, mediasession, notis, ljudfokus, automatiskt nästa kapitel |
| `ui/ReaderScreen.kt` | Webbläsare, spelarpanel och inställningar (Jetpack Compose) |
