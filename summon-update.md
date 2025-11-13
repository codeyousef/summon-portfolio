# Summon Framework Updates Needed

This document tracks functionality that had to be implemented using CSS/JS/HTML workarounds instead of type-safe Kotlin idiomatic alternatives in the Summon framework.

## 1. Dropdown/Menu Component with Hover State

**Issue**: No native dropdown/menu component with proper hover state management.

**Current Workaround**: 
- Using CSS `:hover` and `focus-within` pseudo-selectors
- Manual opacity and pointer-events toggling via CSS
- File: `AppHeader.kt` lines 608-621

**Desired API**:
```kotlin
Dropdown(
    trigger = { Text("Projects") },
    modifier = Modifier().padding(...)
) {
    DropdownItem(
        label = "Summon",
        href = summonUrl,
        onClick = { /* ... */ }
    )
}
```

**Features Needed**:
- Built-in hover state management
- Keyboard navigation (arrow keys, escape)
- Proper ARIA attributes automatically applied
- Configurable trigger behavior (hover, click, or both)
- Automatic positioning (top, bottom, left, right)
- Portal rendering to avoid z-index issues

---

## 2. TextArea SSR Comment Artifact

**Issue**: TextArea component renders visible "<!-- onValueChange handler needed (JS) -->" comment during SSR.

**Current Workaround**:
- JavaScript cleanup script that removes comment text nodes
- Multiple retry attempts with MutationObserver
- File: `static/textarea-cleanup.js`

**Desired Behavior**:
- No visible comments in SSR output
- Proper placeholder for client-side hydration that doesn't leak to UI
- Comments should be actual HTML comments, not text nodes

**Root Cause**:
- Framework is rendering debug/placeholder text as visible content
- Should use proper HTML comment syntax or data attributes instead

---

## 3. Mouse Event Handlers

**Issue**: No native `onMouseEnter` and `onMouseLeave` modifiers.

**Current Workaround**:
- CSS `:hover` pseudo-selectors
- Cannot implement stateful hover effects purely in Kotlin
- File: `AppHeader.kt` (dropdown implementation)

**Desired API**:
```kotlin
Box(
    modifier = Modifier()
        .onMouseEnter { isHovered.value = true }
        .onMouseLeave { isHovered.value = false }
        .onMouseMove { event -> /* ... */ }
) {
    // content
}
```

**Features Needed**:
- All standard mouse events: enter, leave, move, over, out
- Event object with coordinates and button states
- Support for both modifier chain and lambda syntax
- Proper event delegation for performance

---

## 4. Visibility Transitions

**Issue**: No built-in support for enter/exit transitions on conditional rendering.

**Current Workaround**:
- CSS transitions on opacity and transform
- Manual visibility and transition-delay management
- File: `AppHeader.kt` lines 614-617

**Desired API**:
```kotlin
AnimatedVisibility(
    visible = isOpen,
    enter = fadeIn() + slideInVertically(),
    exit = fadeOut() + slideOutVertically()
) {
    // content
}
```

**Features Needed**:
- Fade in/out
- Slide in/out (all directions)
- Scale in/out
- Combination of multiple animations
- Configurable duration and easing
- onEnter/onExit callbacks

---

## 5. Data Attributes Helper

**Issue**: Using raw `.attribute("data-*", value)` for data attributes.

**Current Improvement Needed**:
- We now use `.dataAttribute("key", "value")` which is better
- But could be more idiomatic

**Desired API**:
```kotlin
Box(
    modifier = Modifier()
        .data {
            "menu-open" to isOpen.toString()
            "index" to index.toString()
            "category" to category
        }
) {
    // content
}
```

**Features Needed**:
- Type-safe data attribute builder
- Automatic kebab-case conversion
- Support for boolean, number, and string values
- Merge multiple data attribute sets

---

## 6. ARIA Attributes

**Status**: ✅ Partially Implemented
- `ariaLabel`, `ariaExpanded`, `ariaControls` exist
- Missing: `ariaHidden`, `ariaDescribedBy`, `ariaLabelledBy`, `ariaRole`, etc.

**Desired Improvements**:
```kotlin
Box(
    modifier = Modifier()
        .aria {
            label = "Main navigation"
            hidden = false
            describedBy = "nav-description"
            role = "navigation"
        }
) {
    // content
}
```

---

## 7. CSS Variable Support

**Issue**: No native way to use or define CSS custom properties.

