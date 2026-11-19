# Berlin Sightrun (Android) · version 1.0

Berlin Sightrun guider dig rundt på en halvmarathon-rute på 21,1 km i Berlin, fra TU Berlin på Straße des 17. Juni til Brandenburger Tor. Du hører musik fra din egen musikapp imens. Ved hvert sving dæmper appen musikken og fortæller, hvor du skal løbe. Ved 20 seværdigheder får du en fortælling på cirka 1 minut.

Appen er bygget på samme kode og med samme indstillinger som Paris Sightrun: samme sprog, samme regler for sving, fortællinger, kilometertider og afvigelser, og samme skærme.

Appen taler **dansk, tysk, engelsk og fransk**. Vælg sprog med knapperne DA/DE/EN/FR øverst på forsiden. Appen husker valget og starter første gang på telefonens sprog. Du kan også skifte sprog midt i et løb. Så kommer næste besked på det nye sprog.

Ved hver kilometer siger appen den samlede tid, tiden for den seneste kilometer og gennemsnitsfarten, fx "5 kilometer. Tid: 29 minutter og 33 sekunder. Sidste kilometer: 5 minutter 53. Gennemsnit: 5 minutter 55 per kilometer." Samtidig vises et kilometerkort på skærmen i cirka 300 meter. Resultatskærmen viser alle kilometertider med den hurtigste fremhævet.

Før start viser løbeskærmen en **rød cirkel** med afstanden til startpunktet ved TU Berlin. Inden for 50 m bliver den **grøn** med et flueben, appen siger velkommen, og løbet starter automatisk. Startzonen på kortet skifter farve på samme måde. I demo-løbet kan du vælge at begynde ca. 440 m fra startpunktet for at se det.

## Ruten

Ruten bygger på GPX-sporet fra Berliner Halbmarathon (Openrunner, 2024) med to ændringer, så alle 20 seværdigheder er med, og ruten stadig er 21,1 km:

- **Start ved TU Berlin** i stedet for ved Großer Stern. De første ca. 2 km gennem Tiergarten er sprunget over.
- **Sidste del er lagt om** efter Checkpoint Charlie: Leipziger Straße › Markgrafenstraße (Gendarmenmarkt) › Französische Straße › Werderscher Markt › Schlossplatz (Humboldt Forum) › Rathausstraße (Rotes Rathaus) › Alexanderplatz › Karl-Marx-Allee (vendepunkt ca. 235 m inde) › Karl-Liebknecht-Straße (Berliner Dom) › Unter den Linden › Brandenburger Tor. GPX-sporet vendte i stedet ved Schlossplatz og løb tilbage via Gendarmenmarkt.

Ruten har to vendepunkter: på Kurfürstendamm ved Joachim-Friedrich-Straße (km 6,6) og på Karl-Marx-Allee (km 17,8).

| Nr. | Seværdighed | km |
|---|---|---|
| 1 | TU Berlin | 0,05 |
| 2 | Ernst-Reuter-Platz | 0,29 |
| 3 | Schloss Charlottenburg | 2,26 |
| 4 | Schaubühne | 7,29 |
| 5 | Kurfürstendamm | 8,36 |
| 6 | Café Kranzler | 9,33 |
| 7 | Kaiser-Wilhelm-Gedächtniskirche | 9,57 |
| 8 | Wittenbergplatz | 10,05 |
| 9 | KaDeWe | 10,15 |
| 10 | Nollendorfplatz | 10,96 |
| 11 | Neue Nationalgalerie | 12,67 |
| 12 | Berliner Philharmonie | 12,94 |
| 13 | Potsdamer Platz | 13,32 |
| 14 | Leipziger Platz | 13,5 |
| 15 | Gendarmenmarkt | 15,25 |
| 16 | Humboldt Forum | 16,42 |
| 17 | Rotes Rathaus | 16,83 |
| 18 | Karl-Marx-Allee | 17,45 |
| 19 | Berliner Dom | 19,2 |
| 20 | Brandenburger Tor | 20,7 |

## Byg og installér

1. Installér **Android Studio** (Ladybug eller nyere).
2. Åbn mappen `sightrunberlin` med *File › Open*. Android Studio henter Gradle og afhængigheder første gang. Det tager et par minutter.
3. Slut en Android-telefon til med USB, og slå *USB-fejlfinding* til under *Indstillinger › Udviklerindstillinger*.
4. Tryk ▶ *Run 'app'*. Appen installeres og åbner.

Vil du have en APK-fil, du kan sende til andre, så vælg *Build › Build App Bundle(s) / APK(s) › Build APK(s)*. Filen ligger bagefter i `app/build/outputs/apk/debug/app-debug.apk`.

Fra kommandolinjen virker det sådan, når Android SDK er installeret:

```
gradle wrapper --gradle-version 8.9   # kun første gang, hvis gradlew mangler
./gradlew assembleDebug
./gradlew test                        # kører testene af rutemotoren
```

## Prøv appen hjemmefra

