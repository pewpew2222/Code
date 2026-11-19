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
- Tar bort "censurpunkter" i ord före uppläsning ("sh.i.p.s" → "ships"). Ord censurerade med
  versaler ("S.H.I.T") slås upp i en ordlista så att akronymer som "F.B.I" lämnas orörda.
- **Uttalsregler:** egna regler för ord som läses fel (förvalt "exp" → "experience"), både allmänt och per bok (⋮ → Uttalsregler för boken). "No. 1" läses som "number 1".
- Inställningar: hastighet, tonhöjd, läsa upp rubriken, insomningsstopp efter N kapitel,
  genväg till systemets röst- och språkval.
- **Reklamblockering** i webbläsaren: anrop till cirka 76 000 kända reklam- och spårningsdomäner
  stoppas, vanliga reklamrutor döljs, och popup-fönster samt omdirigeringar till appbutiker blockeras.
  Listan följer med appen och uppdateras automatiskt en gång i veckan, kompletterad med en egen lista över annonsnätverk som är vanliga på novellsajter (Adsterra, Monetag, Ezoic m.fl.).
  Under ⋮ → Reklamblockering på sidan ser du vilka domäner sidan hämtar från och kan blockera eller tillåta dem, och med "Dölj element" trycker du på en annons för att dölja den på hela sajten. Blockeringen går att stänga av i inställningarna.
- **Flikar och bokmärken** som i en vanlig webbläsare. Flikarna sparas mellan starterna, ett långt tryck på en länk ger "Öppna i ny flik", och en ny flik visar en startsida med snabbnavigering (bokmärken och novellsajter per språk, med sökning). Ett bokmärkt kapitel flyttas automatiskt med när uppläsningen går till nästa kapitel, så bokmärket fungerar som läsmarkör.
- **Offline:** ladda ner valfritt antal kapitel (⋮ → Ladda ner kapitel…) och förladda automatiskt de närmaste kapitlen medan du läser. Sparade kapitel visas under ⋮ → Nedladdat och läses upp utan internet. Lästa kapitel tas bort automatiskt (går att stänga av).
- **Fortsätt där du slutade:** stycket som läses sparas per kapitel. Uppläsningen fortsätter från samma stycke nästa gång, även efter att appen stängts, och startsidans flik "Fortsätt läsa" listar påbörjade kapitel.
- **Historik** över besökta sidor, grupperad per dag, med sökning.
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
| `core/HostBlocklist.kt` | Domänlista för reklamblockering |
| `AdBlocker.kt` | Reklamblockering i webbläsaren och uppdatering av listan |
| `core/TextChunker.kt` | Delar upp text i bitar som TTS-motorn klarar |
| `service/ReaderService.kt` | Uppläsning, mediasession, notis, ljudfokus, automatiskt nästa kapitel |
| `ui/ReaderScreen.kt` | Webbläsare, spelarpanel och inställningar (Jetpack Compose) |

## Licenser

Reklamlistan (`app/src/main/assets/blocklist_hosts.txt`) kommer från
[StevenBlack/hosts](https://github.com/StevenBlack/hosts) (MIT-licens).

Ordlistan (`app/src/main/assets/words_en.txt`, `acronyms_en.txt`) bygger på den engelska
Hunspell-ordboken från [LibreOffice](https://github.com/LibreOffice/dictionaries), som i sin tur
kommer från [SCOWL](http://wordlist.aspell.net/) (fri licens, se SCOWL:s copyright).
