# Endbound Ember

<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/%E4%B8%AD%E6%96%87%E7%89%88-%E5%88%87%E6%8D%A2-2ea44f?style=for-the-badge" alt="中文版"></a>
  <a href="README_EN.md"><img src="https://img.shields.io/badge/English-Current-0969da?style=for-the-badge" alt="English"></a>
</p>

Endbound Ember is a vanilla-expansion mod for Minecraft 1.21.1 NeoForge, and an official remake of Enchant-expansion.

## ⚠️ Special Notice

<span style="color:orange">**🎮 This mod modifies some vanilla mechanics** (player movement feel, enchantment growth, combat values, etc.), so the experience is not exactly vanilla.</span>

<span style="color:orange">**🏃 Challenge / speedrun players:** please go through the config files item by item before starting a run, and turn off whatever you don't want.</span>

<span style="color:orange">**🤷 If someone calls you a cheater because you skipped the config… well, that's really not my problem~**</span>

<span style="color:orange">**😴 Why is it like this?** Because I'm too lazy to install a pile of extra mods, so I bundled every mechanic I wanted into this one. 🧩</span>

> **Multiplayer server warning:** This mod changes and adds vanilla mechanics, including movement, block placement, combat, enchantments and trading. Server anti-cheat systems may flag these changes and issue warnings, kicks or bans. Members and assistant admins should check the server rules and test cautiously only in an environment where the mod is permitted. The server staff's rules take precedence.

### Current Additional Mechanics

- **Enchanting table improvements**: improved enchantment candidates with previews, plus a configurable maximum number of enchantments per item.
- **Enchantment compatibility**: adjusted compatibility between selected weapons and enchantments.
- **Villager trading**: permanent discounts, rare Fool villagers, automatic and faster restocking, and profession reset changes.
- **Anvil improvements**: free renaming, no too-expensive limit, and reduced anvil damage.
- **Sword blocking**: right-click blocking, damage reduction, perfect parry bar reset, and deflection of incoming arrows.
- **Movement mechanics**: momentum glide, bunny hop, better air control, Bedrock-style midair bridging, and wall jump. The new mechanics have separate config switches; the wall-jump key is rebindable.
- **Zoom and moving light**: hold C to zoom (rebindable); light-emitting blocks provide moving light when held.
- **Built-in Ironclad Frame**: dash on R by default (rebindable), with a 5-second cooldown and immunity while dashing. Hitting mobs deals kinetic impact, and the dash leaves residual momentum plus a Kinetic Release window.
- **Mob and world additions**: girls assist in defense, and the Time Turbulence biome is added.

## 1.2.0

> This release focuses on quality-of-life and mechanic polish: enchantment compatibility, villager trading, anvil improvements, plus sword blocking, movement feel, zoom and moving light. All of the following mechanics are **enabled by default** and can each be disabled in the config file.

### Enchantment Compatibility

- Tridents can receive Sharpness, Smite and Bane of Arthropods.
- Swords can receive Impaling.
- Bows can receive Multishot and Quick Charge.

### Better Villager Trading (`betterVillagerTrading`, on by default)

- **Permanent discounts**: after winning a raid or a zombie siege, villager prices permanently drop by 20%, stacking up to 80% off.
- **Fool villagers**: a very rare naturally spawning villager (reusing the vanilla nitwit model) with extremely generous trades, such as 1 dirt for 7 emeralds, 1 iron ingot for 1 diamond, 8 apples for 1 enchanted golden apple, or a diamond chestplate with Protection IV.
- **Fool restocking**: fools automatically restock to full every 1–2 in-game days, with no conditions attached.
- **Faster restocking**: normal villagers restock faster overall.
- **Better job switching**: if your standing with a villager is below Master, breaking its workstation (with no other workstation of the same type nearby) turns it back into an unemployed villager, clearing its level and resetting its profession.

### Better Anvil Mechanics (`anvilMechanicsEnabled`, on by default)

- Renaming items costs no experience.
- The "Too Expensive!" limit is removed.
- Anvil damage chance after use is greatly reduced (about 5% of the original chance).

### Disenchantment Table

