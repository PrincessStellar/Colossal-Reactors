---
navigation:
  parent: turbine/turbine-index.md
  title: Vapore, RF e coil
  icon: minecraft:copper_block
  position: 12
categories:
  - multiblock
---

# Vapore, RF e coil

## Quanta RF ottieni

Quando la turbina è **formata correttamente** e **in funzione**, trasforma **vapore** in **RF** ogni tick. In breve:

**RF per tick** ≈ vapore usato × **RF per mB di vapore** × **coil strength** × **bonus layout blade**

- Il **vapore** entra tramite una [Turbine Resource Port](turbine_resource_port.md). La turbina non può usare più vapore per tick di quanto consentano le blade (vedi sotto).

- L’**RF per mB di vapore** è di solito **7** per il vapore standard (scarico acqua). Ricette o impostazioni del pack possono cambiarlo.

- La **coil strength** dipende da quali **blocchi di storage** riempiono la zona coil (oro, rame, netherite, ecc.—vedi sotto).

- Il **bonus layout blade** dipende da come gli anelli si impilano in altezza—vedi [Rod e blade](turbine_rod_and_blades.md). Se qualche colonna rod non ha un rod controller sul deck di chiusura, quel bonus è anche ridotto del **−5%**.

Le impostazioni del pack possono anche moltiplicare complessivamente uscita RF o uso vapore verso l’alto o il basso.

### Perché usare una turbina?

Un reattore a fissione produce molto vapore ma relativamente poca RF diretta da quel vapore. Una turbina completa con blade e coil è pensata per **convertire quel vapore in più RF** di quanto otterresti dal solo reattore—se dimensiona la turbina e tubi abbastanza vapore.

## Quanto vapore puoi usare

Il tetto di **vapore per tick** della turbina cresce con quante **blade valide** hai installato. Più rod con anelli bilanciati significa un tetto più alto. Turbine molto grandi (fino a **65×65×65** interni) sono tarate così che un rotore completo possa consumare dell’ordine di **decine di milioni di mB/t** di vapore se alimentato bene—i numeri esatti dipendono dal pack.

## Zona electrical coil

Gli **strati interni superiori** (default **3** strati, modificabili nel [Turbine Builder](turbine_builder.md)) sono la **zona coil**. Riempili con **blocchi di storage metallici** riconosciuti dal mod (blocco d’oro, di rame, electrum, netherite, ecc.).

- Blocchi migliori danno un **moltiplicatore coil più alto** (ogni tipo ha forza e tetto elencati in **JEI** quando si mostrano le ricette turbina).

- Lo **spazio vuoto** o i blocchi non riconosciuti contano come coil deboli (circa **30%** di forza di default).

- Usa normali **blocchi di storage** di altre mod—non i blocchi **heating coil** del reattore.

Conta la forza media di tutta la zona coil; mischiare più metalli va bene.

## Quale vapore conta

Di default la turbina accetta **vapore** (spesso il tag fluido `#c:steam`) e può emettere **acqua** come scarico. Il tuo modpack può aggiungere altri fluidi o tassi RF diversi nei suoi file dati—**JEI** è il posto per controllare cosa consente il pack.
