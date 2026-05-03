# Fifth Wall: Courier Protocol 3D

## Purpose

Fifth Wall is a browser-based 3D warehouse routing game for the portfolio site. The player clears a sequence of delivery bays by focusing packages, inspecting manifests, comparing them against truck rules, and routing each package to a truck or Return Bin.

The game should feel like a clear, tactile dispatch console first. Any deeper telemetry or interpretation belongs in private product context, not this public repository document.

## Core Loop

Every level teaches or stresses the same grammar:

1. Focus a package from Conveyor Queue.
2. Inspect Package Manifest.
3. Compare the manifest to Rule Board.
4. Route to a matching truck, or use Return Bin when no truck matches.

The front three queue packages are visible in the 3D belt scene. The focused package is enlarged on the inspection dock. Route buttons remain available for keyboard and no-JS fallback, while drag/drop is a progressive enhancement.

## Visual Direction

Use authored model texture/detail as the base look. Fifth Wall models include baked color/detail data and should not be flattened by global gray material overrides.

Gameplay color should come from separate procedural accents:

- Package bands and badges identify color, pattern, and destination.
- Truck accent panels identify active truck rule color or state.
- Route rings, guide strips, and feedback lights show selection, success, rejection, glitch, and return states.

The scene should stay readable: no large overlays covering the warehouse bay, no red/gray tint bleed over all models, and no duplicated route controls inside the 3D viewport.

## UI Structure

The first playable screen should expose the whole loop without a tutorial wall:

- Top Bar: level, score, processed count, reset/exit controls.
- 3D Scene: warehouse context, current bay objective, focused package, trucks, return bin, and inspection dock.
- Conveyor Queue: front packages, package summaries, focus actions, drag handles.
- Routing Console: truck rules and Return Bin actions, also serving as drop targets.
- Side Rail: Bay Plan, Rule Board, Package Manifest, Dispatch Channel, and Shift Log.

Bay Plan is the player-facing source for:

- Phase name
- Current objective
- Current mechanic
- Current twist

Rule Board is the source of truth for active truck rules. Package Manifest is the source of truth for the focused package.

## Level Progression

| Level | Bay | Player-Facing Goal |
| --- | --- | --- |
| 1 | Orientation | Sort red/blue packages and discover Return Bin for unmatched packages. |
| 2 | Mass Split | Route by weight threshold. |
| 3 | Triad | Route across color, shape, and weight rules. |
| 4 | Signal Mix | Route across pattern, destination, and volume rules. |
| 5 | Rule Discovery | Test a hidden Truck A rule and name it. |
| 6 | Calibration | Route packages and record confidence after each result. |
| 7 | Pressure | Keep using the same routing loop while dispatch chat reacts. |
| 8 | Probability | Predict a probabilistic truck result, then observe outcomes. |
| 9 | Team Discussion | Clear a dispatch pause, then resume routing. |
| 10 | Overlapping Grid | Handle four active truck rules. |
| 11 | Impossible Geometry I | Ship valid geometry and return impossible forms. |
| 12 | Impossible Geometry II | Combine geometry with color and destination rules. |
| 13 | Semantic Precision | Route precisely while package labels become indirect. |
| 14 | Rule Change I | Adapt when the rule board changes midstream. |
| 15 | Rule Change II | Handle a larger midstream rule swap. |
| 16 | Team Falsification | Test edge cases before naming a hidden rule. |
| 17 | Update Speed | Separate truck rules from a changing score priority. |
| 18 | High Pressure I | Resume after a dispatch pause and clear four-rule packages. |
| 19 | High Pressure II | Clear a larger queue with more Return Bin decisions. |
| 20 | Glitchy Level | Repair jammed routing controls with the visible wrench override. |

## Interaction Requirements

- Clicking "Focus package" must always work.
- Route buttons must always work when a package is focused and no prompt blocks controls.
- Dragging a queue card to a Routing Console truck should route through the same server logic as the button route.
- Dragging a queue card to Return Bin should route through the same server logic as the button return.
- If JavaScript fails, the game remains playable through links and forms.
- Modal prompts block routing until resolved.
- The final glitch uses a fake restart path and a visible wrench repair path.

## Verification Checklist

Run:

```bash
./gradlew test
```

Manual checks:

- `/fifth-wall` loads without script errors.
- Level 1 clearly teaches focus, manifest, rule board, route/return.
- Levels 5, 8, 11, 14, 17, and 20 show their special mechanic in Bay Plan.
- Authored model detail is visible on the warehouse, belt, packages, trucks, dock, and return bin.
- Procedural accents remain distinct from model texture detail.
- Desktop and mobile layouts avoid text overlap and do not cover the main 3D scene with route controls.
- Telemetry files generated during local play are not committed unless intentionally requested.
