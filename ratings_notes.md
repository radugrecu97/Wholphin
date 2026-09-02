# Wholphin Ratings Architecture & Integration Notes

This document describes how ratings are defined, fetched, formatted, and rendered throughout the Wholphin codebase, including integration points with Remux / Jellyfin and user preferences.

---

## 1. Data Models & Server Mapping

### A. Server (Remux / Jellyfin) to DTO
In the Jellyfin OpenAPI model (`BaseItemDto`), an item exposes three distinct rating fields:
- `officialRating: String?` — Content certification (e.g., `"PG-13"`, `"TV-MA"`, `"R"`). In Remux server (`crates/remux-server/src/api/models.rs`), this maps from `media.certification`.
- `communityRating: Float?` — Audience rating out of 10 (e.g., `7.8`). In Remux, this maps from `media.rating_audience` (`(r * 10.0).round() / 10.0`).
- `criticRating: Float?` — Critic approval percentage out of 100 (e.g., `85.0` for Rotten Tomatoes). In Remux, this maps from `media.rating_critic`.

### B. Client Domain Model (`BaseItem.kt`)
File: `app/src/main/java/com/github/damontecres/wholphin/data/model/BaseItem.kt`
- During `BaseItemUi` construction, `data: BaseItemDto` is converted into a `QuickDetailsData` struct:
  ```kotlin
  data class QuickDetailsData(
      val basic: AnnotatedString? = null,
      val officialRating: AnnotatedString? = null,
      val criticRating: AnnotatedString? = null,
      val communityRating: AnnotatedString? = null,
  )
  ```
- Formatting applied in `BaseItem.kt`:
  - `officialRating`: prepends separator dot (`• {officialRating}`)
  - `communityRating`: prepends dot, formats as 1 decimal place (`• 7.5`), and appends inline image token `[star]` (`appendInlineContent(id = "star")`)
  - `criticRating`: prepends dot, formats as percentage (`• 85%`), and appends inline image token `[fresh]` (if $\ge 60\%$) or `[rotten]` (if $< 60\%$)

---

## 2. UI Display & Components

### A. Synopsis Header (`QuickDetails.kt`)
File: `app/src/main/java/com/github/damontecres/wholphin/ui/components/QuickDetails.kt`
- Displayed in headers: `MovieDetailsHeader.kt`, `EpisodeDetailsHeader.kt`, `FocusedEpisodeHeader.kt`, `ImageDetailsHeader.kt`.
- Checks active user display toggles from `LocalInterfaceCustomization.current.enabledDisplayToggles`:
  ```kotlin
  if (DisplayToggle.OFFICIAL_RATING in enabled) {
      QuickDetailsText(details.officialRating, Modifier, textStyle, inlineContentMap)
  }
  if (DisplayToggle.COMMUNITY_RATING in enabled) {
      QuickDetailsText(details.communityRating, Modifier, textStyle, inlineContentMap)
  }
  if (DisplayToggle.CRITIC_RATING in enabled) {
      QuickDetailsText(details.criticRating, Modifier, textStyle, inlineContentMap)
  }
  ```
- Uses `rememberQuickDetailsContentMap(textStyle)` to render inline icons:
  - `"star"`: `Icons.Filled.Star` tinted `FilledStarColor` (`#FFC700`)
  - `"fresh"`: `painterResource(R.drawable.ic_rotten_tomatoes_fresh)`
  - `"rotten"`: `painterResource(R.drawable.ic_rotten_tomatoes_rotten)`

### B. Rating Widgets (`Rating.kt`)
File: `app/src/main/java/com/github/damontecres/wholphin/ui/components/Rating.kt`
- `SimpleStarRating(communityRating: Float?)`: Text representation accompanied by a gold star icon.
- `TomatoRating(rating: Float?, threshold: Float = 60f)`: Percentage + Rotten Tomatoes fresh/rotten drawable.
- `StarRating(...)`: Interactive 5-star rating control for users to submit personal ratings (0–100 scale) sent back to Jellyfin via `userLibraryApi.updateItemRating(itemId, rating)`.

---

## 3. User Preferences & Settings

### A. DataStore Schema
File: `app/src/main/proto/WholphinDataStore.proto`
- Enum definition:
  ```proto
  enum DisplayToggle {
    OFFICIAL_RATING = 0;
    CRITIC_RATING = 1;
    COMMUNITY_RATING = 2;
  }
  ```
- Stored under `InterfacePreferences.display_toggles` (repeated enum). By default, all valid toggles are enabled.

### B. Settings UI
File: `app/src/main/java/com/github/damontecres/wholphin/preferences/AppPreference.kt`
- Managed via `AppPreference.DisplayTogglesPref`:
  - Multi-choice dialog allowing users to independently toggle visibility of Official, Critic, and Community ratings.

---

## 4. Filtering, Sorting & Screensaver

- **Filtering** (`app/src/main/java/com/github/damontecres/wholphin/data/filter/ItemFilterBy.kt`):
  - `CommunityRatingFilter`: Filters items by minimum audience score (`minCommunityRating`).
  - `OfficialRatingFilter`: Filters library items by certification (`officialRatings`).
- **Sorting** (`app/src/main/java/com/github/damontecres/wholphin/data/model/SortBy.kt`):
  - Supports sorting by `CommunityRating` and `CriticRating`.
- **Screensaver** (`app/src/main/java/com/github/damontecres/wholphin/preferences/ScreensaverPreference.kt`):
  - Filters backdrop images using `max_age_rating` against `officialRating`.
