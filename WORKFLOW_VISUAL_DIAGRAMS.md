# Luồng Nhận Diện & Gợi Ý Món Ăn - Visual Flowcharts

## 🎬 Main Workflow (Luồng Chính)

```
                        ┌─────────────────────┐
                        │  USER SCANS IMAGE   │
                        │  (Camera Screen)    │
                        └────────┬────────────┘
                                 │
                        ┌────────▼────────┐
                        │  Gemini Vision  │
                        │  Detect Items   │
                        │  "gà,cà chua"   │
                        └────────┬────────┘
                                 │
                        ┌────────▼──────────┐
                        │  parseDetected    │
                        │  Ingredients()    │
                        │  [item1,item2]    │
                        │  (error handling) │
                        └────────┬──────────┘
                                 │
                        ┌────────▼──────────────┐
                        │  buildEffective      │
                        │  Ingredients()       │
                        │ + manual ingredients │
                        └────────┬──────────────┘
                                 │
                        ┌────────▼───────────────────┐
                        │  findUncovered             │
                        │  Ingredients()            │
                        │  Check foods.json         │
                        └────────┬───────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
             ✅ []  │    ❌ Has uncovered    │
         (All items │                        │
          in foods) │                        │
                    │                        │
         ┌──────────▼─────────┐   ┌─────────▼──────────────┐
         │  Try LOCAL first   │   │  FORCE AI PATH         │
         │  suggestDishes()   │   │  (forceAiFor Unknown..)│
         │  [strict match]    │   └─────────┬──────────────┘
         │                    │             │
         ▼                    │   ┌─────────▼──────────────┐
    Has local?                │   │  Call GEMINI with      │
    ├─ YES:                   │   │  special prompt        │
    │  ├─ Has combo match?    │   │  (3 dishes exactly)    │
    │  │  ├─ YES:             │   │                        │
    │  │  │  └─ RETURN local  │   └─────────┬──────────────┘
    │  │  │      (skip AI!)   │             │
    │  │  └─ NO: Continue     │   ┌─────────▼──────────┐
    │  └─ Try relaxed         │   │  parseGemini       │
    │     suggestDishes()     │   │  Suggested         │
    │                         │   │  Dishes()          │
    └─ NO: Continue           │   │  [dish1,2,3]       │
                              │   │  (error handling)  │
                              │   └─────────┬──────────┘
                              │             │
                              └─────────┬───┘
                                        │
                             ┌──────────▼──────────┐
                             │  buildAISuggestion  │
                             │  Items()            │
                             │  - Sanitize matched │
                             │  - Sanitize missing │
                             │  - Filter relevance │
                             │  - Merge w/ local   │
                             │  - Fallback check   │
                             └──────────┬──────────┘
                                        │
                             ┌──────────▼──────────┐
                             │  combinedDishItems  │
                             │  result?            │
                             └──────────┬──────────┘
                                        │
                        ┌───────────────┴────────────────┐
                        │                                │
                   ✅ exists               ❌ empty
                        │                                │
                     ┌──▼──────────────┐    ┌──────────▼──────┐
                     │request Recipes  │    │Has local fallback
                     │for AI Dishes()  │    │& NOT forced AI?
                     │(backfill if     │    │                 │
                     │empty)           │    ├─ YES: use local │
                     └──┬──────────────┘    ├─ NO: show error │
                        │                   └──────────────────┘
                     ┌──▼──────────────┐
                     │finishRecognition│
                     │Success()        │
                     │                 │
                     │rankAndLimit()   │
                     │TOP 3            │
                     └──┬──────────────┘
                        │
                     ┌──▼──────────────────┐
                     │ Render UI            │
                     │ Show dish suggestion │
                     │ modal                │
                     └─────────────────────┘
```

---

## 🔀 Decision Logic: LOCAL vs AI

