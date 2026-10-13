---
navigation:
  parent: multiblock/multiblock-index.md
  title: Getting started
  icon: getting_started
  position: 5
item_ids:
  - colossal_reactors:reactor_casing
  - colossal_reactors:reactor_glass
  - colossal_reactors:reactor_rod
  - colossal_reactors:rod_controller
  - colossal_reactors:power_port
  - colossal_reactors:resource_port
  - colossal_reactors:reactor_builder
categories:
  - multiblock
---

# Getting started — your first reactor

Build a closed reactor shell, then pick one of the two setups below. Rotate the scene to inspect each face.

## Shell layout

- **Floor and roof**: **Reactor Casing** (closed frame).
- **Vertical corners**: **Reactor Casing** pillars at the four edges (y=1…5).
- **West, east, and south walls**: **5×5 Reactor Glass** panel on each face (five rows × five columns between the corner pillars).
- **Controller face** (north in the example): the **Reactor Controller** protrudes outward as a **single block**; remaining cells on that face use **Reactor Glass** where no port is placed. **Ports replace glass** on that wall — same idea as on the other faces, not a bare open frame.
- **Ports**: on the **same wall as the controller** — they can sit **anywhere** on that face (the example spreads them away from the controller on purpose).

The [Reactor Builder](reactor_builder.md) places casing, glass (if in buffer), rods, and heat sinks — **not** the controller or ports.

## Heat sinks vs coolant

These are **two different systems**:

| | **Heat sinks** (passive) | **Coolant** (active) |
|---|---|---|
| **What** | Blocks or **stationary** liquid fill **inside** the shell next to rods | Working **fluid** in the controller buffer / resource-port loop |
| **How you place it** | Builder / by hand in the interior (including “fluid heat sinks”) | Controller coolant slot, or **INSERT/EXTRACT** ports in Setup 2 |
| **Consumed while running?** | **No** | **Yes** (and may produce steam / exhaust) |
| **Needed for RF?** | Optional (changes RF and fuel use) | **No** for Setup 1 — leave the coolant buffer **empty** |

Fluid heat sinks placed as interior blocks are **not** the same as piping water into the coolant circuit. Details: [Reactor structure](reactor_structure.md).

## Complete example

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="/assets/assemblies/minimal_reactor_automation.snbt" />
  <IsometricCamera yaw="225" pitch="28" />
</GameScene>

| North face (controller wall) | Block |
|------------------------------|-------|
| Outside center (protruding alone) | [Reactor Controller](reactor_controller.md) |
| Lower-left / lower-right (spread) | Resource Port ×2 |
| Center column, one block below controller | Power Port |
| West / east / south walls | **5×5** glass panels |

Ports are **not** tied to the controller block — any cell on the controller face works. The scene places them **apart** so you can see that.

## Setup 1: Simple energy (RF-only)

Use this when you only want **RF from the reactor** — no steam line, no turbine required.

1. Build the shell and internals (rods + rod controller on top).
2. Place **controller + power port** on one face as in the example (controller **outside**, power port replaces **glass**).
3. Leave the controller **coolant buffer empty** — no fluid coolant is required.
4. Add fuel (controller GUI or hopper/pipe), open controller → **valid** → **ON**.
5. Connect the **power port outside** to an energy acceptor; check **RF/t** &gt; 0.

**Optional automation** (shown in the example): two **resource ports** on the **same face** — configure in each port GUI (**Insert** / **Extract**, filter, medium toggles). See [Resource Port GUI tooltips](resource_port.md) for every button. **Reboot** after changing port modes.

| Port (example positions) | Mode | Filter |
|------|------|--------|
| Lower-left on controller face | **INSERT** | **Fuel only** |
| Lower-right on controller face | **EXTRACT** | **Both** or solid-only |
| Center column, one block below controller | Power Port | — (RF out) |

Manual play works too: skip the resource ports and load fuel by hand in the controller.

## Setup 2: Coolant (steam for a turbine)

Use this when water (or another coolant) should produce **steam** for a [turbine](../turbine/getting_started.md) instead of (or in addition to) direct reactor RF.

1. Build the same shell and place **controller + power port** on one face (power port still useful for any RF the reactor keeps).
2. In the controller, set the **coolant fluid** (typically **water** / steam mode).
3. On the **same face**, add **two more resource ports** (replace **glass** cells):
   - **INSERT** — **Coolant only** (or **Both**) — pipe coolant **in**
   - **EXTRACT** — **Coolant only** — pipe **steam / spent fluid** **out**
4. Keep the **fuel INSERT** and **waste EXTRACT** ports from Setup 1, or handle fuel/waste manually.
5. Pipe **steam** from the reactor **EXTRACT** port into a [turbine resource port](../turbine/getting_started.md) (**INSERT**). Take **RF** from the turbine **power port**.

With enough conversion, reactor **RF/t may drop** while **steam** rises — that is normal. Size the turbine and pipe enough steam; see [Turbine Getting started](../turbine/getting_started.md).

**Reboot** the reactor controller after changing coolant, filters, or port modes.

## Checklist

1. Closed shell: casing floor/roof, corner pillars, **5×5 glass** on three walls; controller face uses **glass + ports** (controller outside alone).
2. **One [Reactor Controller](reactor_controller.md)** on a side face, **alone** outside the shell.
3. **Power port** (+ optional resource ports) on that **same** face.
4. Pick **Setup 1** (empty coolant) or **Setup 2** (coolant loop + turbine).
5. **Redstone Port** is optional.

## Reactor Builder (optional)

1. Builder places **internals + shell** only.
2. **Stop** build, then add controller (outside) and ports on **one** face as in the scene.
3. Validate from the controller.

## Reactor won’t start?

| Symptom | Likely cause |
|---------|----------------|
| Status **Invalid** | Fix shell, rods, or rod controllers (read status line). |
| **ON** but 0 RF/t | No power port or no energy acceptor; or coolant mode without supply/extract path. |
| Stuck **OFF** + redstone port | Redstone / mode on the port. |
| No waste output | Missing **EXTRACT** resource port or wrong filter. |
| No steam for turbine | Coolant not set, starved INSERT, or missing **EXTRACT** coolant port. |

## See also

- [Reactor Builder](reactor_builder.md)
- [Reactor Controller](reactor_controller.md)
- [Reactor Resource Port](resource_port.md)
- [Reactor Power Ports](power_ports.md)
- [Turbine Getting started](../turbine/getting_started.md)
- [Reactor structure](reactor_structure.md)
