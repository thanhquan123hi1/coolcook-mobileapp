# Journal Feature Architecture

## File Plan

### New Java files

- ui/journal/model/JournalEntry.java
- ui/journal/model/JournalDay.java
- ui/journal/data/JournalRepository.java
- ui/journal/JournalViewModel.java
- ui/journal/JournalCalendarAdapter.java
- ui/journal/JournalDayDetailAdapter.java
- ui/journal/JournalCalendarActivity.java
- ui/journal/JournalDayDetailActivity.java

### New layout files

- res/layout/journal_calendar_screen.xml
- res/layout/item_journal_day.xml
- res/layout/journal_day_detail_screen.xml
- res/layout/item_journal_day_detail_photo.xml

### New drawable and animation files

- res/drawable/bg_journal_top_icon_button.xml
- res/drawable/bg_journal_extra_badge.xml
- res/drawable/ic_journal_plus.xml
- res/drawable/ic_journal_plus_brown.xml
- res/drawable/ic_journal_diary_soft.xml
- res/drawable/bg_journal_placeholder_cute.xml
- res/drawable/bg_journal_placeholder_cute_alt.xml
- res/anim/journal_item_rise_in.xml
- res/anim/layout_journal_staggered.xml

### New dimen files

- res/values/dimens_journal.xml
- res/values-sw320dp/dimens_journal.xml
- res/values-sw360dp/dimens_journal.xml
- res/values-sw411dp/dimens_journal.xml

### Updated existing files

- AndroidManifest.xml
- ui/home/HomeActivity.java
- ui/scan/ScanFoodActivity.java
- ui/scan/JournalMoment.java
- res/values/strings.xml

## Data Model

### JournalEntry

Represents one photo document in Firestore at users/{userId}/journal.

Fields:

- id: String
- userId: String
- date: LocalDate (yyyy-MM-dd)
- imageUrl: String
- thumbnailUrl: String
- capturedAt: Date
- caption: String
- mealType: String

Behavior:

- fromSnapshot(DocumentSnapshot)
- toFirestorePayload()
- getPreviewUrl() -> thumbnailUrl fallback to imageUrl

### JournalDay

Represents one rendered calendar day cell.

Fields:

- date: LocalDate nullable for filler cells
- inCurrentMonth: boolean
- dayOfMonth: int
- totalEntryCount: int
- latestEntries: List<JournalEntry> (max 2)
- visualPatternIndex: int

Behavior:

- empty(pattern)
- fromEntries(date, sortedEntries, pattern)
- getExtraCount() -> totalEntryCount - 2

## Class Diagram (Text)

```text
+---------------------+
| JournalCalendarAct. |
+----------+----------+
           |
           | observes
           v
+---------------------+       uses        +----------------------+
|   JournalViewModel  +------------------->  JournalRepository    |
+----------+----------+                    +-----------+----------+
           |                                           |
           | emits List<JournalDay>                    | reads/writes Firestore
           v                                           v
+----------------------+                      users/{uid}/journal
| JournalCalendarAdapter|
+----------+-----------+
           |
           | opens day
           v
+----------------------+
| JournalDayDetailAct. |
+----------+-----------+
           |
           | observes List<JournalEntry>
           v
+-----------------------+
| JournalDayDetailAdapter|
+-----------------------+

+-------------------+          grouped into         +----------------+
|   JournalEntry    +------------------------------->   JournalDay    |
+-------------------+                                  +----------------+
```

## Pseudocode

### JournalRepository

```text
loadEntriesForMonth(userId, month):
  query users/{userId}/journal
    where date >= monthStart
    where date <= monthEnd
  map docs -> JournalEntry
  sort desc by capturedAt
  callback(entries)

loadEntriesForDate(userId, date):
  query users/{userId}/journal where date == yyyy-MM-dd
  map docs -> JournalEntry
  sort desc by capturedAt
  callback(entries)
```

### JournalViewModel

```text
refreshMonth():
  if userId empty:
    emit empty calendar with message
    return

  repo.loadEntriesForMonth(userId, currentMonth) { entries ->
    grouped = entries.groupBy(entry.date)
    cells = []
    add leading filler cells based on weekday offset (Mon first)
    for each day in month:
      dayEntries = grouped[day] sorted desc
      cells += JournalDay.fromEntries(day, dayEntries, visualPattern)
    add trailing filler cells to complete week rows
    emit cells
  }

loadEntriesOfDate(date):
  repo.loadEntriesForDate(userId, date) -> emit day entries
```

### JournalCalendarAdapter (StaggeredGrid)

```text
onBind(day):
  apply visual pattern (variable stack height, image tilt, overlap offset)

  if filler cell:
    hide content but keep span spacing
    return

  latest = day.latestEntries
  if latest.size >= 2:
    show latest[0], latest[1]
  else if latest.size == 1:
    show latest[0] + placeholder alt
  else:
    show placeholder primary + placeholder alt

  if day.totalEntryCount > 2:
    show badge +n
  else:
    hide badge

  on click -> callback(day)
```

### Placeholder Rules

```text
>= 2 photos: render 2 newest photos
1 photo: render 1 newest photo + 1 cute placeholder
0 photo: render 2 cute placeholders
extra badge: + (total - 2)
```

## Animation Notes

Implemented:

- RecyclerView layout animation: rise + fade + slight scale
- Button/FAB interaction: press-compress then overshoot rebound

Optional extension:

- SpringAnimation on FAB scaleX/scaleY for stronger bouncy feel
- staggered item enter delay based on adapter position % 7
