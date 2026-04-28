# CoolCook Food Recognition System - Complete Documentation

## 📚 Documentation Files Created

This package contains complete documentation for the food recognition and dish suggestion system:

1. **WORKFLOW_EXPLANATION.md** - Comprehensive step-by-step explanation
2. **WORKFLOW_QUICK_REFERENCE.md** - Quick reference guide
3. **WORKFLOW_VISUAL_DIAGRAMS.md** - Visual flowcharts and diagrams
4. **README.md** - This file

---

## 🎯 System Overview (TL;DR)

The CoolCook app uses a **two-path hybrid system** for food recognition:

### **Path 1: LOCAL (Fast)**
- User scans food → AI detects ingredients
- System checks: Are all ingredients in `foods.json`?
- ✅ YES → Return local dishes immediately (NO AI CALL)
- ❌ NO → Go to Path 2

### **Path 2: AI (Smart)**
- Unknown ingredients detected → Call Gemini API
- Gemini must return **exactly 3 suggestions**
- Filter by relevance (matched > 0, coverage, etc.)
- Backfill missing recipes if needed
- Display results to user

### **Key Constraint**: If one ingredient is unknown → trigger AI for smart suggestions

---

## 🔍 What Happens Step-by-Step

### **1. Detection Phase**
```
Vision API analyzes image → Extracts ingredients
Output: JSON {"detectedIngredients": [{"name": "gà", "confidence": 0.95}]}

Inside app:
- parseDetectedIngredients() 
- Each item wrapped in try/catch (one bad item doesn't break batch)
- Result: List<DetectedIngredient>
```

### **2. Enrichment Phase**
```
Combine detected + manual user additions
buildEffectiveIngredients()
Result: Complete ingredient list
```

### **3. Analysis Phase**
```
Check: Do we have everything in local database?
findUncoveredIngredients()
Returns: List of ingredients NOT in foods.json

Result:
- [] (empty) → hasCompleteLocalCoverage = true
- ["item1", "item2"] → forceAiForUnknownIngredients = true
```

### **4. Suggestion Phase**
```
Decision: Local or AI?

IF hasCompleteLocalCoverage:
  ├─ suggestDishes() → Get local matches
  ├─ Check: hasLocalCombinationMatch()?
  │  ├─ YES: Return local, DON'T call AI
  │  └─ NO: Call AI anyway
  └─ If empty, try suggestDishesRelaxed()

IF forceAiForUnknownIngredients:
  └─ Call Gemini API directly

ELSE (shouldn't happen):
  └─ Try fallback logic
```

### **5. Filtering Phase**
```
For each AI suggestion:
buildAiSuggestionItems()

├─ Check: isAiSuggestionRelevant()?
│  ├─ matchedIngredients > 0? (must match something)
│  ├─ 2+ ingredients? → must match >= 2
│  ├─ 3+ ingredients? → coverage >= 50%
│  │  BUT 1 ingredient: relaxed (max 5 missing allowed)
│  └─ missingIngredients <= max_allowed?
│
├─ Check: exists in local DB?
│  ├─ YES: Use local version (better recipe)
│  └─ NO: Keep AI version
│
└─ Add to results
```

### **6. Recipe Phase**
```
Check: Any AI dishes with empty recipe?
requestRecipesForAiDishes()

IF YES:
  ├─ Call Gemini: "Generate full recipe for these dishes"
  ├─ parseGeneratedDishes() → Extract JSON
  ├─ mergeGeneratedRecipes() → Update dish items
  └─ Continue to Display

IF NO:
  └─ Skip, go to Display directly
```

### **7. Display Phase**
```
rankAndLimitDishItems() → Sort by quality
├─ Matched ingredients count (most wins)
├─ Is local? (local > AI)
├─ Health filter match?
└─ Confidence score

RESULT: Top 3 dishes → Show in modal
```

---

## 🚨 Critical Decisions Made

### **Decision 1: One Bad Ingredient Shouldn't Break Batch**
**What**: Wrap each JSON item in try/catch
**Why**: Network/AI parsing can produce malformed responses
**Effect**: Skip bad item, keep rest

```java
for (int index = 0; index < array.length(); index++) {
    try {
        // Process item
    } catch (Exception ignoredItem) {
        // Skip and continue
    }
}
```

### **Decision 2: Unknown Ingredients Force AI**
**What**: If ANY ingredient not in foods.json → use AI
**Why**: System must handle new foods gracefully
**Effect**: Smart suggestions for unknown items

