# Luồng Nhận Diện Thực Phẩm và Gợi Ý Món Ăn - CoolCook

## 📊 Sơ Đồ Tổng Thể Luồng (Overall Flow)

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. NGƯỜI DÙNG CHỤP ẢNH THỰC PHẨM                               │
└─────────────────┬───────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. AI NHẬN DIỆN THÀNH PHẦN (Gemini Vision)                      │
│ parseDetectedIngredients() - Phân tích JSON response            │
│ - Mỗi ingredient wrap trong try/catch riêng                    │
│ - Nếu 1 item lỗi, các item khác vẫn được lưu                  │
└─────────────────┬───────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. XÂY DỰNG DANH SÁCH NGUYÊN LIỆU (effectiveIngredients)        │
│ buildEffectiveIngredients() - Kết hợp:                         │
│ - Thành phần nhận diện từ AI                                   │
│ - Thành phần bổ sung manual từ người dùng (currentExtraIngredients)
└─────────────────┬───────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. KIỂM TRA COVERAGE TRONG LOCAL DATABASE (foods.json)         │
│ findUncoveredIngredients() - Xác định:                         │
│ - ❌ Không có: Use AI path                                     │
│ - ✅ Có đủ: Try local suggestions trước                        │
└─────────────────┬───────────────────────────────────────────────┘
                  │
         ┌────────┴────────┐
         │                 │
         ▼                 ▼
    ✅ LOCAL PATH       ❌ AI PATH
         │                 │
    (Nếu có coverage)   (Nếu không đủ)
         │                 │
         ▼                 ▼
┌─────────────────┐   ┌──────────────────────────────────────────┐
│ suggestDishes() │   │ Gọi Gemini để gợi ý                     │
│ + fallback      │   │ buildDishSuggestionPrompt()             │
│ suggestDishesRelaxed()│ - Prompt chi tiết:                      │
│ ────────────┬───┘   │  • Danh sách ingredients                │
│             │       │  • Local suggestions tham khảo          │
│             │       │  • Uncovered ingredients                │
│             │       │  • Rules: phải 3 món, match ingredients │
│             │       └──────────────┬───────────────────────────┘
│             │                      │
│             │                      ▼
│             │       ┌──────────────────────────────────────────┐
│             │       │ Gemini Response:                         │
│             │       │ - Trả JSON với 3 dishes                 │
│             │       │ - Mỗi dish có:                          │
│             │       │   • name, recipe, reason                │
│             │       │   • matchedIngredients                  │
│             │       │   • missingIngredients                  │
│             │       │   • healthTags, confidence              │
│             │       └──────────────┬───────────────────────────┘
│             │                      │
│             │                      ▼
│             │       ┌──────────────────────────────────────────┐
│             │       │ parseSuggestedDishes()                   │
│             │       │ - Parse JSON response                   │
│             │       │ - Mỗi dish wrap try/catch riêng         │
│             │       │ - Tạo SuggestedDish objects             │
│             │       └──────────────┬───────────────────────────┘
│             │                      │
│             │                      ▼
│             │       ┌──────────────────────────────────────────┐
│             │       │ buildAiSuggestionItems()                 │
│             │       │ - Lọc qua isAiSuggestionRelevant()       │
│             │       │   ✓ Phải matchedIngredients > 0         │
│             │       │   ✓ Phải 2+ ingredients (relaxed nếu 1) │
│             │       │   ✓ Phải coverage >= 50% (nếu 3+)       │
│             │       │   ✓ missingIngredients <= max allowed    │
│             │       │ - Merge with local version if exists    │
│             │       │ - Add AI dishes (even if recipe empty)  │
│             │       └──────────────┬───────────────────────────┘
│             │                      │
│             └──────────┬───────────┘
│                        │
│                        ▼
┌───────────────────────────────────────────────────────────────┐
│ 5. KIỂM TRA KẾT QUẢ (combinedDishItems)                       │
│ - ✅ Có kết quả: Gọi requestRecipesForAiDishes() để bổ sung   │
│   công thức nếu empty                                         │
│ - ❌ Không có:                                                │
│   - Nếu allowLocalFallback = true: Trả về local suggestions  │
│   - Nếu forceAiForUnknownIngredients: Gọi LLAMA fallback     │
│   - Nếu không: Hiển thị "Chưa tìm thấy món phù hợp"         │
└──────────────┬──────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 6. BỔ SUNG CÔNG THỨC (requestRecipesForAiDishes)               │
│ - Nếu AI dish có recipe trống:                                │
│   • Gọi Gemini để generate công thức chi tiết                │
│   • parseGeneratedDishes() - Parse recipe response           │
│   • mergeGeneratedRecipes() - Merge vào dish items            │
│ - Gọi rankAndLimitDishItems() sắp xếp kết quả                │
└──────────────┬───────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 7. HIỂN THỊ KẾT QUẢ (finishRecognitionSuccess)                 │
│ - Render detected ingredients                                 │
│ - Hiển thị dish suggestions trong modal                       │
│ - Cho phép user tương tác, chỉnh sửa, lưu journal             │
└──────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Chi Tiết Từng Bước (Step-by-Step)

