---
navigation:
  parent: multiblock/multiblock-index.md
  title: Per iniziare
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

# Per iniziare — il tuo primo reattore

Costruisci un guscio di reattore chiuso, poi scegli uno dei due setup sotto. Ruota la scena per ispezionare ogni faccia.

## Layout del guscio

- **Pavimento e tetto**: **Reactor Casing** (telaio chiuso).
- **Angoli verticali**: pilastri di **Reactor Casing** ai quattro spigoli (y=1…5).
- **Pareti ovest, est e sud**: pannello **5×5 Reactor Glass** su ogni faccia (cinque righe × cinque colonne tra i pilastri d’angolo).
- **Faccia del controller** (nord nell’esempio): il **Reactor Controller** sporge verso l’esterno come **blocco singolo**; le celle rimanenti su quella faccia usano **Reactor Glass** dove non c’è una porta. Le **porte sostituiscono il vetro** su quella parete — stessa idea delle altre facce, non un telaio aperto nudo.
- **Porte**: sulla **stessa parete del controller** — possono stare **ovunque** su quella faccia (l’esempio le distanzia dal controller apposta).

Il [Reactor Builder](reactor_builder.md) posiziona casing, vetro (se nel buffer), rod e heat sink — **non** il controller né le porte.

## Heat sink vs refrigerante

Sono **due sistemi diversi**:

| | **Heat sink** (passivi) | **Refrigerante** (attivo) |
|---|---|---|
| **Cosa** | Blocchi o liquido **stazionario** **dentro** il guscio vicino alle rod | **Fluido** di lavoro nel buffer del controller / loop resource-port |
| **Come lo posizioni** | Builder / a mano nell’interno (inclusi “heat sink fluidi”) | Slot refrigerante del controller, o porte **INSERT/EXTRACT** nello Setup 2 |
| **Consumato in funzione?** | **No** | **Sì** (e può produrre vapore / scarico) |
| **Serve per RF?** | Opzionale (cambia RF e uso combustibile) | **No** per Setup 1 — lascia il buffer refrigerante **vuoto** |

Gli heat sink fluidi posizionati come blocchi interni **non** sono lo stesso che tubare acqua nel circuito refrigerante. Dettagli: [Struttura del reattore](reactor_structure.md).

## Esempio completo

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="/assets/assemblies/minimal_reactor_automation.snbt" />
  <IsometricCamera yaw="225" pitch="28" />
</GameScene>

| Faccia nord (parete controller) | Blocco |
|------------------------------|-------|
| Fuori al centro (sporge da solo) | [Reactor Controller](reactor_controller.md) |
| In basso a sinistra / destra (distanziati) | Resource Port ×2 |
| Colonna centrale, un blocco sotto il controller | Power Port |
| Pareti ovest / est / sud | pannelli vetro **5×5** |

Le porte **non** sono legate al blocco controller — qualsiasi cella sulla faccia del controller va bene. La scena le mette **separate** così si vede.

## Setup 1: Energia semplice (solo RF)

Usalo quando vuoi solo **RF dal reattore** — niente linea vapore, turbina non richiesta.

1. Costruisci guscio e interni (rod + rod controller sopra).
2. Posiziona **controller + power port** su una faccia come nell’esempio (controller **fuori**, power port sostituisce il **vetro**).
3. Lascia il **buffer refrigerante vuoto** — nessun fluido refrigerante richiesto.
4. Aggiungi combustibile (GUI controller o hopper/tubo), apri controller → **valido** → **ON**.
5. Collega la **power port all’esterno** a un accettore di energia; verifica **RF/t** &gt; 0.

**Automazione opzionale** (nell’esempio): due **resource port** sulla **stessa faccia** — configura in ogni GUI porta (**Insert** / **Extract**, filtro, toggle medium). Vedi [tooltip GUI Resource Port](resource_port.md) per ogni pulsante. **Reboot** dopo aver cambiato le modalità delle porte.

| Porta (posizioni esempio) | Modalità | Filtro |
|------|------|--------|
| In basso a sinistra sulla faccia controller | **INSERT** | **Solo combustibile** |
| In basso a destra sulla faccia controller | **EXTRACT** | **Entrambi** o solo solidi |
| Colonna centrale, un blocco sotto il controller | Power Port | — (RF out) |

Si può anche giocare a mano: salta le resource port e carica il combustibile a mano nel controller.

## Setup 2: Refrigerante (vapore per una turbina)

Usalo quando acqua (o altro refrigerante) deve produrre **vapore** per una [turbina](../turbine/getting_started.md) invece di (o in aggiunta a) RF diretto dal reattore.

1. Costruisci lo stesso guscio e posiziona **controller + power port** su una faccia (la power port resta utile per l’RF che il reattore tiene).
2. Nel controller, imposta il **fluido refrigerante** (tipicamente **acqua** / modalità vapore).
3. Sulla **stessa faccia**, aggiungi **altre due resource port** (sostituisci celle di **vetro**):
   - **INSERT** — **Solo refrigerante** (o **Entrambi**) — tubo refrigerante **in**
   - **EXTRACT** — **Solo refrigerante** — tubo **vapore / fluido esausto** **out**
4. Mantieni le porte **INSERT combustibile** e **EXTRACT scorie** dello Setup 1, oppure gestisci combustibile/scorie a mano.
5. Porta il **vapore** dalla porta **EXTRACT** del reattore a una [resource port turbina](../turbine/getting_started.md) (**INSERT**). Preleva **RF** dalla **power port** della turbina.

Con abbastanza conversione, l’**RF/t del reattore può scendere** mentre sale il **vapore** — è normale. Dimensiona la turbina e tubi abbastanza vapore; vedi [Turbina — Per iniziare](../turbine/getting_started.md).

**Reboot** del controller reattore dopo aver cambiato refrigerante, filtri o modalità porte.

## Checklist

1. Guscio chiuso: casing pavimento/tetto, pilastri angolo, **vetro 5×5** su tre pareti; faccia controller con **vetro + porte** (controller fuori da solo).
2. **Un [Reactor Controller](reactor_controller.md)** su una faccia laterale, **da solo** fuori dal guscio.
3. **Power port** (+ resource port opzionali) su quella **stessa** faccia.
4. Scegli **Setup 1** (refrigerante vuoto) o **Setup 2** (loop refrigerante + turbina).
5. La **Redstone Port** è opzionale.

## Reactor Builder (opzionale)

1. Il builder posiziona solo **interni + guscio**.
2. **Stop** del build, poi aggiungi controller (fuori) e porte su **una** faccia come nella scena.
3. Valida dal controller.

## Il reattore non parte?

| Sintomo | Causa probabile |
|---------|----------------|
| Stato **Invalid** | Sistema guscio, rod o rod controller (leggi la riga di stato). |
| **ON** ma 0 RF/t | Niente power port o accettore energia; oppure modalità refrigerante senza percorso supply/extract. |
| Bloccato **OFF** + redstone port | Redstone / modalità sulla porta. |
| Niente uscita scorie | Manca resource port **EXTRACT** o filtro sbagliato. |
| Niente vapore per la turbina | Refrigerante non impostato, INSERT a secco, o manca porta refrigerante **EXTRACT**. |

## Vedi anche

- [Reactor Builder](reactor_builder.md)
- [Reactor Controller](reactor_controller.md)
- [Reactor Resource Port](resource_port.md)
- [Reactor Power Port](power_ports.md)
- [Turbina — Per iniziare](../turbine/getting_started.md)
- [Struttura del reattore](reactor_structure.md)
