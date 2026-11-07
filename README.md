# Albert Heijn Wekelijkse Bestelassistent 🛒

Een slimme en lichtgewicht webapplicatie om je wekelijkse boodschappen bij **Albert Heijn (`ah.nl`)** te plannen en automatisch in je bezorgmandje te plaatsen. Ontworpen om lokaal in **Docker** te draaien op een **Raspberry Pi** (of desktop/Mac).

---

## 🌟 Belangrijkste Functies

1. **Vrijdag Bonus Intelligence**:
   - Vrijdag gaan de nieuwe AH bonusaanbiedingen in.
   - De app scant al je recepten en toont badges voor het aantal bonusaanbiedingen (bijv. `★ 3 in Bonus!`).
   - Sorteer recepten met 1 klik op meeste bonusvoordeel.
2. **Slimme Bonus Swaps voor Vaste Artikelen**:
   - Vervangt een vast artikel (bijv. *AH Liefde & Passie Volkoren*) door een gelijkwaardige bonusvariant (bijv. *AH Zaans Volkoren 1+1 gratis*).
   - Beperkt tot strikt acceptabele alternatieven (dus nooit per ongeluk witbrood of crackers als je volkoren wilt).
3. **Periodieke Artikelen & Bonus Pull-Forward**:
   - Artikelen met een vaste herhaalfrequentie (bijv. olijfolie elke 4 weken, toiletpapier elke 3 weken).
   - **Pull-Forward Alert**: Als olijfolie of wasmiddel nog *niet* aan de beurt is, maar deze week wél met 25% of 1+1 gratis in de folder staat, adviseert de app om deze nu naar voren te halen.
4. **Directe Bezorgslot Synchronisatie**:
   - Push het geconsolideerde en ontdubbelde mandje met 1 klik rechtstreeks naar je actieve **Dinsdag bezorgmoment** (of naar *Mijn Lijstje* in de AH app).
5. **Recepten Tekst Importer**:
   - Herkent je natuurlijke syntaxis direct:
     ```text
     AVG (spruiten, aardappels, spekjes, appel) -- Diepvries: Speklap bbq, geen idee of die nog goed is, maar proberen
     Flespompoen tajine pagina 97 (Flespompoen, knoflook, verse rozemarijn, geschaafde amadel, stemgember, gedroogde abrikozen, honing, couscous)
     Quesadillas met bloemkool (bloemkool, Wraps, kaas, ui, paprika, champignons)
     Pasta pesto (courgette, cherry tomaat, pasta, pesto, paprika, ui, knoflook, mozzarella, avocado, strooikaas, pijnboompitten)
     ```
   - Splitst automatisch titel, ingrediënten en vriezer/kooknotities, en zoekt bijpassende AH producten.

---

## 🏗️ Architectuur & Footprint

- **Backend**: Spring Boot 3.3 in **Kotlin 2.1** (Java 21 LTS).
- **Database**: SQLite (opgeslagen in gemounte map `./data/weeklyorder.db`, zero-config en single-file backup).
- **Frontend**: Vanilla HTML5, CSS en modern JavaScript (geen Node.js/npm build vereist).
- **Testdekking**: 20 unit- en integratietesten (JUnit 5 + Mockito Kotlin + MockMvc) voor alle bedrijfslogica en endpoints.
- **Raspberry Pi Geoptimaliseerd**: Multi-stage Alpine image met SerialGC (`-XX:+UseSerialGC -XX:MaxRAMPercentage=75`), verbruikt slechts **~120-150 MB RAM**.

---

## 🚀 Lokaal Starten (Ontwikkeling)

### Vereisten
- Java 21+
- Maven 3.8+

```bash
# 1. Start de applicatie lokaal
mvn spring-boot:run
```

Open je browser op: **[http://localhost:8080](http://localhost:8080)**

---

## 🐳 Uitrollen op Raspberry Pi (met Docker Compose)

Kopieer het project naar je Raspberry Pi en voer uit:

```bash
# 1. Bouw en start de container in de achtergrond
docker compose up -d --build

# 2. Bekijk de logs
docker compose logs -f
```

De webapplicatie is direct bereikbaar op het IP-adres van je Pi op poort `8080` (bijv. `http://192.168.1.150:8080`).

---

## 🔑 Eenmalige Albert Heijn Koppeling

1. Open in de webapplicatie het tabblad **"⚙️ AH Koppeling & Lijsten"**.
2. Klik op **"1. Open AH Inlogpagina"** en log in met je AH account.
3. Na het inloggen probeert je browser een `appie://login-exit?code=...` link te openen.
4. Kopieer deze link of de code uit de adresbalk en plak deze in het invoerveld.
5. Klik op **"Koppelen"**. Vanaf dat moment worden je tokens automatisch op de achtergrond ververst!