```
                    START
                      │
        ┌─────────────▼──────────────┐
        │ Has UNCOVERED ingredients? │
        │ (items not in foods.json)  │
        └──────────┬────────────────┬┘
                   │                │
              🟢 NO │                │ 🔴 YES
                   │                │
        ┌──────────▼──┐   ┌─────────▼────────────┐
        │ Try LOCAL    │   │ MUST use AI!        │
        │ suggestions  │   │ forceAiForUnknown   │
        │              │   │ = true              │
        └──────┬───────┘   └────────┬────────────┘
               │                    │
        ┌──────▼──────┐      ┌──────▼────────────┐
        │ Found ANY   │      │ Call GEMINI       │
        │ local dish? │      │ buildDishSuggestion
        └──┬──────┬───┘      │ Prompt()          │
      🟢 YES│  🔴 NO         │ "3 dishes must"   │
           │      │         └──────┬────────────┘
        ┌──▼───┐  │               │
        │Check │  │        ┌──────▼────────────┐
        │local │  │        │ Gemini returns:   │
        │combo │  │        │ {dishes: [...]}   │
        │match?│  │        └──────┬────────────┘
        └──┬───┘  │               │
      YES  │ NO   │        ┌──────▼────────────┐
          │   │   │        │ parseSuggested   │
          │   │   │        │ Dishes()         │
          │   │   │        │ Filter relevance │
          │   │   │        │ onAiSuggest...() │
          │   │   │        └──────┬───────────┘
          │   │   │               │
          │   │   │        ┌──────▼────────────┐
          │   │   │        │ Any valid?        │
          │   │   │        └───┬──────────┬────┘
          │   │   │           │          │
          │   │   │         YES│         NO
          │   │   │           │          │
          │   │   └───────┬───┘          │
          │   │           │             │
        ┌─▼───▼─┐  ┌──────▼──────┐   ┌──▼──────────┐
        │RETURN │  │ RETURN      │   │ Show ERROR  │
        │LOCAL  │  │ AI RESULTS  │   │ "Not found" │
        │(DONE!)│  │ (DONE!)     │   └─────────────┘
        └───────┘  └─────────────┘
```

---

## 🔍 Relevance Filtering (isAiSuggestionRelevant)

```
┌─ For each AI-suggested dish ─┐
│                              │
├─ Check 1: Has matched?        │
│  matched > 0?                  │
│  🟢 YES → Continue             │
│  🔴 NO  → ❌ SKIP             │
│                               │
├─ Check 2: Coverage            │
│  availableCount >= 2?          │
│  ├─ YES:                      │
│  │  matchedCount < 2?         │
│  │  🔴 YES → ❌ SKIP          │
│  │  🟢 NO  → Continue         │
│  └─ NO (single ingredient):   │
│     → Skip this check          │
│                               │
├─ Check 3: Deep Coverage       │
│  availableCount >= 3?          │
│  ├─ YES:                      │
│  │  coverage >= 50%?          │
│  │  🔴 NO  → ❌ SKIP          │
│  │  🟢 YES → Continue         │
│  └─ NO: → Skip this check      │
│                               │
├─ Check 4: Missing Count       │
│  Calculate maxAllowed:         │
│  if (availableCount <= 1)      │
│    maxAllowed = max(5, matched+3)
│    ↑ Relaxed for single item  │
│  else                          │
│    maxAllowed = max(2, matched)│
│  ↓                            │
│  missingCount > max?           │
│  🔴 YES → ❌ SKIP             │
│  🟢 NO  → Continue            │
│                               │
└─ Check 5: Main ingredient     │
   suggestsUnavailable          │
   MainIngredient?              │
   🔴 YES → ❌ SKIP             │
   🟢 NO  → ✅ KEEP             │
                               │
   Result: Added to suggestions │
```

---

## 📋 Recipe Backfilling Logic

```
                ┌─────────────────────┐
                │ Have suggestions?   │
                │ (combinedDishItems) │
                └────────┬────────────┘
                         │
                ┌────────▼────────────────┐
                │ For each suggestion:    │
                │ Check recipe empty?     │
                │ item.getRecipe()        │
                │ .trim().isEmpty()       │
                └────────┬────────────────┘
           ┌────────────┴────────────────┐
           │                             │
      ✅ NO │   🟡 SOME EMPTY       🔴 ALL
      (all│                             
     have)│        ┌─────────┐
           │        │ Call    │
      Return│       │ GEMINI: │
           │        │ Generate│
      (DONE)        │ recipes │
                    └────┬────┘
                         │
                    ┌────▼────────────┐
                    │parseGenerated   │
                    │Dishes()         │
                    │[dish1, dish2]   │
                    └────┬────────────┘
                         │
                    ┌────▼──────────────┐
                    │mergeGenerated     │
                    │Recipes()          │
                    │- Match by stableId│
                    │- Update recipes   │
                    │- Rank & limit     │
                    └────┬──────────────┘
                         │
                    ┌────▼──────────────┐
                    │finishRecognition  │
                    │Success()          │
                    │Show results       │
                    └───────────────────┘
```

---

## 🏗️ Architecture Layers