### **Step 1: Camera Capture & Image Recognition**

**File**: `ScanFoodActivity.java`

```java
// Người dùng bấm nút chụp ảnh
// Camera Framework -> Gemini Vision API
// Kết quả: JSON string có detectedIngredients array
```

---

### **Step 2: Parse Detected Ingredients (Phân Tích Thành Phần)**

**File**: `ScanFoodActivity.java` (lines 3226-3258)

```java
private List<DetectedIngredient> parseDetectedIngredients(@NonNull String rawText) {
    List<DetectedIngredient> ingredients = new ArrayList<>();
    try {
        JSONObject rootObject = new JSONObject(extractJsonPayload(rawText));
        JSONArray array = rootObject.optJSONArray("detectedIngredients");
        
        if (array == null) return ingredients;
        
        for (int index = 0; index < array.length(); index++) {
            try {  // ← TRY/CATCH RIÊNG TỪNG ITEM
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                
                String name = item.optString("name", "").trim();
                if (name.isEmpty()) continue;
                
                // Tạo DetectedIngredient object
                ingredients.add(new DetectedIngredient(
                    name,
                    item.optDouble("confidence", 0d),
                    item.optString("category", "other"),
                    item.optString("visibleAmount", "không rõ"),
                    item.optString("notes", "")
                ));
            } catch (Exception ignoredItem) {
                // ← TỰ ĐỘNG SKIP ITEM LỖI, TIẾP TỤC ITEM TIẾP THEO
                // Điều này đảm bảo 1 thành phần lỗi không làm hỏng toàn bộ batch
            }
        }
    } catch (Exception ignored) {
        return new ArrayList<>();
    }
    return ingredients;
}
```

**Ưu điểm**:
- ✅ Nếu item thứ 2 có error, items 1, 3, 4... vẫn được giữ lại
- ✅ Không fail toàn bộ recognition

---

### **Step 3: Build Effective Ingredients List**

**File**: `ScanFoodActivity.java` (lines 2257-2282)

```java
private List<DetectedIngredient> buildEffectiveIngredients(
        @NonNull List<DetectedIngredient> detectedIngredients) {
    List<DetectedIngredient> effectiveIngredients = new ArrayList<>(detectedIngredients);
    
    // Bổ sung thêm các thành phần manual từ người dùng
    for (String extraIngredient : currentExtraIngredients) {
        String normalizedName = scanFoodLocalMatcher.normalizeIngredientName(extraIngredient);
        
        // Kiểm tra xem đã tồn tại chưa (tránh duplicate)
        boolean exists = false;
        for (DetectedIngredient ingredient : effectiveIngredients) {
            if (scanFoodLocalMatcher.createStableId(ingredient.getName(), false)
                    .equals(scanFoodLocalMatcher.createStableId(normalizedName, false))) {
                exists = true;
                break;
            }
        }
        
        // Thêm mới nếu chưa có
        if (!exists) {
            effectiveIngredients.add(new DetectedIngredient(
                normalizedName, 1d, "other", "tự thêm", "Người dùng bổ sung"
            ));
        }
    }
    return effectiveIngredients;
}
```

**Input**: 
- Ingredients từ AI vision + manual ingredients từ người dùng

**Output**: 
- `effectiveIngredients` = danh sách đầy đủ

---

### **Step 4: Check Local Database Coverage**

**File**: `ScanFoodLocalMatcher.java` (lines 85-109)

```java
public List<String> findUncoveredIngredients(
        @NonNull List<DetectedIngredient> detectedIngredients) {
    List<String> uncovered = new ArrayList<>();
    Set<String> seenNormalized = new LinkedHashSet<>();
    
    for (DetectedIngredient ingredient : detectedIngredients) {
        String rawName = ingredient.getName().trim();
        String normalizedName = normalize(rawName);
        
        if (normalizedName.isEmpty() || seenNormalized.contains(normalizedName)) {
            continue;
        }
        seenNormalized.add(normalizedName);
        
        // Kiểm tra thành phần có trong ingredient vocabulary hay không
        if (!isCoveredByLocalDataStrict(rawName)) {
            uncovered.add(rawName);  // ← Thành phần KHÔNG có trong foods.json
        }
    }
    return uncovered;
}
```

**Matching Logic** (trong `isCoveredByLocalDataStrict`):
1. Kiểm tra xem có dish nào có tên match không (score >= 90)
2. Kiểm tra trong normalized vocabulary
3. Kiểm tra tokenized matching (score >= 88)

**Kết quả**:
- ✅ Nếu `uncovered.isEmpty()` = lấy local suggestions
- ❌ Nếu `uncovered` có thành phần = buộc dùng AI

---

### **Step 5: Local Path or AI Path Decision**

