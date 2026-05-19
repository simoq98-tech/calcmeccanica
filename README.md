# Calc Meccanica

Calcolatrice scientifica con orientamento meccanico (supporti, profili, tensioni, ecc.). Doppia distribuzione: app desktop nativa **Windows** (Java/JavaFX) e **PWA** installabile su iPhone/Android/desktop.

Stesso parser, stesso catalogo materiali/profili/tubi, stessa logica.

## Struttura

```
calcmeccanica/
├── desktop/   — applicazione Java 21 + JavaFX 21 (Maven)
└── pwa/       — Progressive Web App (HTML/CSS/JS vanilla)
```

## Modalità (entrambe le versioni)

- **Scientifica** — sin/cos/tan + inverse (`2nd`), ln, log, √, π, e, ^, !, parentesi, fattoriale, Ans, ±, DEG/RAD, notazione scientifica
- **Programmatore** — DEC / HEX / BIN / OCT + AND / OR / XOR / NOT / `<<` / `>>`, conversione di base on-the-fly
- **Calcolo date** — differenza tra date (giorni / settimane / mesi / anni), aggiungi / sottrai giorni
- **Grafici** — plot di `y = f(x)` su canvas, con assi, griglia, zoom in range custom

## Toolkit meccanico

- **Unità** — 14 categorie (lunghezza, massa, forza, pressione, coppia, energia, potenza, velocità, area, volume, angolo, frequenza, densità, temperatura con offset)
- **Materiali** — 16 materiali (S235/S275/S355, C40, AISI 304L/316L, Al 6061/7075, ottone, rame, ghisa, titanio, PTFE, HDPE) con ρ, E, G, ν, σy, σt, α
- **Sezioni** — parametriche (rettangolo, rettangolo cavo, cerchio, tubo) + commerciali (IPE, HEA, HEB, UPN con A, Ix, Iy, Wx, Wy precompilati)
- **Formulario** — 12 card: tensioni assiale/flessione, frecce trave (3 casi), Eulero buckling, coppia bullone, saldatura fillet, hoop tubo, potenza meccanica, dilatazione termica, fattore sicurezza
- **Procedure guidate** — Bulloni ISO M3-M48 × classi 4.6 → 12.9, Saldature, Statistica (media, σ, ecc.), Matrici 3×3, Tempo/Durata
- **Strumenti** — Triangoli (SSS/SAS/SSA/ASA), Vettori 3D, Solver Newton-Raphson, Pipe schedule ASME B36.10

## Altre feature

- **Memoria multi-slot** M1-M5 con `MC/MR/M+/M-` modali (chiedono quale slot)
- **Variabili nominate** usabili nelle espressioni (`F*L/(48*E*I)`)
- **Storico** 50 voci persistito
- **Impostazioni** — colore accent personalizzabile, notazione automatica/scientifica/ingegneristica, cifre significative, tema scuro/chiaro/automatico (segue sistema)
- **Hamburger menu** unificato (modalità + toolkit + dati + sistema)
- **Persistenza** completa (storico, variabili, slot, impostazioni)

## Build desktop

Richiede JDK 21+ e Maven.

```bash
cd desktop
mvn javafx:run                  # avvio rapido in modalità dev
mvn javafx:jlink                # runtime image standalone
```

Per generare l'eseguibile Windows:

```bash
mvn javafx:jlink
jpackage --type app-image \
         --runtime-image target/calc-runtime \
         --module calc/calc.CalculatorApp \
         --name CalcMeccanica \
         --icon calc-icon.ico \
         --dest target/dist
```

L'app standalone (~38 MB, JRE bundled) si trova in `target/dist/CalcMeccanica/CalcMeccanica.exe`. Pinabile alla taskbar.

## Deploy PWA

I file in `pwa/` sono pronti per hosting statico (Netlify, GitHub Pages, Cloudflare Pages, qualsiasi server).

Per test locale:

```bash
cd pwa
python -m http.server 8765
```

Apri `http://localhost:8765/` nel browser. Service worker richiede HTTPS per attivarsi (eccezione: `localhost`).

## Persistenza utente

Tutte le impostazioni utente (storico, variabili, slot M1-M5, tema, accent, notazione) sono salvate localmente:

- **Desktop**: `%APPDATA%\scientific-calculator\` (history.txt, variables.txt, slots.txt, settings.txt)
- **PWA**: `localStorage` del browser / dispositivo

## Stack tecnico

- **Desktop**: Java 21, JavaFX 21, Maven, jlink + jpackage per packaging
- **PWA**: HTML5 + CSS3 + JavaScript ES2020 vanilla (zero dipendenze), service worker per offline, Web Manifest per install