- A regular enchanted item produces one enchanted book containing all of its enchantments; claiming it clears the input item's enchantments and consumes one plain book.
- A multi-enchanted book is processed one enchantment at a time: the output contains the remaining enchantments, while the input becomes a book containing only the removed enchantment. Enchanted books need at least two enchantments to be accepted.
- Each operation costs 25 experience points (free in Creative mode) and uses no lapis lazuli. The experience rules and repair-cost reset can be adjusted in the mod config.
- Legacy results from the former 27 output slots are migrated to a separate recovery queue and can be claimed one by one; new disenchanting is blocked until the queue is empty.

### Sword Right-Click Blocking (`swordBlockingEnabled`, on by default)

- Hold right-click with a sword to enter a blocking stance, reducing frontal damage by 60% (you take 40% of the original damage).
- A blocking bar HUD shows the current blocking state.

### Player Movement Mechanics (`movementTweaksEnabled` master switch, on by default)

- **Momentum glide** (`movementMomentumGlide`): after releasing the movement keys while sprinting, or after landing from a jump, horizontal speed is preserved and gradually decays.
- **Bunny hop** (`movementBunnyHop`): pressing jump just before touching the ground while falling launches another jump immediately, keeping most horizontal speed.
- **Better air control** (`movementAirControl`): directional input in mid-air turns the movement direction more smoothly.

### Other Improvements

- **Zoom** (`zoomEnabled`, on by default): hold the zoom key (default C, rebindable in Controls) to narrow your field of view.
- **Moving light** (`movingLightEnabled`, on by default): holding a torch, glowstone or another light-emitting block lights up the ground around your feet, and goes out when you move away.
- **Girls assist in defense**: all three girls (Zombie Girl, Drowned Girl, Human Girl) actively attack illagers and help players defend against raids, whether tamed or not.
- **Time Turbulence biome**: adds a Time Turbulence dimension biome and re-attaches the biome during chunk loading so the map shows the correct dimension name.

## 1.1.0 (Beta)

> **⚠️ Important: 1.1.0 is still in beta.** The weapons, dimension and boss added in this version have not gone through complete balance validation, so **noticeable imbalance is expected** (possibly too strong, possibly too weak). Mechanics, drops and acquisition methods may also keep changing before the official release. Please treat it as a beta experience, and feedback is welcome.

### Herobrine

#### Summoning

- No natural spawning, no spawn egg and no structure generation; it can only be summoned through a ritual.
- Ritual layout: place 1 Nether Star on the central display pedestal, plus 1 Nether Star on each of the four diagonal pedestals (5 in total), and the entire area directly beneath all 5 pedestals must be Nether-themed blocks.
- The ritual lasts 300 ticks; at tick 240 (about 12 seconds) it consumes the 5 Nether Stars and summons Herobrine.
- Base stats: 1500 health, 24 armor, 15 attack damage, 0.3 movement speed, 0.5 knockback resistance, 64 block follow range.
- It does not despawn on Peaceful difficulty, nor is it removed for being too far away.

#### Title Phases (10 Tiers)

- Each 10% of maximum health lost (150 points) advances one tier, and it **never goes back down**; the boss bar name shows the current title.
- Order: Undead Lord → Thunder Lord → Plague Lord → Flesh Glutton → Legion Lord → Phantom Wraith → Arrow Marshal → Undying Grudge → Bane of Dawn → Myriad Lord.
- Only players within 10 chunks can see the white boss bar; leaving the range removes the bar and its associated debuffs.

#### Detailed Effects of the Ten Titles

1. **Undead Lord** (starting title)
   - The base phase: only Herobrine's base mechanics plus 20% title damage reduction.
   - Regenerates 10 health every 25 ticks; summons 2–3 undead elites every 10 seconds, up to 8 nearby.
   - Undead elites wear full diamond armor with Protection III and Unbreaking II; skeletons carry Power IV, Punch II bows, zombies carry Sharpness III diamond swords.

2. **Thunder Lord**
   - Judgement Thunder interval is shortened to 70% of the base interval.
   - Lightning damage is increased to 7.
   - Players struck by lightning receive a brief Slowness effect.
   - The base thunder mark, duration and summoning mechanics are unchanged.

