---
navigation:
  parent: turbine/turbine-index.md
  title: Struttura della turbina
  icon: turbine_casing
  position: 10
item_ids:
  - colossal_reactors:turbine_casing
  - colossal_reactors:turbine_glass
  - colossal_reactors:turbine_rod_controller
categories:
  - multiblock
---

# Struttura della turbina

## Costruire il guscio

<Row gap="16" fullWidth={true}>
  <BlockImage id="turbine_casing" scale="4" />
  <BlockImage id="turbine_glass" scale="4" />
</Row>

- Usa **Turbine Casing** per pareti solide e **Turbine Glass** dove vuoi vedere dentro.

- Costruisci una **scatola chiusa**. La dimensione massima di default è **65×65×65** (stessa idea dei reattori grandi; il pack può consentire meno).

- Dentro serve spazio per **rod e blade**, una sezione **coil**, **porte** sulle pareti e un **Turbine Controller** su una **parete laterale** esterna (nord, sud, est o ovest—non sopra o sotto della scatola).

## Rod controller

<ItemImage id="turbine_rod_controller" scale="4" />

Posiziona i blocchi **Turbine Rod Controller** sul **deck di chiusura**—lo strato di casing che separa il **rotore** (sotto) dalla **zona coil** (sopra). Impostano la direzione dell’albero (orientabili in **sei direzioni**, come una heating coil).

- Ne serve **almeno uno**. Puoi metterne **uno per ogni colonna di rod**, ciascuno **direttamente sulla cella del deck sopra la pila di rod** di quella colonna.
- Tutti i controller devono condividere lo **stesso piano del deck** e lo **stesso facing**.
- I controller **non** stanno sul bordo esterno del guscio.

Se una colonna ha rod ma **non** ha un controller sulla sua cella del deck, l’efficienza blade prende una penalità **−5%** (vedi [Rod e blade](turbine_rod_and_blades.md)). Un controller su **ogni** colonna annulla quella penalità.

- Le rod devono allinearsi con l’asse del controller. Le rod orientate male vengono corrette quando la turbina controlla il build, quando possibile.

- Questo **non** è il **Turbine Controller** sulla parete esterna—quello apre lo schermo principale della turbina.

## Turbine rod

<ItemImage id="turbine_rod" scale="4" />

- Le **Turbine Rod** formano l’albero. Seguono la direzione del rod controller (su/giù o lungo una parete).

- **Click destro** su pavimento o soffitto per un albero **verticale**; **click destro** su una parete per un albero **orizzontale** lungo quella parete.

- Attacca le **blade** con l’item blade—vedi [Rod e blade](turbine_rod_and_blades.md). Rompere una rod droppa tutte le sue blade su quel punto.

## Cosa va dove (panoramica)

| Area | Dove | Cosa ci metti |
|------|--------|-------------------|
| **Rotore** | Interno principale, sotto i coil | Rod, blade |
| **Deck di chiusura** | Strato tra rotore e coil | Casing + rod controller sopra le colonne rod |
| **Coil** | Strati superiori interni | Blocchi di storage metallici (oro, rame, …) |
| **Porte** | Facce casing | Vapore in, RF out, redstone |

Quanti strati coil usare (default **3**) si imposta nel [Turbine Builder](turbine_builder.md). Per numeri RF e vapore vedi [Vapore, RF e coil](turbine_generation_and_coils.md).
