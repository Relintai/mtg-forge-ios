package forge.ai.simulation;

import com.google.common.collect.Lists;
import forge.ai.ComputerUtilAbility;
import forge.card.CardStateName;
import forge.card.MagicColor;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

import java.util.List;

public class GameSimulatorTest extends SimulationTestCase {

    public void testActivateAbilityTriggers() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);

        addCard("Plains", p);
        addCard("Plains", p);
        addCard("Plains", p);
        String heraldCardName = "Herald of Anafenza";
        Card herald = addCard(heraldCardName, p);
        herald.setSickness(false);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        SpellAbility outlastSA = findSAWithPrefix(herald, "Outlast");
        assertNotNull(outlastSA);

        GameSimulator sim = createSimulator(game, p);
        int score = sim.simulateSpellAbility(outlastSA).value;
        assertTrue(score >  0);
        Game simGame = sim.getSimulatedGameState();

        Card heraldCopy = findCardWithName(simGame, heraldCardName);
        assertNotNull(heraldCopy);
        assertTrue(heraldCopy.isTapped());
        assertTrue(heraldCopy.hasCounters());
        assertEquals(1, heraldCopy.getToughnessBonusFromCounters());
        assertEquals(1, heraldCopy.getPowerBonusFromCounters());

        Card warriorToken = findCardWithName(simGame, "Warrior");
        assertNotNull(warriorToken);
        assertTrue(warriorToken.isSick());
        assertEquals(1, warriorToken.getCurrentPower());
        assertEquals(1, warriorToken.getCurrentToughness());
    }

    public void testStaticAbilities() {
        String sliverCardName = "Sidewinder Sliver";
        String heraldCardName = "Herald of Anafenza";
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card sliver = addCard(sliverCardName, p);
        sliver.setSickness(false);
        Card herald = addCard(heraldCardName, p);
        herald.setSickness(false);
        addCard("Plains", p);
        addCard("Plains", p);
        addCard("Plains", p);
        addCard("Spear of Heliod", p);
        
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);
        game.getAction().checkStateEffects(true);

        assertEquals(1, sliver.getAmountOfKeyword("Flanking"));
        assertEquals(2, sliver.getNetPower());
        assertEquals(2, sliver.getNetToughness());

        SpellAbility outlastSA = findSAWithPrefix(herald, "Outlast");
        assertNotNull(outlastSA);

        GameSimulator sim = createSimulator(game, p);
        int score = sim.simulateSpellAbility(outlastSA).value;
        assertTrue(score >  0);
        Game simGame = sim.getSimulatedGameState();
        Card sliverCopy = findCardWithName(simGame, sliverCardName);
        assertEquals(1, sliverCopy.getAmountOfKeyword("Flanking"));
        assertEquals(2, sliver.getNetPower());
        assertEquals(2, sliver.getNetToughness());
    }

    public void testStaticEffectsMonstrous() {
        String lionCardName = "Fleecemane Lion";
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card lion = addCard(lionCardName, p);
        lion.setSickness(false);
        lion.setMonstrous(true);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);
        assertTrue(lion.isMonstrous());
        assertEquals(1, lion.getAmountOfKeyword("Hexproof"));
        assertEquals(1, lion.getAmountOfKeyword("Indestructible"));

        GameSimulator sim = createSimulator(game, p);
        Game simGame = sim.getSimulatedGameState();
        Card lionCopy = findCardWithName(simGame, lionCardName);
        assertTrue(lionCopy.isMonstrous());
        assertEquals(1, lionCopy.getAmountOfKeyword("Hexproof"));
        assertEquals(1, lionCopy.getAmountOfKeyword("Indestructible"));
    }

    public void testEquippedAbilities() {
        String bearCardName = "Runeclaw Bear";
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card bear = addCard(bearCardName, p);
        bear.setSickness(false);
        Card cloak = addCard("Whispersilk Cloak", p);
        cloak.equipCard(bear);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);
        assertEquals(1, bear.getAmountOfKeyword("Unblockable"));

        GameSimulator sim = createSimulator(game, p);
        Game simGame = sim.getSimulatedGameState();
        Card bearCopy = findCardWithName(simGame, bearCardName);
        assertEquals(1, bearCopy.getAmountOfKeyword("Unblockable"));
    }

    public void testEnchantedAbilities() {
        String bearCardName = "Runeclaw Bear";
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card bear = addCard(bearCardName, p);
        bear.setSickness(false);
        Card lifelink = addCard("Lifelink", p);
        lifelink.enchantEntity(bear);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        game.getAction().checkStateEffects(true);
        assertEquals(1, bear.getAmountOfKeyword("Lifelink"));

        GameSimulator sim = createSimulator(game, p);
        Game simGame = sim.getSimulatedGameState();
        Card bearCopy = findCardWithName(simGame, bearCardName);
        assertEquals(1, bearCopy.getAmountOfKeyword("Lifelink"));
    }
    
    public void testEtbTriggers() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Black Knight", p);
        for (int i = 0; i < 5; i++)
            addCard("Swamp", p);

        String merchantCardName = "Gray Merchant of Asphodel";
        Card c = addCardToZone(merchantCardName, p, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        SpellAbility playMerchantSa = c.getSpellAbilities().get(0);
        playMerchantSa.setActivatingPlayer(p);

        GameSimulator sim = createSimulator(game, p);
        int origScore = sim.getScoreForOrigGame().value;
        int score = sim.simulateSpellAbility(playMerchantSa).value;
        assertTrue(String.format("score=%d vs. origScore=%d",  score, origScore), score > origScore);
        Game simGame = sim.getSimulatedGameState();
        assertEquals(24, simGame.getPlayers().get(1).getLife());
        assertEquals(16, simGame.getPlayers().get(0).getLife());
    }
    
    public void testSimulateUnmorph() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card ripper = createCard("Ruthless Ripper", p);
        ripper.setState(CardStateName.FaceDown, true);
        p.getZone(ZoneType.Battlefield).add(ripper);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        assertEquals(20, game.getPlayers().get(0).getLife());
        
        GameSimulator sim = createSimulator(game, p);
        Game simGame = sim.getSimulatedGameState();

        SpellAbility unmorphSA = findSAWithPrefix(ripper, "Morph—Reveal a black card");
        assertNotNull(unmorphSA);
        sim.simulateSpellAbility(unmorphSA);
        assertEquals(18, simGame.getPlayers().get(0).getLife());
    }
    
    public void testFindingOwnCard() {
        Game game = initAndCreateGame();
        Player p0 = game.getPlayers().get(0);
        Player p1 = game.getPlayers().get(1);
        addCardToZone("Skull Fracture", p0, ZoneType.Hand);
        addCardToZone("Runeclaw Bear", p0, ZoneType.Hand);
        Card fractureP1 = addCardToZone("Skull Fracture", p1, ZoneType.Hand);
        addCard("Swamp", p1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p1);
        game.getAction().checkStateEffects(true);
        
        GameSimulator sim = createSimulator(game, p1);
        Game simGame = sim.getSimulatedGameState();

        SpellAbility fractureSa = fractureP1.getSpellAbilities().get(0);
        assertNotNull(fractureSa);
        fractureSa.getTargets().add(p0);
        sim.simulateSpellAbility(fractureSa);
        assertEquals(1, simGame.getPlayers().get(0).getCardsIn(ZoneType.Hand).size());
        assertEquals(0, simGame.getPlayers().get(1).getCardsIn(ZoneType.Hand).size());
    }
    
    public void testPlaneswalkerAbilities() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card sorin = addCard("Sorin, Solemn Visitor", p);
        sorin.addCounter(CounterType.LOYALTY, 5, sorin, false);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        CardCollection cards = ComputerUtilAbility.getAvailableCards(game, p);
        List<SpellAbility> abilities = ComputerUtilAbility.getSpellAbilities(cards, p);
        SpellAbility minusTwo = findSAWithPrefix(abilities, "-2: Create a 2/2 black Vampire");
        assertNotNull(minusTwo);
        minusTwo.setActivatingPlayer(p);
        assertTrue(minusTwo.canPlay());

        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(minusTwo);
        Game simGame = sim.getSimulatedGameState();
        Card vampireToken = findCardWithName(simGame, "Vampire");
        assertNotNull(vampireToken);

        Player simP = simGame.getPlayers().get(1);
        cards = ComputerUtilAbility.getAvailableCards(simGame, simP);
        abilities = ComputerUtilAbility.getSpellAbilities(cards, simP);
        SpellAbility minusTwoSim = findSAWithPrefix(abilities, "-2: Create a 2/2 black Vampire");
        assertNotNull(minusTwoSim);
        minusTwoSim.setActivatingPlayer(simP);
        assertFalse(minusTwoSim.canPlay());
        assertEquals(1, minusTwoSim.getActivationsThisTurn());
        
        GameCopier copier = new GameCopier(simGame);
        Game copy = copier.makeCopy();
        Player copyP = copy.getPlayers().get(1);
        cards = ComputerUtilAbility.getAvailableCards(copy, copyP);
        abilities = ComputerUtilAbility.getSpellAbilities(cards, copyP);
        SpellAbility minusTwoCopy = findSAWithPrefix(abilities, "-2: Create a 2/2 black Vampire");
        minusTwoCopy.setActivatingPlayer(copyP);
        assertFalse(minusTwoCopy.canPlay());
        assertEquals(1, minusTwoCopy.getActivationsThisTurn());
    }

    public void testPlaneswalkerEmblems() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        String bearCardName = "Runeclaw Bear";
        addCard(bearCardName, p);
        Card gideon = addCard("Gideon, Ally of Zendikar", p);
        gideon.addCounter(CounterType.LOYALTY, 4, gideon, false);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        CardCollection cards = ComputerUtilAbility.getAvailableCards(game, p);
        List<SpellAbility> abilities = ComputerUtilAbility.getSpellAbilities(cards, p);
        SpellAbility minusFour = findSAWithPrefix(abilities, "-4: You get an emblem");
        assertNotNull(minusFour);
        minusFour.setActivatingPlayer(p);
        assertTrue(minusFour.canPlay());

        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(minusFour);
        Game simGame = sim.getSimulatedGameState();
        Card simBear = findCardWithName(simGame, bearCardName);
        assertEquals(3, simBear.getNetPower());

        GameCopier copier = new GameCopier(simGame);
        Game copy = copier.makeCopy();
        Card copyBear = findCardWithName(copy, bearCardName);
        assertEquals(3, copyBear.getNetPower());
    }

    public void testManifest() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Plains", p);
        addCard("Plains", p);
        Card soulSummons = addCardToZone("Soul Summons", p, ZoneType.Hand);
        addCardToZone("Ornithopter", p, ZoneType.Library);
        
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        SpellAbility manifestSA = soulSummons.getSpellAbilities().get(0);
        
        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(manifestSA);
        Game simGame = sim.getSimulatedGameState();
        Card manifestedCreature = findCardWithName(simGame, "");
        assertNotNull(manifestedCreature);

        SpellAbility unmanifestSA = findSAWithPrefix(manifestedCreature, "Unmanifest");
        assertNotNull(unmanifestSA);
        assertEquals(2, manifestedCreature.getNetPower());
        assertFalse(manifestedCreature.hasKeyword("Flying"));

        GameSimulator sim2 = createSimulator(simGame, simGame.getPlayers().get(1));
        sim2.simulateSpellAbility(unmanifestSA);
        Game simGame2 = sim2.getSimulatedGameState();
        Card ornithopter = findCardWithName(simGame2, "Ornithopter");
        assertEquals(0, ornithopter.getNetPower());
        assertTrue(ornithopter.hasKeyword("Flying"));
        assertNull(findSAWithPrefix(ornithopter, "Unmanifest"));

        GameCopier copier = new GameCopier(simGame2);
        Game copy = copier.makeCopy();
        Card ornithopterCopy = findCardWithName(copy, "Ornithopter");
        assertNull(findSAWithPrefix(ornithopterCopy, "Unmanifest"));
    }

    public void testManifest2() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Plains", p);
        addCard("Plains", p);
        Card soulSummons = addCardToZone("Soul Summons", p, ZoneType.Hand);
        addCardToZone("Plains", p, ZoneType.Library);
        
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        SpellAbility manifestSA = soulSummons.getSpellAbilities().get(0);
        
        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(manifestSA);
        Game simGame = sim.getSimulatedGameState();
        Card manifestedCreature = findCardWithName(simGame, "");
        assertNotNull(manifestedCreature);
        assertNull(findSAWithPrefix(manifestedCreature, "Unmanifest"));

        GameCopier copier = new GameCopier(simGame);
        Game copy = copier.makeCopy();
        Card manifestedCreatureCopy = findCardWithName(copy, "");
        assertNull(findSAWithPrefix(manifestedCreatureCopy, "Unmanifest"));
    }

    public void testManifest3() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Plains", p);
        addCard("Plains", p);
        Card soulSummons = addCardToZone("Soul Summons", p, ZoneType.Hand);
        addCardToZone("Dryad Arbor", p, ZoneType.Library);
        
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        SpellAbility manifestSA = soulSummons.getSpellAbilities().get(0);
        
        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(manifestSA);
        Game simGame = sim.getSimulatedGameState();
        Card manifestedCreature = findCardWithName(simGame, "");
        assertNotNull(manifestedCreature);
        assertNull(findSAWithPrefix(manifestedCreature, "Unmanifest"));

        GameCopier copier = new GameCopier(simGame);
        Game copy = copier.makeCopy();
        Card manifestedCreatureCopy = findCardWithName(copy, "");
        assertNull(findSAWithPrefix(manifestedCreatureCopy, "Unmanifest"));
    }

    public void testTypeOfPermanentChanging() {
        String sarkhanCardName = "Sarkhan, the Dragonspeaker";
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card sarkhan = addCard(sarkhanCardName, p);
        sarkhan.addCounter(CounterType.LOYALTY, 4, sarkhan, false);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        assertFalse(sarkhan.isCreature());
        assertTrue(sarkhan.isPlaneswalker());

        SpellAbility becomeDragonSA = findSAWithPrefix(sarkhan, "+1");
        assertNotNull(becomeDragonSA);

        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(becomeDragonSA);
        Game simGame = sim.getSimulatedGameState();
        Card sarkhanSim = findCardWithName(simGame, sarkhanCardName);
        assertTrue(sarkhanSim.isCreature());
        assertFalse(sarkhanSim.isPlaneswalker());

        GameCopier copier = new GameCopier(simGame);
        Game copy = copier.makeCopy();
        Card sarkhanCopy = findCardWithName(copy, sarkhanCardName);
        assertTrue(sarkhanCopy.isCreature());
        assertFalse(sarkhanCopy.isPlaneswalker());
    }
    
    public void testDistributeCountersAbility() {
        String ajaniCardName = "Ajani, Mentor of Heroes";
        String ornithoperCardName = "Ornithopter";
        String bearCardName = "Runeclaw Bear";

        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard(ornithoperCardName, p);
        addCard(bearCardName, p);
        Card ajani = addCard(ajaniCardName, p);
        ajani.addCounter(CounterType.LOYALTY, 4, ajani, false);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        SpellAbility sa = findSAWithPrefix(ajani, "+1: Distribute");
        assertNotNull(sa);
        sa.setActivatingPlayer(p);

        MultiTargetSelector selector = new MultiTargetSelector(sa, null);
        while (selector.selectNextTargets()) {
            GameSimulator sim = createSimulator(game, p);
            sim.simulateSpellAbility(sa);
            Game simGame = sim.getSimulatedGameState();
            Card thopterSim = findCardWithName(simGame, ornithoperCardName);
            Card bearSim = findCardWithName(simGame, bearCardName);
            assertEquals(3, thopterSim.getCounters(CounterType.P1P1) + bearSim.getCounters(CounterType.P1P1));
        }
    }
    
    public void testDamagePreventedTrigger() {
        String ajaniCardName = "Ajani Steadfast";
        String selflessCardName = "Selfless Squire";

        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        addCard(selflessCardName, p);
        addCard("Mountain", p);
        Card boltCard = addCardToZone("Lightning Bolt", p, ZoneType.Hand);
        SpellAbility boltSA = boltCard.getFirstSpellAbility();

        Card ajani = addCard(ajaniCardName, p);
        ajani.addCounter(CounterType.LOYALTY, 8, ajani, false);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);
        

        SpellAbility sa = findSAWithPrefix(ajani, "-7:");
        assertNotNull(sa);
        sa.setActivatingPlayer(p);

        GameSimulator sim = createSimulator(game, p);
        boltSA.getTargets().add(p);
        sim.simulateSpellAbility(sa);
        sim.simulateSpellAbility(boltSA);
        Game simGame = sim.getSimulatedGameState();
        Card simSelfless = findCardWithName(simGame, selflessCardName);

        // only one damage
        assertEquals(19, simGame.getPlayers().get(0).getLife());

        // only triggered once
        assertTrue(simSelfless.hasCounters());
        assertEquals(2, simSelfless.getCounters(CounterType.P1P1));
        assertEquals(2, simSelfless.getToughnessBonusFromCounters());
        assertEquals(2, simSelfless.getPowerBonusFromCounters());
    }

    public void testChosenColors() {
        String bearCardName = "Runeclaw Bear";

        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Card bear = addCard(bearCardName, p);
        Card hall = addCard("Hall of Triumph", p);
        hall.setChosenColors(Lists.newArrayList("green"));
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);
        assertEquals(3, bear.getNetToughness());
        
        GameCopier copier = new GameCopier(game);
        Game copy = copier.makeCopy();
        Card bearCopy = findCardWithName(copy, bearCardName);
        assertEquals(3, bearCopy.getNetToughness());
    }

    public void testDarkDepthsCopy() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Swamp", p);
        addCard("Swamp", p);
        Card depths = addCard("Dark Depths", p);
        depths.addCounter(CounterType.ICE, 10, depths, false);
        Card thespian = addCard("Thespian's Stage", p);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);
        assertTrue(depths.hasCounters());
        
        SpellAbility sa = findSAWithPrefix(thespian, "{2}, {T}: CARDNAME becomes a copy of target land and gains this ability.");
        assertNotNull(sa);
        sa.getTargets().add(depths);

        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(sa);
        Game simGame = sim.getSimulatedGameState();

        String strSimGame = gameStateToString(simGame);
        assertNull(strSimGame, findCardWithName(simGame, "Dark Depths"));
        assertNull(strSimGame, findCardWithName(simGame, "Thespian's Stage"));
        assertNotNull(strSimGame, findCardWithName(simGame, "Marit Lage"));
    }
    
    public void testThespianStageSelfCopy() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Swamp", p);
        addCard("Swamp", p);
        Card thespian = addCard("Thespian's Stage", p);
        assertTrue(thespian.isLand());
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);
        
        SpellAbility sa = findSAWithPrefix(thespian, "{2}, {T}: CARDNAME becomes a copy of target land and gains this ability.");
        assertNotNull(sa);
        sa.getTargets().add(thespian);

        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(sa);
        Game simGame = sim.getSimulatedGameState();
        Card thespianSim = findCardWithName(simGame, "Thespian's Stage");
        assertNotNull(gameStateToString(simGame), thespianSim);
        assertTrue(thespianSim.isLand());
    }

    public void testDash() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);

        addCard("Mountain", p);
        addCard("Mountain", p);
        String berserkerCardName = "Lightning Berserker";
        Card berserkerCard = addCardToZone(berserkerCardName, p, ZoneType.Hand);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        SpellAbility dashSA = findSAWithPrefix(berserkerCard, "Dash");
        assertNotNull(dashSA);

        GameSimulator sim = createSimulator(game, p);
        int score = sim.simulateSpellAbility(dashSA).value;
        assertTrue(score >  0);
        Game simGame = sim.getSimulatedGameState();

        Card berserker = findCardWithName(simGame, berserkerCardName);
        assertNotNull(berserker);
        assertEquals(1, berserker.getNetPower());
        assertEquals(1, berserker.getNetToughness());
        assertFalse(berserker.isSick());
        
        SpellAbility pumpSA = findSAWithPrefix(berserker, "{R}: CARDNAME gets +1/+0 until end of turn.");
        assertNotNull(pumpSA);
        GameSimulator sim2 = createSimulator(simGame, (Player) sim.getGameCopier().find(p));
        sim2.simulateSpellAbility(pumpSA);
        Game simGame2 = sim2.getSimulatedGameState();

        Card berserker2 = findCardWithName(simGame2, berserkerCardName);
        assertNotNull(berserker2);
        assertEquals(2, berserker2.getNetPower());
        assertEquals(1, berserker2.getNetToughness());
        assertFalse(berserker2.isSick());
    }

    public void testTokenAbilities() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);

        addCard("Forest", p);
        addCard("Forest", p);
        addCard("Forest", p);
        Card callTheScionsCard = addCardToZone("Call the Scions", p, ZoneType.Hand);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        SpellAbility callTheScionsSA = callTheScionsCard.getSpellAbilities().get(0);

        GameSimulator sim = createSimulator(game, p);
        int score = sim.simulateSpellAbility(callTheScionsSA).value;
        assertTrue(score >  0);
        Game simGame = sim.getSimulatedGameState();

        Card scion = findCardWithName(simGame, "Eldrazi Scion");
        assertNotNull(scion);
        assertEquals(1, scion.getNetPower());
        assertEquals(1, scion.getNetToughness());
        assertTrue(scion.isSick());
        assertNotNull(findSAWithPrefix(scion, "Sacrifice CARDNAME: Add {C} to your mana pool."));

        GameCopier copier = new GameCopier(simGame);
        Game copy = copier.makeCopy();
        Card scionCopy = findCardWithName(copy, "Eldrazi Scion");
        assertNotNull(scionCopy);
        assertEquals(1, scionCopy.getNetPower());
        assertEquals(1, scionCopy.getNetToughness());
        assertTrue(scionCopy.isSick());
        assertNotNull(findSAWithPrefix(scionCopy, "Sacrifice CARDNAME: Add {C} to your mana pool."));
    }

    public void testMarkedDamage() {
        // Marked damage is important, as it's used during the AI declare attackers logic
        // which affects game state score - since P/T boosts are evaluated differently for
        // creatures participating in combat.

        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        String giantCardName = "Hill Giant";
        Card giant = addCard(giantCardName, p);
        addCard("Mountain", p);
        Card shockCard = addCardToZone("Shock", p, ZoneType.Hand);
        SpellAbility shockSA = shockCard.getFirstSpellAbility();

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);
        
        assertEquals(3, giant.getNetPower());
        assertEquals(3, giant.getNetToughness());
        assertEquals(0, giant.getDamage());
        
        GameSimulator sim = createSimulator(game, p);
        shockSA.setTargetCard(giant);
        sim.simulateSpellAbility(shockSA);
        Game simGame = sim.getSimulatedGameState();
        Card simGiant = findCardWithName(simGame, giantCardName);
        assertEquals(2, simGiant.getDamage());
        
        GameCopier copier = new GameCopier(simGame);
        Game copy = copier.makeCopy();
        Card giantCopy = findCardWithName(copy, giantCardName);
        assertEquals(2, giantCopy.getDamage());
    }
    
    public void testLifelinkDamageSpell() {
        Game game = initAndCreateGame();
        Player p1 = game.getPlayers().get(0);
        Player p2 = game.getPlayers().get(1);

        String kalitasName = "Kalitas, Traitor of Ghet";
        String pridemateName = "Ajani's Pridemate";
        String indestructibilityName = "Indestructibility";
        String ignitionName = "Chandra's Ignition";
        String broodName = "Brood Monitor";

        // enough to cast Chandra's Ignition
        for (int i = 0; i < 5; ++i) {
            addCard("Mountain", p1);
        }

        Card kalitas = addCard(kalitasName, p1);
        Card pridemate = addCard(pridemateName, p1);
        Card indestructibility = addCard(indestructibilityName, p1);

        indestructibility.enchantEntity(pridemate);

        Card ignition = addCardToZone(ignitionName, p1, ZoneType.Hand);
        SpellAbility ignitionSA = ignition.getFirstSpellAbility();

        addCard(broodName, p2);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p1);
        game.getAction().checkStateEffects(true);

        GameSimulator sim = createSimulator(game, p1);
        ignitionSA.setTargetCard(kalitas);
        sim.simulateSpellAbility(ignitionSA);
        Game simGame = sim.getSimulatedGameState();
        Card simKalitas = findCardWithName(simGame, kalitasName);
        Card simPridemate = findCardWithName(simGame, pridemateName);
        Card simBrood = findCardWithName(simGame, broodName);

        // because it was destroyed
        assertNull(simBrood);

        assertEquals(0, simKalitas.getDamage());
        assertEquals(3, simPridemate.getDamage());

        // only triggered once
        assertTrue(simPridemate.hasCounters());
        assertEquals(1, simPridemate.getCounters(CounterType.P1P1));
        assertEquals(1, simPridemate.getToughnessBonusFromCounters());
        assertEquals(1, simPridemate.getPowerBonusFromCounters());

        // 3 times 3 damage with life gain = 9 + 20 = 29
        assertEquals(29, simGame.getPlayers().get(0).getLife());
        assertEquals(17, simGame.getPlayers().get(1).getLife());
    }

    public void testLifelinkDamageSpellMultiplier() {
        Game game = initAndCreateGame();
        Player p1 = game.getPlayers().get(0);
        Player p2 = game.getPlayers().get(1);

        String kalitasName = "Kalitas, Traitor of Ghet";
        String pridemateName = "Ajani's Pridemate";
        String giselaName = "Gisela, Blade of Goldnight";
        String ignitionName = "Chandra's Ignition";
        String broodName = "Brood Monitor";

        // enough to cast Chandra's Ignition
        for (int i = 0; i < 5; ++i) {
            addCard("Mountain", p1);
        }

        Card kalitas = addCard(kalitasName, p1);
        addCard(pridemateName, p1);
        addCard(giselaName, p1);

        Card ignition = addCardToZone(ignitionName, p1, ZoneType.Hand);
        SpellAbility ignitionSA = ignition.getFirstSpellAbility();

        addCard(broodName, p2);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p1);
        game.getAction().checkStateEffects(true);

        GameSimulator sim = createSimulator(game, p1);
        ignitionSA.setTargetCard(kalitas);
        sim.simulateSpellAbility(ignitionSA);
        Game simGame = sim.getSimulatedGameState();
        Card simKalitas = findCardWithName(simGame, kalitasName);
        Card simPridemate = findCardWithName(simGame, pridemateName);
        Card simGisela = findCardWithName(simGame, giselaName);
        Card simBrood = findCardWithName(simGame, broodName);

        // because it was destroyed
        assertNull(simBrood);

        assertEquals(0, simKalitas.getDamage());
        // 2 of the 3 are prevented
        assertEquals(1, simPridemate.getDamage());
        assertEquals(1, simGisela.getDamage());

        // only triggered once
        assertTrue(simPridemate.hasCounters());
        assertEquals(1, simPridemate.getCounters(CounterType.P1P1));
        assertEquals(1, simPridemate.getToughnessBonusFromCounters());
        assertEquals(1, simPridemate.getPowerBonusFromCounters());

        // 2 times 3 / 2 rounded down = 2 * 1 = 2
        // 2 times 3 * 2 = 12
        assertEquals(34, simGame.getPlayers().get(0).getLife());
        assertEquals(14, simGame.getPlayers().get(1).getLife());
    }

    public void testLifelinkDamageSpellRedirected() {
        Game game = initAndCreateGame();
        Player p1 = game.getPlayers().get(0);
        Player p2 = game.getPlayers().get(1);

        String kalitasName = "Kalitas, Traitor of Ghet";
        String pridemateName = "Ajani's Pridemate";
        String indestructibilityName = "Indestructibility";
        String ignitionName = "Chandra's Ignition";
        String broodName = "Brood Monitor";
        String palisadeName = "Palisade Giant";

        // enough to cast Chandra's Ignition
        for (int i = 0; i < 5; ++i) {
            addCard("Mountain", p1);
        }

        Card kalitas = addCard(kalitasName, p1);
        Card pridemate = addCard(pridemateName, p1);
        Card indestructibility = addCard(indestructibilityName, p1);

        indestructibility.enchantEntity(pridemate);

        Card ignition = addCardToZone(ignitionName, p1, ZoneType.Hand);
        SpellAbility ignitionSA = ignition.getFirstSpellAbility();

        addCard(broodName, p2);
        addCard(palisadeName, p2);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p1);
        game.getAction().checkStateEffects(true);

        GameSimulator sim = createSimulator(game, p1);
        ignitionSA.setTargetCard(kalitas);
        sim.simulateSpellAbility(ignitionSA);
        Game simGame = sim.getSimulatedGameState();
        Card simKalitas = findCardWithName(simGame, kalitasName);
        Card simPridemate = findCardWithName(simGame, pridemateName);
        Card simBrood = findCardWithName(simGame, broodName);
        Card simPalisade = findCardWithName(simGame, palisadeName);

        // not destroyed because damage redirected
        assertNotNull(simBrood);
        assertEquals(0, simBrood.getDamage());

        //destoryed because of to much redirected damage
        assertNull(simPalisade);

        assertEquals(0, simKalitas.getDamage());
        assertEquals(3, simPridemate.getDamage());

        // only triggered once
        assertTrue(simPridemate.hasCounters());
        assertEquals(1, simPridemate.getCounters(CounterType.P1P1));
        assertEquals(1, simPridemate.getToughnessBonusFromCounters());
        assertEquals(1, simPridemate.getPowerBonusFromCounters());

        // 4 times 3 damage with life gain = 12 + 20 = 32
        assertEquals(32, simGame.getPlayers().get(0).getLife());
        assertEquals(20, simGame.getPlayers().get(1).getLife());
    }

    public void testLifelinkDamageSpellMultipleDamage() {
        Game game = initAndCreateGame();
        Player p1 = game.getPlayers().get(0);
        Player p2 = game.getPlayers().get(1);

        String soulfireName = "Soulfire Grand Master";
        String pridemateName = "Ajani's Pridemate";
        String coneName = "Cone of Flame";

        String bearCardName = "Runeclaw Bear";
        String giantCardName = "Hill Giant";

        String tormentName = "Everlasting Torment";
        String meliraName = "Melira, Sylvok Outcast";

        // enough to cast Cone of Flame
        for (int i = 0; i < 5; ++i) {
            addCard("Mountain", p1);
        }

        addCard(soulfireName, p1);
        addCard(pridemateName, p1);

        Card bearCard = addCard(bearCardName, p2);
        Card giantCard = addCard(giantCardName, p2);

        Card cone = addCardToZone(coneName, p1, ZoneType.Hand);
        SpellAbility coneSA = cone.getFirstSpellAbility();

        coneSA.setTargetCard(bearCard); // one damage to bear
        coneSA.getSubAbility().setTargetCard(giantCard); // two damage to giant
        coneSA.getSubAbility().getSubAbility().getTargets().add(p2); // three damage to player

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p1);
        game.getAction().checkStateEffects(true);

        GameSimulator sim = createSimulator(game, p1);

        sim.simulateSpellAbility(coneSA);
        Game simGame = sim.getSimulatedGameState();
        Card simBear = findCardWithName(simGame, bearCardName);
        Card simGiant = findCardWithName(simGame, giantCardName);
        Card simPridemate = findCardWithName(simGame, pridemateName);

        // spell deals multiple damages to multiple targets, each of them causes lifegain
        assertNotNull(simPridemate);
        assertTrue(simPridemate.hasCounters());
        assertEquals(3, simPridemate.getCounters(CounterType.P1P1));
        assertEquals(3, simPridemate.getToughnessBonusFromCounters());
        assertEquals(3, simPridemate.getPowerBonusFromCounters());

        assertNotNull(simBear);
        assertEquals(1, simBear.getDamage());

        assertNotNull(simGiant);
        assertEquals(2, simGiant.getDamage());

        // 1 + 2 + 3 lifegain
        assertEquals(26, simGame.getPlayers().get(0).getLife());
        assertEquals(17, simGame.getPlayers().get(1).getLife());
        
        // second pard with Everlasting Torment
        addCard(tormentName, p2);

        GameSimulator sim2 = createSimulator(game, p1);

        sim2.simulateSpellAbility(coneSA);
        Game simGame2 = sim2.getSimulatedGameState();
        Card simBear2 = findCardWithName(simGame2, bearCardName);
        Card simGiant2 = findCardWithName(simGame2, giantCardName);
        Card simPridemate2 = findCardWithName(simGame2, pridemateName);

        // no Lifegain because of Everlasting Torment
        assertNotNull(simPridemate2);
        assertFalse(simPridemate2.hasCounters());
        assertEquals(0, simPridemate2.getCounters(CounterType.P1P1));
        assertEquals(0, simPridemate2.getToughnessBonusFromCounters());
        assertEquals(0, simPridemate2.getPowerBonusFromCounters());

        assertNotNull(simBear2);
        assertEquals(0, simBear2.getDamage());
        assertTrue(simBear2.hasCounters());
        assertEquals(1, simBear2.getCounters(CounterType.M1M1));
        assertEquals(-1, simBear2.getToughnessBonusFromCounters());
        assertEquals(-1, simBear2.getPowerBonusFromCounters());

        assertNotNull(simGiant2);
        assertEquals(0, simGiant2.getDamage());
        assertTrue(simGiant2.hasCounters());
        assertEquals(2, simGiant2.getCounters(CounterType.M1M1));
        assertEquals(-2, simGiant2.getToughnessBonusFromCounters());
        assertEquals(-2, simGiant2.getPowerBonusFromCounters());

        // no life gain
        assertEquals(20, simGame2.getPlayers().get(0).getLife());
        assertEquals(17, simGame2.getPlayers().get(1).getLife());

        // third pard with Melira prevents wither 
        addCard(meliraName, p2);

        GameSimulator sim3 = createSimulator(game, p1);

        sim3.simulateSpellAbility(coneSA);
        Game simGame3 = sim3.getSimulatedGameState();
        Card simBear3 = findCardWithName(simGame3, bearCardName);
        Card simGiant3 = findCardWithName(simGame3, giantCardName);
        Card simPridemate3 = findCardWithName(simGame3, pridemateName);

        // no Lifegain because of Everlasting Torment
        assertNotNull(simPridemate3);
        assertFalse(simPridemate3.hasCounters());
        assertEquals(0, simPridemate3.getCounters(CounterType.P1P1));
        assertEquals(0, simPridemate3.getToughnessBonusFromCounters());
        assertEquals(0, simPridemate3.getPowerBonusFromCounters());

        assertNotNull(simBear3);
        assertEquals(0, simBear3.getDamage());
        assertFalse(simBear3.hasCounters());
        assertEquals(0, simBear3.getCounters(CounterType.M1M1));
        assertEquals(0, simBear3.getToughnessBonusFromCounters());
        assertEquals(0, simBear3.getPowerBonusFromCounters());

        assertNotNull(simGiant3);
        assertEquals(0, simGiant3.getDamage());
        assertFalse(simGiant3.hasCounters());
        assertEquals(0, simGiant3.getCounters(CounterType.M1M1));
        assertEquals(0, simGiant3.getToughnessBonusFromCounters());
        assertEquals(0, simGiant3.getPowerBonusFromCounters());

        // no life gain
        assertEquals(20, simGame2.getPlayers().get(0).getLife());
        assertEquals(17, simGame2.getPlayers().get(1).getLife());
    }

    public void testTransform() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Swamp", p);
        addCard("Swamp", p);
        addCard("Swamp", p);
        String lilianaCardName = "Liliana, Heretical Healer";
        String lilianaPWName = "Liliana, Defiant Necromancer";
        Card lilianaInPlay = addCard(lilianaCardName, p);
        Card lilianaInHand = addCardToZone(lilianaCardName, p, ZoneType.Hand);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);
        
        assertTrue(lilianaInPlay.isCreature());
        assertEquals(2, lilianaInPlay.getNetPower());
        assertEquals(3, lilianaInPlay.getNetToughness());
        
        SpellAbility playLiliana = lilianaInHand.getSpellAbilities().get(0);
        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(playLiliana);
        Game simGame = sim.getSimulatedGameState();
        assertNull(findCardWithName(simGame, lilianaCardName));
        Card lilianaPW = findCardWithName(simGame, lilianaPWName);
        assertNotNull(lilianaPW);
        assertTrue(lilianaPW.isPlaneswalker());
        assertEquals(3, lilianaPW.getCurrentLoyalty());

        GameCopier copier = new GameCopier(simGame);
        Game copy = copier.makeCopy();
        Card lilianaPWCopy = findCardWithName(copy, lilianaPWName);
        assertNotNull(lilianaPWCopy);
        assertTrue(lilianaPWCopy.isPlaneswalker());
        assertEquals(3, lilianaPWCopy.getCurrentLoyalty());
    }

    public void testEnergy() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Island", p);
        String turtleCardName = "Thriving Turtle";
        Card turtleCard = addCardToZone(turtleCardName, p, ZoneType.Hand);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);
        assertEquals(0, p.getCounters(CounterType.ENERGY));

        SpellAbility playTurtle = turtleCard.getSpellAbilities().get(0);
        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(playTurtle);
        Game simGame = sim.getSimulatedGameState();
        Player simP = simGame.getPlayers().get(1);
        assertEquals(2, simP.getCounters(CounterType.ENERGY));
        
        GameCopier copier = new GameCopier(simGame);
        Game copy = copier.makeCopy();
        Player copyP = copy.getPlayers().get(1);
        assertEquals(2, copyP.getCounters(CounterType.ENERGY));
    }

    public void testFloatingMana() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Swamp", p);
        Card darkRitualCard = addCardToZone("Dark Ritual", p, ZoneType.Hand);
        Card darkConfidantCard = addCardToZone("Dark Confidant", p, ZoneType.Hand);
        Card deathriteCard = addCardToZone("Deathrite Shaman", p, ZoneType.Hand);

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);
        assertTrue(p.getManaPool().isEmpty());

        SpellAbility playRitual = darkRitualCard.getSpellAbilities().get(0);
        GameSimulator sim = createSimulator(game, p);
        sim.simulateSpellAbility(playRitual);
        Game simGame = sim.getSimulatedGameState();
        Player simP = simGame.getPlayers().get(1);
        assertEquals(3, simP.getManaPool().totalMana());
        assertEquals(3, simP.getManaPool().getAmountOfColor(MagicColor.BLACK));

        Card darkConfidantCard2 = (Card) sim.getGameCopier().find(darkConfidantCard);
        SpellAbility playDarkConfidant2 = darkConfidantCard2.getSpellAbilities().get(0);
        Card deathriteCard2 = (Card) sim.getGameCopier().find(deathriteCard);
        
        GameSimulator sim2 = createSimulator(simGame, simP);
        sim2.simulateSpellAbility(playDarkConfidant2);
        Game sim2Game = sim2.getSimulatedGameState();
        Player sim2P = sim2Game.getPlayers().get(1);
        assertEquals(1, sim2P.getManaPool().totalMana());
        assertEquals(1, sim2P.getManaPool().getAmountOfColor(MagicColor.BLACK));

        Card deathriteCard3 = (Card) sim2.getGameCopier().find(deathriteCard2);
        SpellAbility playDeathriteCard3 = deathriteCard3.getSpellAbilities().get(0);

        GameSimulator sim3 = createSimulator(sim2Game, sim2P);
        sim3.simulateSpellAbility(playDeathriteCard3);
        Game sim3Game = sim3.getSimulatedGameState();
        Player sim3P = sim3Game.getPlayers().get(1);
        assertEquals(0, sim3P.getManaPool().totalMana());
        assertEquals(0, sim3P.getManaPool().getAmountOfColor(MagicColor.BLACK));
    }

    public void testEnKor() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        
        String soulfireName = "Soulfire Grand Master";
        String pridemateName = "Ajani's Pridemate";
        
        String enKorName = "Spirit en-Kor";
        String bearName = "Runeclaw Bear";
        String shockName = "Shock";
        
        addCard("Mountain", p);

        addCard(soulfireName, p);
        addCard(pridemateName, p);

        Card shockCard = addCardToZone(shockName, p, ZoneType.Hand);
        
        Card enKor = addCard(enKorName, p);
        
        SpellAbility enKorSA = findSAWithPrefix(enKor, "{0}:");
        
        Card bear = addCard(bearName, p);
        
        SpellAbility shockSA = shockCard.getFirstSpellAbility();

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);
        
        assertEquals(2, enKor.getNetPower());
        assertEquals(2, enKor.getNetToughness());
        assertEquals(0, enKor.getDamage());
        
        assertEquals(2, bear.getNetPower());
        assertEquals(2, bear.getNetToughness());
        assertEquals(0, bear.getDamage());
        
        GameSimulator sim = createSimulator(game, p);
        enKorSA.setTargetCard(bear);
        shockSA.setTargetCard(enKor);
        sim.simulateSpellAbility(enKorSA);
        sim.simulateSpellAbility(shockSA);
        Game simGame = sim.getSimulatedGameState();
        Card simEnKor = findCardWithName(simGame, enKorName);
        Card simBear = findCardWithName(simGame, bearName);
        
        assertNotNull(simEnKor);
        assertEquals(1, simEnKor.getDamage());

        assertNotNull(simBear);
        assertEquals(1, simBear.getDamage());
        
        Card simPridemate = findCardWithName(simGame, pridemateName);

        // only triggered once
        assertTrue(simPridemate.hasCounters());
        assertEquals(1, simPridemate.getCounters(CounterType.P1P1));
        assertEquals(1, simPridemate.getToughnessBonusFromCounters());
        assertEquals(1, simPridemate.getPowerBonusFromCounters());

        assertEquals(22, simGame.getPlayers().get(0).getLife());
    }
    
    public void testRazia() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);

        String soulfireName = "Soulfire Grand Master";
        String pridemateName = "Ajani's Pridemate";

        String raziaName = "Razia, Boros Archangel";
        String bearName = "Runeclaw Bear";
        String greetingName = "Alchemist's Greeting";

        for (int i = 0; i < 5; ++i) {
            addCard("Mountain", p);
        }

        addCard(soulfireName, p);
        addCard(pridemateName, p);

        Card greetingCard = addCardToZone(greetingName, p, ZoneType.Hand);

        Card razia = addCard(raziaName, p);

        SpellAbility preventSA = findSAWithPrefix(razia, "{T}:");

        Card bear = addCard(bearName, p);

        SpellAbility greetingSA = greetingCard.getFirstSpellAbility();

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        assertEquals(0, razia.getDamage());

        assertEquals(2, bear.getNetPower());
        assertEquals(2, bear.getNetToughness());
        assertEquals(0, bear.getDamage());

        GameSimulator sim = createSimulator(game, p);
        preventSA.setTargetCard(razia);
        preventSA.getSubAbility().setTargetCard(bear);
        greetingSA.setTargetCard(razia);
        sim.simulateSpellAbility(preventSA);
        sim.simulateSpellAbility(greetingSA);
        Game simGame = sim.getSimulatedGameState();
        Card simRazia = findCardWithName(simGame, raziaName);
        Card simBear = findCardWithName(simGame, bearName);

        assertNotNull(simRazia);
        assertEquals(1, simRazia.getDamage());

        // bear destroyed
        assertNull(simBear);

        Card simPridemate = findCardWithName(simGame, pridemateName);

        // only triggered once
        assertTrue(simPridemate.hasCounters());
        assertEquals(1, simPridemate.getCounters(CounterType.P1P1));
        assertEquals(1, simPridemate.getToughnessBonusFromCounters());
        assertEquals(1, simPridemate.getPowerBonusFromCounters());

        assertEquals(24, simGame.getPlayers().get(0).getLife());
    }

    public void testRazia2() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        
        String soulfireName = "Soulfire Grand Master";
        String pridemateName = "Ajani's Pridemate";
        
        String raziaName = "Razia, Boros Archangel";
        String elementalName = "Air Elemental";
        String shockName = "Shock";
        
        for (int i = 0; i < 2; ++i) {
            addCard("Mountain", p);
        }

        addCard(soulfireName, p);
        addCard(pridemateName, p);

        Card shockCard1 = addCardToZone(shockName, p, ZoneType.Hand);
        Card shockCard2 = addCardToZone(shockName, p, ZoneType.Hand);
        
        Card razia = addCard(raziaName, p);
        
        SpellAbility preventSA = findSAWithPrefix(razia, "{T}:");
        
        Card elemental = addCard(elementalName, p);
        
        SpellAbility shockSA1 = shockCard1.getFirstSpellAbility();
        SpellAbility shockSA2 = shockCard2.getFirstSpellAbility();

        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        game.getAction().checkStateEffects(true);

        assertEquals(0, razia.getDamage());
        
        assertEquals(4, elemental.getNetPower());
        assertEquals(4, elemental.getNetToughness());
        assertEquals(0, elemental.getDamage());
        
        GameSimulator sim = createSimulator(game, p);
        preventSA.setTargetCard(razia);
        preventSA.getSubAbility().setTargetCard(elemental);
        shockSA1.setTargetCard(razia);
        shockSA2.setTargetCard(razia);
        sim.simulateSpellAbility(preventSA);
        sim.simulateSpellAbility(shockSA1);
        sim.simulateSpellAbility(shockSA2);
        Game simGame = sim.getSimulatedGameState();
        Card simRazia = findCardWithName(simGame, raziaName);
        Card simElemental = findCardWithName(simGame, elementalName);
        
        assertNotNull(simRazia);
        assertEquals(1, simRazia.getDamage());

        // elemental not destroyed
        assertNotNull(simElemental);
        assertEquals(3, simElemental.getDamage());
        
        Card simPridemate = findCardWithName(simGame, pridemateName);

        // only triggered twice
        assertTrue(simPridemate.hasCounters());
        assertEquals(2, simPridemate.getCounters(CounterType.P1P1));
        assertEquals(2, simPridemate.getToughnessBonusFromCounters());
        assertEquals(2, simPridemate.getPowerBonusFromCounters());

        assertEquals(24, simGame.getPlayers().get(0).getLife());
    }

    public void testMassRemovalVsKalitas() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Player opp = game.getPlayers().get(1);

        addCardToZone("Kalitas, Traitor of Ghet", p, ZoneType.Battlefield);
        for (int i = 0; i < 4; i++) {
            addCardToZone("Plains", p, ZoneType.Battlefield);
        }

        for (int i = 0; i < 2; i++) {
            addCardToZone("Aboroth", opp, ZoneType.Battlefield);
        }

        Card wrathOfGod = addCardToZone("Wrath of God", p, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);

        SpellAbility wrathSA = wrathOfGod.getFirstSpellAbility();
        assertNotNull(wrathSA);

        GameSimulator sim = createSimulator(game, p);
        int score = sim.simulateSpellAbility(wrathSA).value;
        assertTrue(score > 0);
        Game simGame = sim.getSimulatedGameState();

        int numZombies = countCardsWithName(simGame, "Zombie");
        assertTrue(numZombies == 2);
    }

    public void testKalitasNumberOfTokens() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Player opp = game.getPlayers().get(1);

        addCardToZone("Kalitas, Traitor of Ghet", p, ZoneType.Battlefield);
        addCardToZone("Anointed Procession", p, ZoneType.Battlefield);
        addCardToZone("Swamp", p, ZoneType.Battlefield);
        for (int i = 0; i < 4; i++) {
            addCardToZone("Mountain", p, ZoneType.Battlefield);
        }

        Card goblin = addCardToZone("Raging Goblin", opp, ZoneType.Battlefield);
        Card goblin2 = addCardToZone("Raging Goblin", opp, ZoneType.Battlefield);

        // Fatal Push: should generate 2 tokens
        Card fatalPush = addCardToZone("Fatal Push", p, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, p);
        SpellAbility fatalPushSA = fatalPush.getFirstSpellAbility();
        assertNotNull(fatalPushSA);
        fatalPushSA.setTargetCard(goblin);

        // Electrify: should only generate 1 token
        Card electrify = addCardToZone("Electrify", p, ZoneType.Hand);
        SpellAbility electrifySA = electrify.getFirstSpellAbility();
        assertNotNull(electrifySA);
        electrifySA.setTargetCard(goblin2);

        GameSimulator sim = createSimulator(game, p);
        int score = sim.simulateSpellAbility(fatalPushSA).value;
        assertTrue(score > 0);
        assertTrue(countCardsWithName(sim.getSimulatedGameState(), "Zombie") == 2);

        score = sim.simulateSpellAbility(electrifySA).value;
        assertTrue(score > 0);
        assertTrue(countCardsWithName(sim.getSimulatedGameState(), "Zombie") == 3);
    }

    public void testPlayerXCount() {
        // If playerXCount is operational, then conditions that count something about the player (e.g.
        // cards in hand, life total) should work, similar to the Bloodghast "Haste" condition.
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        Player opp = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        Card bloodghast = addCardToZone("Bloodghast", p, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);

        assert(!bloodghast.hasKeyword("Haste"));

        opp.setLife(5, null);
        game.getAction().checkStateEffects(true);

        assert(bloodghast.hasKeyword("Haste"));
    }

    public void testDeathsShadow() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        addCardToZone("Platinum Angel", p, ZoneType.Battlefield);
        Card deathsShadow = addCardToZone("Death's Shadow", p, ZoneType.Battlefield);

        p.setLife(1, null);
        game.getAction().checkStateEffects(true);
        assert(deathsShadow.getNetPower() == 12);

        p.setLife(-1, null);
        game.getAction().checkStateEffects(true);
        assert(deathsShadow.getNetPower() == 13); // on negative life, should always be 13/13
    }
}