**File**: `ScanFoodActivity.java` (lines 2169-2250)

#### **5a. Chuẩn Bị**
```java
private void requestDishSuggestions(
        @NonNull List<DetectedIngredient> ingredients,
        @NonNull String sourceLabel) {
    
    // Bước 1: Xây dựng danh sách thực
    List<DetectedIngredient> effectiveIngredients = buildEffectiveIngredients(ingredients);
    
    if (effectiveIngredients.isEmpty()) {
        finishRecognitionWithError("Chưa nhận diện được thực phẩm");
        return;
    }
    
    // Bước 2: Kiểm tra uncovered
    List<String> uncoveredIngredients = scanFoodLocalMatcher.findUncoveredIngredients(
        effectiveIngredients);
    final boolean forceAiForUnknownIngredients = !uncoveredIngredients.isEmpty();
    // forceAiForUnknownIngredients = TRUE nếu có thành phần ngoài foods.json
    
    // Bước 3: Lấy local suggestions (ngay cả nếu non-perfect match)
    boolean hasCompleteLocalCoverage = !forceAiForUnknownIngredients;
    List<ScanDishItem> resolvedLocalSuggestions = 
        scanFoodLocalMatcher.suggestDishes(
            effectiveIngredients,
            SUGGESTION_LIMIT,
            selectedHealthFilter);
    
    // Bước 4: Nếu strict version không có kết quả, thử relaxed version
    if (hasCompleteLocalCoverage && resolvedLocalSuggestions.isEmpty()) {
        resolvedLocalSuggestions = scanFoodLocalMatcher.suggestDishesRelaxed(
            effectiveIngredients,
            SUGGESTION_LIMIT,
            selectedHealthFilter);
    }
    final List<ScanDishItem> localSuggestions = resolvedLocalSuggestions;
```

#### **5b. Decision Gate**
```java
    // Kiểm tra có local match không
    final boolean hasLocalCombinationMatch = hasLocalCombinationMatch(
        effectiveIngredients, 
        localSuggestions);
    
    // ❌ EARLY RETURN: Nếu có full local coverage + combination match
    if (hasCompleteLocalCoverage && hasLocalCombinationMatch && !localSuggestions.isEmpty()) {
        runOnUiThread(() -> finishRecognitionSuccess(localSuggestions, sourceLabel));
        return;  // ← RETURN LUÔN, không gọi AI
    }
    
    // Ngược lại: gọi AI
```

**Decision Logic**:
- Điều kiện ALL phải true mới return local suggestions:
  1. `hasCompleteLocalCoverage` (tất cả ingredients có trong foods.json)
  2. `hasLocalCombinationMatch` (có combination của ingredients)
  3. `!localSuggestions.isEmpty()` (local suggestions không trống)

---

### **Step 6: Call Gemini AI for Suggestions**

**File**: `ScanFoodActivity.java` (lines 2199-2234)

```java
geminiRepository.requestStructuredResponse(
    DISH_SUGGESTION_SYSTEM_PROMPT,  // "Bạn là AI gợi ý món ăn cho CoolCook..."
    buildDishSuggestionPrompt(effectiveIngredients, localSuggestions, uncoveredIngredients),
    null, null,
    new GeminiRepository.StreamCallback() {
        @Override
        public void onStart() {
            updateScanStatus("Đang gợi ý món ăn...");
        }
        
        @Override
        public void onCompleted(@NonNull String finalText) {  // ← Nhận response từ Gemini
            ExecutorService executor = recognitionExecutor == null
                    ? Executors.newSingleThreadExecutor()
                    : recognitionExecutor;
            executor.execute(() -> {
                // Parse AI response
                List<ScanDishItem> combinedDishItems = buildAiSuggestionItems(
                    effectiveIngredients,
                    localSuggestions,
                    parseSuggestedDishes(finalText),  // ← Parse JSON
                    forceAiForUnknownIngredients);
                
                runOnUiThread(() -> {
                    if (combinedDishItems.isEmpty()) {
                        finishRecognitionWithError("Chưa tìm thấy món phù hợp");
                    } else {
                        finishRecognitionSuccess(combinedDishItems, sourceLabel);
                    }
                });
            });
        }
        
        @Override
        public void onError(@NonNull String friendlyError) {
            // Fallback nếu AI error
            if (!forceAiForUnknownIngredients && hasCompleteLocalCoverage
                    && hasLocalCombinationMatch && !localSuggestions.isEmpty()) {
                finishRecognitionSuccess(localSuggestions, sourceLabel);
            } else {
                finishRecognitionWithError("AI đang bận, vui lòng thử lại");
            }
        }
    }
);
```

---

### **Step 7: AI Prompt Engineering**

**File**: `ScanFoodActivity.java` (lines 3159-3223)

