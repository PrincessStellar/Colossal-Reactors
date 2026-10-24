---
navigation:
  parent: turbine/turbine-index.md
  title: Turbine Builder
  icon: turbine_builder
  position: 30
item_ids:
  - colossal_reactors:turbine_builder
categories:
  - multiblock
---

# Turbine Builder

<BlockImage id="turbine_builder" scale="4" />

The **Turbine Builder** builds the turbine for you from items in its inventory—similar to the [Reactor Builder](../multiblock/reactor_builder.md).

## Typical workflow

1. **Place** the builder on the face where you want the turbine shell to start.
2. **Open** the screen and set **width, height, and depth** (within your pack’s size limit).
3. Stock the builder with **casing**, **glass**, **rod controllers** (one per rod column the pattern needs), **rods**, **blades**, and the **storage blocks** you want for coils.
4. Pick a **layout** (see [Rods and blades](turbine_rod_and_blades.md)):
   - **Efficient** — one center shaft; blades climb ring by ring (**+3%** height bonus possible).
   - **Productive** — one center shaft; full rings every layer (maximum steam sooner).
   - **Hyperflow** — multi-rod grid; places a **rod controller on each column** (no −5% missing-controller penalty).
   - **Website** — paste a design **seed / URL** into the design field; rods (and coil choice on Website) follow that layout.
   - **Custom / None** — frame (and coils for Custom) without rotor contents.
5. Choose **coil type** (cycles supported metals; editable on Website too) and **coil layer count** (wide button under Preview, next to it).
6. Use **Preview** (under Center, same width) and the **Simulation** panel for RF/t, steam, and materials. Warnings appear **beside Center**.
7. **Mark Input** (just above the builder buffer):
   - Click a buffer slot while Mark Input is on to set which item that slot accepts for build steps.
   - You can also **drag items from JEI** onto buffer slots to set ghost mark filters; double-click a slot to clear.
8. Press **Build** when ready.

Build order: **outer shell** → **closure deck** → **rod controllers** (one per rod column) → **rods** → **blades** → **coil blocks** in the top interior layers.

The builder does **not** place the exterior **Turbine Controller** or ports—add those by hand after the build.

## Simulation panel

Shows expected **RF/t** and **steam**, coil and blade bonuses (including the −5% when any rod column lacks a controller), and a **material list**. Website seeds are included in the simulation when the design is valid. Button names can vary by version; read the tooltips on screen while you work.
