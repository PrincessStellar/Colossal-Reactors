---
navigation:
  parent: multiblock/multiblock-index.md
  title: Reactor Controller
  icon: reactor_controller
  position: 20
item_ids:
  - colossal_reactors:reactor_controller
categories:
  - multiblock
---

# Reactor Controller

<BlockImage id="reactor_controller" scale="4" />

Apri il controller con **click destro** quando è su una faccia **laterale** del casing (non sopra o sotto), **sostituendo un blocco casing** sul telaio esterno con lo schermo rivolto verso l’**esterno**.

Nuovo ai multiblocco? Inizia da [Per iniziare](colossal_reactors:multiblock/getting_started.md).

## Funzionamento

- **Valida / avvia** — la prima apertura esegue la validazione; se valida il reattore può andare **ON** e mostrare RF/tick, combustibile e refrigerante.
- **Combustibile** — sposta il fuel tra inventario e storage delle rod quando gli slot sono visibili.
- **Fluido refrigerante** — visualizza livello e riempi/svuota quando i controlli sono presenti (opzionale per il gioco **solo RF** con buffer refrigerante vuoto).
- **Reboot** — rivalida la struttura e aggiorna le capacità dei serbatoi delle porte dopo modifiche al build o alle impostazioni delle porte.

## Fermare senza smontare il multiblocco

Usa una [Reactor Redstone Port](redstone_port.md) se vuoi che la redstone esterna **trattenga** o **fermi** il funzionamento mentre il guscio resta formato. **Senza** redstone port, il reattore gira quando è valido e **ON** (nessun gate redstone).

Se le letture di **stabilità** sono abilitate per il tuo pack, compaiono qui — vedi [Instabilità del reattore](reactor_instability.md).

## Reattore valido ma non produce?

| Controllo | |
|-------|---|
| Power port presente e cablata? | L’RF richiede una [Power Port](power_ports.md) con qualcosa che accetta energia all’**esterno**. |
| Modalità solo RF? | Buffer refrigerante vuoto = nessun fluido richiesto; modalità acqua/vapore serve refrigerante + percorso extract. |
| Redstone port? | Il reattore può restare **OFF** finché modalità + segnale della porta non consentono la corsa. |
| Scorie accumulano? | Aggiungi una [Resource Port](resource_port.md) in **EXTRACT** o svuota le scorie a mano. |

I tooltip e la riga di stato sullo schermo spiegano lo stato corrente.
