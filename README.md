# Swarmer
Nylo Death Indicators for the scarab swarms at Kephri in the Tombs of Amascut. A swarm is hidden as soon as the damage on it is enough to kill it, before the hitsplat lands and the death animation plays, so you can move straight on to the next one.

- Your damage is read from your Hitpoints XP drop, which works for every combat style. Chinchompas and burst or barrage spells hide the whole clump if they did enough damage for all of it. Scythe, Dinh's bulwark and Venator bow hits are ignored because their XP can't be split between targets.
- Hitsplats that land, from anyone, count towards a swarm's health.
- With party sync on, damage from party members who also use the plugin is added too, so a swarm is hidden once your team's combined hits kill it.
- If a hidden swarm is still alive 5 ticks later, it's shown again.
- Can hide swarms, or just their wave numbers, above a wave threshold. These late swarms can't reach Kephri before she gets back up. There is a separate threshold for her first down and for later downs.
- Shows the wave each swarm spawned in above it. If you use this, turn off the Swarmer overlay in the Tombs of Amascut plugin so the numbers aren't drawn twice.

Based on Nylo Death Indicators by InfernoStats. The wave number overlay is based on the one in the Tombs of Amascut plugin by LlemonDuck.
