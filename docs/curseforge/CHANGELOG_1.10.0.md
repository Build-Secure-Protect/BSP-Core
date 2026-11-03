# BSP-Core 1.10.0

Teams can now ally. An **alliance** joins up to three teams with their own chat. Allies can see where each other's players are, and each team chooses which totem perks its allies get. Unlike a team, an alliance can be betrayed: an ally who starts a steal on an ally's totem breaks it, and the whole alliance hears about it.

**Existing worlds:** nothing to do by hand. On a network the new tables (`alliances`, `alliance_teams`, `alliance_messages`, `alliance_reads`, `alliance_waits`, `alliance_former`) and the new position columns on `online_players` are added by themselves. **Every player needs 1.10.0 as well as the servers**: update the pack and the servers together. An older client is told to update when it joins.

## For players: alliances

- **The ALLIANCE tab.** The Team screen has two tabs at the top, TEAM and ALLIANCE. The TEAM tab is the team screen you know. ALLIANCE turns the frame teal and has its own keys: ALLIES, INVITE TEAM, ALLY BOARD, ALLY CHAT, PERKS and LEAVE.
- **Making one.** Your team's leader names the alliance and presses CREATE, then invites teams that are in no alliance from INVITE TEAM. The invite goes to the other team's leader on any server: a teal card at the top right with a chime, and a chat line with Accept and Decline. The same keys as a team invite answer it.
  - An alliance holds up to three teams, and a team is in one at a time.
  - Only leaders make an alliance, invite, answer, set the perks and leave. Every member sees it all.
- **ALLIES.** Every player of the other teams is listed with where they are:
  - online here, on which server, or offline and last seen when;
  - the dimension they are in;
  - their exact block, but only if their team's leader switches on **Share our coordinates**, which is off to start.
- **PERKS.** Your team chooses what its allies get on your totems. Every perk is off to start:
  - they see through our cloaks;
  - our Ward leaves them alone;
  - our Alarm stays quiet for them;
  - they may use our machines and plasma.

  The same page shows what each other team gives you back. Allies never upgrade, pick up or recall your totems.
- **ALLY BOARD and ALLY CHAT.**
  - The board keeps the alliance's lines, each tagged with the speaker's team in its colour. A teal count shows the new ones.
  - ALLY CHAT sends what you type to the alliance until you press it again, with a teal pill above the chat. `/a message` sends one line.
  - Team chat and ally chat are never on together.
- **Betrayal.** An ally who starts a steal on an ally's totem (placed, or setting in a Totem Forge) throws their own team out of the alliance at once, and its perks stop.
  - The steal goes ahead but takes half as long again: 7:30 instead of 5:00, so the others have time to come.
  - Everyone in the alliance, on any server, gets a red **ALLIANCE BROKEN** card with a warning sound and chat lines.
  - The thief's team waits 24 hours before it can ally with any of those teams again.
  - An alliance left with one team ends.
- **No way round it.** For 24 hours after a team leaves an alliance, a steal between it and the teams it left still counts as breaking it: the longer steal, the red card and the wait.
- **Teleporting to allies** comes with BSP-Teleport. The ALLIES page shows TELEPORT keys once it supports them, and the ally you are going to is asked first.
- The guide book has an **Alliances** entry right after Teams.

## For server owners

- **Settings** in a new `[alliance]` section:

  | Setting | Default | What it does |
  |---|---|---|
  | `enabled` | | Turns alliances on or off. |
  | `maxTeams` | 3 | Most teams in one alliance. |
  | `breakCooldownHours` | 24 | How long a team that broke an alliance waits before allying again with those teams. |
  | `breakStealMultiplier` | 1.5 | How much longer a steal that breaks an alliance takes. |
  | `leftStealHours` | 24 | How long after leaving a steal still counts as a break. |
  | `positionSeconds` | 5 | How often allied players' positions are shared across the network. |

  `allowCloak`, `allowWard`, `allowAlarm` and `allowMachines` forbid a perk for every team.
- **Commands:** `/bsp team alliances` lists the alliances, and `/bsp team dissolve <alliance>` ends one and frees every team at once.
- **On a network** an alliance is one alliance on every server. A team joins in a single database step that counts the places and checks the waits, so two servers can never fill one past its size. Changes, invites and the break card travel over Redis.
- **For the other BSP mods:**
  - `BspTeams.allianceOf`, `allied`, `allyFlags` and `allies` (with the server, the dimension and, when shared, the position);
  - the events `TeamEvent.Allied`, `AllianceLeft`, `AllianceBroken` and `AllyTeleportRequest`;
  - `BspTeams.allyTeleportsHandledBy`, so a teleport mod can add ally teleports.

## Fixes

- The mouse wheel scrolls the Team screen's INVITE list the right way.