```java
List<String> uncovered = findUncoveredIngredients(...);
if (!uncovered.isEmpty()) {
    forceAiForUnknownIngredients = true;  // ← AI path
}
```

### **Decision 3: Local-Only Fast Path**
**What**: If all ingredients in local DB + combination match → return local suggestions WITHOUT calling AI
**Why**: Fast, reliable, no network latency
**Effect**: Most common case (known ingredients) returns in milliseconds

```java
if (hasCompleteLocalCoverage && hasLocalCombinationMatch && !localSuggestions.isEmpty()) {
    return localSuggestions;  // ← Skip AI call entirely
}
```

### **Decision 4: Relaxed Single-Ingredient Filtering**
**What**: When user has only 1 ingredient (e.g., "gà"), allow more missing
**Why**: Single ingredient can match many dishes with missing components
**Effect**: Single ingredient like "gà" can suggest dishes with 5+ additional ingredients

```java
if (availableCount <= 1) {
    maxAllowedMissing = Math.max(5, matchedCount + 3);  // Relaxed!
} else {
    maxAllowedMissing = Math.max(2, matchedCount);       // Strict
}
```

### **Decision 5: AI Dishes Can Appear Without Initial Recipe**
**What**: Add AI suggestions even if recipe empty initially
**Why**: Better UX - show something, backfill recipe later
**Effect**: User sees suggestions faster, recipe loads after

```java
// Later: requestRecipesForAiDishes() backfills
```

### **Decision 6: Local Version Preferred Over AI Version**
**What**: If AI suggests dish already in local DB → use local version
**Why**: Local has better recipe, guaranteed quality
**Effect**: AI suggestions merged with local DB knowledge

```java
FoodItem localFood = scanFoodLocalMatcher.findDishByName(suggestedDish.getName());
if (localFood != null) {
    // Use local version instead of AI
}
```

---

## 📊 Data Models Used

### **DetectedIngredient** (from Vision API)
```java
name: String              // "gà"
confidence: Double        // 0.95 (95% sure)
category: String          // "meat", "vegetable"
visibleAmount: String     // "2 thứa", "một chút"
notes: String             // "tự thêm" or notes from AI
```

### **SuggestedDish** (from Gemini)
```java
name: String              // "Gà nướng"
usedIngredients: List     // ["gà", "ớt", "nước mắm"]
missingIngredients: List  // ["nước tương"]
reason: String            // "Vì gà + ớt nên phù hợp"
recipe: String            // "### Gà nướng..."
healthTags: List          // ["ít dầu", "tăng cơ"]
confidence: Double        // 0.85 (85% sure)
```

### **ScanDishItem** (final result)
```java
stableId: String          // "local:gà_nướng" or "ai:gà_nướng"
name: String              // Display name
localFood: FoodItem|null  // Reference to local DB if exists
usedIngredients: List     // Sanitized
missingIngredients: List  // Sanitized
reason: String            // Why recommended
recipe: String            // Full recipe text
healthTags: List          // Health info
confidence: Double        // Match confidence
isLocal: Boolean          // From DB or AI?
```

---

## 🔑 Key Methods & Their Purpose

| Method | File | Does What |
|--------|------|-----------|
| `requestDishSuggestions()` | ScanFoodActivity | Main orchestrator entry point |
| `findUncoveredIngredients()` | ScanFoodLocalMatcher | Detects unknown ingredients |
| `hasCompleteLocalCoverage()` | ScanFoodLocalMatcher | All ingredients in local DB? |
| `suggestDishes()` | ScanFoodLocalMatcher | Get local suggestions (strict) |
| `suggestDishesRelaxed()` | ScanFoodLocalMatcher | Get local suggestions (loose) |
| `hasLocalCombinationMatch()` | ScanFoodActivity | Any local dish uses ALL ingredients? |
| `buildDishSuggestionPrompt()` | ScanFoodActivity | Create Gemini prompt with constraints |
| `buildAiSuggestionItems()` | ScanFoodActivity | Filter & merge AI suggestions |
| `isAiSuggestionRelevant()` | ScanFoodActivity | Check if suggestion passes all filters |
| `requestRecipesForAiDishes()` | ScanFoodActivity | Generate missing recipes |
| `mergeGeneratedRecipes()` | ScanFoodActivity | Backfill recipes into suggestions |
| `parseDetectedIngredients()` | ScanFoodActivity | Extract from Vision API response |
| `parseSuggestedDishes()` | ScanFoodActivity | Extract from Gemini suggestion |
| `parseGeneratedDishes()` | ScanFoodActivity | Extract from recipe generation |
| `rankAndLimitDishItems()` | ScanFoodActivity | Sort and take TOP 3 |
| `finishRecognitionSuccess()` | ScanFoodActivity | Display results to user |

