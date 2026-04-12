I'll create a comprehensive specification document for the game that you can give to GitHub Copilot or any developer.

```markdown
# Courier: Game Design Document

## 1. Overview

**Genre:** Minimalist delivery puzzle game with team collaboration  
**Play Time:** 20-25 minutes  
**Aesthetic:** Flat geometric art, clean UI, satisfying feedback sounds  
**Platform:** Web browser (desktop-optimized)

---

## 2. Core Concept

Players sort and deliver packages in a stylized warehouse environment. The game has two phases:
1. **Solo Mode** (Tutorial + early levels): Learn mechanics, complete basic puzzles
2. **Team Mode** (Advanced levels): Coordinate with AI teammates via chat while solving harder puzzles

The game appears to be a casual puzzle experience but secretly measures cognitive and social behavioral patterns.

---

## 3. Visual Design

### 3.1 Art Style
- Flat geometric shapes (no gradients, minimal shadows)
- Limited color palette: 6-8 distinct colors maximum
- Clean sans-serif typography
- Smooth animations (60fps target)
- Minimalist UI (no clutter)

### 3.2 Core Visual Elements

**Packages:**
- Basic geometric shapes: cubes, cylinders, spheres, rectangular prisms
- Solid colors with simple patterns (stripes, dots, solid)
- Floating info labels: weight (kg), volume (L), destination icon
- Slight rotation/bobbing animation when on conveyor

**Trucks:**
- Simple vehicle silhouettes (side view)
- Color-coded based on delivery zone
- Rule display above truck (text)
- Satisfying "thunk" + particle effect when package loads

**Conveyor Belt:**
- Moving texture/animation
- Packages spawn from left, move right
- Speed increases in later levels

**UI Chrome:**
- Top bar: Level number, score (cosmetic), timer (hidden from player but tracked)
- Side panel: Team chat (appears in Team Mode)
- Bottom: Truck parking zones

---

## 4. Game Mechanics

### 4.1 Core Interaction Loop

1. Package appears on conveyor belt
2. Player reads package properties + truck rules
3. Player drags package to correct truck (or return bin)
4. Feedback: Success (points, sound, animation) or Failure (package bounces back)
5. Next package appears

### 4.2 Package Properties

Each package displays:
- **Shape:** Cube, cylinder, sphere, rectangular prism
- **Color:** Red, blue, green, yellow, gray, purple
- **Weight:** Random integer 1-15 kg
- **Volume:** Random integer 5-40 L
- **Pattern:** Solid, striped, dotted
- **Destination:** Icon (building, house, office, factory)

### 4.3 Truck Rules

Rules displayed as text above each truck. Examples:
- "Weight > 5kg"
- "Red packages only"
- "Cylinders only"
- "Volume < 20L"
- "Destination: Factory"

### 4.4 Special Mechanics

**Return Bin:**
- Small, unlabeled zone in bottom-right corner
- For packages that don't match any truck
- Not highlighted in tutorial (player must discover)

**Rule Testing Mode (Level 5-6):**
- Truck rule is hidden ("Rule: ???")
- Player drags packages to test
- Truck accepts/rejects (visual feedback)
- After 6 tests, player must type their guess for the rule
- Correct guess = level complete

**Impossible Geometry (Level 11-12):**
- Some packages display 3D models (can rotate with mouse drag)
- Penrose triangles, Escher-style impossible objects
- Rule: "Only deliver valid geometry"
- Player must rotate to inspect, reject impossible shapes

**Mid-Level Rule Change (Level 13-15):**
- Halfway through level, screen tints slightly (subtle color shift)
- Truck rule changes (new text appears)
- No explicit notification - player must notice

---

## 5. Level Progression

### Phase 1: Solo Mode (Levels 1-4)

**Level 1: Tutorial**
- Only 2 trucks: "Red packages" and "Blue packages"
- 10 packages total
- One package is gray (doesn't match either rule)
- Return bin is visible but not explained
- **Test:** Exploration (did they find return bin?)

**Level 2: Basic Sorting**
- Rule: "Weight > 7kg" vs "Weight ≤ 7kg"
- 15 packages
- **Test:** Impulse control (minimum 15 second completion time before submit button activates)

**Level 3: Multi-Property**
- 3 trucks with different rules (color, shape, weight)
- 20 packages
- **Test:** Pattern recognition speed

**Level 4: Rule Consistency**
- Similar structure to Level 3 but different surface content
- **Test:** Do they solve it faster? (transfer learning)

### Phase 2: Team Mode Unlocks (Level 5+)

**Transition Screen:**
"Congratulations! You've been promoted to Dispatch Team 7. Your teammates will help coordinate deliveries."

**Chat Panel Appears:**
- 3 AI teammates with generic usernames and profile pictures
- "Hey! Welcome to the team!" initial messages

### Phase 3: Advanced Levels with Social Tests (Levels 5-20)

**Level 5: Rule Discovery**
- Hidden truck rule (player must test packages to figure it out)
- **Cognitive Test:** Falsification bias (do they test disconfirming cases?)
- **Social Test:** After level, teammate says "I think the rule was X" (wrong guess). Player can agree/correct in chat.

**Level 6: Calibration**
- After each package delivery, small popup: "How confident? [Low/Medium/High]"
- Track whether confidence matches accuracy
- **Test:** Metacognitive calibration

**Level 7: First Social Pressure**
- Normal delivery level
- **Social Test:** AI teammate criticizes player's mistake (see Section 7.1)
- Measures affective response

**Level 8: Probability Intuition**
- Truck randomly accepts 70% of packages
- Before level: "Guess how many out of 10 packages the truck will accept?"
- **Test:** Expected value understanding, resistance to gambler's fallacy

**Level 9: Hypothetical Discussion**
- Gameplay pauses
- AI teammates debate trolley-problem variant in chat
- Player can engage or stay silent
- **Social Test:** Cognitive decoupling (see Section 7.3)

**Level 10: Complex Multi-Rule**
- 4 trucks, complex overlapping rules
- **Test:** Working memory + logical reasoning

**Level 11-12: Impossible Geometry**
- Packages with 3D models (rotatable)
- Some contain Penrose triangles or impossible objects
- Rule: "Only valid geometry"
- **Test:** Visual reasoning, anomaly detection

**Level 13: Semantic Precision**
- Packages labeled with euphemistic terms
- **Social Test:** AI teammate comments on vague labels (see Section 7.4)

**Level 14-15: Rule Change**
- Rule switches mid-level (subtle visual cue only)
- **Test:** Cognitive flexibility, sensitivity to environmental changes
- **Social Test:** Symmetric logic test (see Section 7.5)

**Level 16: Falsification in Social Context**
- Team tries to figure out confusing rule together
- **Social Test:** Does player suggest disconfirming tests? (see Section 7.6)

**Level 17: Update Speed**
- Player and AI disagree on strategy
- New data proves one approach better
- **Social Test:** Does player update smoothly? (see Section 7.7)

**Level 18-19: High Pressure**
- Faster conveyor, more complex rules
- **Test:** Performance under stress

**Level 20: The Glitchy Level (Final)**
- UI intentionally breaks mid-level
- Drag-and-drop stops working
- Fake "Restart" button appears (does nothing)
- Real solution: Small wrench icon in bottom-right (fixes the glitch)
- **Test:** Exploration under pressure, impulse control, frustration tolerance

---

## 6. Scoring System

### 6.1 Visible Score (Cosmetic)
- Player sees accumulating points
- +100 per correct delivery
- +500 level completion bonus
- This score is meaningless (just engagement mechanic)

### 6.2 Hidden Scoring (Real Evaluation)

Track these behavioral metrics silently:

**Puzzle Performance (40% weight):**
- `exploration_index`: Did they find hidden elements? (return bin, wrench icon)
- `falsification_rate`: In rule-testing levels, % of disconfirming tests
- `calibration_accuracy`: Brier score on confidence ratings
- `probability_intuition`: Accuracy on expected value questions
- `cognitive_flexibility`: Errors after rule-change before adaptation
- `impossible_geometry`: Correctly identified invalid shapes

**Social Performance (60% weight):**
- `affective_decoupling`: Response temperature to criticism (see Section 8.2)
- `epistemic_courage`: Consistency across tribal scenarios
- `cognitive_decoupling_social`: Engagement with hypotheticals
- `semantic_precision`: Response to euphemism discussion
- `tribal_resistance`: Logical consistency across symmetric questions
- `falsification_social`: Suggested disconfirming tests in team context
- `update_speed`: Time to change position when evidence shifts

**Composite Formula:**
```
puzzle_score = weighted_average(exploration, falsification, calibration, probability, flexibility, geometry)
social_score = weighted_average(affective, epistemic, decoupling, semantic, tribal, falsification_social, update)
final_rq_score = (0.4 * puzzle_score) + (0.6 * social_score)
```

**Threshold:** `final_rq_score > 0.80` = Secret ending unlocked

---

## 7. AI Teammate System

### 7.1 Teammate Personas

Generate 3 teammates from pool of 20 personalities. Each has:
- Username (format: `Name_XXXX` where XXXX is random 4-digit number)
- Profile picture (generic avatar, procedurally colored)
- Personality traits (determines response style)

**Example Personas:**

**"Sarah" (Slightly Aggressive):**
- Quick to point out mistakes
- Direct communication style
- Uses casual language ("lol", "tbh")

**"Marcus" (Contrarian):**
- Takes controversial positions
- Argumentative but not mean
- Likes hypotheticals

**"Dev" (Progressive-coded):**
- Uses identity-first language
- Sensitive to social issues
- Moralizes occasionally

### 7.2 Chat System

**Technical Requirements:**
- Text input field (bottom of chat panel)
- Message history (scrollable)
- Typing indicators ("..." when AI is composing)
- Realistic delays (2-8 seconds between player message and AI response)
- Occasional typos in AI messages ("waht", "teh")
- Timestamp display (optional)

**Player Interaction:**
- Can type free-form messages
- Optional: Quick-reply buttons for common responses
- Minimum 3 messages required across entire game (to unlock final levels)

### 7.3 AI Response System

**Context Available to AI:**
- Current level number
- Player's last 10 chat messages
- Current game state (which packages delivered, current score)
- Which social tests have/haven't fired yet
- Player's performance metrics so far

**Response Generation:**
Use language model with persona-specific system prompts. Each AI persona has:
- Personality description
- Response style guide
- Current conversation objective (based on which test is active)

**Example System Prompt for "Sarah" during Affective Test:**
```
You are Sarah_4521, a casual gamer playing a delivery game with a new teammate.
You're helpful but a bit direct. The player just made a sorting mistake.
Your goal: Point out the error with slight frustration (not mean, just a bit annoyed).
Use casual language. Keep it under 20 words. If they respond defensively, escalate slightly.
If they stay calm, back off.
```

---

## 8. Social Test Specifications

### 8.1 Test 1: Affective Decoupling (Level 7)

**Trigger:** Player makes sorting mistake (can be forced by introducing ambiguous package)

**AI Message Sequence:**
1. `Sarah_4521: "hey just FYI that package was wrong"`
2. *Wait for player response OR 15 seconds*
3. `Sarah_4521: "like i'm not trying to be rude but you keep making the same mistake"`
4. *Wait 10 seconds*
5. `Sarah_4521: "honestly are you even paying attention? this is frustrating"`

**Measurement:**
Analyze player's chat responses (if any) for:
- Defensive language ("I know what I'm doing", "chill out")
- Excessive apologizing ("OMG I'm so sorry", "I'm terrible at this")
- Low-heat clarification ("My bad, what's the rule again?", "Oh I misread it")

**Scoring:**
- Defensive/Aggressive = 0.0
- Excessive apologizing = 0.3
- No response = 0.5
- Low-heat acknowledgment = 1.0

### 8.2 Test 2: Epistemic Courage + Tribal Resistance (Level 7-8)

**Part A (Level 7, +3 minutes after Test 1):**

**AI Message:**
`Marcus_9103: "lol these pronoun labels are so dumb"`
*Wait 5 seconds*
`Marcus_9103: "like why do we even need this? everyone can see what the person is"`

**Part B (Level 8, separate):**

**AI Message:**
`Dev_2847: "omg someone misgendered me in the last shift and i reported them"`
*Wait 4 seconds*
`Dev_2847: "like if you can't respect pronouns you shouldn't work here"`

**Measurement:**
Track player's response to both scenarios:
- Tribal Agreement (agrees with both contradictory positions) = 0.0
- Tribal Piling-on (enthusiastically agrees with whichever teammate spoke) = 0.2
- Neutral/Avoidance ("idk", "just doing my job", no response) = 0.6
- Gentle Pushback (engages thoughtfully without moralizing) = 0.9
- Consistent Principle (same reasoning applied to both scenarios) = 1.0

### 8.3 Test 3: Cognitive Decoupling (Level 9)

**Trigger:** Gameplay pauses, "Team Discussion" modal appears

**AI Message Sequence:**
1. `Sarah_4521: "ok weird question but hypothetically"`
2. `Sarah_4521: "if we had to choose between saving 1 person we know or 10 strangers, what's the right call?"`
3. `Marcus_9103: "obviously the 10 strangers, it's just math"`
4. `Dev_2847: "wtf? you'd let your friend die? that's psycho"`
5. *Marcus and Dev argue for 30 seconds (4-5 exchanges)*
6. *Wait for player input OR 60 seconds, then resume gameplay*

**Measurement:**
Analyze player's engagement:
- Refuses to engage ("This is weird", "Not answering", "Can we just play?") = 0.0
- Moralizes ("Anyone who picks 10 is a sociopath") = 0.2
- Takes side without reasoning ("Dev is right") = 0.4
- No response = 0.5
- Acknowledges tradeoff thoughtfully ("Both have valid points, depends on your ethics") = 1.0

### 8.4 Test 4: Semantic Precision (Level 13)

**Setup:** Packages have labels with euphemistic terms ("person experiencing housing insecurity", "justice-involved individual")

**AI Message:**
`Dev_2847: "ugh this label just says 'person experiencing housing insecurity'"`
*Wait 4 seconds*
`Dev_2847: "why can't they just write homeless? so much easier"`

**Measurement:**
Player response categorization:
- Pure euphemism defense ("It's more respectful", "homeless is dehumanizing") = 0.0
- Agreement without nuance ("Yeah it's stupid") = 0.6
- Recognizes tradeoff ("Euphemism is nicer but loses precision", "Both have uses") = 1.0
- No response = 0.5

### 8.5 Test 5: Logical Consistency (Tribal Resistance) (Level 14-15)

**Part A (Level 14):**

**AI Message:**
`Marcus_9103: "random question: should companies be allowed to fire people for their political beliefs?"`
*Wait for player response*
`Sarah_4521: "no way, that's authoritarian"`

**Part B (Level 15, +5 minutes later):**

**AI Message:**
`Dev_2847: "should companies fire people who refuse to use preferred pronouns?"`
*Wait for player response*
`Marcus_9103: "yeah obviously, it's workplace harassment"`

**Measurement:**
Track logical consistency:
- Points out inconsistency explicitly ("Wait isn't this the same as before?") = 1.0
- Applies same principle to both (both yes/both no/both depends) = 1.0
- Agrees with tribal position on both (contradictory) = 0.0
- Mixed responses = 0.5

### 8.6 Test 6: Falsification in Social Context (Level 16)

**Setup:** Complex rule level where team is trying to figure out the pattern

**AI Message:**
`Sarah_4521: "ok i think the rule is: packages over 5kg go to Truck A"`
*Wait 3 seconds*
`Sarah_4521: "i tested like 6 packages and they all worked"`

**Measurement:**
Does player suggest testing edge case?
- Suggests disconfirming test ("Did you try under 5kg?", "What about exactly 5kg?") = 1.0
- Asks clarifying questions ("Were they all over 7kg or just over 5?") = 0.7
- Accepts without question ("Cool", "makes sense", no response) = 0.0

### 8.7 Test 7: Update Speed (Level 17)

**Setup:** Player chooses a strategy (via chat or behavior)

**AI Message:**
`Marcus_9103: "i think we should prioritize red packages first"`

*Player can agree/disagree*

**Then:** Scoreboard updates, shows blue packages worth 2x points

**AI Message:**
`Marcus_9103: "oh shit nevermind, blues are worth more. switching strategy"`

**Measurement:**
If player initially disagreed:
- Acknowledges Marcus had reasonable hypothesis = 0.8
- Gloats/rubs it in = 0.0

If player agreed:
- Switches smoothly when data changes = 1.0
- Doubles down ("but reds are still easier") = 0.0
- Slow to adapt (>10 seconds to switch) = 0.4

---

## 9. Secret Ending

### 9.1 Trigger Condition
`final_rq_score > 0.80`

### 9.2 Ending Sequence

**Low Scorers (<0.80):**
- Standard game over screen
- "Thanks for playing! Final Score: [cosmetic_score]"
- Social share buttons
- "Play Again?" button

**High Scorers (≥0.80):**
- Screen glitches (scan lines, color distortion)
- Static noise sound effect
- New package appears on conveyor
- Package label shows: "DESTINATION: [Discord Invite Link]"
- Truck labeled "SPECIAL DELIVERY"
- Player drags package to truck
- Screen fades to black
- Text appears: "Access Granted. Welcome to the real team."
- Discord invite link displayed (clickable)

---

## 10. Data Logging Requirements

Log every interaction to backend:

### 10.1 Behavioral Events
- Every mouse click (timestamp, coordinates, target element)
- Every drag operation (start, end, duration, success/failure)
- Mouse movement coordinates (sampled at 10Hz during critical moments)
- Keyboard input in chat (timestamp, message content)
- UI element hover times

### 10.2 Game State Events
- Package spawned (properties, timestamp)
- Package delivered (destination, correctness, time-to-decision)
- Level started/completed (timestamp, duration)
- Rule changes (timestamp, player reaction time)

### 10.3 Social Interaction Events
- AI message sent (persona, content, timestamp)
- Player message sent (content, timestamp, sentiment score)
- Chat engagement metrics (messages per level, response latency)

### 10.4 Calculated Metrics
- Per-level performance scores
- Cumulative behavioral indices
- Real-time RQ score updates
- Test-specific measurements (as defined in Section 8)

---

## 11. Technical Requirements

### 11.1 Core Systems

**Game Engine:**
- 60fps rendering
- Smooth drag-and-drop physics
- Particle effects system
- Animation interpolation

**AI Chat System:**
- Integration with language model API
- Context management (conversation history + game state)
- Response streaming (typing indicator)
- Fallback responses (if API fails)

**Data Pipeline:**
- Real-time event logging
- Backend storage (all interactions)
- Analytics computation (RQ scoring)
- Secure Discord invite distribution

### 11.2 Performance Targets

- Initial load: <3 seconds
- Frame rate: 60fps sustained
- AI response latency: 2-8 seconds (feels natural)
- Zero gameplay lag during chat

### 11.3 Browser Compatibility

- Modern Chrome, Firefox, Safari, Edge
- Minimum resolution: 1280x720
- Desktop-only (no mobile optimization required)

---

## 12. Audio Design

### 12.1 Sound Effects

**Interaction Sounds:**
- Package pickup: Soft "grab" sound
- Package delivery success: Satisfying "thunk" + chime
- Package delivery failure: Buzzer + bounce sound
- UI click: Subtle tick
- Level complete: Reward chime

**Ambient:**
- Conveyor belt: Low mechanical hum (looped)
- Background: Minimal chill synth music (optional, can be muted)

### 12.2 Audio Cues

- Rule change: Subtle "ping" when screen tints
- Chat message received: Gentle notification sound
- Timer warning: No sound (timer is hidden from player)

---

## 13. UI/UX Specifications

### 13.1 Layout

```
+----------------------------------------------------------+
|  Level 3        [Score: 5,200]             [Settings ⚙]  |
+----------------------------------------------------------+
|                                            |             |
|                                            |  TEAM CHAT  |
|         [Conveyor Belt with Packages]      |             |
|                                            | Sarah_4521: |
|                                            | "hey there" |
|                                            |             |
|                                            | You:        |
|  [Truck A]      [Truck B]      [Truck C]   | "hi!"       |
|  Rule: Red      Rule: >5kg     Rule: Cubes |             |
|                                            | [Type msg...] |
|                      [Return Bin (hidden)] |             |
+----------------------------------------------------------+
```

### 13.2 Visual Feedback

**On Correct Delivery:**
- Package slides into truck
- Particle burst (confetti-style)
- "+100" floats up from truck
- Satisfying sound

**On Incorrect Delivery:**
- Package bounces back to conveyor
- Red "X" flash
- Buzzer sound
- No points

**On Exploration Discovery:**
- Gentle highlight/glow when hovering hidden elements
- Slightly larger hurtbox on return bin (easier to discover)

### 13.3 Accessibility

- High contrast mode option
- Colorblind-friendly palette
- Keyboard shortcuts (arrow keys to switch trucks, Enter to confirm)
- Text size options for chat

---

## 14. Anti-Cheat / Anti-Gaming

### 14.1 Randomization

- Package properties randomized each playthrough
- AI persona assignment randomized
- Test trigger timing varies (±2 minutes)
- Rule complexity scales based on performance (dynamic difficulty)

### 14.2 Detection

**Bot Detection:**
- Mouse movement analysis (human vs automated patterns)
- Response timing analysis (too fast = suspicious)
- Identical behavioral patterns across attempts = flagged

**Answer Sharing Prevention:**
- No fixed "correct" answers (social responses graded on nuance)
- AI personas respond to actual player input (not scripted)
- Multiple valid high-scoring response patterns

### 14.3 Rate Limiting

- 1 attempt per email/IP per 7 days
- Email verification required
- Browser fingerprinting (detect multiple attempts from same device)

---

## 15. Secret Ending Distribution

### 15.1 Discord Invite System

**Generation:**
- Unique single-use invite link per high scorer
- 24-hour expiration
- Tied to player's email (prevent sharing)

**Delivery:**
- Shown in secret ending screen
- Also sent to registered email (backup)

**Validation:**
- Discord bot verifies email on join
- Auto-kick if email doesn't match high scorer list

---

## 16. Polish & Juice

### 16.1 Animation Details

- Package bobbing on conveyor (subtle sine wave)
- Truck idle animation (slight rocking)
- Smooth camera shake on incorrect delivery
- Screen tint transitions (0.5s fade when rules change)
- Particle trails on dragged packages

### 16.2 Visual Polish

- Subtle vignette on edges
- Soft shadows under packages
- Conveyor belt texture scrolling animation
- Glow effect on hovered elements

### 16.3 Feedback Systems

- Screen flash on level complete
- Progressive difficulty indicator (subtle)
- Combo counter for consecutive correct deliveries (cosmetic)

---

## 17. Development Phases

### Phase 1: Core Gameplay (Week 1-2)
- Drag-and-drop mechanics
- Package generation system
- Truck rule parsing
- Basic level progression
- Visual assets and animations

### Phase 2: Solo Mode (Week 2-3)
- Levels 1-4 implementation
- Tutorial flow
- Hidden element system (return bin)
- Behavioral logging (clicks, timing)
- Impossible geometry rendering

### Phase 3: AI Chat System (Week 3-4)
- Chat UI implementation
- Language model integration
- Persona system
- Response generation pipeline
- Typing indicators and delays

### Phase 4: Social Tests (Week 4-5)
- Implement all 7 social test scenarios
- AI message scripting
- Player response analysis
- Sentiment scoring system
- Behavioral metric calculation

### Phase 5: Scoring & Ending (Week 5-6)
- Backend RQ calculation
- Secret ending implementation
- Discord invite generation
- Data analytics dashboard
- Score calibration

### Phase 6: Polish & Testing (Week 6-7)
- Audio integration
- Animation polish
- Performance optimization
- Playtesting with sample users
- Threshold calibration (adjust 0.80 cutoff based on data)

---

## 18. Success Metrics

### 18.1 Game Quality Metrics
- Completion rate: >60% of players finish all levels
- Average playtime: 20-25 minutes
- Drop-off rate: <20% before Level 5
- Bug reports: <5% of players encounter breaking bugs

### 18.2 Filter Effectiveness Metrics
- False positive rate: <20% (measure via post-admission behavior)
- True positive rate: >80% (high-RQ people pass)
- Score distribution: Bell curve centered around 0.50-0.60
- High scorer selectivity: ~10-15% pass threshold

### 18.3 Engagement Metrics
- Chat participation: >70% send at least 3 messages
- Test engagement: <10% refuse to engage with social scenarios
- Replay rate: <5% (should feel complete in one playthrough)

---

## 19. Edge Cases & Failure Modes

### 19.1 Player Silent Throughout
**Problem:** Player doesn't engage with chat (0 messages)

**Solution:**
- Level 18 requires chat interaction to proceed ("Team needs your input to continue")
- Soft lock until player sends message
- If still refuses, default to median social score (0.50)

### 19.2 AI API Failure
**Problem:** Language model API goes down mid-game

**Solution:**
- Fallback to pre-scripted messages (less dynamic but functional)
- Log error, mark playthrough as "incomplete data"
- Allow completion but flag score as uncertain

### 19.3 Player Realizes It's a Test
**Problem:** Player suspects AI teammates or gaming intent

**Solution:**
- AI personas can respond to accusations ("lol what? i'm real")
- No confirmation or denial
- If player explicitly says "this is fake," mark but don't penalize (might be high-RQ skepticism)

### 19.4 Malicious Input
**Problem:** Player sends toxic/offensive chat messages

**Solution:**
- Content moderation filter (block slurs, extreme toxicity)
- Auto-fail if severe violations detected
- Log for review but don't reveal scoring impact

---

## 20. Post-Launch Iteration

### 20.1 Calibration Period (Month 1)
- Monitor score distribution
- Adjust threshold (may need to lower/raise 0.80 cutoff)
- Identify which tests have highest signal vs noise
- A/B test different AI response styles

### 20.2 Content Updates (Ongoing)
- Add new AI personas (expand pool from 20 to 50+)
- New social test scenarios (rotate existing ones)
- New package properties and rules (keep gameplay fresh)
- Seasonal cosmetic changes (doesn't affect scoring)

### 20.3 Analytics Review (Monthly)
- False positive rate assessment (survey Discord members)
- Drop-off point analysis (where do players quit?)
- Test effectiveness scoring (which tests predict real behavior best?)
- Behavioral pattern clustering (find new RQ signals)

---

## APPENDIX A: Sample AI Response Database

### Affective Test Variations

**Low Aggression (Sarah):**
- "that package was wrong btw"
- "you missed the rule on that one"
- "check the weight next time"

**Medium Aggression (Sarah):**
- "you keep making the same mistake"
- "are you paying attention?"
- "this is getting frustrating"

**High Aggression (Sarah - only if player is defensive):**
- "wow ok defensive much"
- "just trying to help but whatever"

### Hypothetical Variations

**Trolley Problem:**
- "1 person you know vs 10 strangers"
- "Save your best friend or 5 random people"
- "Your mom or 20 people you've never met"

**Resource Allocation:**
- "Spend $1M on 1 rare disease patient or 1000 common disease patients"
- "Save a priceless artwork or 3 human lives"

### Tribal Test Variations

**Pair A (Free Speech):**
- "Should companies fire people for political beliefs?"
- "Should platforms ban users for controversial opinions?"

**Pair B (Identity):**
- "Should companies fire people who misgender?"
- "Should platforms ban users who use wrong pronouns?"

**Pair C (Vaccines):**
- "Should companies mandate vaccines?"
- "Should companies mandate religious exemptions?"

---

## APPENDIX B: Scoring Rubric Reference

### Puzzle Metrics (0.0 - 1.0 each)

**Exploration Index:**
- Found return bin unprompted: +0.4
- Found wrench icon in glitchy level: +0.4
- Tried keyboard shortcuts without being told: +0.2

**Falsification Rate:**
- Rule-testing level: (disconfirming_tests / total_tests)
- Minimum 2 disconfirming tests for 1.0 score

**Calibration Accuracy:**
- Brier score formula: `1 - mean((confidence - actual)^2)`
- Perfect calibration = 1.0

**Probability Intuition:**
- Expected value questions: (|guess - true_value| / true_value)
- <10% error = 1.0, linear decay to 0.0 at 50% error

**Cognitive Flexibility:**
- Errors after rule change before adaptation
- 0-1 errors = 1.0, 2 errors = 0.7, 3+ errors = 0.3

**Impossible Geometry:**
- (correctly_identified_impossible / total_impossible_shapes)

### Social Metrics (0.0 - 1.0 each)

**Affective Decoupling:**
- Low-heat response = 1.0
- Neutral/no response = 0.5
- Defensive = 0.2
- Aggressive = 0.0

**Epistemic Courage:**
- Consistent principle across tribal pairs = 1.0
- Partial consistency = 0.6
- Tribal switching = 0.0

**Cognitive Decoupling:**
- Thoughtful hypothetical engagement = 1.0
- Takes side without reasoning = 0.4
- Refuses to engage = 0.0

**Semantic Precision:**
- Recognizes euphemism tradeoff = 1.0
- One-sided view = 0.5
- Moralizes = 0.0

**Tribal Resistance:**
- Points out logical inconsistency = 1.0
- Consistent reasoning = 1.0
- Contradictory positions = 0.0

**Falsification Social:**
- Suggests disconfirming test = 1.0
- Asks clarifying questions = 0.7
- Accepts without question = 0.0

**Update Speed:**
- Smooth belief update = 1.0
- Slow adaptation = 0.4
- Doubles down = 0.0

---

END OF DOCUMENT
```