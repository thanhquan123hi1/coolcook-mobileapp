# Luồng Nhận Diện Thực Phẩm - Quick Reference

## 🚀 Tóm Tắt 30 Giây

```
User scans food image
    ↓
Gemini AI detects ingredients (e.g., "gà", "cà chua")
    ↓
CHECK: Tất cả ingredients có trong foods.json không?
    ├─ YES: Use LOCAL suggestions (fastest)
    └─ NO: Use AI to suggest 3 dishes
    ↓
Filter suggestions by relevance
    ↓
Backfill missing recipes
    ↓
Show TOP 3 suggestions to user
```

---

## 🎯 Decision Flow (Quyết Định Chính)

```
1. Parse detected ingredients
   ├─ Error handling: per-item try/catch
   └─ Skip bad items, keep valid ones

2. Build effective ingredients
   ├─ Add AI-detected items
   └─ Add manual user-added items

3. Check local database coverage
   ├─ findUncoveredIngredients()
   ├─ ALL in foods.json? → Try LOCAL first
   └─ Some missing? → FORCE AI path

4. Try LOCAL suggestions first
   ├─ suggestDishes() [strict match]
   ├─ If empty + hasCompleteLocalCoverage
   │  → suggestDishesRelaxed() [looser match]
   └─ Check: hasLocalCombinationMatch()?
      ├─ YES → RETURN, don't call AI
      └─ NO → Call AI anyway

5. Call GEMINI AI for suggestions
   ├─ If uncovered exists: MUST return exactly 3 dishes
   ├─ Parse JSON response
   ├─ Try: parseSuggestedDishes()
   └─ If error: return []

6. Build AI suggestion items
   ├─ For each suggested dish:
   │  ├─ Filter: isAiSuggestionRelevant()
   │  ├─ Check: exists in local DB?
   │  │  ├─ YES: Use local version
   │  │  └─ NO: Use AI version (if recipe exists)
   │  └─ Add to items list
   └─ If ALL empty + allows fallback:
      └─ Return local suggestions instead

7. Backfill missing recipes
   ├─ requestRecipesForAiDishes()
   ├─ For AI dishes with empty recipe:
   │  ├─ Call Gemini: generate full recipe
   │  ├─ parseGeneratedDishes()
   │  └─ mergeGeneratedRecipes()
   └─ rankAndLimitDishItems() → TOP 3

8. Display results
   ├─ If items.isEmpty() → Show error
   └─ Else → Show suggestion dialog
```

---

## 🔑 Key Methods

| Method | File | Purpose |
|--------|------|---------|
| `findUncoveredIngredients()` | ScanFoodLocalMatcher | Detect unknown ingredients |
| `suggestDishes()` | ScanFoodLocalMatcher | Get local suggestions (strict) |
| `suggestDishesRelaxed()` | ScanFoodLocalMatcher | Get local suggestions (loose) |
| `hasCompleteLocalCoverage()` | ScanFoodLocalMatcher | All ingredients in local DB? |
| `hasLocalCombinationMatch()` | ScanFoodActivity | Any local dish uses all ingredients? |
| `buildDishSuggestionPrompt()` | ScanFoodActivity | Create Gemini prompt |
| `buildAiSuggestionItems()` | ScanFoodActivity | Filter & merge AI suggestions |
| `isAiSuggestionRelevant()` | ScanFoodActivity | Check if AI suggestion passes filters |
| `requestRecipesForAiDishes()` | ScanFoodActivity | Generate recipes for AI dishes |
| `mergeGeneratedRecipes()` | ScanFoodActivity | Backfill missing recipes |
| `parseDetectedIngredients()` | ScanFoodActivity | Extract from AI vision response |
| `parseSuggestedDishes()` | ScanFoodActivity | Extract from Gemini suggestion |
| `parseGeneratedDishes()` | ScanFoodActivity | Extract from Gemini recipe generation |

---

## 🎛️ Important Thresholds

### Ingredient Matching Scores
- **88+**: Local ingredient match (strict vocabulary check)
- **90+**: Direct dish match ("gà" → "Gà nướng" dish)
- **72+**: Recipe ingredient to detected ingredient match
- **70+**: Fuzzy dish name match (findDishByName)

### AI Suggestion Filters
- **matched > 0**: Must match at least 1 ingredient
- **2+ ingredients**: If user has 2+ items, must match ≥ 2
- **3+ ingredients**: Coverage must be ≥ 50%
- **Missing threshold**: 
  - 1 ingredient: allow ≤ max(5, matched+3) missing
  - 2+ ingredients: allow ≤ max(2, matched) missing

### Final Result
- **SUGGESTION_LIMIT**: Show TOP 3 dishes

---

## 🐛 Error Handling

### ✅ Already Fixed Issues

**1. Malformed ingredient in JSON**
```java
for (int index = 0; index < array.length(); index++) {
    try {
        // Process item
    } catch (Exception ignoredItem) {
        // Skip bad item, continue with next
    }
}
```
Result: 1 bad item doesn't break entire batch

**2. Unknown ingredients block suggestions**
```java
List<String> uncoveredIngredients = findUncoveredIngredients(...);
if (!uncoveredIngredients.isEmpty()) {
    forceAiForUnknownIngredients = true;
    // → Call AI with special prompt
}
```
Result: Unknown items trigger smart AI path

**3. Single ingredient too strict**
```java
int maxAllowedMissing = availableCount <= 1
    ? Math.max(5, matchedCount + 3)  // Relaxed!
    : Math.max(2, matchedCount);
```
Result: Single ingredient like "gà" can still get suggestions

