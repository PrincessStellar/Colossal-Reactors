---
navigation:
  parent: turbine/turbine-index.md
  title: Per iniziare
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

# Per iniziare — la tua prima turbina

Costruisci un guscio di turbina chiuso con rotore e coil all’interno, poi scegli uno dei due setup sotto. Ruota la scena per ispezionare ogni faccia.

## Layout del guscio

- **Pavimento e tetto**: **Turbine Casing** (telaio chiuso).
- **Angoli verticali**: pilastri di **Turbine Casing** ai quattro spigoli (y=1…5).
- **Pareti ovest, est e sud**: pannello **5×5 Turbine Glass** su ogni faccia.
- **Parete divisoria** all’altezza del **rod controller** (y=3 nell’esempio): anello di **Turbine Casing** dentro e **fascia casing** sulle pareti laterali — questa riga **taglia** il vetro 5×5 su quelle tre facce.
- **Faccia del controller** (nord nell’esempio): il **Turbine Controller** sporge verso l’esterno come **blocco singolo**; le celle rimanenti usano **Turbine Glass** dove non c’è una porta. Le **porte sostituiscono il vetro** su quella parete — stesso layout dell’[esempio reattore](../multiblock/getting_started.md).
- **Zona coil** (sopra il divisore): **due strati** in cima all’interno (y=4–5 nell’esempio) — **blocchi di rame** direttamente sotto il casing del tetto.
- **Zona rotore** (sotto il divisore): **due segmenti rod** (y=1–2) — **quattro blade** sull’anello inferiore, **otto blade** in due anelli sul segmento superiore; il **Turbine Rod Controller** sta sullo strato divisore (y=3).

Il [Turbine Builder](turbine_builder.md) posiziona guscio, rotore e coil — **non** il controller né le porte.

## Coil

La **zona coil** è l’**interno superiore** sopra la parete divisoria (sotto il tetto). Deve essere riempita con **blocchi di storage metallici** riconosciuti dal mod — rame, oro, netherite, electrum, ecc.

| | Dettaglio |
|---|---|
| **Ruolo** | Moltiplica l’**RF** dal vapore (`coil strength` nella formula RF sotto) |
| **Dove** | Strati interni **sopra** il divisore — non lo spazio rotore / blade |
| **Cosa usare** | Normali **blocchi di storage** (es. blocco di rame). Controlla **JEI** per forza e tetti |
| **Cosa non usare** | Blocchi **heating coil** del reattore — sono un pezzo macchina diverso |
| **Celle vuote** | Validano comunque come coil deboli (~**30%** di forza di default) |
| **Strati** | L’esempio ne usa **due**; il [Turbine Builder](turbine_builder.md) può impostarne quanti |

Mischiare metalli va bene — la turbina usa la **media** di forza di tutta la zona. Numeri completi: [Vapore, RF e coil](turbine_generation_and_coils.md).

## Esempio completo

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="/assets/assemblies/minimal_turbine.snbt" />
  <IsometricCamera yaw="225" pitch="28" />
</GameScene>

| Faccia nord (parete controller) | Blocco |
|------------------------------|-------|
| Fuori al centro (sporge da solo) | [Turbine Controller](turbine_controller.md) |
| In basso a sinistra / destra (distanziati) | Turbine Resource Port ×2 |
| Colonna centrale, un blocco sotto il controller | Turbine Power Port |
| Pareti ovest / est / sud | vetro **5×5** (spezzato dal divisore all’altezza del rod controller) |

Come l’esempio reattore: **due resource port + una power port** sulla faccia del controller (stesse posizioni della scena reattore).

## Efficienza — cosa influenza l’uscita RF

Quando la turbina è **formata correttamente** e **in funzione**, l’RF per tick segue:

**RF/t** ≈ vapore usato × **RF per mB** × **coil strength** × **bonus layout blade**

Vedi [Vapore, RF e coil](turbine_generation_and_coils.md) per il dettaglio. In breve:

- **Approvvigionamento vapore** — limitato da conteggio blade e layout; più anelli bilanciati → più vapore/tick.
- **Blocchi coil** — storage metallici nei **due strati interni superiori** (rame, oro, netherite, …). Le celle coil vuote contano come deboli (~30%); controlla JEI per i valori.
- **Bonus altezza blade** — anelli che crescono verso il divisore guadagnano **+3%** per strato ascendente con più blade; anelli che si restringono perdono **−3%**. Contano i layout **Efficient** vs **Productive** — vedi [Rod e blade](turbine_rod_and_blades.md).
- **Formazione valida** — guscio, coil, rod controller e porte devono validare prima che la generazione giri.

