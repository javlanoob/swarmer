# Swarmer
A RuneLite plugin that hides scarab swarms in Tombs of Amascut when they are attacked and  hides swarms over a customizable wave threshold

The plugin uses the `HitsplatApplied` event to detect when a scarab swarm is hit, immediately marking it as dead. This provides faster response than waiting for the NPC to fully despawn. As a fallback, it also monitors XP drops to ensure swarms are marked even if the hitsplat event is missed.

## Credits

- Author: javlanoob
- Swarm number overlay taken from the Tombs of Amascut plugin by Llemonduck
- Based on Nylo Death Indicator by InfernoStats

## Support

If you encounter any issues or have suggestions, please report them on the [GitHub repository](https://github.com/javlanoob/swarmer).