```java
private String buildDishSuggestionPrompt(
        @NonNull List<DetectedIngredient> ingredients,
        @NonNull List<ScanDishItem> localSuggestions,
        @NonNull List<String> uncoveredIngredients) {
    
    // Tạo prompt với constraints:
    StringBuilder prompt = new StringBuilder();
    
    prompt.append("Nguyên liệu đã nhận diện: ").append(ingredientArray).append('.');
    
    if (!uncoveredIngredients.isEmpty()) {
        prompt.append("Nguyên liệu chưa có trong foods.json: ")
              .append(new JSONArray(uncoveredIngredients)).append('.');
        
        // ← KEY CONSTRAINT: Khi có uncovered ingredients, BUỘC dùng AI
        prompt.append("Khi có nguyên liệu ngoài foods.json, bắt buộc suy luận bằng AI ");
        prompt.append("từ tập nguyên liệu hiện có và trả về ĐÚNG 3 món khác nhau, phù hợp nhất.");
    }
    
    // Main directive
    prompt.append("Hãy gợi ý ĐÚNG 3 món phù hợp nhất có thể nấu từ tập nguyên liệu này.");
    
    // Constraints
    prompt.append("matchedIngredients BẮT BUỘC phải copy đúng nguyên văn từng tên ");
    prompt.append("từ danh sách nguyên liệu hợp lệ. Không được đổi tên hay tự thêm tên mới.");
    
    prompt.append("missingIngredients chỉ được phép là gia vị hoặc nguyên liệu phụ nhỏ.");
    prompt.append("Nếu thiếu nguyên liệu chính của món thì KHÔNG được đề xuất món đó.");
    
    prompt.append("Nếu không tìm được món phù hợp, trả về dishes rỗng [].");
    prompt.append("Không được cố gắng trả về đủ 3 món bằng cách đoán món lệch nguyên liệu.");
    
    // JSON schema
    prompt.append("Chỉ trả về JSON hợp lệ theo schema:\n");
    prompt.append("{\n");
    prompt.append("  \"dishes\": [\n");
    prompt.append("    {\n");
    prompt.append("      \"name\": \"...\",\n");
    prompt.append("      \"matchedIngredients\": [\"...\"],\n");
    prompt.append("      \"missingIngredients\": [\"...\"],\n");
    prompt.append("      \"reason\": \"...\",\n");
    prompt.append("      \"healthTags\": [],\n");
    prompt.append("      \"recipe\": \"### Tên món | ...\",\n");
    prompt.append("      \"confidence\": 0.0\n");
    prompt.append("    }\n");
    prompt.append("  ]\n");
    prompt.append("}");
    
    return prompt.toString();
}
```

**Key Constraints**:
1. ✅ ĐÚNG 3 món (đặc biệt khi có uncovered ingredients)
2. ✅ Không được đoán tên ingredients mới
3. ✅ Phải có recipe trong format CoolCook
4. ✅ Nếu không phù hợp = trả rỗng

---

### **Step 8: Parse AI Response**

**File**: `ScanFoodActivity.java` (lines 3261-3291)

```java
private List<SuggestedDish> parseSuggestedDishes(@NonNull String rawText) {
    List<SuggestedDish> dishes = new ArrayList<>();
    try {
        JSONObject rootObject = new JSONObject(extractJsonPayload(rawText));
        JSONArray array = rootObject.optJSONArray("dishes");  // ← Tìm "dishes" array
        
        if (array == null) return dishes;
        
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) continue;
            
            String name = item.optString("name", "").trim();
            if (name.isEmpty()) continue;
            
            // Tạo SuggestedDish object từ JSON
            dishes.add(new SuggestedDish(
                name,
                toStringList(item.optJSONArray("matchedIngredients")),
                toStringList(item.optJSONArray("missingIngredients")),
                toStringList(item.optJSONArray("healthTags")),
                item.optString("reason", ""),
                item.optString("recipe", ""),
                item.optDouble("confidence", 0d)
            ));
        }
    } catch (Exception ignored) {
        return new ArrayList<>();
    }
    return dishes;
}
```

**Safe Parsing**:
- Không throw exception nếu JSON invalid
- Return empty list nếu error

---

### **Step 9: Build AI Suggestion Items with Filtering**

**File**: `ScanFoodActivity.java` (lines 2323-2393)

