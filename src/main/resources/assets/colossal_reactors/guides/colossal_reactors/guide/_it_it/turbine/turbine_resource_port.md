---
navigation:
  parent: turbine/turbine-index.md
  title: Turbine Resource Port
  icon: turbine_resource_port
  position: 35
item_ids:
  - colossal_reactors:turbine_resource_port
categories:
  - ports
---

# Turbine Resource Port

<BlockImage id="turbine_resource_port" scale="4" />

Attacca una o più **Turbine Resource Port** al casing per spostare **vapore**, **acqua** e altri fluidi consentiti dal pack tra tubi/serbatoi e la turbina.

Layout tipico: **due resource port + una power port** sulla stessa faccia del [Turbine Controller](turbine_controller.md) — vedi [Per iniziare](getting_started.md). Passa sui diamanti nella scena sotto.

<GameScene zoom="6" interactive={true} background="transparent">
  <ImportStructure src="/assets/assemblies/minimal_turbine.snbt" />
  <IsometricCamera yaw="200" pitch="28" />
  <BlockAnnotation x="1" y="2" z="0" color="#55ffff">
**Esempio: vapore IN** — modalità **Insert**, **Liquid** abilitato. Tooltip: *Pulls steam (and other allowed fluids) into the turbine.*
  </BlockAnnotation>
  <BlockAnnotation x="5" y="2" z="0" color="#5599ff">
**Esempio: acqua OUT** — modalità **Extract**, **Liquid** abilitato. Tooltip: *Pushes water or exhaust fluid out to pipes.*
  </BlockAnnotation>
</GameScene>

Tubo vapore dal tuo [reattore a fissione](../multiblock/getting_started.md) (o qualsiasi fonte vapore) nella porta **INSERT**. Tubo **acqua / scarico** fuori dalla porta **EXTRACT**. Quanto la turbina può usare dipende dalle blade — vedi [Vapore, RF e coil](turbine_generation_and_coils.md).

## GUI — pulsanti e tooltip

La porta turbina usa lo stesso layout schermo della porta reattore, ma **senza riga Solid** e **senza filtro Fuel/Coolant**. Passa su ogni controllo in gioco — il testo sotto corrisponde ai tooltip di default.

### Modalità (pulsante in alto — clicca per ciclare)

| Etichetta | Tooltip |
|-------|---------|
| **Insert** | Pulls steam (and other allowed fluids) into the turbine. |
| **Extract** | Pushes water or exhaust fluid out to pipes. |
| **Eject** | Ejects stored fluid from the port tank to the outside. |

### Toggle medium (clicca per abilitare — sottolineato se attivo)

| Pulsante | Tooltip |
|--------|---------|
| **Liquid** | Click to allow or block fluids through this port. |
| **Gas** | Click to allow or block Mekanism gases through this port. |

### Serbatoi e dump

| Controllo | Tooltip |
|---------|---------|
| **Serbatoio liquido** (passa sulla barra) | Liquid: amount / capacity mB, plus fluid name |
| **Serbatoio gas** (passa sulla barra, Mekanism) | Gas: amount / capacity mB, plus gas name |
| **D** sotto serbatoio liquido | Dump: empty the internal fluid tank (fluid is discarded) |
| **D** sotto serbatoio gas | Dump: empty the gas tank (gas is discarded) |

Tubi e serbatoi si collegano all’**esterno**; la faccia **interna** deve puntare dentro la turbina.

Dopo aver cambiato modalità, fai **Reboot** del [Turbine Controller](turbine_controller.md).

## Ridimensionamento automatico della capacità

Quando la turbina **rivalida**, il serbatoio fluido della porta si ridimensiona da **vapore mB/t stimato × 10**, con pavimento **16.000 mB** e **nessun tetto fisso**. La capacità cresce subito quando aumenta la domanda e si riduce solo se il vapore memorizzato entra ancora nella dimensione più piccola.
