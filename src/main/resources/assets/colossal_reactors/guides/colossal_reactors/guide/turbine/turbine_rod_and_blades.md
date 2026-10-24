---
navigation:
  parent: turbine/turbine-index.md
  title: Rods and blades
  icon: turbine_blade
  position: 15
item_ids:
  - colossal_reactors:turbine_blade
  - colossal_reactors:turbine_rod
categories:
  - multiblock
---

# Rods and blades

<Row gap="16" fullWidth={true}>
  <ItemImage id="turbine_rod" scale="4" />
  <ItemImage id="turbine_blade" scale="4" />
</Row>

## Placing blades

The **Turbine Blade** is not placed like a normal block. **Right-click a Turbine Rod** while holding a blade to attach the next blade on that rod. Each click uses one blade from your inventory.

Blades grow outward in **rings** around the rod, kept even on all four sides:

1. **First step** — 2 blades on opposite sides.
2. **Next** — 4 blades (one on each side).
3. **Then** — 8, 12, 16… up to the pack limit (default **31** rings per rod, up to **124** blades on a single rod).

The game always picks the side that keeps the layout balanced before moving to the next ring.

## Blades in the world

- Blades are **ethereal**: you cannot walk into them, mine them by hand, or pick them up directly.

- If you **break the rod**, every blade on that rod is removed and dropped as items **on the rod block**—you do not need to search the turbine interior.

## Blade bonus by height

Along the rod controller’s direction, blades are counted **per layer** (like a floor at each height on a vertical shaft).

| How layers compare | Effect |
|--------------------|--------|
| Upper layer has **more** blades than the one below | **+3%** output bonus per step (pack default) |
| Same blade count | No change |
| Upper layer has **fewer** blades | Bonus chain **lost**; each drop also applies about **−3%** |

For the best bonus, add rings **from the bottom upward** (the builder’s **Efficient** layout does this). Filling every layer to the maximum right away (**Productive**) skips that climbing bonus but reaches full steam throughput sooner.

On some packs, only **even rings** (4, 8, 12 blades on a layer, and so on) count fully toward steam capacity—check your mod config if numbers look low.

## Missing rod-controller penalty

If the turbine has **any rod column** whose closure-deck cell is **not** a [Turbine Rod Controller](turbine_structure.md), blade efficiency is multiplied by **0.95 (−5%)**.

- One controller on a single-shaft layout: no penalty.
- Multi-column layouts (**Hyperflow**, Website seeds, hand builds): place a controller **on every rod column** to cancel the −5%.

## Builder layouts

The [Turbine Builder](turbine_builder.md) can place rods, blades, and rod controllers for you:

- **Efficient** — single centered rod column; blade rings **climb** layer by layer (best height bonus).
- **Productive** — same single column; each layer gets the **maximum** ring count immediately.
- **Hyperflow** — multi-rod grid (with a one-cell margin from walls); the builder places a **rod controller on every column**.
- **Website** — rods from an imported design seed; blade growth follows climb/full like Efficient/Productive.

Hand-placing blades with the item follows the same ring order as the builder.
