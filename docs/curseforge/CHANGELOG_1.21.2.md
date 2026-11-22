# BSP-Core 1.21.2

Show off your VIPs! The new **OUR VIPS** board puts every VIP of the server on a Score Screen: their faces in gold frames, a message from the admins, and a SUPPORT key that opens the server's support page. Also in this version: the Magnetic Centrifuge takes a whole stack of Magnatite Nuggets for Charged Resonance Crystals. This file also covers 1.21.1, which made store credit for plots work across the whole network.

**Existing worlds:** nothing to do by hand. Score Screens keep the boards they show; the VIPs board appears only where an admin picks it.

**Every player needs 1.21.2 as well as the servers** (the network protocol changed). An older client is told to update when it joins. On a network, update every server together.

## For players: the VIPs board

- **OUR VIPS** is a new Score Screen board. Every VIP of the server has a tile: their face, hat layer included, in a gold frame, with their name under it, sorted by name.
- When there are more VIPs than fit, the rows slide slowly sideways. Only the heads move; the texts stay put.
- **A purple banner** across the top carries a message from the admins, and a second message runs along the foot.
- **The SUPPORT key.** Right-click the gold key at the foot, from up to 16 blocks away, and Minecraft asks whether to open the server's support page. Nothing opens without your yes.
- Every VIP shows, online or not.
- The guide book's Scores and Seasons entry has a page on the board.

## Fixes

- **The Magnetic Centrifuge takes a whole stack of Magnatite Nuggets.** Its upgrade slot held a single nugget, so making Charged Resonance Crystals meant putting a nugget in by hand before every crystal. The slot now holds up to 64, by hand or by shift-click, and the centrifuge still uses one nugget for each crystal: a stack lasts 64 crystals. The Copper Tetrium Coil still goes in one at a time, and the slot's tooltip names both.
- **Store credit pays for plots with long names** (1.21.1). Store credit meant for a plot whose name is longer than 64 characters now pays for it at the Land Office.

## For server owners

- **Setting up the VIPs board:**
  - VIPs are the members of the LuckPerms groups listed in `[vipBoard] groups` (by default `vip`), and of any group that inherits one. With LuckPerms' storage shared across the network, every server shows the same list. Without LuckPerms the board is empty.
  - The list is read when the server starts, every five minutes, and shortly after a player logs in. `/bsp vips` lists the VIPs and the groups read; `/bsp vips refresh` reads them again at once.
  - **Pick the board** on a Score Screen (or tick it in a rotation), then press **VIP TEXTS** in its settings for the top message, the bottom message (80 characters each, two lines, `&` colour codes), the key's label (24 characters) and its link. Each screen keeps its own texts.
  - Only an `http://` or `https://` address is taken as the link; without one the key is hidden.
  - Screens with the default rotation don't start showing the board: it joins a rotation only where you tick it.
  - An admin clicking anywhere else on the screen still gets its settings.
- **Store credit for plots across the network** (1.21.1). The CREDIT tab's LAND OFFICE list offers every plot for sale on every server of the network, in every dimension, each name with how many plots share it and the servers they're on ("3 plots · hub, survival-1"). The search line finds plots by server too.
  - A stopped server's plots stay listed for a week. A saved name that nothing sells any more reads "not for sale on the network". If the other servers' plots can't be read, the tab says so and shows the list it read last.
  - Without network storage the tab lists this server's plots in every dimension.
  - The CREDIT form keeps a kind you just saved, even when you refresh just before saving.
- **Settings:**

  | Setting | Default | What it does |
  |---|---|---|
  | `vipBoard.groups` | `vip` | The LuckPerms groups whose members (and the members of any group inheriting one) are shown on the VIPs board. |

- **On a network.** One new table, made by itself at start: `land_plots` (1.21.1), where each server lists its Region Guard plots for sale.
- **For the other BSP mods:** no API changes. Mods built against an earlier BSP-Core keep working.
