---
navigation:
  parent: turbine/turbine-index.md
  title: Turbine Builder
  icon: turbine_builder
  position: 30
item_ids:
  - colossal_reactors:turbine_builder
categories:
  - multiblock
---

# Turbine Builder

<BlockImage id="turbine_builder" scale="4" />

Il **Turbine Builder** costruisce la turbina per te dagli item nel suo inventario—simile al [Reactor Builder](../multiblock/reactor_builder.md).

## Flusso tipico

1. **Posiziona** il builder sulla faccia dove vuoi che inizi il guscio della turbina.
2. **Apri** lo schermo e imposta **larghezza, altezza e profondità** (entro il limite del pack).
3. Rifornisci il builder con **casing**, **vetro**, **rod controller**, **rod**, **blade** e i **blocchi di storage** che vuoi per i coil.
4. Scegli un **layout**:
   - **Efficient** — meno colonne rod (scacchiera); le blade crescono anello per anello lungo l’albero (**+3%** di bonus altezza possibile—vedi [Rod e blade](turbine_rod_and_blades.md)).
   - **Productive** — rod su ogni colonna consentita; ogni strato ottiene subito anelli pieni (massimo vapore prima).
5. Scegli **tipo coil** (cicla i metalli supportati dal mod) e **quanti strati coil** (default **3**, cambia con ◀ ▶ a schermo).
6. Usa **anteprima** e il pannello **simulazione** per vedere RF/t stimato, uso vapore e materiali necessari.
7. **Mark input** dice al builder quali slot inventario forniscono quali blocchi per ogni passo.
8. Premi **Build** quando sei pronto. Puoi aggiungere fluido al serbatoio del builder se il pack lo usa per i controlli di setup.

Ordine di build: **guscio esterno** → **rod controller e rod** → **blade** → **blocchi coil** negli strati interni superiori.

## Pannello simulazione

Mostra se il layout è valido, **RF/t** e **vapore** attesi, bonus coil e blade, e un **elenco materiali**—stessa idea del reactor builder. I nomi dei pulsanti possono variare per versione; leggi i tooltip a schermo mentre lavori.
