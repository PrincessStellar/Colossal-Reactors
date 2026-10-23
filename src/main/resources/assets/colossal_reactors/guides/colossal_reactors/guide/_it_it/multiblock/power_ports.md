---
navigation:
  parent: multiblock/multiblock-index.md
  title: Reactor Power Port
  icon: power_port
  position: 36
item_ids:
  - colossal_reactors:power_port
  - colossal_reactors:high_cond_power_port
categories:
  - ports
---

# Reactor Power Port

<Row gap="16" fullWidth={true}>
  <BlockImage id="power_port" scale="4" />
  <BlockImage id="high_cond_power_port" scale="4" />
</Row>

Le **power port del reattore** sono attacchi **solo sulla faccia**: inviano energia dal reattore a cavi, condotti o macchine all’**esterno**. **Non c’è schermo**—cablare la faccia esterna e leggere il flusso dalla rete energetica.

## Reactor Power Port

La **Reactor Power Port** standard basta per la maggior parte dei modpack e dei tipici tassi RF.

## Reactor High Conduction Power Port

La **Reactor High Conduction Power Port** è per reattori molto grandi che producono enormi RF per tick. Memorizza e sposta quantità di energia molto più alte e funziona meglio con mod che supportano trasferimenti enormi (ad esempio Draconic Evolution o Flux Networks se installati).

Si crafta da una **Reactor Power Port** più materiali di alto livello—controlla **JEI** per il tuo pack.

## Ridimensionamento automatico della capacità

Quando il reattore **rivalida** (reboot del controller o controllo struttura dopo modifiche), ogni **power port** sul multiblocco ridimensiona il buffer RF:

- La dimensione obiettivo è circa **RF/t stimato × 10** (margine di sicurezza), mai sotto **10.000 RF**.
- **Reactor Power Port** ha tetto a **2,1B RF** (2.147.483.647).
- **High Conduction Power Port** usa storage a **64 bit** — massimo pratico **9,22×10¹⁸ RF**.

La capacità **cresce subito** quando il reattore serve più spazio. **Si riduce solo** se ciò che è già memorizzato entra ancora nel target più piccolo, così non si perde energia per un resize.

Rivalida dopo grandi cambiamenti di combustibile, refrigerante o heat sink così i buffer delle porte seguono la nuova uscita.

## Funzioni (entrambe le varianti)

- Posiziona su una faccia esterna del casing; l’**interno** deve guardare verso l’interno del reattore.

- Collega handler energetici compatibili di altre mod all’**esterno** della porta.