```java
private List<ScanDishItem> buildAiSuggestionItems(
        @NonNull List<DetectedIngredient> effectiveIngredients,
        @NonNull List<ScanDishItem> localSuggestions,
        @NonNull List<SuggestedDish> aiSuggestions,
        boolean forceAiForUnknownIngredients) {
    
    List<ScanDishItem> items = new ArrayList<>();
    List<String> seenStableIds = new ArrayList<>();  // ← Avoid duplicates
    
    for (SuggestedDish suggestedDish : aiSuggestions) {
        // Sanitize matched/missing ingredients
        List<String> sanitizedMatchedIngredients = sanitizeMatchedIngredients(
            effectiveIngredients, suggestedDish.getUsedIngredients());
        List<String> sanitizedMissingIngredients = sanitizeMissingIngredients(
            effectiveIngredients, suggestedDish.getMissingIngredients());
        
        // ✅ FILTER: Kiểm tra relevance
        if (!isAiSuggestionRelevant(
                effectiveIngredients,
                suggestedDish,
                sanitizedMatchedIngredients,
                sanitizedMissingIngredients)) {
            continue;  // ← Skip dish này nếu không phù hợp
        }
        
        // ✅ Check: Nếu dish này có trong local database
        FoodItem localFood = scanFoodLocalMatcher.findDishByName(suggestedDish.getName());
        if (localFood != null) {
            // Ưu tiên dùng local version (có recipe đầy đủ)
            String stableId = scanFoodLocalMatcher.createStableId(localFood.getId(), true);
            if (seenStableIds.contains(stableId)) continue;
            
            seenStableIds.add(stableId);
            items.add(new ScanDishItem(
                stableId,
                localFood.getName(),
                localFood,
                sanitizedMatchedIngredients,
                sanitizedMissingIngredients,
                suggestedDish.getHealthTags().isEmpty() 
                    ? localFood.getSuitableFor() 
                    : suggestedDish.getHealthTags(),
                suggestedDish.getReason(),
                localFood.getRecipe(),  // ← Use local recipe
                suggestedDish.getConfidence()
            ));
            if (items.size() >= SUGGESTION_LIMIT) break;
            continue;
        }
        
        // ✅ Nếu là AI dish: thêm nếu có recipe (hoặc sẽ bổ sung sau)
        if (!suggestedDish.getRecipe().trim().isEmpty()) {
            String stableId = scanFoodLocalMatcher.createStableId(suggestedDish.getName(), false);
            if (seenStableIds.contains(stableId)) continue;
            
            seenStableIds.add(stableId);
            items.add(new ScanDishItem(
                stableId,
                suggestedDish.getName(),
                null,  // ← Not a local dish
                sanitizedMatchedIngredients,
                sanitizedMissingIngredients,
                suggestedDish.getHealthTags(),
                suggestedDish.getReason(),
                suggestedDish.getRecipe(),
                suggestedDish.getConfidence()
            ));
            if (items.size() >= SUGGESTION_LIMIT) break;
        }
    }
    
    // ✅ Fallback: Nếu AI suggestions kosong nhưng có local suggestions
    if (items.isEmpty() && !localSuggestions.isEmpty() && !forceAiForUnknownIngredients) {
        return new ArrayList<>(localSuggestions);
    }
    
    return items;
}
```

---

### **Step 10: Relevance Filtering**

**File**: `ScanFoodActivity.java` (lines 2395-2423)

```java
private boolean isAiSuggestionRelevant(
        @NonNull List<DetectedIngredient> effectiveIngredients,
        @NonNull SuggestedDish suggestedDish,
        @NonNull List<String> sanitizedMatchedIngredients,
        @NonNull List<String> sanitizedMissingIngredients) {
    
    // ✅ Filter 1: Phải có matched ingredients
    if (effectiveIngredients.isEmpty() || sanitizedMatchedIngredients.isEmpty()) {
        return false;  // ← Skip nếu không match cái gì
    }
    
    int availableCount = effectiveIngredients.size();
    int matchedCount = sanitizedMatchedIngredients.size();
    
    // ✅ Filter 2: Nếu 2+ ingredients, phải match >= 2
    if (availableCount >= 2 && matchedCount < 2) {
        return false;  // ← Skip nếu có 2 ingredients nhưng chỉ match 1
    }
    
    // ✅ Filter 3: Nếu 3+ ingredients, phải coverage >= 50%
    double coverage = matchedCount / (double) Math.max(1, availableCount);
    if (availableCount >= 3 && coverage < 0.5d) {
        return false;  // ← Skip nếu 3 ingredients mà chỉ match được 1
    }
    
    // ✅ Filter 4: Missing ingredients không được quá nhiều
    int maxAllowedMissing = availableCount <= 1
            ? Math.max(5, matchedCount + 3)  // ← RELAXED cho 1 ingredient case
            : Math.max(2, matchedCount);
    if (sanitizedMissingIngredients.size() > maxAllowedMissing) {
        return false;  // ← Skip nếu thiếu quá nhiều
    }
    
    // ✅ Filter 5: Không được là nguyên liệu chính mà thiếu
    return !suggestsUnavailableMainIngredient(
        effectiveIngredients,
        suggestedDish.getName(),
        sanitizedMatchedIngredients);
}
```

**Single-Ingredient Special Case**:
```
availableCount = 1 (chỉ có "gà")
allowedMissing = max(5, 1 + 3) = max(5, 4) = 5

=> Cho phép thiếu đến 5 ingredient khác
=> "gà" có thể match với các món nhiều nguyên liệu khác
```

---

### **Step 11: Recipe Backfilling (Nếu Công Thức Trống)**

**File**: `ScanFoodActivity.java` (lines 2537-2574)