3. **Plague Lord**
   - Judgement Thunder is removed entirely and replaced by Wither for 60 ticks applied to players inside the boss bar range every 120 ticks.
   - Healing received by players is reduced by 15%.
   - Both the Wither and the healing reduction skip players carrying the Dawn Amulet or this mod's halos.

4. **Flesh Glutton**
   - Undead regeneration interval is shortened from 25 to 15 ticks, still restoring 10 health each time.
   - Each successful undead elite summon applies Weakness for 60 ticks to players inside the boss bar range.
   - Summon count and the nearby elite cap remain 2–3 and 8.

5. **Legion Lord**
   - Each wave summons 4–5 undead elites, and the nearby cap rises to 12.
   - When an undead elite dies, it leaves a 60-tick wraith zone within 4 blocks of its death position, granting Slowness I to players inside.
   - Each nearby living undead elite makes Herobrine restore an extra 25 health per regeneration and shortens the interval by 5 ticks.

6. **Phantom Wraith**
   - Movement speed increases to 1.4× the base value.
   - Teleports next to the target (about 8 blocks away) every 100 ticks (5 seconds).
   - Players within 4 blocks of the arrival point receive Blindness for 30 ticks.
   - The teleport keeps the original soul particles and enderman teleport sound, and never truly despawns the boss.

7. **Arrow Marshal**
   - Each volley fires 14 arrows, and the gap between volleys shortens to 20 ticks.
   - Players hit by special arrows cannot sprint for 40 ticks.
   - Arrow base damage remains 8, and shots lead the target based on movement speed and distance.

8. **Undying Grudge**
   - Triggers only once per fight.
   - Instead of dying to lethal damage, health is locked at 1 and 25% of maximum health (375) is restored immediately.
   - On trigger, players inside the boss bar range receive Darkness for 100 ticks.
   - This health lock does not block void damage; Smite still bypasses Herobrine's damage reduction normally.

9. **Bane of Dawn**
   - Deals 1.2× damage to players holding the Sword of the Free Will, wearing the Dawn Amulet, or using dawn-related equipment.
   - The bonus only applies to qualifying players and does not change damage against anyone else.
   - All other title parameters return to that phase's base values.

10. **Myriad Lord** (final title)
    - Combines all effects of the previous 9 titles and further strengthens them: title damage reduction rises to 25%, summon cap +2 (14), regeneration interval reduced by another 3 ticks, thunder interval multiplier multiplied by another 0.8, volley size +2 (16 arrows), movement speed multiplier +0.1 (1.5× total).
    - Healing reduction rises to 25%; Judgement Thunder and the Plague Lord's Wither aura are both retained.
    - At combat start it plunders the four armor pieces, End Star and Life Star from players inside the boss bar range (including matching items in curio slots).
    - Plundered items are never lost permanently: after Herobrine dies, a chest is buried at a safe spot below the death point and the items are returned; anything that does not fit is dropped on the ground.
    - Sophisticated Backpacks items, the Stardust Halo, Time Halo and Dawn Amulet are always protected; other items can be whitelisted via the `herobrinePlunderProtected` config.

#### Combat Behavior

- **Melee**: switches to melee within 4 blocks, swings within 3 blocks, about 1 second between swings, 15 damage.
- **Arrow volleys**: fires alternately beyond 4.5 blocks, one arrow every 2 ticks, 10 arrows per volley with a 30-tick gap between volleys; arrow base damage 8, leading the target's movement. Arrow Marshal fires 14 arrows and Myriad Lord 16.
- **Ranged positioning**: keeps 7–20 blocks of distance, circles sideways and periodically reverses direction.
- **Judgement Thunder**: players inside the boss bar are continuously marked for about 10 minutes, struck every 200 / 150 / 40 ticks depending on difficulty for 5 damage by default; Thunder Lord deals 0.7× damage and adds Slowness, while Plague Lord disables the lightning and uses a Wither aura instead.
- **Undead call**: summons 2–3 fully diamond-geared undead elites every 10 seconds, up to 8 within 24 blocks; Flesh Glutton adds Weakness, Legion Lord summons 4–5 with a cap of 12, Myriad Lord caps at 14.
- **Teleport (Phantom Wraith)**: teleports next to the target every 5 seconds, blinding nearby players for 30 ticks.
- **Wraith zone (Legion Lord)**: dying elites slow players around them.

