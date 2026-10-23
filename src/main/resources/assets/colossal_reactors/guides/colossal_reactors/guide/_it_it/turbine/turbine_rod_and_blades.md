---
navigation:
  parent: turbine/turbine-index.md
  title: Rod e blade
  icon: turbine_blade
  position: 15
item_ids:
  - colossal_reactors:turbine_blade
  - colossal_reactors:turbine_rod
categories:
  - multiblock
---

# Rod e blade

<Row gap="16" fullWidth={true}>
  <ItemImage id="turbine_rod" scale="4" />
  <ItemImage id="turbine_blade" scale="4" />
</Row>

## Posizionare le blade

La **Turbine Blade** non si posiziona come un blocco normale. **Click destro su una Turbine Rod** tenendo una blade per attaccare la blade successiva su quella rod. Ogni click usa una blade dall’inventario.

Le blade crescono verso l’esterno in **anelli** intorno alla rod, bilanciati sui quattro lati:

1. **Primo passo** — 2 blade su lati opposti.
2. **Poi** — 4 blade (una per lato).
3. **Quindi** — 8, 12, 16… fino al limite del pack (default **31** anelli per rod, fino a **124** blade su una singola rod).

Il gioco sceglie sempre il lato che mantiene il layout bilanciato prima di passare all’anello successivo.

## Blade nel mondo

- Le blade sono **eteriche**: non ci cammini dentro, non le scavi a mano e non le raccogli direttamente.

- Se **rompi la rod**, ogni blade su quella rod viene rimossa e droppata come item **sul blocco rod**—non serve cercare nell’interno della turbina.

## Bonus blade per altezza

Lungo la direzione del rod controller, le blade si contano **per strato** (come un piano a ogni altezza su un albero verticale).

| Confronto tra strati | Effetto |
|--------------------|--------|
| Lo strato superiore ha **più** blade di quello sotto | Bonus uscita **+3%** per passo (default pack) |
| Stesso conteggio blade | Nessun cambiamento |
| Lo strato superiore ha **meno** blade | Catena bonus **persa**; ogni calo applica anche circa **−3%** |

Per il bonus migliore, aggiungi anelli **dal basso verso l’alto** (il layout **Efficient** del builder fa così). Riempire ogni strato al massimo subito (**Productive**) salta quel bonus di salita ma raggiunge prima il throughput vapore pieno.

Su alcuni pack, solo gli **anelli pari** (4, 8, 12 blade su uno strato, ecc.) contano pienamente verso la capacità vapore—controlla la config del mod se i numeri sembrano bassi.

## Layout del builder

Il [Turbine Builder](turbine_builder.md) può posizionare rod e blade per te:

- **Efficient** — rod a scacchiera; gli anelli blade crescono più alti passo dopo passo (meglio per il bonus altezza).
- **Productive** — rod ovunque consentito; ogni strato ottiene subito il conteggio anello massimo.

Posizionare le blade a mano con l’item segue lo stesso ordine di anelli del builder.
