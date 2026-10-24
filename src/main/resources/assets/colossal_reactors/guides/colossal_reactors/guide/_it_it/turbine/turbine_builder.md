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
3. Rifornisci il builder con **casing**, **vetro**, **rod controller** (uno per ogni colonna rod del pattern), **rod**, **blade** e i **blocchi di storage** per i coil.
4. Scegli un **layout** (vedi [Rod e blade](turbine_rod_and_blades.md)):
   - **Efficient** — un albero centrale; le blade salgono anello per anello (**+3%** bonus altezza possibile).
   - **Productive** — un albero centrale; anelli pieni a ogni strato (massimo vapore prima).
   - **Hyperflow** — griglia multi-rod; posiziona un **rod controller su ogni colonna** (niente penalità −5% per controller mancante).
   - **Website** — incolla un **seed / URL** di design nel campo dedicato; rod (e scelta coil su Website) seguono quel layout.
   - **Custom / None** — telaio (e coil per Custom) senza contenuto rotore.
5. Scegli **tipo coil** (cicla i metalli supportati; modificabile anche su Website) e **numero strati coil** (tasto largo sotto Preview, accanto a esso).
6. Usa **Preview** (sotto Center, stessa larghezza) e il pannello **Simulation** per RF/t, vapore e materiali. Gli avvisi compaiono **accanto a Center**.
7. **Mark Input** (appena sopra il buffer del builder):
   - Con Mark Input attivo, click su uno slot del buffer per impostare quale item accetta per i passi di build.
   - Puoi anche **trascinare item da JEI** sugli slot del buffer per i ghost mark filter; doppio click su uno slot per azzerarlo.
8. Premi **Build** quando sei pronto.

Ordine di build: **guscio esterno** → **deck di chiusura** → **rod controller** (uno per colonna rod) → **rod** → **blade** → **blocchi coil** negli strati interni superiori.

Il builder **non** posiziona il **Turbine Controller** esterno né le porte—aggiungili a mano dopo il build.

## Pannello simulazione

Mostra **RF/t** e **vapore** attesi, bonus coil e blade (incluso il −5% se qualche colonna rod non ha controller) e un **elenco materiali**. I seed Website entrano nella simulazione quando il design è valido. I nomi dei pulsanti possono variare; leggi i tooltip a schermo.
