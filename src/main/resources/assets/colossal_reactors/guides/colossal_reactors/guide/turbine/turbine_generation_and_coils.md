---
navigation:
  parent: turbine/turbine-index.md
  title: Steam, RF, and coils
  icon: minecraft:copper_block
  position: 12
categories:
  - multiblock
---

# Steam, RF, and coils

## How much RF you get

When the turbine is **formed correctly** and **running**, it turns **steam** into **RF** each tick. In short:

**RF per tick** ≈ steam used × **RF per mB of steam** × **coil strength** × **blade layout bonus**

- **Steam** comes in through a [Turbine Resource Port](turbine_resource_port.md). The turbine cannot use more steam per tick than your blades allow (see below).

- **RF per mB of steam** is usually **7** for standard steam (water exhaust). Pack recipes or settings can change this.

- **Coil strength** depends on which **storage blocks** fill the coil zone (gold, copper, netherite, and so on—see below).

- **Blade layout bonus** depends on how rings stack by height—see [Rods and blades](turbine_rod_and_blades.md). If any rod column lacks a rod controller on the closure deck, that bonus is also cut by **−5%**.

Pack settings can also multiply overall RF output or steam use up or down.

### Why bother with a turbine?

A fission reactor makes a lot of steam but relatively little direct RF from that steam. A full turbine with blades and coils is meant to **convert that steam into more RF** than you would get from the reactor alone—if you size the turbine and pipe enough steam in.

## How much steam you can use

The turbine’s **steam per tick** cap grows with how many **valid blades** you have installed. More rods with balanced rings means a higher cap. Very large turbines (up to **65×65×65** interior) are tuned so a fully built rotor can consume on the order of **tens of millions of mB/t** of steam when fed properly—exact numbers depend on your pack.

## Electrical coil zone

The **upper interior layers** (default **3** layers, changeable in the [Turbine Builder](turbine_builder.md)) are the **coil zone**. Fill them with **metal storage blocks** that the mod recognizes (gold block, copper block, electrum, netherite, etc.).

- Better blocks give a **higher coil multiplier** (each block type has a strength and a cap listed in **JEI** when turbine recipes are shown).

- **Empty space** or blocks the mod does not recognize count as weak coil (about **30%** strength by default).

- Use normal **storage blocks** from other mods—not the reactor’s **heating coil** blocks.

Average strength across the whole coil zone is what matters; mixing several metals is fine.

## Which steam counts

By default the turbine accepts **steam** (often the `#c:steam` fluid tag) and may output **water** as exhaust. Your modpack can add other fluids or different RF rates in its data files—**JEI** is the place to check what your pack allows.