Tryk **Prøv demo-løb hjemmefra** på forsiden og vælg hastighed (1×, 5×, 10× eller 30×). Du kan også starte midt på ruten, fx ved km 8 på Kurfürstendamm eller km 16 lige før Humboldt Forum.

En virtuel løber løber så ruten, og du hører al vejledning og alle fortællinger, præcis som i Berlin. Start din musik i Spotify eller en anden musikapp først, så kan du høre, at den bliver dæmpet. Mens der fortælles, sænkes demo-farten til 2×, så du kan følge med på kortet.

## Løb i Berlin

1. Gå hen til TU Berlins hovedbygning på Straße des 17. Juni (nærmeste station: S Tiergarten eller U Ernst-Reuter-Platz).
2. Start din musik.
3. Tryk **Start løb**, og giv appen adgang til din placering og notifikationer.
4. Følg den røde cirkel hen til startpunktet. Når den bliver grøn (inden for 50 m), starter løbet automatisk, og du kan lægge telefonen i lommen.

Appen kører videre med slukket skærm. På nogle telefoner (Samsung, Xiaomi, Huawei) skal du slå batterioptimering fra for Berlin Sightrun. Ellers kan systemet lukke appen undervejs.

## Stemmer

Appen bruger telefonens tekst-til-tale. Mangler stemmen for det valgte sprog, skriver appen det på forsiden. Installér den under *Indstillinger › Tilgængelighed › Tekst-til-tale › Installér stemmedata*.

Indspillede fortællinger kan lægges i `app/src/main/assets/audio/<sprog>/` som `01.ogg` … `20.ogg`, fx `audio/de/07.ogg` for Kaiser-Wilhelm-Gedächtniskirche på tysk. Så bruges de i stedet for den syntetiske stemme.

## Tilføj et sprog

1. Tilføj sproget i `Lang` i `i18n/Strings.kt`, og lav et nyt objekt med teksterne (kopiér `English`).
2. Tilføj navn og fortælling for hver seværdighed i `assets/route/berlin_sightrun.json` (felterne `name`, `text` og `finishText` har én tekst pr. sprogkode).
3. Kør `./gradlew test`. Testen `everyLanguageHasAllTexts` fejler, hvis en tekst mangler.

## Sådan er appen bygget

| Del | Fil | Gør |
|---|---|---|
| Rute og seværdigheder | `assets/route/berlin_sightrun.json` | 67 waypoints med gadenavne, 20 seværdigheder med geofence og fortælling på fire sprog |
| Sprog | `i18n/Strings.kt` | Skærmtekster og talebeskeder på dansk, tysk, engelsk og fransk, plus det gemte sprogvalg |
| Rutemotor | `route/Route.kt` | Afstand, sving-detektion og projektion af GPS-punkter ind på ruten |
| Logik under løbet | `run/RunEngine.kt` | Beslutter hvornår der siges sving, fortælling, km og afvigelse |
| Forgrundstjeneste | `run/RunService.kt` | GPS hvert sekund eller demo-løber, notifikation med Pause og Stop |
| Lydkø | `audio/Announcer.kt` | Prioriteret kø: afvigelse > sving > fortælling > km |
| Musik-dæmpning | `audio/VoiceAudioFocus.kt` | Midlertidig lydfokus med dæmpning |
| Skærme | `ui/OverviewScreen.kt`, `ui/RunScreen.kt`, `ui/SummaryScreen.kt` | Oversigt med kort og liste, løbeskærm og resultat |
| Kort | `ui/RouteMap.kt` | OpenStreetMap (osmdroid) i mørke farver med ruten i lime |

Regler under løbet:

- **Sving:** varsles 100 m før og igen ved selve svinget.
- **Fortællinger:** starter kun, når næste sving er mindst 250 m væk. Kommer der et sving midt i en fortælling, pauses fortællingen og fortsætter bagefter.
- **Afvigelse:** er du mere end 40 m fra ruten i 10 sekunder, siger appen det og guider dig tilbage.

## Før lancering

- Løb ruten med GPS-ur, og ret waypoints til. Strækningen frem til Checkpoint Charlie er forenklet fra et rigtigt GPX-spor, men den omlagte sidste del (Gendarmenmarkt til Brandenburger Tor) er lagt ind i hånden i luftlinje. Hele ruten er skaleret til 21,1 km.
- Få fakta og oversættelser gennemlæst af en indfødt på hvert sprog, og indspil gerne fortællingerne med en speaker.
- Ruten følger halvmarathonens gader, men på almindelige dage er der trafik. Løb på fortov og cykelsti, og kryds kun ved lyskryds. Det gælder især vendepunkterne på Kurfürstendamm og Karl-Marx-Allee og pladserne Ernst-Reuter-Platz, Potsdamer Platz og Alexanderplatz.
- Skift `signingConfig` i `app/build.gradle.kts` til din egen nøgle før udgivelse i Google Play.
- Kortet henter fliser fra OpenStreetMaps servere. Til mange brugere skal I bruge en betalt flise-udbyder eller offline-kort, jf. OSM's brugsregler.
