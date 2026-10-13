---
navigation:
  parent: turbine/turbine-index.md
  title: Getting started
  icon: getting_started
  position: 5
item_ids:
  - colossal_reactors:turbine_casing
  - colossal_reactors:turbine_glass
  - colossal_reactors:turbine_rod
  - colossal_reactors:turbine_rod_controller
  - colossal_reactors:turbine_blade
  - colossal_reactors:turbine_power_port
  - colossal_reactors:turbine_resource_port
  - colossal_reactors:turbine_builder
categories:
  - multiblock
---

# Getting started — your first turbine

Build a closed turbine shell with a rotor and coils inside, then pick one of the two setups below. Rotate the scene to inspect each face.

## Shell layout

- **Floor and roof**: **Turbine Casing** (closed frame).
- **Vertical corners**: **Turbine Casing** pillars at the four edges (y=1…5).
- **West, east, and south walls**: **5×5 Turbine Glass** panel on each face.
- **Divider wall** at **rod-controller height** (y=3 in the example): **Turbine Casing** ring inside and **casing band** on the side walls — this row **cuts through** the 5×5 glass on those three faces.
- **Controller face** (north in the example): the **Turbine Controller** protrudes outward as a **single block**; remaining cells on that face use **Turbine Glass** where no port is placed. **Ports replace glass** on that wall — same layout as the [reactor example](../multiblock/getting_started.md).
- **Coil zone** (above the divider): **two layers** at the top of the interior (y=4–5 in the example) — **copper blocks** directly under the roof casing.
- **Rotor zone** (below the divider): **two rod segments** (y=1–2) — **four blades** on the lower ring, **eight blades** in two rings on the upper segment; **Turbine Rod Controller** sits on the divider layer (y=3).

The [Turbine Builder](turbine_builder.md) places the shell, rotor, and coils — **not** the controller or ports.

## Coils

The **coil zone** is the **upper interior** above the divider wall (under the roof). It must be filled with **metal storage blocks** the mod recognizes — copper, gold, netherite, electrum, and so on.

| | Detail |
|---|---|
| **Role** | Multiplies **RF** from steam (`coil strength` in the RF formula below) |
| **Where** | Interior layers **above** the divider — not the rotor / blade space |
| **What to use** | Normal **storage blocks** (e.g. copper block). Check **JEI** for strength and caps |
| **What not to use** | Reactor **heating coil** blocks — those are a different machine part |
| **Empty cells** | Still validate as weak coil (~**30%** strength by default) |
| **Layers** | Example uses **two**; the [Turbine Builder](turbine_builder.md) can set how many |

Mixing metals is fine — the turbine uses the **average** strength of the whole zone. Full numbers: [Steam, RF, and coils](turbine_generation_and_coils.md).

## Complete example

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="/assets/assemblies/minimal_turbine.snbt" />
  <IsometricCamera yaw="225" pitch="28" />
</GameScene>

| North face (controller wall) | Block |
|------------------------------|-------|
| Outside center (protruding alone) | [Turbine Controller](turbine_controller.md) |
| Lower-left / lower-right (spread) | Turbine Resource Port ×2 |
| Center column, one block below controller | Turbine Power Port |
| West / east / south walls | **5×5** glass (split by divider at rod-controller height) |

Like the reactor example: **two resource ports + one power port** on the controller face (same positions as the reactor scene).

## Efficiency — what affects RF output

When the turbine is **formed correctly** and **running**, RF per tick follows:

**RF/t** ≈ steam used × **RF per mB** × **coil strength** × **blade layout bonus**

See [Steam, RF, and coils](turbine_generation_and_coils.md) for the full breakdown. In short:

- **Steam supply** — capped by blade count and layout; more balanced rings → higher steam/tick.
- **Coil blocks** — metal storage blocks in the **two upper interior layers** (copper, gold, netherite, …). Empty coil cells count as weak (~30% strength); check JEI for block values.
- **Blade height bonus** — rings that grow taller toward the divider gain **+3%** per ascending layer with more blades; shrinking rings lose **−3%**. **Efficient** vs **Productive** layouts matter — see [Rods and blades](turbine_rod_and_blades.md).
- **Valid formation** — shell, coils, rod controller, and ports must all validate before generation runs.