**4. AI recipes empty**
```java
if (!suggestedDish.getRecipe().trim().isEmpty()) {
    // Add AI dish
} 
// Later: requestRecipesForAiDishes() backfills if needed
```
Result: AI dishes appear even with empty recipe initially

**5. AI returns no suggestions**
```java
if (combinedDishItems.isEmpty()) {
    // Fallback: return local suggestions if available
    if (!localSuggestions.isEmpty() && !forceAiForUnknownIngredients) {
        return localSuggestions;
    }
    // Show error
}
```
Result: Try local as backup before showing error

---

## 📝 Testing Scenarios

### ✅ Test Case 1: Single Known Ingredient
**Input**: Photo of "gà" (chicken)
- findUncoveredIngredients() → [] (empty)
- suggestDishes(local) → ["Gà nướng", "Gà xào", "Cơm gà"]
- hasLocalCombinationMatch() → true
- **Expected**: Return local suggestions immediately, no AI call

### ✅ Test Case 2: Single Unknown Ingredient
**Input**: Photo of unfamiliar herb "Herb X"
- findUncoveredIngredients() → ["Herb X"]
- forceAiForUnknownIngredients → true
- Call Gemini with "must suggest 3 dishes using Herb X"
- **Expected**: AI suggests 3 dishes

### ✅ Test Case 3: Mixed Known + Unknown
**Input**: "Gà" + "Spice X"
- findUncoveredIngredients() → ["Spice X"]
- Force AI path
- Gemini: dishes using gà + spice X
- **Expected**: 3 AI suggestions

### ✅ Test Case 4: Multiple Known (No AI Needed)
**Input**: "Gà + Cà chua + Dầu"
- findUncoveredIngredients() → []
- suggestDishes(local) → 3+ dishes
- **Expected**: Fast local suggestions, no AI

### ⚠️ Test Case 5: AI Returns Empty
**Input**: Unrecognizable/unusual combination
- AI can't find 3 suitable dishes
- combinedDishItems → []
- If local fallback available: show local
- Else: show "Chưa tìm thấy món phù hợp"

---

## 🔄 Data Structures

### DetectedIngredient
```java
name: String              // "gà"/"cà chua"
confidence: Double        // 0.95
category: String          // "meat"/"vegetable"
visibleAmount: String     // "2 thứa"/unknown
notes: String             // "tự thêm" or from AI
```

### SuggestedDish (from Gemini)
```java
name: String                      // "Gà nướng"
usedIngredients: List<String>     // ["gà", "ớt"]
missingIngredients: List<String>  // ["nước tương"]
healthTags: List<String>          // ["it dau", "tang co"]
reason: String                    // "Vì sao là phù hợp"
recipe: String                    // "### Tên hôn..."
confidence: Double                // 0.85
```

### ScanDishItem (final result)
```java
stableId: String              // "local:123" or "ai:gà_nướng"
name: String                  // Display name
localFood: FoodItem|null      // If from DB
usedIngredients: List         // Matched ingredients
missingIngredients: List      // What's needed
healthTags: List              // Diet info
reason: String                // Why recommended
recipe: String                // Full recipe text
confidence: Double            // Match score
isLocal: Boolean              // From DB or AI?
```

---

## ⚡ Performance Notes

### CPU/Memory Intensive
- `scoreMatch()` on every ingredient pair (O(n²) if many)
- Gemini API calls (network latency ~3-5s)
- Recipe generation call (optional, adds ~2-3s)

### Optimization Done
- Try local FIRST before AI
- Only call recipe generation if needed
- Limit suggestions to TOP 3

### Future Optimizations
- Cache ingredient vocabulary (already done)
- Batch Gemini calls
- Add local fallback scores quickly

---

## 🎯 User Experience Flow

```
Start Scan
    ↓
[Scanning...]
    ↓
AI detects: "Bạn vừa scan: gà, cà chua, dầu"
    ↓
Checking local database...
    ↓
Found 5 local dishes with these ingredients
    ↓
Show suggestion popup:
- Gà xào cà chua (Best match)
- Canh chua gà
- Bánh mì gà
    ↓
User can:
- Accept & add to meal journal
- Edit quantity/ingredients
- Request manual selection
```

---

## 🚨 Known Issues & Workarounds

| Issue | Root Cause | Workaround |
|-------|-----------|-----------|
| Single ingredient shows no suggestion | Too strict filter | Relaxed threshold (max 5 missing) |
| One malformed JSON breaks batch | No error handling | Added per-item try/catch |
| AI suggestion has empty recipe | AI may not generate | Call backfill function |
| AI returns 0 dishes | Ingredients too vague | Show local suggestions as fallback |
| User manually adds wrong ingredient | No validation | Show in UI, let user fix |

---

## 📚 Related Files

| File | Purpose |
|------|---------|
| ScanFoodActivity.java | Main orchestrator |
| ScanFoodLocalMatcher.java | Local database matching |
| DetectedIngredient.java | Model class |
| SuggestedDish.java | Model class |
| ScanDishItem.java | Final dish result |
| foods.json | Local ingredient database |
| GeminiRepository.java | API client |

---

## 🎓 Summary for New Developers

1. **User scans image** → Gemini detects ingredients
2. **System checks** → Are items in local foods.json?
3. **If YES**: Show local dishes (fast!) and return
4. **If NO**: Call Gemini AI to suggest 3 dishes
5. **Filter & merge**: Keep only relevant suggestions
6. **Backfill recipes**: Generate missing recipe text
7. **Display**: Show TOP 3 to user

The system is defensive:
- Handles malformed JSON gracefully
- Falls back to local if AI fails
- Relaxes filters for edge cases (single ingredient)
- Always shows something (never blank)


