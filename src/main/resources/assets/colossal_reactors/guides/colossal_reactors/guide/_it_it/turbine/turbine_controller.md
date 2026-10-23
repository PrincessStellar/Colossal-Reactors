---
navigation:
  parent: turbine/turbine-index.md
  title: Turbine Controller
  icon: turbine_controller
  position: 20
item_ids:
  - colossal_reactors:turbine_controller
categories:
  - multiblock
---

# Turbine Controller

<BlockImage id="turbine_controller" scale="4" />

**Click destro** sul controller quando la turbina è **costruita correttamente** per aprire lo schermo principale.

Nuovo ai multiblocco? Inizia da [Per iniziare](colossal_reactors:turbine/getting_started.md).

## Funzionamento

- **Avvia e ferma** la turbina e osserva **uscita RF**, **uso vapore** ed efficienza a schermo.

- La turbina viene ricontrollata di tanto in tanto mentre gira; se qualcosa si rompe, si ferma e ti dice perché (action bar e messaggio GUI).

- Di norma puoi avere solo **un** turbine controller per multiblocco.

- Posizionalo su una **parete laterale** del guscio (nord, sud, est o ovest nel mondo—non sopra o sotto). È separato dalla direzione dell’**albero delle rod** all’interno.

## Fluidi e potenza

- Il **vapore** entra tramite [Turbine Resource Port](turbine_resource_port.md).

- L’**RF** esce tramite [Turbine Power Port](turbine_power_ports.md).

- Il vapore esausto spesso diventa **acqua** (o un altro fluido definito dal pack)—vedi [Vapore, RF e coil](turbine_generation_and_coils.md).

## Fermare senza rompere blocchi

Una [Turbine Redstone Port](turbine_redstone_port.md) può spegnere la turbina dalla redstone esterna lasciando la struttura intatta—stesso ruolo di una [Reactor Redstone Port](../multiblock/redstone_port.md).

Ciò che vedi sul controller dipende dal **layout blade** e dai **blocchi coil**; regola quelli dentro la turbina, non solo su questo schermo.
