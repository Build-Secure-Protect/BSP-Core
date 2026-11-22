# BSP-Core 1.22.0

Dress up your spawn! **Demo Totems** are a block for admins that looks exactly like a Shatter Totem and wears any look a totem can: colours, gradients, a painting, a player's head and any hat. Put them in a showroom, at spawn or in a tutorial so players can see what totems look like. Also in this version: everything that spins, bobs or fades now moves smoothly, and a VIP's new totem no longer arrives wearing a hat.

**Existing worlds:** nothing to do by hand.

**Every player needs 1.22.0 as well as the servers** (a new block and item are added). On a network, update every server together.

## For players: Demo Totems

- A Demo Totem looks exactly like an owned Shatter Totem: the same gold body, size and glow. Dressed, it looks exactly like a dressed totem, with the gold band round its base and its hat.
- It is only for show. It has no upgrades and can't be stolen. It doesn't count for scores or the board, and Totem Compasses don't point at it.
- Right-click it to see it turning in its look, with its hat named.
- Only admins hand them out; there is no recipe. Jade names it "Demo Totem".

## Smoother animations

- **Everything BSP-Core draws now moves smoothly.** On a world with a lot of game time behind it, spinning and bobbing things moved in small jumps instead of turning smoothly.
- Fixed everywhere:
  - the totem turning in the LOOKS tab;
  - items on Market Stalls and Stall Displays, and the Vendor Post;
  - a totem's upgrade orbs, aura cubes, hats and the Score Leader's glow;
  - the machines, the Plasma Tank and the plasma parts;
  - the Coin Factory, Coin Vault, Bank ATM, Land Office, Lead-Lined Chest, Compass Tuner and Anti Totem;
  - the Cloak's fade in and out.

## Fixes

- **A VIP's or operator's new totem wears no hat.** A totem with no look of its own wore its owner's chosen hat, which by default was the hat LuckPerms gave them last (often the chef's hat). A VIP or operator whose permissions include every hat counted all of them as bought. Now a totem without a look wears no hat while its owner has the VIP rank or is an operator; they pick a hat in the LOOKS tab.
  - A player with bought hats but no VIP still has their chosen hat put on by itself (by default the newest).
  - So does a player whose VIP has ended.

## For server owners and admins

- **Getting Demo Totems:**
  - `/bsp totem demo` gives you one; `/bsp totem demo <player> <count>` gives up to 64.
  - They are also in BSP-Core's creative tab, right after the Shatter Totem, and are hidden from JEI and EMI.
  - The command only gives them to admins.
- **Who counts as an admin:** the names in the server config's `restrictions.admins` list, or every operator when that list is empty. Only admins can place, mine or dress a demo, by hand or with a placing tool. Anyone else holding one can't place it.
- **Dressing one:** right-click it to open its **LOOKS** screen and dress it the way an owner dresses a totem:
  - one colour, a gradient or a painting;
  - your own head;
  - any hat, the exclusive ones too;
  - or **PLAIN**: the totem's own gold body with just a hat on it.
  
  No VIP rank is needed, and there is no wait between looks. Your saved looks work here too.
- **It's an ordinary block:** none of the totem's rules apply to it. It can go in any dimension, into a chest, and stays in your inventory when you log out.
  - Explosions, pistons, mobs, Create's contraptions and drills, and Mekanism's cardboard box can't move or break it.
  - Mining it gives the item back with its look or hat, and in creative a middle-click copies it with its look.
  - Plain ones stack to 64.
- **Records:** a demo keeps its look when an admin's rank ends or staff clear their looks. Every change to a demo is in the server log with the admin's name.
- **For the other BSP mods:** no API changes. A Demo Totem is never a totem to the API (`bsp_core:demo_totem`, see `docs/API.md`). Mods built against an earlier BSP-Core keep working.