**Current Workaround**:
- Hard-coded color values
- Can't easily support theming without rebuilding

**Desired API**:
```kotlin
// Define variables
GlobalStyle(
    """
    :root {
      ${cssVar("primary-color", "#ff4668")}
      ${cssVar("surface-color", "rgba(255,255,255,0.04)")}
    }
    """.trimIndent()
)

// Use variables
Box(
    modifier = Modifier()
        .backgroundColor(cssVar("surface-color"))
        .color(cssVar("primary-color"))
)
```

---

## 8. Media Query Helpers

**Issue**: Media queries must be written in CSS strings.

**Current Workaround**:
- GlobalStyle with raw CSS containing @media queries
- File: `AppHeader.kt` lines 540-605

**Desired API**:
```kotlin
Box(
    modifier = Modifier()
        .display(Display.Flex)
        .mediaQuery(MediaQuery.MinWidth(960.px)) {
            display(Display.None)
        }
        .mediaQuery(MediaQuery.MaxWidth(959.px)) {
            display(Display.Block)
        }
) {
    // content
}
```

**Features Needed**:
- Min/max width and height
- Orientation (portrait/landscape)
- Prefers color scheme (dark/light)
- Prefers reduced motion
- Hover capability
- Combination of multiple queries (and/or logic)

---

## 9. Pseudo-selector Modifiers

**Issue**: Pseudo-selectors like `:hover`, `:focus`, `:active` require CSS.

**Current Status**:
- `.hover()` exists for some components
- Missing: `:focus`, `:active`, `:focus-within`, `:first-child`, `:last-child`, etc.

**Desired API**:
```kotlin
Box(
    modifier = Modifier()
        .backgroundColor(Colors.SURFACE)
        .hover { backgroundColor(Colors.SURFACE_STRONG) }
        .focus { outlineColor(Colors.ACCENT) }
        .active { transform(scale(0.95)) }
        .focusWithin { borderColor(Colors.ACCENT) }
) {
    // content
}
```

---

## 10. Class Name Utilities

**Status**: ✅ Implemented
- `.className()` works correctly
- This is the proper type-safe way to add CSS classes

---

## 11. Portal/Teleport Component

**Issue**: No way to render content outside the current DOM hierarchy.

**Use Cases**:
- Modals that need to render at document body level
- Tooltips that need to escape overflow:hidden containers
- Dropdowns that need proper z-index stacking

**Desired API**:
```kotlin
Portal(target = "body") {
    Modal(
        open = isOpen,
        onClose = { isOpen = false }
    ) {
        // modal content
    }
}
```

---

## 12. Form Validation Helpers

**Issue**: No built-in form validation beyond basic HTML5 attributes.

**Current State**:
- Using `required` attribute
- No visual feedback for errors
- No custom validation logic

**Desired API**:
```kotlin
FormTextField(
    name = "email",
    label = "Email",
    validators = listOf(
        Validator.required("Email is required"),
        Validator.email("Must be a valid email"),
        Validator.custom { value ->
            if (!value.endsWith("@company.com")) {
                "Must use company email"
            } else null
        }
    ),
    onValidationChange = { isValid -> /* ... */ }
)
```

---

## 13. Scroll Utilities

**Issue**: No way to programmatically scroll or react to scroll events.

**Desired API**:
```kotlin
val scrollY = rememberScrollPosition()

Box(
    modifier = Modifier()
        .onScroll { event ->
            // handle scroll
        }
        .scrollTo(x = 0, y = 500, animated = true)
) {
    // content
}
```

---

## Priority Order

Based on current codebase needs:

1. **High Priority**:
   - TextArea SSR comment artifact fix (blocking UX issue)
   - Mouse event handlers (needed for rich interactions)
   - Dropdown/Menu component (common pattern, current solution is fragile)

2. **Medium Priority**:
   - Visibility transitions (improves UX significantly)
   - Media query helpers (makes responsive design cleaner)
   - Pseudo-selector modifiers (reduces CSS dependency)

3. **Low Priority**:
   - CSS variable support (nice to have for theming)
   - Portal component (needed for advanced components)
   - Scroll utilities (needed for scroll-based effects)
   - Form validation (current HTML5 validation sufficient for now)

---

## Notes

- All workarounds are documented in code with comments
- CSS workarounds are centralized in `GlobalStyle` blocks where possible
- JavaScript is avoided except where absolutely necessary (TextArea cleanup)
- This list should be updated as new gaps are discovered
