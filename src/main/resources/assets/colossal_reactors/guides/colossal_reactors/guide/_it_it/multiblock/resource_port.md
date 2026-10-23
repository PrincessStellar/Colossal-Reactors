---
navigation:
  parent: multiblock/multiblock-index.md
  title: Reactor Resource Port
  icon: resource_port
  position: 35
item_ids:
  - colossal_reactors:resource_port
categories:
  - ports
---

# Reactor Resource Port

<BlockImage id="resource_port" scale="4" />

Attacca una o più **Reactor Resource Port** al **casing** per spostare item e/o fluidi tra il mondo esterno e il reattore.

## Layout tipici

### Manuale / solo RF (porte non obbligatorie)

Combustibile e scorie si gestiscono dalla GUI del [Reactor Controller](reactor_controller.md). Serve comunque almeno una [Power Port](power_ports.md).

### Automazione: combustibile in + scorie out (**due porte**, stessa faccia del controller)

Metti **entrambe le resource port sulla stessa parete** del reactor controller. Esempio in [Per iniziare](getting_started.md) — le porte sono distanziate su quella faccia; passa sui diamanti nella scena per suggerimenti di setup.

| Porta (esempio) | Modalità | Filtro | Sposta |
|----------------|------|--------|-------|
| In basso a sinistra | **INSERT** | **Combustibile** | Combustibile solido **nelle** rod |
| In basso a destra | **EXTRACT** | **Scorie** | **Scorie** **out** |

Aggiungi una **power port** sulla stessa faccia. Tubo/hopper sull’**esterno** di ogni porta.

<GameScene zoom="6" interactive={true} background="transparent">
  <ImportStructure src="/assets/assemblies/minimal_reactor_automation.snbt" />
  <IsometricCamera yaw="200" pitch="28" />
  <BlockAnnotation x="1" y="2" z="0" color="#55ff55">
**Esempio: combustibile IN** — apri la porta → **Insert** → filtro **Fuel** → abilita **Solid** / **Liquid** come serve. Tooltip su **Insert**: *The reactor accepts solid fuel and a coolant fluid.*
  </BlockAnnotation>
  <BlockAnnotation x="5" y="2" z="0" color="#ffaa55">
**Esempio: scorie OUT** — **Extract** → filtro **Waste**. Tooltip su **Extract**: *The reactor extracts nuclear waste and spent coolant fluid.*
  </BlockAnnotation>
</GameScene>

### Loop refrigerante fluido (**porte extra**, opzionale)

Se usi **refrigerante attivo** (Setup 2 in [Per iniziare](getting_started.md)):

- **INSERT** con filtro **Coolant** — tubo refrigerante **in** (*Import: coolant fluid only.*)
- **EXTRACT** con filtro **Coolant** — tubo **vapore / fluido esausto** **out** → [Turbina — Per iniziare](../turbine/getting_started.md) (*Output: exhausted liquid coolant only.*)
- Mantieni **EXTRACT waste** su una porta separata; non aspettarti che una sola porta EXTRACT espella fluidi non correlati.

## GUI — pulsanti e tooltip

Apri la GUI della porta e **passa il mouse su ogni controllo** — il testo sotto è quello in gioco (inglese).

### Modalità (pulsante in alto — clicca per ciclare)

| Etichetta | Tooltip |
|-------|---------|
| **Insert** | The reactor accepts solid fuel and a coolant fluid. |
| **Extract** | The reactor extracts nuclear waste and spent coolant fluid. |
| **Eject** | The reactor ejects the solid fuel it contains. |

### Filtro (solo reattore — clicca per ciclare ruolo combustibile / refrigerante)

Dipende dalla **modalità**; passa sul pulsante per il filtro corrente:

| Modalità | Etichetta filtro | Tooltip |
|------|--------------|---------|
| **Insert** | All / Fuel / Coolant | *Import: fuel and coolant.* / *Import: fuel only (items or fluid).* / *Import: coolant fluid only.* |
| **Extract** | All / Waste / Coolant | *Output: solid waste and liquid coolant.* / *Output: solid waste only.* / *Output: exhausted liquid coolant only.* |
| **Eject** | Both / Fuel Only / Coolant Only | *Eject: fuel items and coolant fluid from rods.* / *Eject: fuel items only…* / *Eject: coolant fluid only.* |

### Toggle medium (clicca per abilitare — sottolineato se attivo)

| Pulsante | Tooltip |
|--------|---------|
| **Solid** | Click to allow or block solid items through this port. |
| **Liquid** | Click to allow or block fluids through this port. |
| **Gas** | Click to allow or block Mekanism gases through this port. |

### Serbatoi e dump

| Controllo | Tooltip |
|---------|---------|
| **Serbatoio liquido** (passa sulla barra) | Liquid: amount / capacity mB, plus fluid name |
| **Serbatoio gas** (passa sulla barra, Mekanism) | Gas: amount / capacity mB, plus gas name |
| **D** sotto serbatoio liquido | Dump: empty the internal fluid tank (fluid is discarded) |
| **D** sotto serbatoio gas | Dump: empty the gas tank (gas is discarded) |

- **Slot item** — I/O manuale o hopper/tubo sulla faccia **esterna**.

Dopo aver cambiato modalità o filtri, fai **Reboot** del [Reactor Controller](reactor_controller.md).

## Ridimensionamento automatico della capacità

Quando il reattore **rivalida**, ogni resource port ridimensiona i serbatoi fluido/gas (gas Mekanism se abilitato):

- Target ≈ **mB/t stimato per modalità e filtro della porta × 10**, minimo **16.000 mB** per serbatoio.
- **Nessun tetto fisso** sul volume fluido/gas per reattori grandi.
- La capacità **cresce** subito quando aumenta la domanda; **si riduce** solo se il contenuto memorizzato entra ancora nella dimensione più piccola.