---

## ⚙️ Important Thresholds

### Ingredient Matching
- **90+**: Direct dish name match ("gà" → "Gà nướng" in DB)
- **88+**: Strict ingredient vocabulary match
- **72+**: Recipe ingredient to detected ingredient
- **70+**: Fuzzy dish lookup

### AI Suggestion Relevance
- **matched > 0**: Must have at least 1 matched ingredient
- **2+ ingredients**: Must match ≥ 2 items
- **3+ ingredients**: Must have ≥ 50% coverage
- **Single ingredient**: Allow up to max(5, matched+3) missing

### Results
- **TOP 3**: Maximum suggestions to show

---

## 🎬 Real-World Scenarios

### Scenario 1: Known Ingredient ("Gà")
```
Input: Photo of chicken
↓
Vision AI: "Detected: gà (95% confidence)"
↓
findUncoveredIngredients(): [] (gà is in foods.json)
↓
LOCAL PATH: suggestDishes()
↓
Found: ["Gà nướng", "Gà xào", "Cơm gà"]
↓
hasLocalCombinationMatch(): YES
↓
✅ RETURN local suggestions
   (NO AI CALL - instant!)
```

### Scenario 2: Unknown Ingredient ("Spice X")
```
Input: Photo with unknown spice
↓
Vision AI: "Detected: spice X (unknown)"
↓
findUncoveredIngredients(): ["spice X"]
↓
forceAiForUnknownIngredients = true
↓
AI PATH: Call Gemini with special prompt
   "Have spice X, must suggest 3 dishes"
↓
Gemini: {
  dishes: [
    {name: "Gà nướng kiểu exotic", recipe: "..."},
    {name: "Gà xào đặc biệt", recipe: "..."},
    {name: "Canh gà cay", recipe: "..."}
  ]
}
↓
Filter relevance, merge with local DB
↓
Add recipes if empty
↓
✅ SHOW 3 AI suggestions
```

### Scenario 3: Multiple Known + Unknown
```
Input: "Gà + Cà chua + Unknown herb"
↓
Vision AI detects all 3
↓
findUncoveredIngredients(): ["Unknown herb"]
↓
forceAiForUnknownIngredients = true
↓
AI PATH optimized with context:
  "Use gà + cà chua + unknown herb to suggest dishes"
↓
Gemini suggests 3 dishes that fit
↓
✅ SMART suggestions considering unknown item
```

### Scenario 4: Single Ingredient ("Gà")
```
Input: Just chicken
↓
Vision AI: "gà"
↓
LOCAL PATH: suggestDishes()
  ├─ Strict: Find dishes with gà
  ├─ Found few or none
  └─ Try suggestDishesRelaxed()
↓
hasLocalCombinationMatch(): YES (gà is the main ingredient)
↓
✅ Show local dishes
   (may have many missing ingredients, but that's OK for 1 ingredient)
```

---

## 🚨 Error Handling Strategy

### **Parsing Errors**
```
Problem: One malformed JSON item
Solution: try/catch per item
Result: Bad items skipped, rest processed fine
```

### **Unknown Ingredients**
```
Problem: "spice_XYZ_unknown" not in foods.json
Solution: Detected → trigger AI path
Result: Smart suggestions using AI reasoning
```

### **AI API Failures**
```
Problem: Gemini API error/timeout
Solution: fallback to local suggestions if available
Result: User still gets suggestions (local) instead of error
```

### **Empty Results**
```
Problem: AI returns empty array
Solution: 
  1. Show local fallback if available
  2. If no fallback: show error "Không tìm thấy món phù hợp"
Result: User aware nothing matches, can retry
```

### **Recipe Generation Failures**
```
Problem: Recipe generation API error
Solution: Still show dishes (recipe might be from AI response)
Result: Dishes appear, user can view/edit without full recipe
```

---

## 📈 Performance Characteristics

### Fast Path (Local Only)
- **Time**: ~50-200ms
- **Network calls**: 0 (no API)
- **When**: All ingredients in foods.json

### Medium Path (AI Suggestion)
- **Time**: ~3-5s
- **Network calls**: 1 (Gemini suggestion)
- **When**: Some unknown ingredients

### Slow Path (With Recipe Generation)
- **Time**: ~5-7s
- **Network calls**: 2 (Suggestion + Recipe)
- **When**: AI dishes need recipe backfill