#### Special Mechanics

- **Damage reduction**: 40% base + 20% from the title (25% for Myriad Lord), for 60% / 65% total; void damage and Smite damage ignore this reduction.
- **Single-hit damage cap of 150**, unless the attacker holds the Sword of the Free Will or wears the Dawn Amulet.
- **Smite**: each level additionally delays regeneration by 75 ticks (up to level 12).
- **Undead regeneration**: restores 10 health every 25 ticks by default, faster with more nearby elites (15 ticks for Flesh Glutton, 12 for Myriad Lord).
- **Undying Grudge**: once per fight, locks health at 1 on lethal damage, then restores 25% of maximum health and blinds all players.
- **Bane of Dawn**: 1.2× damage against players with the Dawn Amulet or dawn enchantments.
- **Arrow Marshal**: hit players cannot sprint for a short time.
- **Myriad Lord**: at combat start, plunders four armor pieces, the End Star and the Life Star from spectating players (including curio slots); after defeat it buries a chest below the death point to return them. Sophisticated Backpacks items, the Stardust Halo, Time Halo and Dawn Amulet are always protected, and other items can be whitelisted via the `herobrinePlunderProtected` config.

#### Drops and Rewards

- 16–32 diamonds, 32–64 Netherite Ingots, 1 Eternal Totem (affected by `doMobLoot`; the main-hand weapon does not drop).
- 150000 experience points, dropped in batches of 500 over 80 ticks.
- A roughly 9-second death animation plays after the kill.

## 1.0.0

### Mobs

- Added Zombie Girl.
- Added Drowned Girl.
- Added Human Girl.
- Zombie Girl and Drowned Girl use the Alex/slim player model and 64×64 two-layer skins.
- Supports `zombie_girl.png` and `zombie_girl_1.png` through `zombie_girl_19.png`.
- Supports `human_girl.png` and `human_girl_1.png` through `human_girl_19.png`.
- Baby variants use the same numbered skins.

### Combat and Behavior

- Zombie Girl and Drowned Girl are undead creatures and keep the extra damage relationship with the Smite enchantment.
- They do not attack players, friendly mobs, villagers or their own kind.
- They have increased health, attack damage and movement speed.
- They can fight with melee weapons, bows, crossbows and tridents.
- Bows consume arrows from the companion backpack and can recover missed arrows during combat.
- Drowned Girl can sink and swim underwater and tries to reach the surface depending on the environment.
- Wild individuals wander on their own when no enemies are around.
- At low health they may flee from damage sources not caused by players.

### Taming, Backpack and Equipment

- Zombie Girl and Drowned Girl can be tamed with rotten flesh and raw meat.
- After taming, right-click to open the companion backpack.
- Provides 6 equipment slots and 64 slots of companion storage.
- The first four equipment slots correspond to helmet, chestplate, leggings and boots.
- Equipment grants vanilla attributes and enchantment effects but is not rendered on the entity model.
- Drowned Girl spawns holding a trident.

### Curing and Infection

- Right-clicking a Zombie Girl or Drowned Girl with the Life Star starts the curing process.
- The curing process includes zombie villager curing sounds, green particles and a white smoke particle stage.
- On completion it turns into a Human Girl, keeping the matching skin number, owner, equipment and backpack.
- A Human Girl that is hit by undead mobs and drops to half health or below becomes infected again.
- A Human Girl cured from a Drowned Girl turns back into a Drowned Girl when infected.

### Spawning

- Zombie Girl and Drowned Girl have their own respawn logic around nearby players.
- Respawning is limited to the area around players, with a population cap and distant cleanup to prevent unlimited accumulation and lag.
- Special respawning is also allowed on Peaceful difficulty.
- Zombie Girl can still spawn in high-light areas.

## Texture Placement

Place entity textures in:

```text
src/main/resources/assets/endbound_ember/textures/entity/
```

Place item textures in:

```text
src/main/resources/assets/endbound_ember/textures/item/
```

## Development Environment

- Minecraft 1.21.1
- NeoForge 21.1.262
- Java 21
- Gradle Wrapper