```java
private void requestRecipesForAiDishes(
        @NonNull List<DetectedIngredient> ingredients,
        @NonNull List<ScanDishItem> dishItems,
        @NonNull String sourceLabel) {
    
    // Kiểm tra có dish nào cần recipe không
    boolean hasEmptyRecipe = false;
    for (ScanDishItem item : dishItems) {
        if (!item.isLocal() && item.getRecipe().trim().isEmpty()) {
            hasEmptyRecipe = true;
            break;
        }
    }
    
    if (!hasEmptyRecipe) {
        // Tất cả đã có recipe
        finishRecognitionSuccess(dishItems, sourceLabel);
        return;
    }
    
    // ← Gọi Gemini để tạo recipe
    updateScanStatus("Đang bổ sung công thức món ăn...");
    geminiRepository.requestStructuredResponse(
        RECIPE_GENERATION_SYSTEM_PROMPT,
        buildRecipeGenerationPrompt(ingredients, dishItems),
        null, null,
        new GeminiRepository.StreamCallback() {
            @Override
            public void onCompleted(@NonNull String finalText) {
                ExecutorService executor = recognitionExecutor == null
                        ? Executors.newSingleThreadExecutor()
                        : recognitionExecutor;
                executor.execute(() -> {
                    // Parse recipe response
                    List<GeneratedDishPayload> generatedRecipes = parseGeneratedDishes(finalText);
                    
                    // Merge vào dishItems
                    List<ScanDishItem> merged = mergeGeneratedRecipes(dishItems, generatedRecipes);
                    
                    runOnUiThread(() -> finishRecognitionSuccess(merged, sourceLabel));
                });
            }
            
            @Override
            public void onError(@NonNull String friendlyError) {
                // Nếu tạo recipe lỗi, vẫn hiển thị dishes (có thể chỉnh sửa sau)
                runOnUiThread(() -> finishRecognitionSuccess(dishItems, sourceLabel));
            }
        }
    );
}
```

**Process**:
1. ✅ Kiểm tra có dish nào có recipe trống
2. ✅ Gọi Gemini generate recipes cho các dish đó
3. ✅ Merge kết quả vào dishes
4. ✅ Display results

---

### **Step 12: Merge Generated Recipes**

**File**: `ScanFoodActivity.java` (lines 2625-2660)

```java
private List<ScanDishItem> mergeGeneratedRecipes(
        @NonNull List<ScanDishItem> sourceItems,
        @NonNull List<GeneratedDishPayload> generatedDishes) {
    
    // Map recipes by dish name
    Map<String, GeneratedDishPayload> payloadByStableId = new LinkedHashMap<>();
    for (GeneratedDishPayload payload : generatedDishes) {
        payloadByStableId.put(
            scanFoodLocalMatcher.createStableId(payload.name, false),
            payload
        );
    }
    
    List<ScanDishItem> mergedItems = new ArrayList<>();
    for (ScanDishItem item : sourceItems) {
        // ✅ Local dishes hoặc đã có recipe từ đầu = giữ nguyên
        if (item.isLocal() || !item.getRecipe().trim().isEmpty()) {
            mergedItems.add(item);
            continue;
        }
        
        // ✅ AI dish không có recipe = tìm recipe mới
        GeneratedDishPayload payload = payloadByStableId.get(item.getStableId());
        if (payload == null) {
            mergedItems.add(item);  // Giữ nguyên nếu không tìm thấy recipe
            continue;
        }
        
        // ✅ Tạo item mới với recipe được bổ sung
        mergedItems.add(new ScanDishItem(
            item.getStableId(),
            item.getName(),
            item.getLocalFood(),
            item.getUsedIngredients(),
            item.getMissingIngredients(),
            payload.healthTags.isEmpty() ? item.getHealthTags() : payload.healthTags,
            payload.reason.trim().isEmpty() ? item.getReason() : payload.reason,
            payload.recipe.trim(),  // ← New recipe
            item.getConfidence()
        ));
    }
    
    // Sắp xếp lại kết quả
    return rankAndLimitDishItems(mergedItems);
}
```

---

### **Step 13: Final Result Display**

**File**: `ScanFoodActivity.java` (lines 2662-2695)

```java
private void finishRecognitionSuccess(
        @NonNull List<ScanDishItem> dishItems,
        @NonNull String sourceLabel) {
    
    isRecognitionInProgress = false;
    setProcessingUiEnabled(true);
    
    // 1. Lưu kết quả
    allSuggestedDishItems.clear();
    allSuggestedDishItems.addAll(rankAndLimitDishItems(dishItems));
    selectedDishItem = null;
    
    // 2. Render UI
    renderDetectedIngredients(currentDetectedIngredients);
    applyDishFilter();
    updateRecognitionPreviewState();
    
    if (allSuggestedDishItems.isEmpty()) {
        updateScanStatus("Chưa tìm thấy món phù hợp");
    } else {
        updateScanStatus("Mở popup gợi ý món ăn");
        showSuggestionDialog();  // ← Hiển thị modal
    }
    
    // 3. Toast notification
    if ("cap nhat".equalsIgnoreCase(sourceLabel)) {
        Toast.makeText(this, "Đã cập nhật gợi ý 1 món", Toast.LENGTH_SHORT).show();
    } else {
        Toast.makeText(this, "Đã nhận diện nguyên liệu từ ảnh " + sourceLabel, Toast.LENGTH_SHORT).show();
    }
}
```