## Setup 1: Simple energy (standalone steam)

Use this when **steam already exists** (tanks, another mod, creative) and you only need **RF from the turbine**.

1. Build shell, rotor (rods + blades), coils, and **rod controller** — or use the [Turbine Builder](turbine_builder.md).
2. Place **controller + three ports** on one face as in the example (controller **outside**, ports **spread** on the same face).
3. Configure **two resource ports**: **INSERT** (steam in) and **EXTRACT** (water / spent fluid out). Pipe both on the **outside**.
4. Connect the **power port outside** to an energy acceptor.
5. Open controller → **valid** → **ON**; check **RF/t** and steam use.

| Port (example positions) | Mode | Role |
|------|------|------|
| Lower-left on controller face | Resource Port — **INSERT** | Steam **in** |
| Lower-right on controller face | Resource Port — **EXTRACT** | Water / exhaust **out** |
| Center column, one block below controller | Power Port | RF **out** |

Configure **two resource ports** in their GUIs — hover each button for tooltips ([full list](turbine_resource_port.md)). **Reboot** after changing port modes or rotor layout.

## Setup 2: Coolant chain (reactor steam)

Use this with [Reactor Getting started — Setup 2 (Coolant)](../multiblock/getting_started.md): the fission reactor makes **steam**, the turbine turns it into **more RF**.

1. Build the turbine as in Setup 1 (shell, rotor, coils, controller, ports on **one** face).
2. On the reactor, run **Setup 2**: coolant in controller, **INSERT** coolant + **EXTRACT** steam/spent fluid on the **same face as the reactor controller**.
3. Pipe **steam** from the reactor **EXTRACT** port → turbine **INSERT** resource port. Pipe **water** from the turbine **EXTRACT** resource port to tanks or back to the reactor coolant loop.
4. Take **RF** from the turbine **power port** (reactor power port is optional backup RF).

Match turbine size and blades to reactor steam output — see [Steam, RF, and coils](turbine_generation_and_coils.md). If steam backs up, add capacity, a larger turbine, or more blades.

## Checklist

1. Closed shell: casing floor/roof, corner pillars, **5×5 glass** on three walls (divider at **y=3** cuts one row); controller face uses **glass + ports**.
2. Valid **rotor** below divider: **two rod blocks** (y=1–2), **4 blades** on the lower ring, **8 blades** (two rings) on the upper segment + **two coil layers** at the top (y=4–5, e.g. copper blocks).
3. **One [Turbine Controller](turbine_controller.md)** on a side face, **outside** the box alone.
4. **Two resource ports + one power port** on the controller face (any positions on that face).
5. Pick **Setup 1** (any steam source) or **Setup 2** (reactor coolant loop).
6. **Redstone Port** is optional.

## Turbine Builder (optional)

1. Builder places **shell + rotor + coils** only.
2. **Stop** build, then add controller (outside) and ports on **one** face as in the scene.
3. Validate from the controller.

## Turbine won’t run?

| Symptom | Likely cause |
|---------|----------------|
| Status **Invalid** | Fix shell, rods, blades, coils, or rod controller (read status line). |
| **ON** but 0 RF/t | No steam supply, no power port, or no energy acceptor. |
| Low RF vs steam in | Too few blades/coils for the steam rate — see [Rods and blades](turbine_rod_and_blades.md). |
| No steam use | Resource port not **INSERT**, or pipe connected on wrong side. |
| Water backs up | Missing **EXTRACT** resource port or no tank/pipe on the outside. |

## See also

- [Turbine Builder](turbine_builder.md)
- [Turbine Controller](turbine_controller.md)
- [Turbine Resource Port](turbine_resource_port.md)
- [Turbine Power Ports](turbine_power_ports.md)
- [Reactor Getting started — Coolant setup](../multiblock/getting_started.md)
- [Steam, RF, and coils](turbine_generation_and_coils.md)
