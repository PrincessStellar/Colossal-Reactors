---
navigation:
  parent: multiblock/multiblock-index.md
  title: Reactor Builder
  icon: reactor_builder
  position: 30
item_ids:
  - colossal_reactors:reactor_builder
categories:
  - multiblock
---

# Reactor Builder

<BlockImage id="reactor_builder" scale="4" />

Il **Reactor Builder** posiziona **interno e guscio** del reattore dai materiali nel buffer e nel serbatoio. **Non** posiziona il **Reactor Controller**, le **power port** né le **resource port** — vedi [Per iniziare](getting_started.md).

## Cosa posiziona Build (in ordine)

1. Guscio di **casing** (opzionalmente **open top** per modifiche successive)
2. **Rod controller** sullo strato superiore sopra ogni colonna di rod
3. **Reactor rod** nel pattern interno
4. Heat sink **fluidi** (1000 mB per cella) se configurati
5. Blocchi heat sink **solidi** nelle celle interne rimanenti

## Flusso tipico

1. **Posiziona** il builder contro l’impronta prevista (l’orientamento conta).
2. **Apri** la GUI: imposta **dimensioni**, **pattern rod**, **modalità pattern**, tipo heat sink e **open top** se serve.
   - Pattern **Website**: incolla un **seed / URL** di design; celle heat sink / rod seguono il seed (anche heat sink misti per colonna). Su Website il tasto heat sink è inattivo.
3. **Preview** (sotto Center) — bordo viola, posizioni rod gialle, rosso = blocchi non validi/in eccesso. Gli avvisi compaiono **accanto a Center**.
4. **Mark Input** (appena sopra il buffer del builder):
   - Assegna gli slot del buffer a item specifici (vedi tooltip dei pulsanti).
   - **Trascina item da JEI** sugli slot del buffer per i ghost mark filter; doppio click su uno slot per azzerarlo.
5. **Build** quando buffer/serbatoio hanno abbastanza materiali; **Stop** se compaiono zone rosse o devi modificare.
6. **Rimuovi o ignora** il blocco builder — non fa parte del multiblocco.
7. Aggiungi **a mano** [controller, power port e resource port](getting_started.md) dopo il build — controller **fuori** da una parete; porte sulla **stessa** faccia, sostituendo celle di **vetro**.

La **simulazione** stima stats e conteggi materiali (seed Website inclusi se validi); **non** avvia il reattore reale.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="/assets/assemblies/minimal_reactor_automation.snbt" />
  <IsometricCamera yaw="210" pitch="25" />
</GameScene>

Esempio di layout **finito** (builder + porte posizionate a mano sul **telaio casing**). Vedi [Per iniziare](getting_started.md) per le etichette.