---

## 🔑 Key Decisions & Rules (Quyết Định Quan Trọng)

### **Decision 1: Local vs AI Path**

```
┌─────────────────────────────────────┐
│ findUncoveredIngredients()          │
│ ❌ Có thành phần ngoài foods.json?  │
└────────┬────────────────────────────┘
         │
    ┌────┴────┐
    │         │
   SỬ DỤng   KHÔNG
   LOCAL    │
    │       └──────────┬────────────────────┐
    │                  │                    │
    │           ✅ Gọi AI Gemini      ❌ Error
    │           │ (3 suggestions)     │
    │           │                     │
    ❌ Nếu AI   │                     │
   trả rỗng: ──┤ ← requestRecipesFor  │
   Bật LLAMA   │   AiDishes()         │
   fallback    │   (kiểm tra: empty?) │
              │                     │
              │    ❌ AI empty        │
              │    + force = TRUE     │
              │    = Bật LLAMA        │
              └─────────────────────────┘
```

### **Decision 2: Relevance Filtering**

```
matcher exists?
├─ YES: Use local dish version
│   (Highest quality - best recipe)
│
├─ NO: Check isAiSuggestionRelevant()
│   ├─ matched > 0? (phải có )
│   ├─ 2+ ingredients? match >= 2? 
│   │ (single ingredient = relaxed)
│   ├─ Coverage >= 50% (khi 3+ ingredients)?
│   ├─ Missing <= max allowed?
│   └─ Not main ingredient missing?
│
└─ If pass: Add to suggestions
```

### **Decision 3: Ranking**

```java
Comparators:
1. By usedIngredients.size() DESC (ingredient count)
2. By isLocal (health filter match) DESC
3. By confidence DESC
4. By getName() ASC (alphabetical)

Result: Top SUGGESTION_LIMIT items
```

---

## 📊 Data Flow Diagram

```
┌─────────────┐
│ Image Input │
└──────┬──────┘
       │
       ▼
┌──────────────────────────┐     ┌─────────────────────┐
│ Gemini Vision (detect)   │────→│ parseDetectedIngr.. │
│ "gà, cà chua, dầu"       │     │ [gà, cà chua, dầu] │
└──────────────────────────┘     └────────┬────────────┘
                                          │
                                          ▼
                                 ┌─────────────────────┐
                                 │buildEffectiveIngr.. │
                                 │ + manual ingredients│
                                 └────────┬────────────┘
                                          │
                                          ▼
                                 ┌─────────────────────┐
                                 │findUncoveredIngr... │
                                 │ Check foods.json    │
                                 └─┬──────────────────┘
                                   │
                    ┌──────────────┬┴──────────────┐
                    │              │               │
                ✅ ALL OK     Partial OK        ❌ Unknown
                    │              │               │
                    ▼              ▼               ▼
            ┌───────────────┐  │  ┌───────────────────────┐
            │suggestDishes()│  │  │Gemini Suggestion API  │
            │  (local DB)   │  │  │ buildDishSuggestion.. │
            └───────┬───────┘  │  │ 3 dishes expected     │
                    │          │  └──────────┬────────────┘
                    │          │             │
                    │          └─────┬───────┤
                    │                │ ▼
                    │                │ parseSuggestedDishes()
                    │                │ [dish1, dish2, dish3]
                    │                │
                    └────────┬───────┴────────┐
                             ▼                ▼
                    ┌─────────────────────────────────┐
                    │ buildAiSuggestionItems()        │
                    │ - Sanitize ingredients          │
                    │ - Filter by relevance           │
                    │ - Merge with local if exists    │
                    │ - Try local fallback if empty   │
                    └──────────┬──────────────────────┘
                               │
                               ▼
                    ┌─────────────────────────────────┐
                    │ requestRecipesForAiDishes()     │
                    │ - Check empty recipes           │
                    │ - Generate via Gemini if needed │
                    │ - mergeGeneratedRecipes()       │
                    └──────────┬──────────────────────┘
                               │
                               ▼
                    ┌─────────────────────────────────┐
                    │ finishRecognitionSuccess()      │
                    │ - Rank and limit (TOP 3)        │
                    │ - Display results               │
                    │ - Show suggestion dialog        │
                    └─────────────────────────────────┘
```

---

## ⚙️ Important Constants & Thresholds

