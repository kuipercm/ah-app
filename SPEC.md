# Albert Heijn Wekelijkse Bestelassistent — Repository & Functional Specification

## 1. Executive Summary & Purpose

The `weekly-order` repository is a self-hosted, lightweight web application designed to automate and optimize weekly grocery orders at **Albert Heijn (ah.nl)**. 

The application is deployed as a single Docker container on a local **Raspberry Pi** (accessible on the local network) and solves the repetitive friction of:
- Deciding what to cook based on Albert Heijn's weekly discounts ("Bonus aanbiedingen" published every Friday).
- Aggregating ingredients from chosen recipes and combining them with recurring household staples.
- Dynamically swapping standing staples for equivalent products currently on sale (e.g., swapping a preferred whole-wheat bread for another brand's whole-wheat bread on 1+1 sale, while preventing inappropriate swaps like white bread or crackers).
- Managing cadence-based occasional items (olive oil, toilet paper, butter) with intelligent "pull-forward" suggestions if they are on sale ahead of schedule.
- Pushing the consolidated, deduplicated basket directly into an active recurring **Tuesday delivery slot** via Albert Heijn's mobile API (or falling back to the "Mijn Lijst" shopping list).

---

## 2. Business Workflow & Rules

1. **Tuesday Delivery Cadence**: The user orders groceries weekly for a recurring Tuesday delivery slot.
2. **Friday Bonus Trigger**: Albert Heijn publishes new weekly deals every Friday. The user plans groceries over the weekend for the following Tuesday delivery.
3. **Recipe Scoring**:
   - Every recipe shows how many linked ingredients are currently on bonus.
   - Recipes can be sorted by bonus savings to help pick 4–5 dinners.
4. **Staple Handling**:
   - **Weekly Staples**: Essential items ordered every week (eggs, milk, bread). Always pre-selected.
   - **Bonus Swaps**: If an acceptable alternative product for a staple item is on bonus, a swap suggestion is surfaced with details (e.g., normal price vs bonus price, discount mechanism).
   - **Occasional Items**: Items with a repeat cadence (`frequencyWeeks`, e.g., toilet paper every 3 weeks). The planner categorizes them into:
     1. *Due*: Interval has elapsed since `lastOrderedDate` → pre-checked.
     2. *Pull-Forward*: Not due yet, but on bonus this week → highlighted with an opt-in toggle.
     3. *Not Due*: Collapsed/hidden to avoid visual clutter.
5. **Basket Consolidation**:
   - Ingredients from selected recipes and all checked staples are aggregated.
   - Identical products are deduplicated and their quantities summed.
6. **Order Placement**:
   - The aggregated cart is pushed directly into the active AH delivery slot.
   - On successful push, the `lastOrderedDate` of all included occasional items is updated.

---

## 3. Technology Stack & Constraints

| Layer | Choice | Details / Rationale |
|---|---|---|
| **Language & Platform** | Kotlin 2.1.0, Java 21 LTS | Concise syntax, null-safety, modern JVM features. |
| **Backend Framework** | Spring Boot 3.3.5 | Spring Web, Spring Data JPA, Hibernate, Jackson. |
| **Database** | SQLite 3.47 (`org.sqlite.JDBC`) | Stored in single file (`./data/weeklyorder.db`) on mounted volume. Zero maintenance, easy backup. |
| **Frontend** | Vanilla HTML5, CSS3, ES6 JavaScript | No Node.js build step, no npm overhead, single-page application (SPA) with Dutch UI. |
| **Containerization** | Docker (Alpine JRE 21 multi-stage) | Multi-arch (ARM64 for Raspberry Pi, AMD64 for desktop). Optimized memory footprint (~120 MB RAM via `-XX:+UseSerialGC -XX:MaxRAMPercentage=75`). |
| **Testing** | JUnit 5, Mockito Kotlin, MockMvc | Comprehensive unit and integration test suite. |

---

## 4. Architecture & Key Components

```
nl.nielskuiper.weeklyorder/
├── WeeklyOrderApplication.kt         # Application entry point & SQLite dir initializer
├── model/
│   ├── Recipe.kt                     # Recipe entity & Ingredient child entity
│   ├── Product.kt                    # AH Product entity with pricing & bonus details
│   ├── StapleItem.kt                 # Standing items (WEEKLY or OCCASIONAL)
│   ├── WeeklyPlan.kt                 # Weekly plan & PlanItem line items
│   └── AhCredential.kt               # OAuth credentials (access & refresh tokens)
├── repository/
│   └── Repositories.kt               # Spring Data JPA repositories
├── service/
│   ├── AhApiService.kt               # Unofficial AH Mobile API client (OAuth, search, cart push)
│   ├── RecipeService.kt              # Recipe text parser, AH product linking, bonus scoring
│   ├── StapleService.kt              # Cadence evaluation, pull-forward detection, swap finder
│   ├── WeeklyPlanService.kt          # Delivery date math, cart build/aggregation, order sync
│   └── DataInitializerService.kt     # Seeds initial recipes and staples on fresh start
└── controller/
    └── Controllers.kt                # REST API controllers for recipes, staples, planner, AH
```

---

## 5. Domain Model & Database Schema

### `Recipe` & `Ingredient`
- `Recipe`: `id`, `name`, `notes` (e.g., freezer or cooking reminders like *“Diepvries: Speklap bbq”*), `defaultServings`, `createdAt`.
- `Ingredient`: `id`, `recipe` (FK), `name`, `quantity`, `unit`, `product` (FK, nullable).
- *Format parser*: Recognizes the user's bulk text pattern: `Title (ing1, ing2, ...) [-- notes]`.

### `Product` (AH Catalog Snapshot)
- `webshopId` (PK — Albert Heijn product ID)
- `title`, `brand`, `price`, `bonusPrice`, `isBonus`, `bonusMechanism` (e.g. "1+1 gratis", "25% korting")
- `unitSize`, `imageUrl`, `category`, `lastPriceUpdate`
- Calculated: `effectivePrice` (returns `bonusPrice` if `isBonus` is true, otherwise `price`).

### `StapleItem`
- `id`, `name`, `type` (`WEEKLY` or `OCCASIONAL`), `frequencyWeeks`, `defaultQuantity`, `lastOrderedDate`
- `defaultProduct` (FK to `Product`)
- `alternativeProducts` (ManyToMany to `Product`): Pre-approved substitutes eligible for bonus swapping.
- `bonusSearchQuery`: Optional search term (e.g. `"volkoren brood"`) to query the AH API for bonus deals if no explicit alternatives are linked.
- Guardrails: Ensures strict category substitution (e.g., only acceptable whole-wheat breads replace whole-wheat bread).

### `WeeklyPlan` & `PlanItem`
- `WeeklyPlan`: `id`, `yearNumber`, `weekNumber`, `targetDeliveryDate` (always next Tuesday), `status` (`DRAFT`, `ORDERED`), `ahOrderId`, `orderedAt`.
- `PlanItem`: `id`, `weeklyPlan` (FK), `product` (FK), `quantity`, `sourceType` (`RECIPE`, `WEEKLY_STAPLE`, `OCCASIONAL`, `BONUS_PULL_FORWARD`), `sourceName`, `isBonus`, `bonusMechanism`, `isSwapped`, `originalProduct` (FK, nullable), `estimatedCost`.

### `AhCredential`
- Singleton row storing: `accessToken`, `refreshToken`, `expiresAt`, `userName`, `memberId`.

---

## 6. Albert Heijn API Integration (Unofficial Mobile API)

The app integrates with the `api.ah.nl` endpoints using standard Android mobile client headers (`User-Agent: Appie/8.22.3 Android/12`, `X-Application: AHWEBSHOP`).

1. **Authentication Flow**:
   - App provides the official login authorization URL (`https://login.ah.nl/secure/oauth/authorize?...&redirect_uri=appie://login-exit`).
   - The user signs in via browser; the final redirect to `appie://login-exit?code=...` contains the auth code.
   - The user inputs the URL or code into the app.
   - Backend exchanges the code for access & refresh tokens at `POST /mobile-auth/v1/auth/token`.
   - Tokens are persisted and automatically refreshed when expired.
2. **Product Catalog Search**:
   - `GET /mobile-services/product/search/v2?query={query}&sortOn=RELEVANCE`
   - Parses product titles, brands, current prices, bonus prices, and bonus promotional labels.
3. **Cart & Shopping List Push**:
   - Pushes to active delivery slot via `POST /mobile-services/order/v1/orders/{orderId}/items` (queries `GET /mobile-services/order/v1/summaries/active` to locate the current Tuesday slot).
   - Falls back to updating the user's "Mijn Lijst" shopping list via `PATCH /mobile-services/shoppinglist/v2/items` if no active slot is detected.

---

## 7. REST API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/recipes` | List recipes with ingredient link status and bonus count |
| `POST` | `/api/recipes` | Create a recipe manually |
| `DELETE` | `/api/recipes/{id}` | Delete a recipe |
| `POST` | `/api/recipes/bulk-parse` | Parse raw multiline text into recipes & ingredients |
| `POST` | `/api/recipes/ingredients/{id}/link` | Link ingredient to an AH product (`webshopId`) |
| `GET` | `/api/staples` | List all staples |
| `GET` | `/api/staples/evaluated` | Evaluated staples for next Tuesday (`due`, `pullForward`, `upcoming`) |
| `POST` | `/api/staples` | Create or update staple item |
| `DELETE` | `/api/staples/{id}` | Delete staple item |
| `POST` | `/api/staples/{id}/alternative` | Add an acceptable alternative product ID |
| `GET` | `/api/planner/upcoming` | Fetch or initialize draft plan for the upcoming Tuesday |
| `POST` | `/api/planner/{id}/build` | Build aggregated cart from selected recipe IDs & staple IDs |
| `POST` | `/api/planner/{id}/push-ah` | Push aggregated cart to AH order or shopping list |
| `GET` | `/api/ah/auth/status` | Check if AH credentials are active and valid |
| `POST` | `/api/ah/auth/exchange` | Exchange OAuth code for access & refresh tokens |
| `POST` | `/api/ah/auth/logout` | Clear stored credentials |
| `GET` | `/api/ah/search?query=` | Search AH mobile catalog for products |
| `GET` | `/api/ah/active-order` | Find active AH order summary |
| `GET` | `/api/ah/lists` | Retrieve existing AH shopping lists |

---

## 8. Frontend Interface (SPA)

The UI is a single-page application served directly from Spring Boot (`src/main/resources/static/`):
- **Tab 1: 🛒 Weekplanner**:
  - Upcoming Tuesday delivery date header.
  - Recipe selector with bonus badges and "Sort by Bonus" toggle.
  - Weekly essentials list with active bonus swap cards and "Accept swap" toggles.
  - Occasional items segmented into *Due this week*, *Bonus Pull-Forward alerts*, and *Not due yet*.
  - Live aggregated cart preview with item counts, combined quantities, and estimated total price.
  - Primary action: "Naar AH Mandje Sturen" (Push to AH).
- **Tab 2: 🍲 Recepten**:
  - Recipe cards showing ingredients and notes.
  - Bulk import modal for pasting raw recipe text.
  - Ingredient linker modal with live AH product search and one-click linking.
- **Tab 3: 🥛 Vaste & Periodieke Artikelen**:
  - Management of standing items: set cadence (weekly vs periodic frequency in weeks), default quantity, linked default product, approved swap alternatives, and bonus search keywords.
- **Tab 4: ⚙️ AH Koppeling & Lijsten**:
  - OAuth login helper and token status.
  - Profile shopping list browser.

---

## 9. Verification & Test Suite

The codebase includes 20 automated tests:
- `WeeklyOrderIntegrationTests`: Full Spring Boot context verification with SQLite and seeded data.
- `ControllerIntegrationTests`: MockMvc endpoint tests for recipes, staples, planner, and AH controller.
- `RecipeServiceTest`: Bulk recipe parser accuracy, ingredient quantity extraction, product linking, bonus score calculations.
- `StapleServiceTest`: Date/cadence evaluation, pull-forward bonus detection, bonus swap selection via pre-linked alternatives and fallback queries.
- `WeeklyPlanServiceTest`: Tuesday delivery date calculation, cart deduplication and aggregation, bonus swap pricing, and `lastOrderedDate` updates upon ordering.
- `AhApiServiceTest`: Login URL generation, request headers, and response JSON parsing.