## License

This project is licensed under the GNU General Public License v3.0 (GPLv3). The full license text is in [LICENSE](LICENSE).

### Art Assets

All art assets created for this project — including project-authored block textures, block models, item textures and icons, and GUI assets — are released under **Creative Commons Attribution 4.0 International (CC BY 4.0)**. You may copy, modify and redistribute them freely (including commercially), provided you give appropriate credit and indicate whether changes were made. Upstream Dis-Enchanting-Table GUI and block textures are excluded and remain under the Unlicense described in [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES.md). The full license text for project-authored assets is in [LICENSE-ASSETS](LICENSE-ASSETS).

Suggested attribution format:

```text
Endbound Ember art assets by Emberstar, licensed under CC BY 4.0.
Source: <repository URL>
```

Notes:

- The license above only covers art assets created for this project.
- Assets provided by third parties (for example the Zombie Girl skin, provided by Wildcanyon) remain the property of their original authors and are outside this license; please obtain permission from the original author before use.
- For audio, entity skins and other assets without an explicit statement, please contact the author to confirm licensing.

Minecraft, NeoForge and their related assets belong to their respective rights holders. This project does not claim ownership of Mojang's or NeoForged's trademarks or official assets.

### Third-Party Open-Source Components

The disenchantment table's behavior and interface layout follow **Dis-Enchanting-Table** (Cursee Development, Minecraft 1.21.1 branch, Unlicense), and use its GUI background, short-table top texture and side texture. See [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES.md) for details.

The rendering approach and physical behavior of the Stardust Halo are based on **Halo Mod** (by Azusa_Ke, MIT License, <https://github.com/AzusaKe/Halo>). As required by the MIT License, the original copyright notice and full license text are in [THIRD-PARTY-NOTICES](THIRD-PARTY-NOTICES.md).

<span style="color:red">**⚠️ WARNING (Disclaimer): The Zombie Girl's hurt sound is EXTREMELY embarrassing. Do NOT play it out loud in public! If you really want to play, please wear headphones!**</span>

<span style="color:orange">**⚠️ Audio Warning: The bell sounds in this mod have a lingering resonance effect. When wearing headphones, it may cause potential hearing damage. Please adjust the volume appropriately.**</span>

## Connect With Me

- 📺 **Bilibili**: submissions are welcome on my personal page: <https://b23.tv/6agWJVH>
- 💖 **Afdian support** (if this helped you, a little support is appreciated):
  - Support link: <https://www.ifdian.net/a/Emberstar>
  - Afdian homepage: <https://www.ifdian.net/a/Emberstar>

---

## 🙏 Acknowledgements

**Production involvement**: Shasnow

**Open-source thanks**: Azusa_Ke, author of [Halo Mod](https://github.com/AzusaKe/Halo). The rendering approach and physical behavior of the Stardust Halo are based on that project (MIT License); we thank them and retain the original copyright notice.

**Disenchantment table thanks**: Cursee Development, for the implementation reference and GUI/block textures from [Dis-Enchanting-Table](https://github.com/Cursee-Development/Dis-Enchanting-Table/tree/1.21.1). Although the Unlicense does not require attribution, we are glad to acknowledge their work here.

**Additional thanks**: Wildcanyon, for providing the Zombie Girl skin. [Bilibili page](https://space.bilibili.com/622498204?spm_id_from=333.788.upinfo.head.click)

**Beta testers**: Shixiangye, Shasnow, andan_, 鸿銞

**Sponsors** (in no particular order):

| Sponsor | Amount |
|---------|--------|
| andan_ | ¥23.14 |
| 鸿銞 | ¥15 |
| 。乄夜幽魂。☆ | ¥5 |
| 丝羽珍 | ¥14.5 |
|@我是💩，我写的东西全是？ | ¥5 |

**Feedback contributors**: Shasnow, andan_, 鸿銞, Shixiangye

Thanks to the beta group for their feedback and support, and to my friends for their creative ideas.

---

## AI Relay Promotion — Source of Development Models

- AI relay website: <https://rcolaapi.top/sign-up?aff=lp3B/>
- Operator: Zch
- Relay group: **272395232**