## Setup 1: Energia semplice (vapore standalone)

Usalo quando il **vapore esiste già** (serbatoi, altro mod, creative) e ti serve solo **RF dalla turbina**.

1. Costruisci guscio, rotore (rod + blade), coil e **rod controller** — oppure usa il [Turbine Builder](turbine_builder.md).
2. Posiziona **controller + tre porte** su una faccia come nell’esempio (controller **fuori**, porte **distanziate** sulla stessa faccia).
3. Configura **due resource port**: **INSERT** (vapore in) e **EXTRACT** (acqua / fluido esausto out). Tubi entrambi all’**esterno**.
4. Collega la **power port all’esterno** a un accettore di energia.
5. Apri controller → **valido** → **ON**; controlla **RF/t** e uso vapore.

| Porta (posizioni esempio) | Modalità | Ruolo |
|------|------|------|
| In basso a sinistra sulla faccia controller | Resource Port — **INSERT** | Vapore **in** |
| In basso a destra sulla faccia controller | Resource Port — **EXTRACT** | Acqua / scarico **out** |
| Colonna centrale, un blocco sotto il controller | Power Port | RF **out** |

Configura **due resource port** nelle loro GUI — passa su ogni pulsante per i tooltip ([elenco completo](turbine_resource_port.md)). **Reboot** dopo aver cambiato modalità porte o layout rotore.

## Setup 2: Catena refrigerante (vapore dal reattore)

Usalo con [Reattore — Per iniziare — Setup 2 (Refrigerante)](../multiblock/getting_started.md): il reattore a fissione produce **vapore**, la turbina lo trasforma in **più RF**.

1. Costruisci la turbina come nello Setup 1 (guscio, rotore, coil, controller, porte su **una** faccia).
2. Sul reattore, esegui lo **Setup 2**: refrigerante nel controller, **INSERT** refrigerante + **EXTRACT** vapore/fluido esausto sulla **stessa faccia del reactor controller**.
3. Tubo **vapore** dalla porta **EXTRACT** del reattore → resource port **INSERT** della turbina. Tubo **acqua** dalla resource port **EXTRACT** della turbina a serbatoi o di nuovo al loop refrigerante del reattore.
4. Preleva **RF** dalla **power port** della turbina (la power port del reattore è RF di backup opzionale).

Abbina dimensioni e blade della turbina all’uscita vapore del reattore — vedi [Vapore, RF e coil](turbine_generation_and_coils.md). Se il vapore si accumula, aumenta capacità, turbina più grande o più blade.

## Checklist

1. Guscio chiuso: casing pavimento/tetto, pilastri angolo, **vetro 5×5** su tre pareti (divisore a **y=3** taglia una riga); faccia controller con **vetro + porte**.
2. **Rotore** valido sotto il divisore: **due blocchi rod** (y=1–2), **4 blade** sull’anello inferiore, **8 blade** (due anelli) sul segmento superiore + **due strati coil** in cima (y=4–5, es. blocchi di rame).
3. **Un [Turbine Controller](turbine_controller.md)** su una faccia laterale, **fuori** dalla scatola da solo.
4. **Due resource port + una power port** sulla faccia del controller (qualsiasi posizione su quella faccia).
5. Scegli **Setup 1** (qualsiasi fonte vapore) o **Setup 2** (loop refrigerante reattore).
6. La **Redstone Port** è opzionale.

## Turbine Builder (opzionale)

1. Il builder posiziona solo **guscio + rotore + coil**.
2. **Stop** del build, poi aggiungi controller (fuori) e porte su **una** faccia come nella scena.
3. Valida dal controller.

## La turbina non gira?

| Sintomo | Causa probabile |
|---------|----------------|
| Stato **Invalid** | Sistema guscio, rod, blade, coil o rod controller (leggi la riga di stato). |
| **ON** ma 0 RF/t | Niente vapore, niente power port o accettore energia. |
| RF basso vs vapore in | Troppe poche blade/coil per il tasso di vapore — vedi [Rod e blade](turbine_rod_and_blades.md). |
| Niente uso vapore | Resource port non in **INSERT**, o tubo sul lato sbagliato. |
| Acqua accumula | Manca resource port **EXTRACT** o serbatoio/tubo all’esterno. |

## Vedi anche

- [Turbine Builder](turbine_builder.md)
- [Turbine Controller](turbine_controller.md)
- [Turbine Resource Port](turbine_resource_port.md)
- [Turbine Power Port](turbine_power_ports.md)
- [Reattore — Per iniziare — Setup refrigerante](../multiblock/getting_started.md)
- [Vapore, RF e coil](turbine_generation_and_coils.md)
