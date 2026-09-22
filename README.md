# Swarmer
Nylo Death Indicators for the scarab swarms at Kephri in the Tombs of Amascut. Swarms have -100 defence, so every hit on them lands and kills one. A swarm is hidden the moment you hit it, before the hitsplat lands and the death animation plays, so you can move straight on to the next one.

## How a kill is spotted
- **Your XP drop.** Killing a swarm gives a single XP in your attack style's skill and no Hitpoints XP, so an XP drop in any skill while you're attacking a swarm hides it right away.
- **Chinchompas and burst or barrage spells** hide every swarm in the 3x3 around your target.
- **Landed hitsplats**, from anyone, hide the swarm too, so kills by players without the plugin are covered.
- **Party.** In a RuneLite party, swarms hit by party members who also use the plugin are hidden for you as well.
- **Wrong guesses.** If a hidden swarm is still alive 5 ticks later, it's shown again.

## Settings
**Hide killed swarms**: hides a swarm as soon as it's hit. Other plugins, like NPC Indicators, also treat it as dead.

**High waves**
Swarms from late waves can't reach Kephri before she gets back up, so they can be ignored.
- **Hide high wave swarms**: hides swarms above the wave threshold.
- **Hide high wave numbers**: hides only the wave numbers above the threshold.
- **First down threshold** and **Later downs threshold**: the highest wave that still counts, set separately for Kephri's first down and for her later ones.

**Wave numbers**
- **Show wave numbers**: shows the wave each swarm spawned in above it. Numbers that would overlap are moved apart. If you use this, turn off the Swarmer overlay in the Tombs of Amascut plugin so the numbers aren't drawn twice.
- **Font**, **Bold**, **Font size** and **Font color**: the look of the numbers.

**Highlight**
- **Highlight swarms**: highlights swarms like NPC Indicators. Only swarms that are still alive and under the wave threshold are highlighted.
- **Mode**: true tile, tile, hull or outline. True tile shows the tile the server has the swarm on, not where it's drawn mid-walk.
- **Color**: the color of the highlight.

## Credits
Based on Nylo Death Indicators by InfernoStats. The wave number overlay is based on the one in the Tombs of Amascut plugin by LlemonDuck.