```
┌─────────────────────────────────────────────────────┐
│                   UI LAYER                          │
│        (ScanFoodActivity - UI rendering)            │
│  - showSuggestionDialog()                          │
│  - renderDetectedIngredients()                     │
│  - renderSuggestedDishes()                         │
└──────────────────┬────────────────────────────────┘
                   │
┌──────────────────▼────────────────────────────────┐
│              ORCHESTRATION LAYER                   │
│     (ScanFoodActivity - main orchestrator)        │
│  - requestDishSuggestions()                       │
│  - buildEffectiveIngredients()                    │
│  - buildAiSuggestionItems()                       │
│  - requestRecipesForAiDishes()                    │
│  - isAiSuggestionRelevant()                       │
└──────────────────┬────────────────────────────────┘
                   │
┌──────────────────▼────────────────────────────────┐
│         BUSINESS LOGIC LAYER                       │
│    (ScanFoodLocalMatcher - local matching)        │
│  - findUncoveredIngredients()                     │
│  - suggestDishes() / suggestDishesRelaxed()       │
│  - hasCompleteLocalCoverage()                     │
│  - findDishByName()                               │
│  - scoreMatch()                                   │
└──────────────────┬────────────────────────────────┘
                   │
┌──────────────────▼────────────────────────────────┐
│           DATA ACCESS LAYER                        │
│  - FoodJsonRepository (load foods.json)           │
│  - GeminiRepository (AI calls)                    │
│  - Local database (vocabularies)                  │
└─────────────────────────────────────────────────┘
```

---

## 🔄 Sequence Diagram: Complete Flow

```
User          Camera      Vision API    Gemini      App Logic      UI
│               │             │           │              │         │
│──── scan ────→│             │           │              │         │
│               │             │           │              │         │
│               │─ detect ───→│           │              │         │
│               │           ←─ JSON      │              │         │
│               │ image data─────────────→ (analyze)    │         │
│               │                         │              │         │
│               │                      ←─ Ingredients──→│         │
│               │                                    parseDetected()│
│               │                                        │ [gà]    │
│               │                                        │         │
│               │                                        ├─ Local? │
│               │                                        │ check   │
│               │                                        │         │
│               │                                        ├─ YES:   │
│               │                                        │suggest  │
│               │                                        │Dishes() │
│               │                                        │         │
│               │                                    ┌─ Has combo?
│               │                                    ├─ YES:      
│               │                                    │ Return!   ┌──→showSuggestion()
│               │                                    │           │
│               │                                    ├─ NO:      │
│               │                                    │call AI    │
│               │                                    │           │
│               │               buildDishSuggestion()│           │
│               │← ← ← ← ← ← ← ←prompt───────────────→           │
│               │                                    │           │
│               │                            (generate)          │
│               │                                    │           │
│               │           JSON response ←─────────┤           │
│               │        {dishes: [3x]}             │           │
│               │                                    │           │
│               │                            parseSuggested()    │
│               │                            buildAiSuggestion   │
│               │                            Items()             │
│               │                            isAiSuggestion      │
│               │                            Relevant()          │
│               │                                    │           │
│               │              requestRecipesFor────→│           │
│               │              AiDishes()            │           │
│               │                                    │           │
│               │          (if recipe empty)         │           │
│               │                                    │           │
│               │               prompt+recipes       │           │
│               │← ← ← ← ← ← ← ←────────────────────→           │
│               │                                    │           │
│               │                            mergeGenerated()    │
│               │                            rankAndLimit()      │
│               │                                    │           │
│               │                                    ├──────────→──→ Show TOP 3
│               │                                    │ finishRecognition
│               │                                    │ Success()
│               │                                    │
└───────────────┴────────────────────────────────────┴────────────┴────
```

---

## 🛡️ Error Handling Paths

```
┌─────────────────────┐
│ Parsing Error       │
├──────────┬──────────┤
│ Before   │ After   │
├──────────┼──────────┤
│ Entire   │ Single  │
│ batch    │ item    │
│fails ❌  │ skipped │
│          │✅       │
└──────────┴──────────┘
   |                
   | Per-item try/catch
   | → ignoredItem caught
   | → continue loop
   
───────────────────────

┌─────────────────────┐
│ AI Call Error       │
├──────────┬──────────┤
│ onError()│ If had  │
│callback  │ local:  │
│invoked   │return   │
│          │ local   │
└──────────┴──────────┘
   |
   | If no local fallback:
   | show"AI bận, thử lại"
   
───────────────────────

┌─────────────────────┐
│ Empty AI Response   │
├──────────┬──────────┤
│Combined  │Fallback  │
│empty     │check:    │
│check     │if local  │
│          │return it │
└──────────┴──────────┘
   |
   | if NO fallback:
   | show "Không tìm..."
   
───────────────────────

┌─────────────────────┐
│ Recipe Gen Error    │
├──────────┬──────────┤
│ onError()│ Still    │
│ ignored  │ show    │
│          │ dishes │
│          │ (editable)
└──────────┴──────────┘

```