| Hằng Số | Giá Trị | Ý Nghĩa |
|---------|--------|---------|
| `SUGGESTION_LIMIT` | 3 | Max suggestions to show |
| `LocalMatcher.scoreMatch >= 88` | 88 | Min score for ingredient match (strict) |
| `LocalMatcher.scoreMatch >= 90` | 90 | Min score for direct dish match |
| `LocalMatcher.scoreMatch >= 72` | 72 | Min score for recipe-to-detected match |
| `LocalMatcher.scoreMatch >= 70` | 70 | Min score for `findDishByName()` |
| `isAiSuggestionRelevant: >= 2` | 2 | Min matched for multi-ingredient |
| `isAiSuggestionRelevant: >= 50%` | 0.5 | Min coverage for 3+ ingredients |
| `isAiSuggestionRelevant: single-ingredient` | 5 | Max allowed missing for 1 ingredient |

---

## 🐛 Error Handling & Fallback

```
┌────────────────────────────────────────────┐
│ Problem: Single ingredient (e.g., "gà")    │
│ Old: Too strict filters = empty suggestions│
│ Fix: Relaxed thresholds for single case    │
└────────────────────────────────────────────┘

┌────────────────────────────────────────────┐
│ Problem: Malformed JSON in ingredient list │
│ Old: 1 bad item = entire recognition fails │
│ Fix: try/catch per item, skip bad ones     │
└────────────────────────────────────────────┘

┌────────────────────────────────────────────┐
│ Problem: AI dishes missing recipes         │
│ Old: Filtered out, not shown to user       │
│ Fix: requestRecipesForAiDishes() backfill  │
└────────────────────────────────────────────┘

┌────────────────────────────────────────────┐
│ Problem: AI returns 0 suggestions          │
│ Current: Show error "Chưa tìm thấy..."     │
│ Future: Can add LLAMA fallback             │
└────────────────────────────────────────────┘
```

---

## 📱 User Journey Example

### **Scenario**: User scans "gà" (chicken)

```
1️⃣  Camera -> Gemini detects "gà"
2️⃣  parseDetectedIngredients() -> [DetectedIngredient("gà")]
3️⃣  buildEffectiveIngredients() -> [gà] (no extra ingredients)
4️⃣  findUncoveredIngredients() -> 
    - "gà" exists in ingredient vocabulary
    - Return [] (empty uncovered list)
5️⃣  forceAiForUnknownIngredients = false  (all covered)
6️⃣  suggestDishes(effectiveIngredients=[gà], ...)
    - Search dishes in foods.json matching "gà"
    - Find: "gà nướng", "gà xào", "cơm gà", etc.
    - Return top 3 local dishes
7️⃣  hasLocalCombinationMatch() = true  (gà found in dishes)
8️⃣  ✅ Return local suggestions directly
    - Skip AI call
    - Show "gà nướng", "gà xào", "cơm gà"

Result: ✅ "Gợi ý: gà nướng, gà xào, cơm gà"
```

### **Scenario 2**: User scans "gà + unknown spice"

```
1️⃣  Camera -> Gemini detects "gà" + "spice X"
2️⃣  parseDetectedIngredients() -> [DetectedIngredient("gà"), 
                                    DetectedIngredient("spice X")]
3️⃣  buildEffectiveIngredients() -> [gà, spice X]
4️⃣  findUncoveredIngredients():
    - "gà" in vocabulary ✅
    - "spice X" NOT in vocabulary ❌
    - Return ["spice X"] (uncovered)
5️⃣  forceAiForUnknownIngredients = true
    - Don't use local suggestions alone
    - Force AI to think about unknown spice
6️⃣  Call Gemini with prompt including uncovered ingredients
7️⃣  Gemini response:
    - "3 dishes that work with gà + spice X":
    - Dish 1: "gà nướng kiểu Western"
    - Dish 2: "gà xào cay"
    - Dish 3: "gà chiên nước mắm"
8️⃣  parseSuggestedDishes() -> [3 SuggestedDish objects]
9️⃣  buildAiSuggestionItems() with filtering:
    - Check relevance for each
    - Filter out if too many missing
    - Keep AI suggestions
🔟  requestRecipesForAiDishes():
    - Check if recipes empty
    - Generate full recipes if needed
1️⃣1️⃣ Show results:
    - "gà nướng kiểu Western" (recipe: ...)
    - "gà xào cay" (recipe: ...)
    - "gà chiên nước mắm" (recipe: ...)

Result: ✅ "Gợi ý: 3 AI-suggested dishes with full recipes"
```

---

## 🎯 Summary: Luồng Chính

1. **Detect** → Parse detected ingredients with error handling
2. **Combine** → Mix AI-detected + manual ingredients
3. **Check** → Find uncovered ingredients in local database
4. **Decide** → Local suggestions prioritized IF fully covered + matches
5. **Call AI** → Gemini generates 3 suggestions when incomplete
6. **Filter** → Relevance checks on AI suggestions
7. **Backfill** → Generate missing recipes
8. **Rank** → Sort by quality metrics
9. **Display** → Show final results to user

Key Feature: If AI returns empty → Show error (Future: LLAMA fallback)