### Optimization Used
1. ✅ Fast path prioritized (no unnecessary AI)
2. ✅ Recipe generation only when needed
3. ✅ Parallel processing for non-blocking UI
4. ✅ Limited results (TOP 3)

---

## 📱 User Experience Flow

```
┌─────────────────┐
│ 1. Camera Screen│
└────────┬────────┘
         │ [Scan]
         ▼
┌───────────────────────┐
│ 2. "Scanning..."      │
│ Gemini analyzing...   │
└────────┬──────────────┘
         │
         ▼
┌────────────────────────┐
│ 3. "Analyzing results" │
│ (Local or AI path)     │
└────────┬───────────────┘
         │
         ▼
┌────────────────────────┐
│ 4. Results Modal       │
│ ┌──────────────────┐   │
│ │ Gà nướng (Best)  │   │
│ │ ✓ gà, ớt         │   │
│ │ ✗ nước tương     │   │
│ ├──────────────────┤   │
│ │ Gà xào           │   │
│ │ Cơm gà           │   │
│ └──────────────────┘   │
│ [Select] [Add More]    │
└────┬───────────────────┘
     │ [Choose]
     ▼
┌──────────────────────┐
│ 5. Meal Journal      │
│ Add quantity, notes  │
└──────────────────────┘
```

---

## 🎓 For New Developers

1. **Start with**: `ScanFoodActivity.requestDishSuggestions()` method
2. **Understand**: The three core questions:
   - Are all ingredients in local DB?
   - Should we call AI?
   - Are suggestions relevant enough?
3. **Key insight**: System optimizes for the common case (known ingredients)
4. **Error strategy**: Fail gracefully, always show something (or error)
5. **Testing**: Test with single ingredient, multiple, and unknown items

---

## 📚 Related Files

```
coolcook-mobileapp/
├── app/src/main/java/com/coolcook/app/feature/camera/
│   ├── ui/
│   │   └── ScanFoodActivity.java          [Main orchestrator]
│   ├── data/
│   │   ├── ScanFoodLocalMatcher.java      [Local matching logic]
│   │   └── ScanHealthFilters.java         [Health category matching]
│   └── model/
│       ├── DetectedIngredient.java
│       ├── SuggestedDish.java
│       └── ScanDishItem.java
├── app/src/main/res/
│   └── layout/
│       ├── activity_scan_food.xml         [UI layout]
│       └── bottom_sheet_scan_suggestions.xml
├── app/src/main/assets/
│   └── foods.json                         [Local ingredient database]
├── app/src/main/java/com/coolcook/app/feature/
│   ├── chatbot/data/GeminiRepository.java [API client]
│   └── search/
│       ├── model/FoodItem.java
│       └── data/FoodJsonRepository.java
└── docs/WORKFLOW_*.md                     [This documentation]
```

---

## ✅ Features Implemented

- [x] Graceful error handling for malformed JSON
- [x] Single item error doesn't break batch
- [x] Unknown ingredients trigger AI path
- [x] AI generates exactly 3 suggestions
- [x] Relevance filtering with special single-ingredient handling
- [x] Recipe backfilling for AI dishes
- [x] Local version preferred when dish exists in DB
- [x] Fallback to local suggestions if AI fails
- [x] Top 3 ranking by quality metrics
- [x] Fast local-only path optimization

---

## ⚠️ Known Limitations

- AI model (Gemini) quality depends on prompt precision
- Single unknown ingredient may get poor suggestions if too vague
- Local database (foods.json) must be comprehensive for fast path
- Recipe generation needs formatting awareness (Markdown)
- Network latency adds 3-5s to AI path

---

## 🔮 Future Improvements

- [ ] Add LLAMA fallback when AI returns empty
- [ ] Cache ingredient-to-dish mappings
- [ ] Progressive loading: show local suggestions while AI loads
- [ ] User feedback loop: learn from ignored suggestions
- [ ] Multi-language support for prompts
- [ ] Estimated cook time prediction
- [ ] Nutritional info calculation

---

## 📞 Support & Questions

For questions about the implementation:

1. Check the detailed explanation in `WORKFLOW_EXPLANATION.md`
2. Review flowcharts in `WORKFLOW_VISUAL_DIAGRAMS.md`
3. Look up methods in `WORKFLOW_QUICK_REFERENCE.md`
4. Search in the source files directly

---

**Last Updated**: 2024-12-28  
**System**: CoolCook Mobile Food Recognition App  
**Version**: Post-context-continuation  


