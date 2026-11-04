# Items each quest asks you to bring, from the "Items required" and "Recommended" rows of the quest's
# details box on the Old School RuneScape Wiki (https://oldschool.runescape.wiki/w/<Quest>), read 2026-11-04.
# tools/build-quest-items.py turns this into src/main/resources/com/codex/navigator/quest-items.json.
#
# One line per item: "Name", "Name x12", then optional flags:
#   ~        obtainable during the quest (the wiki says so)
#   ?        not checkable: a kind of thing ("Any axe", "Food") rather than one item
#   | note   a short note shown with the item
#   Alternatives that also count: "Name / Other name".
# Group names in angle brackets stand for every listed tier: <axe>, <pickaxe>.
# Only quests listed here get a prep list; others say the list is not bundled yet.

QUESTS = {
    "Cook's Assistant": {
        "items": ["Egg ~", "Bucket of milk ~ | or bring a bucket and milk a cow", "Pot of flour ~ | or bring a pot and mill some"],
        "recommended": [],
    },
    "Demon Slayer": {
        "items": ["Bucket of water ~ | or any water container", "Bones x25 | unnoted, can be handed in a few at a time", "Coins x1"],
        "recommended": ["Varrock teleport", "Armour ?", "Food ?"],
    },
    "The Restless Ghost": {"items": [], "recommended": ["Lumbridge teleport"]},
    "Rune Mysteries": {"items": ["Air talisman ~ | given at the start"], "recommended": ["Lumbridge teleport", "Varrock teleport"]},
    "Druidic Ritual": {
        "items": ["Raw bear meat", "Raw rat meat", "Raw beef", "Raw chicken"],
        "recommended": [],
    },
    "Jungle Potion": {
        "items": [],
        "recommended": ["Antidote++ / Antipoison", "Stamina potion", "Food ?", "Brimhaven teleport"],
    },
    "Death Plateau": {
        "items": ["Coins x60 | 1,000 for the dice with Harold", "Bread x10 | unnoted", "Trout x10 | unnoted, cooked",
                  "Iron bar | for spiked boots", "Asgarnian ale | one sits on the bar of the Burthorpe pub"],
        "recommended": ["Premade blurb' sp. / Blurberry special", "Games necklace / Combat bracelet", "Stamina potion / Energy potion"],
    },
    "Troll Stronghold": {
        "items": ["Climbing boots | or 12 coins to buy a pair"],
        "recommended": ["Food ?", "Armour ?", "Games necklace", "Stamina potion / Energy potion"],
    },
    "Priest in Peril": {
        "items": ["Rune essence / Pure essence x50 | unnoted, over several trips", "Bucket ~"],
        "recommended": ["Varrock teleport x3", "Stamina potion / Energy potion / Super energy", "Food ?"],
    },
    "Lost City": {
        "items": ["Knife", "<axe> | not blessed; banked before Entrana"],
        "recommended": ["Stamina potion / Energy potion", "Food ? | no armour or weapons on Entrana"],
    },
    "Waterfall Quest": {
        "items": ["Air rune x6", "Earth rune x6", "Water rune x6", "Rope"],
        "recommended": ["Food ?"],
    },
    "Shilo Village": {
        "items": ["Spade", "Torch / Candle lantern / Candle / Black candle | lit", "Rope", "Bronze wire", "Chisel",
                  "Bones x3 ~"],
        "recommended": ["Antipoison", "Stamina potion / Energy potion", "Food ?"],
    },
    "Goblin Diplomacy": {
        "items": ["Goblin mail x3 ~", "Blue dye | or 2 woad leaves and 5 coins for Aggie",
                  "Red dye | or 3 redberries and 5 coins for Aggie", "Yellow dye | or 2 onions and 5 coins for Aggie"],
        "recommended": [],
    },
    "Nature Spirit": {
        "items": ["Silver sickle | a silver bar in a sickle mould, 18 Crafting", "Ghostspeak amulet"],
        "recommended": ["Salve graveyard teleport x2", "Prayer potion | for a low Prayer level", "Stamina potion / Energy potion", "Food ?"],
    },
    "Shield of Arrav": {"items": ["Coins x20 | Phoenix Gang route"], "recommended": []},
    "Plague City": {
        "items": ["Dwellberries", "Rope", "Spade", "Bucket of water x4 | four uses", "Hangover cure / Chocolate dust / Chocolate bar | or its parts: chocolate dust (grind a bar), milk and snape grass", "Bucket of milk",
                  "Snape grass", "Picture ~ | of Elena, found in her parents' house"],
        "recommended": ["Ardougne teleport"],
    },
    "Biohazard": {
        "items": ["Priest gown ~", "Gas mask ~"],
        "recommended": ["Ardougne teleport", "Varrock teleport", "Stamina potion / Energy potion"],
    },
    "Merlin's Crystal": {
        "items": ["Bread", "Tinderbox", "Bucket of wax ~", "Bat bones ~"],
        "recommended": ["Stamina potion / Energy potion"],
    },
    "Gertrude's Cat": {
        "items": ["Bucket of milk", "Coins x100", "Seasoned sardine / Raw sardine", "Doogle leaves ~"],
        "recommended": ["Ring of charos(a) | to choose the kitten's colour"],
    },
    "Big Chompy Bird Hunting": {
        "items": ["<axe> | not blessed", "Feather x100 | or 50 coins for 25 from Fycie", "Knife | or 10 coins for a knife and chisel from Bugs", "Chisel | or 10 coins for a knife and chisel from Bugs", "Wolf bones x4 ~", "Cabbage ~ | not Draynor",
                  "Tomato ~", "Onion ~", "Potato ~", "Equa leaves ~", "Doogle leaves ~"],
        "recommended": ["Stamina potion / Energy potion", "Food ?", "Feldip hills teleport"],
    },
    "The Lost Tribe": {
        "items": ["<pickaxe>", "Light source ? | a candle, torch or lantern"],
        "recommended": ["Lockpick", "Stamina potion"],
    },
    "Underground Pass": {
        "items": ["Rope | 2 recommended", "Bow ? | any bow that fires your arrows", "Arrows ? | 1 to 5, not poisoned",
                  "Spade", "Bucket ~", "Tinderbox ~"],
        "recommended": ["Summer pie", "Saradomin brew", "Stamina potion", "Agility potion x2", "Food ?", "Plank ~",
                        "West ardougne teleport"],
    },
    "The Dig Site": {
        "items": ["Pestle and mortar", "Vial", "Tinderbox", "Cup of tea / Nettle tea", "Rope x2", "Opal / Uncut opal",
                  "Charcoal ~"],
        "recommended": ["Stamina potion / Energy potion", "Digsite teleport"],
    },
    "Black Knights' Fortress": {
        "items": ["Cabbage | not from Draynor Manor", "Iron chainbody | or the black armour set", "Bronze med helm | or the black armour set"],
        "recommended": ["Food ?", "Armour ?", "Falador teleport"],
    },
    "Dragon Slayer I": {
        "items": ["Unfired bowl", "Wizard's mind bomb", "Lobster pot", "Silk", "Hammer", "Anti-dragon shield ~",
                  "Steel nails x90", "Plank x3 | normal planks", "Coins x2000",
                  "Law rune | with an air rune, for Telekinetic Grab at 33 Magic; or 10,000 coins", "Air rune | for Telekinetic Grab"],
        "recommended": ["Antifire potion", "Stamina potion / Energy potion"],
    },
    "The Fremennik Trials": {
        "items": ["Coins x5252", "Beer tankard / Beer ~", "Tinderbox", "Raw shark / Raw manta ray / Raw sea turtle",
                  "Lyre | or make one: 25 Fletching, 40 Woodcutting, 40 Crafting", "<axe>", "Knife", "Potato ~",
                  "Onion ~", "Cabbage ~"],
        "recommended": ["Prayer potion x3 | 3 to 5", "Stamina potion", "Ring of recoil x7 | only to fight Koschei's final form",
                        "Food ?", "Camelot teleport / Rellekka teleport"],
    },
    "Creature of Fenkenstrain": {
        "items": ["Ghostspeak amulet", "Silver bar", "Bronze wire x3 | unnoted", "Needle", "Thread x5", "Spade",
                  "Coins x50 | or Telekinetic Grab"],
        "recommended": ["Holy symbol", "Stamina potion / Energy potion", "Food ?", "Fenkenstrain's castle teleport"],
    },
    "X Marks the Spot": {"items": ["Spade | not the Eastfloor spade"], "recommended": ["Energy potion / Stamina potion"]},
    "Family Crest": {
        "items": ["Shrimps", "Salmon", "Tuna", "Bass", "Swordfish", "<pickaxe>", "Ruby x2", "Ring mould",
                  "Necklace mould", "Antipoison | any poison cure, you lose it",
                  "Blast runes ? | runes for all four Blast spells"],
        "recommended": ["Antipoison"],
    },
    "Temple of Ikov": {
        "items": ["Bow ? | yew or better, for ice arrows; or any thrown weapon", "Limpwurt root x20 | unnoted",
                  "Boots of lightness ~ / Graceful boots | anything that takes weight below 0"],
        "recommended": ["Prayer potion", "Food ?", "Ava's accumulator", "Ardougne teleport"],
    },
    "Heroes' Quest": {
        "items": ["Ice gloves ~ / Smiths gloves (i)", "Fishing rod ~", "Fishing bait ~", "Dusty key / Knife | or 60 Sailing; depends on the lava eel spot you use",
                  "Harralander potion (unf) | or a harralander and a vial of water", "<pickaxe>",
                  "Black full helm | Black Arm Gang; not trimmed", "Black platebody | Black Arm Gang; not trimmed",
                  "Black platelegs | Black Arm Gang; not trimmed"],
        "recommended": ["Anti-dragon shield", "Antifire potion", "Stamina potion / Energy potion"],
    },
    "The Grand Tree": {
        "items": ["Coins x1000 ? | only if you turned down Femi's help"],
        "recommended": ["Stamina potion / Energy potion"],
    },
    "Eadgar's Ruse": {
        "items": ["Climbing boots | or 12 coins", "Vodka", "Pineapple chunks", "Logs | one; bring two and a tinderbox",
                  "Grain x10 ~", "Raw chicken x5", "Pestle and mortar", "Ranarr potion (unf)"],
        "recommended": ["Tinderbox", "Energy potion / Stamina potion", "Food ?"],
    },
    "Sheep Shearer": {"items": ["Ball of wool x20 | unnoted", "Shears ~ | only to shear sheep yourself"], "recommended": []},
    "Romeo & Juliet": {"items": ["Cadava berries ~"], "recommended": ["Varrock teleport"]},
    "Ernest the Chicken": {"items": ["Spade ~", "Fish food ~", "Poison ~"], "recommended": ["Food ?", "Draynor manor teleport"]},
    "Vampyre Slayer": {"items": ["Hammer", "Beer ~"],
                       "recommended": ["Garlic ~", "Combat equipment ? | Magic if you use the safespot", "Varrock teleport"]},
    "Imp Catcher": {"items": ["Black bead", "Yellow bead", "Red bead", "White bead"], "recommended": []},
    "Prince Ali Rescue": {
        "items": ["Soft clay", "Ball of wool x3", "Yellow dye | or 2 onions and 5 coins", "Redberries", "Ashes",
                  "Bucket of water ~ / Jug of water", "Pot of flour", "Bronze bar", "Pink skirt", "Beer x3",
                  "Rope ~ | Ned sells one during the quest", "Coins x40 | 30 to 40 for the Al Kharid toll gate if you walk"],
        "recommended": ["Lumbridge teleport | for a furnace"],
    },
    "Doric's Quest": {"items": ["Clay x6 | not soft clay", "Copper ore x4", "Iron ore x2"],
                      "recommended": ["<pickaxe> | only to mine the ores yourself, at 15 Mining"]},
    "Witch's Potion": {"items": ["Burnt meat", "Eye of newt", "Onion", "Rat's tail ~"], "recommended": []},
    "The Knight's Sword": {
        "items": ["Redberry pie | a whole pie", "Iron bar x2", "Blurite ore ~", "<pickaxe>"],
        "recommended": ["Food ?", "Falador teleport"],
    },
    "Pirate's Treasure": {
        "items": ["Karamjan rum ~", "White apron ~", "Coins x60 | 30 with a Ring of charos(a)", "Banana x10 ~", "Spade ~"],
        "recommended": [],
    },
    "Below Ice Mountain": {
        "items": ["Cooked meat ~", "Bread", "Knife", "Beer ~ / Asgarnian ale / Dwarven stout / Wizard's mind bomb"],
        "recommended": ["<pickaxe> | with 10 Mining, beats the Ancient Guardian without a fight", "Food ?", "Falador teleport",
                        "Energy potion / Stamina potion"],
    },
    "Recipe for Disaster - Another Cook's Quest": {
        "items": ["Eye of newt", "Greenman's ale", "Rotten tomato", "Fruit blast ? | any fruit blast, not premade",
                  "Ashes"],
        "recommended": [],
    },
    "Fishing Contest": {
        "items": ["Spade", "Coins x5 | 10 if you buy a fishing rod from Jack", "Garlic ~",
                  "Fishing rod / Pearl fishing rod | not the oily rod", "Red vine worm ~"],
        "recommended": ["Food ?", "Camelot teleport"],
    },
    "Tree Gnome Village": {
        "items": ["Logs x6 | or an axe to cut them", "Combat equipment ?", "Food ?"],
        "recommended": ["Stamina potion / Energy potion"],
    },
    "Fight Arena": {"items": ["Coins x5 | for a Khali brew"], "recommended": ["Combat equipment ?", "Food ?", "Ardougne teleport"]},
    "Witch's House": {
        "items": ["Cheese | bring a few in case", "Leather gloves ~ | or other gloves"],
        "recommended": ["Food ?"],
    },
    "Icthlarin's Little Helper": {
        "items": ["Cat ? | any grown cat or kitten, following you", "Tinderbox", "Willow logs",
                  "Bag of salt / Bucket | an empty bucket gets salt in Sophanem", "Bucket of sap",
                  "Waterskin(4) | full, given away", "Linen"],
        "recommended": ["Waterskin(4)", "Knife", "Antipoison / Superantipoison / Antidote+ / Antidote++", "Ghostspeak amulet", "Food ?",
                        "Energy potion", "Nardah teleport"],
    },
    "Client of Kourend": {
        "items": ["Feather | not a magic gold feather"],
        "recommended": ["Stamina potion / Energy potion"],
    },
    "Recruitment Drive": {"items": [], "recommended": []},
    "Regicide": {
        "items": ["Coal x9 | 6 to 9; 25 recommended", "Bow ? | or lit fire arrows", "Arrows ?", "Rope x2 | 3 in case you fail",
                  "Spade", "Limestone | or a pickaxe and 10 Mining", "Tinderbox", "Strip of cloth", "Pestle and mortar",
                  "Gloves ? | see the wiki's Quicklime list", "Pot ~", "Cooked rabbit ~ / Roast rabbit"],
        "recommended": ["Antidote++ / Antipoison", "Stamina potion", "Summer pie x3", "Food ?", "<axe> | to cook the rabbit anywhere",
                        "Rimmington teleport", "West ardougne teleport"],
    },
    "In Search of the Myreque": {
        "items": ["Steel longsword", "Steel sword x2", "Steel mace", "Steel warhammer", "Steel dagger",
                  "Steel nails x225", "Druid pouch | with at least 5 charges",
                  "Mort myre fungus x6 / Mort myre pear / Mort myre stem | to fill the pouch: 6 fungus, or 3 pears, or 3 stems",
                  "Silver sickle (b) | only to gather the fillings yourself",
                  "Hammer / Imcando hammer", "Plank x6 | normal planks", "Coins x10 | or wear a Ring of charos(a)"],
        "recommended": ["Prayer potion", "Food ?"],
    },
    "Sheep Herder": {"items": ["Coins x100"], "recommended": ["Energy potion", "Ardougne teleport"]},
    "Murder Mystery": {"items": ["Pot / Pot of flour"], "recommended": []},
}
