# BSP-Core 1.8.0

The market's taxes have been piling up in a lottery pot since 1.7.0. Now someone wins it. An admin draws the **lottery** whenever they like: every server counts down from 5 in chat, and one player takes the whole pot. Everyone who has played on the network in the past week is in, one equal chance each, with no tickets to buy. Half of everything players spend at the shops goes into the pot too. There is also a **daily login reward**: 50 pieces into your bank every day you play.

**Existing worlds:** nothing to do by hand. On a network, the new tables (`lottery_players`, `daily_rewards`) are added by themselves. **Every player needs 1.8.0 as well as the servers**: update the pack and the servers together.

## For players: the lottery

- **One pot for the whole network.** It fills from the market's two 5% taxes and from half of what players pay for shop items. Selling to a shop, kits and bank fees do not add to it.
- **Who is in.** Everyone who has logged on to any server of the network in the past 7 days, one equal chance each. There are no tickets to buy or earn. Operators and staff ranks are left out.
- **The draw.** An admin draws the lottery when they choose. Chat on every server says it is being drawn and counts down from 5, then names the winner and what they won. The winner takes the whole pot, straight into their bank. A winner who is offline is told when they next log in. After a draw the pot starts again, and everyone who keeps playing is in the next one.
- **The LOTTERY tab on the Trade Post.** A drum turns through the names of everyone in, beside the pot, how many are in and your own chance ("1 in 62", or why you are left out). The last five winners are ticket stubs along the bottom. During a draw the drum spins with the count, then slows to a stop on the winner. It is on the Portable Banker's market too.
- **The Lottery board** for Score Screens: the drum of names, the pot, how many are in and the last three winners, spinning and counting down during a draw.
- **The Portable Banker** shows a lottery card on its account page: the pot, your chance and the last winner.
- **The guide book** has a page on it: Shatter Coins, The Lottery.

## For players: the daily reward

- **50 pieces a day** go into your bank by themselves, with a line in chat: at your first login of the day on any server, or at midnight if you are online then.
- **Once a day across the network**, however many servers you visit. Days run from midnight to midnight.
- The guide book's Shatter Bank entry has a page on it.

## For players: also new

- **The Trade Post's tabs** are a little narrower so the LOTTERY tab fits beside the list of traders. A Collect box of more than 99 stacks reads COLLECT 99+.

## For server owners

- **The lottery's admin page.** The ADMIN key on a Trade Post's LOTTERY tab, or `/bsp lottery admin` anywhere, operators only. It has:
  - a table of everyone seen this season: when they were last seen, their rank, and IN or OUT with the reason (operator, a rank left out, or not seen within the days);
  - SEEN WITHIN: how many days back a player must have played, 1 to 90 (7 by default);
  - LEFT OUT: tick the LuckPerms ranks to leave out (admin, mod, moderator, owner and staff by default);
  - ADD and TAKE: put pieces into the pot or take them out, with a reason that goes into the pot's log;
  - DRAW, which asks for a second press.

  The settings are shared by every server of the network.
- **Lottery commands:** `/bsp lottery` shows the pot, how many are in, how many are left out and why, the settings and the last five winners. `/bsp lottery draw`, `/bsp lottery add <pieces> <reason>` and `/bsp lottery take <pieces> <reason>` work from the console too. Only one draw runs at a time across the network, a draw with nobody in or an empty pot is refused before the countdown, and the pot cannot be changed while a draw runs. Every draw is in the server log with the random seed and the winner.
- **Settings:** `shop.lotteryPercent` is the share of each shop purchase paid into the pot (50 by default; 0 turns it off). `bank.dailyRewardPieces` is the daily reward (50 by default; 0 turns it off). Midnight follows `shop.timeZone`: on a network, set it the same on every server (a server with it empty warns at start).
- **Payouts** show in bank logs as LOTTERY, and the daily reward as "daily reward".
- **Every board can be picked in a Score Screen's settings.** The Board row showed only four boards and the Rotation row only three, so with BSP-Server Utilities' and BSP-Teleport's boards installed some could not be chosen. Both rows now turn pages with ‹ and › when there are more than fit.
- **The other BSP mods** all work with 1.8.0 as they are. Each has been built against it.

## Fixes

- **Built to stay safe on a network:** a server whose database is down never draws the lottery or pays a reward from its own old records, a lost database answer can never pay a pot or a day twice, and a claim that could not be settled is tried again by itself.