---

## 📊 State Transitions

```
STATES:
1. INITIAL
2. DETECTING_INGREDIENTS
3. CHECKING_LOCAL
4. CALLING_AI
5. FILTERING_SUGGESTIONS
6. GENERATING_RECIPES
7. DISPLAYING_RESULTS
8. ERROR


TRANSITIONS:

INITIAL
  ├─ onCameraCapture()
  └─→ DETECTING_INGREDIENTS

DETECTING_INGREDIENTS
  ├─ AI Success → parseDetectedIngredients()
  ├─→ CHECKING_LOCAL
  └─ AI Fail → onError() → ERROR

CHECKING_LOCAL
  ├─ All in DB + has combination
  ├─→ DISPLAYING_RESULTS (local only)
  └─ Some missing
    └─→ CALLING_AI

CALLING_AI
  ├─ Gemini Success → FILTERING_SUGGESTIONS
  └─ Gemini Fail → fallback to local or ERROR

FILTERING_SUGGESTIONS
  ├─ buildAiSuggestionItems()
  ├─→ GENERATING_RECIPES (if empty recipe)
  └─→ DISPLAYING_RESULTS (if recipes complete)

GENERATING_RECIPES
  ├─ Success → mergeGeneratedRecipes()
  ├─→ DISPLAYING_RESULTS
  └─ Fail → still go to DISPLAYING_RESULTS

DISPLAYING_RESULTS
  ├─ Items > 0 → Show modal
  └─ Items = 0 → ERROR

ERROR
  └─ Show error message, allow retry
```

---

## 🎯 Key Decision Points

| # | Decision | Condition | Path A | Path B |
|---|----------|-----------|--------|---------|
| 1 | Use Local? | Any uncovered? | NO → Local | YES → AI |
| 2 | Early Return? | Local + combo match? | YES → Return | NO → Continue |
| 3 | Keep AI Dish? | isRelevant()? | YES → Add | NO → Skip |
| 4 | Use Local Version? | Exists in DB? | YES → Use | NO → Use AI |
| 5 | Generate Recipe? | Recipe empty? | YES → Generate | NO → Keep |
| 6 | Show Error? | Results empty? | YES → Error | NO → Display |

---

## 💾 Data Flow Diagram

```
┌─ Input: Image Bytes ─┐
│                      │
└──────────┬───────────┘
           │
    ┌──────▼────────────┐
    │ Gemini Vision     │
    │ Detection         │
    └──────┬────────────┘
           │
    ┌──────▼──────────────────────┐
    │ DetectedIngredient[]         │
    │ [{name: "gà",               │
    │   confidence: 0.95,         │
    │   ...}]                     │
    └──────┬──────────────────────┘
           │
    ┌──────▼───────────────────────────────┐
    │ buildEffectiveIngredients()          │
    │ Add manual user-supplied ingredients │
    └──────┬────────────────────────────────┘
           │
    ┌──────▼─────────────────────┐
    │ findUncoveredIngredients() │
    │ List<String> uncovered     │
    └──────┬─────────────────────┘
           │
    ┌──────┴────────┐
    │               │
    ▼               ▼
 LOCAL          AI PATH
 PATH           │
 │              │
 │        ┌─────▼──────────────┐
 │        │ buildDishSuggestion│
 │        │ Prompt()           │
 │        │ with uncovered     │
 │        └─────┬──────────────┘
 │              │
 │        ┌─────▼───────────────┐
 │        │ Gemini API Call     │
 │        │ (network request)   │
 │        └─────┬───────────────┘
 │              │
 │        ┌─────▼──────────────────────┐
 │        │ SuggestedDish[]            │
 │        │ [{name: "...",             │
 │        │   matchedIngredients: [],  │
 │        │   recipe: "...",           │
 │        │   ...}]                    │
 │        └─────┬──────────────────────┘
 │              │
 │        ┌─────▼─────────────────────┐
 └───────→│ ScanDishItem[]             │
          │(final results)             │
          │ [{stableId, name,          │
          │   localFood,               │
          │   usedIngredients,         │
          │   missingIngredients,      │
          │   recipe, ...}]            │
          └────────┬────────────────────┘
                   │
            ┌──────▼──────────┐
            │Display to User  │
            │showSuggestion() │
            │Dialog()         │
            └─────────────────┘
```

This comprehensive guide should help you understand the complete workflow of the food recognition system!


