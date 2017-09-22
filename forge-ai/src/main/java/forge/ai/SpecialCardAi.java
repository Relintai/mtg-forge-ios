/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.ai;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.cost.CostPart;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerPredicates;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.zone.ZoneType;
import forge.util.Aggregates;
import forge.util.TextUtil;
import forge.util.maps.LinkedHashMapToAmount;
import forge.util.maps.MapToAmount;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Special logic for individual cards
 * 
 * Specific methods for each card that requires special handling are stored in inner classes 
 * Each class should have a name based on the name of the card and ideally preceded with a 
 * single-line comment with the full English card name to make searching for them easier.
 * 
 * Class methods should return "true" if they are successful and have completed their task in full,
 * otherwise should return "false" to signal that the AI should not use the card under current
 * circumstances. A good convention to follow is to call the method "consider" if it's the only
 * method necessary, or considerXXXX if several methods do different tasks, and use at least two
 * mandatory parameters (Player ai, SpellAbility sa, in this order) and, if necessary, additional
 * parameters later. Methods that perform utility tasks and return a certain value for further
 * processing should be called getXXXX. If they take Player and SpellAbility parameters, it is
 * good practice to put them in the same order as for considerXXXX methods (Player ai, SpellAbility
 * sa, followed by any additional parameters necessary).
 * 
 * If this class ends up being busy, consider splitting it into individual classes, each in its
 * own file, inside its own package, for example, forge.ai.cards.
 */
public class SpecialCardAi {

    // Black Lotus and Lotus Bloom
    public static class BlackLotus {
        public static boolean consider(final Player ai, final SpellAbility sa, final ManaCostBeingPaid cost) {
            CardCollection manaSources = ComputerUtilMana.getAvailableManaSources(ai, true);
            int numManaSrcs = manaSources.size();

            CardCollection allCards = CardLists.filter(ai.getAllCards(), Arrays.asList(CardPredicates.Presets.NON_TOKEN,
                    Predicates.not(CardPredicates.Presets.LANDS), CardPredicates.isOwner(ai)));

            int numHighCMC = CardLists.count(allCards, CardPredicates.greaterCMC(5));
            int numLowCMC = CardLists.count(allCards, CardPredicates.lessCMC(3));

            boolean isLowCMCDeck = numHighCMC <= 6 && numLowCMC >= 25;
            
            int minCMC = isLowCMCDeck ? 3 : 4; // probably not worth wasting a lotus on a low-CMC spell (<4 CMC), except in low-CMC decks, where 3 CMC may be fine
            int paidCMC = cost.getConvertedManaCost();
            if (paidCMC < minCMC) {
                if (paidCMC == 3 && numManaSrcs < 3) {
                    // if it's a CMC 3 spell and we're more than one mana source short for it, might be worth it anyway
                    return true;
                } 

                return false;
            }

            return true;
        }
    }

    // Bonds of Faith
    public static class BondsOfFaith {
        public static Card getBestAttachTarget(final Player ai, final SpellAbility sa, final List<Card> list) {
            Card chosen = null;
            
            List<Card> aiHumans = CardLists.filter(list, new Predicate<Card>() {
                @Override
                public boolean apply(final Card c) {
                    // Don't buff opponent's humans
                    if (!c.getController().equals(ai)) {
                        return false;
                    }
                    return c.getType().hasCreatureType("Human");
                }
            });
            List<Card> oppNonHumans = CardLists.filter(list, new Predicate<Card>() {
                @Override
                public boolean apply(final Card c) {
                    // Don't debuff AI's own non-humans
                    if (c.getController().equals(ai)) {
                        return false;
                    }
                    return !c.getType().hasCreatureType("Human") && !ComputerUtilCard.isUselessCreature(ai, c);
                }
            });

            if (!aiHumans.isEmpty() && !oppNonHumans.isEmpty()) {
                Card bestAi = ComputerUtilCard.getBestCreatureAI(aiHumans);
                Card bestOpp = ComputerUtilCard.getBestCreatureAI(oppNonHumans);
                chosen = ComputerUtilCard.evaluateCreature(bestAi) > ComputerUtilCard.evaluateCreature(bestOpp) ? bestAi : bestOpp;
            } else if (!aiHumans.isEmpty()) {
                chosen = ComputerUtilCard.getBestCreatureAI(aiHumans);
            } else if (!oppNonHumans.isEmpty()) {
                chosen = ComputerUtilCard.getBestCreatureAI(oppNonHumans);
            }
            
            return chosen;
        }
    }

    // Chain of Acid
    public static class ChainOfAcid {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            List<Card> AiLandsOnly = CardLists.filter(ai.getCardsIn(ZoneType.Battlefield),
                    CardPredicates.Presets.LANDS);
            List<Card> OppPerms = CardLists.filter(ai.getOpponents().getCardsIn(ZoneType.Battlefield),
                    Predicates.not(CardPredicates.Presets.CREATURES));

            // TODO: improve this logic (currently the AI has difficulty evaluating non-creature permanents,
            // which it can only distinguish by their CMC, considering >CMC higher value).
            // Currently ensures that the AI will still have lands provided that the human player goes to
            // destroy all the AI's lands in order (to avoid manalock).
            return !OppPerms.isEmpty() && AiLandsOnly.size() > OppPerms.size() + 2;
        }
    }

    // Chain of Smog
    public static class ChainOfSmog {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            if (ai.getCardsIn(ZoneType.Hand).isEmpty()) {
                // to avoid failure to add to stack, provide a legal target opponent first (choosing random at this point)
                // TODO: this makes the AI target opponents with 0 cards in hand, but bailing from here causes a
                // "failed to add to stack" error, needs investigation and improvement.
                Player targOpp = Aggregates.random(ai.getOpponents());

                for (Player opp : ai.getOpponents()) {
                    if (!opp.getCardsIn(ZoneType.Hand).isEmpty()) {
                        targOpp = opp;
                        break;
                    }
                }

                sa.getParent().resetTargets();
                sa.getParent().getTargets().add(targOpp);
                return true;
            }

            return false;
        }
    }

    // Cursed Scroll
    public static class CursedScroll {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            CardCollectionView hand = ai.getCardsIn(ZoneType.Hand);
            if (hand.isEmpty()) {
                return false;
            }

            // For now, see if all cards in hand have the same name, and then proceed if true
            return CardLists.filter(hand, CardPredicates.nameEquals(hand.getFirst().getName())).size() == hand.size();
        }

        public static String chooseCard(final Player ai, final SpellAbility sa) {
            int maxCount = 0;
            Card best = null;
            CardCollectionView hand = ai.getCardsIn(ZoneType.Hand);

            for (Card c : ai.getCardsIn(ZoneType.Hand)) {
                int count = CardLists.filter(hand, CardPredicates.nameEquals(c.getName())).size();
                if (count > maxCount) {
                    maxCount = count;
                    best = c;
                }
            }

            return best.getName();
        }
    }

    // Deathgorge Scavenger
    public static class DeathgorgeScavenger {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            Card worstCreat = ComputerUtilCard.getWorstAI(CardLists.filter(ai.getOpponents().getCardsIn(ZoneType.Graveyard), CardPredicates.Presets.CREATURES));
            Card worstNonCreat = ComputerUtilCard.getWorstAI(CardLists.filter(ai.getOpponents().getCardsIn(ZoneType.Graveyard), Predicates.not(CardPredicates.Presets.CREATURES)));
            if (worstCreat == null) {
                worstCreat = ComputerUtilCard.getWorstAI(CardLists.filter(ai.getCardsIn(ZoneType.Graveyard), CardPredicates.Presets.CREATURES));
            }
            if (worstNonCreat == null) {
                worstNonCreat = ComputerUtilCard.getWorstAI(CardLists.filter(ai.getCardsIn(ZoneType.Graveyard), Predicates.not(CardPredicates.Presets.CREATURES)));
            }

            sa.resetTargets();
            if (worstCreat != null && ai.getLife() <= ai.getStartingLife() / 4) {
                sa.getTargets().add(worstCreat);
            } else if (worstNonCreat != null && ai.getGame().getCombat() != null
                    && ai.getGame().getCombat().isAttacking(sa.getHostCard())) {
                sa.getTargets().add(worstNonCreat);
            } else if (worstCreat != null) {
                sa.getTargets().add(worstCreat);
            }

            return sa.getTargets().getNumTargeted() > 0;
        }
    }

    // Desecration Demon
    public static class DesecrationDemon {
        private static final int demonSacThreshold = Integer.MAX_VALUE; // if we're in dire conditions, sac everything from worst to best hoping to find an answer

        public static boolean considerSacrificingCreature(final Player ai, final SpellAbility sa) {
            CardCollection flyingCreatures = CardLists.filter(ai.getCardsIn(ZoneType.Battlefield), Predicates.and(CardPredicates.Presets.UNTAPPED, Predicates.or(CardPredicates.hasKeyword("Flying"), CardPredicates.hasKeyword("Reach"))));
            boolean hasUsefulBlocker = false;

            for (Card c : flyingCreatures) {
                if (!ComputerUtilCard.isUselessCreature(ai, c)) {
                    hasUsefulBlocker = true;
                }
            }

            if (ai.getLife() <= sa.getHostCard().getNetPower() && !hasUsefulBlocker) {
                return true;
            } else {
                return false;
            }
        }
        
        public static int getSacThreshold() {
            return demonSacThreshold;
        }
    }

    // Donate
    public static class Donate {
        public static boolean considerTargetingOpponent(final Player ai, final SpellAbility sa) {
            final Card donateTarget = ComputerUtil.getCardPreference(ai, sa.getHostCard(), "DonateMe", CardLists.filter(
                    ai.getCardsIn(ZoneType.Battlefield).threadSafeIterable(), CardPredicates.hasSVar("DonateMe")));
            if (donateTarget != null) {
                // first filter for opponents which can be targeted by SA
                final Iterable<Player> oppList = Iterables.filter(ai.getOpponents(),
                        PlayerPredicates.isTargetableBy(sa));

                // All opponents have hexproof or something like that
                if (Iterables.isEmpty(oppList)) {
                    return false;
                }

                // filter for player who does not have donate target already
                Iterable<Player> oppTarget = Iterables.filter(oppList,
                        PlayerPredicates.isNotCardInPlay(donateTarget.getName()));
                // fall back to previous list
                if (Iterables.isEmpty(oppTarget)) {
                    oppTarget = oppList;
                }

                // select player with less lands on the field (helpful for Illusions of Grandeur and probably Pacts too)
                Player opp = Collections.min(Lists.newArrayList(oppTarget),
                        PlayerPredicates.compareByZoneSize(ZoneType.Battlefield, CardPredicates.Presets.LANDS));

                if (opp != null) {
                    sa.resetTargets();
                    sa.getTargets().add(opp);
                    return true;
                }
                return true;
            }
            // No targets found to donate, so do nothing.
            return false;
        }

        public static boolean considerDonatingPermanent(final Player ai, final SpellAbility sa) {
            Card donateTarget = ComputerUtil.getCardPreference(ai, sa.getHostCard(), "DonateMe", CardLists.filter(ai.getCardsIn(ZoneType.Battlefield).threadSafeIterable(), CardPredicates.hasSVar("DonateMe")));
            if (donateTarget != null) {
                sa.resetTargets();
                sa.getTargets().add(donateTarget);
                return true;
            }

            // Should never get here because targetOpponent, called before targetPermanentToDonate, should already have made the AI bail
            System.err.println("Warning: Donate AI failed at SpecialCardAi.Donate#targetPermanentToDonate despite successfully targeting an opponent first.");
            return false;
        }
    }

    // Electrostatic Pummeler
    public static class ElectrostaticPummeler {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            final Card source = sa.getHostCard();
            Game game = ai.getGame();
            Combat combat = game.getCombat();
            Pair<Integer, Integer> predictedPT = getPumpedPT(ai, source.getNetCombatDamage(), source.getNetToughness());

            // Try to save the Pummeler from death by pumping it if it's threatened with a damage spell
            if (ComputerUtil.predictThreatenedObjects(ai, null, true).contains(source)) {
                SpellAbility saTop = game.getStack().peekAbility();

                if (saTop.getApi() == ApiType.DealDamage || saTop.getApi() == ApiType.DamageAll) {
                    int dmg = AbilityUtils.calculateAmount(saTop.getHostCard(), saTop.getParam("NumDmg"), saTop);
                    if (source.getNetToughness() - source.getDamage() <= dmg && predictedPT.getRight() - source.getDamage() > dmg)
                    return true;
                }
            }

            // Do not activate if damage will be prevented
            if (source.staticDamagePrevention(predictedPT.getLeft(), source, true, true) == 0) {
                return false;
            }

            // Activate Electrostatic Pummeler's pump only as a combat trick
            if (game.getPhaseHandler().is(PhaseType.COMBAT_BEGIN)) {
                if (predictOverwhelmingDamage(ai, sa)) {
                    // We'll try to deal lethal trample/unblocked damage, so remember the card for attack
                    // and wait until declare blockers step.
                    AiCardMemory.rememberCard(ai, source, AiCardMemory.MemorySet.MANDATORY_ATTACKERS);
                    return false;
                }
            } else if (!game.getPhaseHandler().is(PhaseType.COMBAT_DECLARE_BLOCKERS)) {
                return false;
            }

            if (combat == null || !(combat.isAttacking(source) || combat.isBlocking(source))) {
                return false;
            }

            boolean isBlocking = combat.isBlocking(source);
            boolean cantDie = ComputerUtilCombat.attackerCantBeDestroyedInCombat(ai, source);

            CardCollection opposition = isBlocking ? combat.getAttackersBlockedBy(source) : combat.getBlockers(source);
            int oppP = Aggregates.sum(opposition, CardPredicates.Accessors.fnGetAttack);
            int oppT = Aggregates.sum(opposition, CardPredicates.Accessors.fnGetNetToughness);

            boolean oppHasFirstStrike = false;
            boolean oppCantDie = true;
            boolean unblocked = opposition.isEmpty();
            boolean canTrample = source.hasKeyword("Trample");

            if (!isBlocking && combat.getDefenderByAttacker(source) instanceof Card) {
                int loyalty = ((Card)combat.getDefenderByAttacker(source)).getCounters(CounterType.LOYALTY);
                int totalDamageToPW = 0;
                for (Card atk : (combat.getAttackersOf(combat.getDefenderByAttacker(source)))) {
                    if (combat.isUnblocked(atk)) {
                        totalDamageToPW += atk.getNetCombatDamage();
                    }
                }
                if (totalDamageToPW >= oppT + loyalty) {
                    // Already enough damage to take care of the planeswalker
                    return false;
                }
                if ((unblocked || canTrample) && predictedPT.getLeft() >= oppT + loyalty) {
                    // Can pump to kill the planeswalker, go for it
                    return true;
                }

            }

            for (Card c : opposition) {
                if (c.hasKeyword("First Strike") || c.hasKeyword("Double Strike")) {
                    oppHasFirstStrike = true;
                }
                if (!ComputerUtilCombat.attackerCantBeDestroyedInCombat(c.getController(), c)) {
                    oppCantDie = false;
                }
            }

            if (!isBlocking) {
                int oppLife = combat.getDefendingPlayerRelatedTo(source).getLife();
                if (((unblocked || canTrample) && (predictedPT.getLeft() - oppT > oppLife / 2))
                        || (canTrample && predictedPT.getLeft() - oppT > 0 && predictedPT.getRight() > oppP)) {
                    // We can deal a lot of damage (either a lot of damage directly to the opponent,
                    // or kill the blocker(s) and damage the opponent at the same time, so go for it
                    AiCardMemory.rememberCard(ai, source, AiCardMemory.MemorySet.MANDATORY_ATTACKERS);
                    return true;
                }
            }

            if (predictedPT.getRight() - source.getDamage() <= oppP && oppHasFirstStrike && !cantDie) {
                // Can't survive first strike or double strike, don't pump
                return false;
            }
            if (predictedPT.getLeft() < oppT && (!cantDie || predictedPT.getRight() - source.getDamage() <= oppP)) {
                // Can't pump enough to kill the blockers and survive, don't pump
                return false;
            }
            if (source.getNetCombatDamage() > oppT && source.getNetToughness() > oppP) {
                // Already enough to kill the blockers and survive, don't overpump
                return false;
            }
            if (oppCantDie && !source.hasKeyword("Trample") && !source.hasKeyword("Wither")
                    && !source.hasKeyword("Infect") && predictedPT.getLeft() <= oppT) {
                // Can't kill or cripple anyone, as well as can't Trample over, so don't pump
                return false;
            }

            // If we got here, it should be a favorable combat pump, resulting in at least one
            // opposing creature dying, and hopefully with the Pummeler surviving combat.
            return true;
        }

        public static boolean predictOverwhelmingDamage(final Player ai, final SpellAbility sa) {
            final Card source = sa.getHostCard();
            int oppLife = ai.getWeakestOpponent().getLife();
            CardCollection oppInPlay = ai.getWeakestOpponent().getCreaturesInPlay();
            CardCollection potentialBlockers = new CardCollection();

            for (Card b : oppInPlay) {
                if (CombatUtil.canBlock(sa.getHostCard(), b)) {
                    potentialBlockers.add(b);
                }
            }

            Pair<Integer, Integer> predictedPT = getPumpedPT(ai, source.getNetCombatDamage(), source.getNetToughness());
            int oppT = Aggregates.sum(potentialBlockers, CardPredicates.Accessors.fnGetNetToughness);

            if (potentialBlockers.isEmpty() || (sa.getHostCard().hasKeyword("Trample") && predictedPT.getLeft() - oppT >= oppLife)) {
                return true;
            }

            return false;
        }

        public static Pair<Integer, Integer> getPumpedPT(Player ai, int power, int toughness) {
            int energy = ai.getCounters(CounterType.ENERGY);
            if (energy > 0) {
                int numActivations = energy / 3;
                for (int i = 0; i < numActivations; i++) {
                    power *= 2;
                    toughness *= 2;
                }
            }

            return Pair.of(power, toughness);
        }
    }

    // Force of Will
    public static class ForceOfWill {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            CardCollection blueCards = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.isColor(MagicColor.BLUE));

            boolean isExileMode = false;
            for (CostPart c : sa.getPayCosts().getCostParts()) {
                if (c.toString().contains("Exile")) {
                    isExileMode = true; // the AI is trying to go for the "exile and pay life" alt cost
                    break;
                }
            }

            if (isExileMode) {
                if (blueCards.size() < 2) {
                    // Need to have something else in hand that is blue in addition to Force of Will itself,
                    // otherwise the AI will fail to play the card and the card will disappear from the pool
                    return false;
                } else if (CardLists.filter(blueCards, CardPredicates.lessCMC(3)).isEmpty()) {
                    // We probably need a low-CMC card to exile to it, exiling a higher CMC spell may be suboptimal
                    // since the AI does not prioritize/value cards vs. permission at the moment.
                    return false;
                }
            }

            return true;
        }
    }

    // Guilty Conscience
    public static class GuiltyConscience {
        public static Card getBestAttachTarget(final Player ai, final SpellAbility sa, final List<Card> list) {
            Card chosen = null;
            
            List<Card> aiStuffies = CardLists.filter(list, new Predicate<Card>() {
                @Override
                public boolean apply(final Card c) {
                    // Don't enchant creatures that can survive
                    if (!c.getController().equals(ai)) {
                        return false;
                    }
                    final String name = c.getName();
                    return name.equals("Stuffy Doll") || name.equals("Boros Reckoner") || name.equals("Spitemare");
                }
            });
            if (!aiStuffies.isEmpty()) {
                chosen = aiStuffies.get(0);
            } else {
                List<Card> creatures = CardLists.filterControlledBy(list, ai.getOpponents());
                creatures = CardLists.filter(creatures, new Predicate<Card>() {
                    @Override
                    public boolean apply(final Card c) {
                        // Don't enchant creatures that can survive
                        if (!c.canBeDestroyed() || c.getNetCombatDamage() < c.getNetToughness() || c.isEnchantedBy("Guilty Conscience")) {
                            return false;
                        }
                        return true;
                    }
                });
                chosen = ComputerUtilCard.getBestCreatureAI(creatures);
            }
            
            return chosen;
        }
    }

    // Intuition (and any other card that might potentially let you pick N cards from the library,
    // one of which will then be picked for you by the opponent)
    public static class Intuition {
        public static CardCollection considerMultiple(final Player ai, final SpellAbility sa) {
            if (ai.getController().isAI()) {
                if (!((PlayerControllerAi) ai.getController()).getAi().getBooleanProperty(AiProps.INTUITION_SPECIAL_LOGIC)) {
                    return new CardCollection(); // fall back to standard ChangeZoneAi considerations
                }
            }

            int changeNum = AbilityUtils.calculateAmount(sa.getHostCard(), sa.getParam("ChangeNum"), sa);
            CardCollection lib = CardLists.filter(ai.getCardsIn(ZoneType.Library),
                    Predicates.not(CardPredicates.nameEquals(sa.getHostCard().getName())));
            Collections.sort(lib, CardLists.CmcComparatorInv);

            // Additional cards which are difficult to auto-classify but which are generally good to Intuition for
            List<String> highPriorityNamedCards = Lists.newArrayList("Accumulated Knowledge");

            // figure out how many of each card we have in deck
            MapToAmount<String> cardAmount = new LinkedHashMapToAmount<>();
            for (Card c : lib) {
                cardAmount.add(c.getName());
            }

            // Trix: see if we can complete the combo
            int numIllusionsInHand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.nameEquals("Illusions of Grandeur")).size();
            int numDonateInHand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.nameEquals("Donate")).size();
            int numIllusionsInLib = CardLists.filter(ai.getCardsIn(ZoneType.Library), CardPredicates.nameEquals("Illusions of Grandeur")).size();
            int numIllusionsOTB = CardLists.filter(ai.getCardsIn(ZoneType.Battlefield), CardPredicates.nameEquals("Illusions of Grandeur")).size();
            int numDonateInLib = CardLists.filter(ai.getCardsIn(ZoneType.Library), CardPredicates.nameEquals("Donate")).size();
            CardCollection comboList = new CardCollection();
            if ((numIllusionsInHand > 0 || numIllusionsOTB > 0) && numDonateInHand == 0 && numDonateInLib >= 3) {
                for (Card c : lib) {
                    if (c.getName().equals("Donate")) {
                        comboList.add(c);
                    }
                }
                return comboList;
            } else if (numDonateInHand > 0 && numIllusionsInHand == 0 && numIllusionsInLib >= 3) {
                for (Card c : lib) {
                    if (c.getName().equals("Illusions of Grandeur")) {
                        comboList.add(c);
                    }
                }
                return comboList;
            }

            // Create a priority list for cards that we have no more than 4 of and that are not lands
            CardCollection libPriorityList = new CardCollection();
            CardCollection libHighPriorityList = new CardCollection();
            CardCollection libLowPriorityList = new CardCollection();
            List<String> processed = Lists.newArrayList();
            for (int i = 4; i > 0; i--) {
                for (Card c : lib) {
                    if (cardAmount.get(c.getName()) == i && !c.isLand() && !processed.contains(c.getName())) {
                        // if it's a card that is generally good to place in the graveyard, also add it
                        // to the mix
                        boolean canRetFromGrave = false;
                        String name = c.getName().replace(',', ';');
                        for (Trigger t : c.getTriggers()) {
                            SpellAbility ab = null;
                            if (t.hasParam("Execute")) {
                                ab = AbilityFactory.getAbility(c.getSVar(t.getParam("Execute")), c);
                            }
                            if (ab == null) { continue; }

                            if (ab.getApi() == ApiType.ChangeZone
                                    && "Self".equals(ab.getParam("Defined"))
                                    && "Graveyard".equals(ab.getParam("Origin"))
                                    && "Battlefield".equals(ab.getParam("Destination"))) {
                                canRetFromGrave = true;
                            }
                            if (ab.getApi() == ApiType.ChangeZoneAll
                                    && TextUtil.concatNoSpace("Creature.named", name).equals(ab.getParam("ChangeType"))
                                    && "Graveyard".equals(ab.getParam("Origin"))
                                    && "Battlefield".equals(ab.getParam("Destination"))) {
                                canRetFromGrave = true;
                            }
                        }
                        boolean isGoodToPutInGrave = c.hasSVar("DiscardMe") || canRetFromGrave
                                || (ComputerUtil.isPlayingReanimator(ai) && c.isCreature());

                        for (Card c1 : lib) {
                            if (c1.getName().equals(c.getName())) {
                                if (CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.nameEquals(c1.getName())).isEmpty()
                                        && ComputerUtilMana.hasEnoughManaSourcesToCast(c1.getFirstSpellAbility(), ai)) {
                                    // Try not to search for things we already have in hand or that we can't cast
                                    libPriorityList.add(c1);
                                } else {
                                    libLowPriorityList.add(c1);
                                }
                                if (isGoodToPutInGrave || highPriorityNamedCards.contains(c.getName())) {
                                    libHighPriorityList.add(c1);
                                }
                            }
                        }
                        processed.add(c.getName());
                    }
                }
            }

            // If we're playing Reanimator, we're really interested just in the highest CMC spells, not the
            // ones we necessarily have multiples of
            if (ComputerUtil.isPlayingReanimator(ai)) {
                Collections.sort(libHighPriorityList, CardLists.CmcComparatorInv);
            }

            // Otherwise, try to grab something that is hopefully decent to grab, in priority order
            CardCollection chosen = new CardCollection();
            if (libHighPriorityList.size() >= changeNum) {
                for (int i = 0; i < changeNum; i++) {
                    chosen.add(libHighPriorityList.get(i));
                }
            } else if (libPriorityList.size() >= changeNum) {
                for (int i = 0; i < changeNum; i++) {
                    chosen.add(libPriorityList.get(i));
                }
            } else if (libLowPriorityList.size() >= changeNum) {
                for (int i = 0; i < changeNum; i++) {
                    chosen.add(libLowPriorityList.get(i));
                }
            }

            return chosen;
        }
    }

    // Living Death (and possibly other similar cards using AILogic LivingDeath)
    public static class LivingDeath {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            int aiBattlefieldPower = 0, aiGraveyardPower = 0;
            int threshold = 320; // approximately a 4/4 Flying creature worth of extra value

            CardCollection aiCreaturesInGY = CardLists.filter(ai.getZone(ZoneType.Graveyard).getCards(), CardPredicates.Presets.CREATURES);

            if (aiCreaturesInGY.isEmpty()) {
                // nothing in graveyard, so cut short
                return false;
            }
            
            for (Card c : ai.getCreaturesInPlay()) {
                if (!ComputerUtilCard.isUselessCreature(ai, c)) {
                    aiBattlefieldPower += ComputerUtilCard.evaluateCreature(c);
                }
            }
            for (Card c : aiCreaturesInGY) {
                aiGraveyardPower += ComputerUtilCard.evaluateCreature(c);
            }

            int oppBattlefieldPower = 0, oppGraveyardPower = 0; 
            List<Player> opponents = ai.getOpponents();
            for (Player p : opponents) {
                int playerPower = 0;
                int tempGraveyardPower = 0;
                for (Card c : p.getCreaturesInPlay()) {
                    playerPower += ComputerUtilCard.evaluateCreature(c);
                }
                for (Card c : CardLists.filter(p.getZone(ZoneType.Graveyard).getCards(), CardPredicates.Presets.CREATURES)) {
                    tempGraveyardPower += ComputerUtilCard.evaluateCreature(c);
                }
                if (playerPower > oppBattlefieldPower) {
                    oppBattlefieldPower = playerPower;
                }
                if (tempGraveyardPower > oppGraveyardPower) {
                    oppGraveyardPower = tempGraveyardPower;
                }
            }

            // if we get more value out of this than our opponent does (hopefully), go for it
            return (aiGraveyardPower - aiBattlefieldPower) > (oppGraveyardPower - oppBattlefieldPower + threshold);
        }
    }

    // Mairsil, the Pretender
    public static class MairsilThePretender {
        // Scan the fetch list for a card with at least one activated ability.
        // TODO: can be improved to a full consider(sa, ai) logic which would scan the graveyard first and hand last
        public static Card considerCardFromList(final CardCollection fetchList) {
            for (Card c : CardLists.filter(fetchList, Predicates.or(CardPredicates.Presets.ARTIFACTS, CardPredicates.Presets.CREATURES))) {
                for (SpellAbility ab : c.getSpellAbilities()) {
                    if (ab.isAbility() && !ab.isTrigger()) {
                        Player controller = c.getController();
                        boolean wasCaged = false;
                        for (Card caged : CardLists.filter(controller.getCardsIn(ZoneType.Exile),
                                CardPredicates.hasCounter(CounterType.CAGE))) {
                            if (c.getName().equals(caged.getName())) {
                                wasCaged = true;
                                break;
                            }
                        }

                        if (!wasCaged) {
                            return c;
                        }
                    }
                }
            }
            return null;
        }
    }

    // Momir Vig, Simic Visionary Avatar
    public static class MomirVigAvatar {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            Card source = sa.getHostCard();

            if (source.getGame().getPhaseHandler().getPhase().isBefore(PhaseType.MAIN1)) {
                return false;
            }
            // Set PayX here to maximum value.
            int tokenSize = ComputerUtilMana.determineLeftoverMana(sa, ai);

            // Some basic strategy for Momir
            if (tokenSize < 2) {
                return false;
            }

            if (tokenSize > 11) {
                tokenSize = 11;
            }

            source.setSVar("PayX", Integer.toString(tokenSize));

            return true;
        }
    }

    // Necropotence
    public static class Necropotence {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            Game game = ai.getGame();
            int computerHandSize = ai.getZone(ZoneType.Hand).size();
            int maxHandSize = ai.getMaxHandSize();

            if (!CardLists.filter(ai.getCardsIn(ZoneType.Battlefield), CardPredicates.nameEquals("Yawgmoth's Bargain")).isEmpty()) {
                // Prefer Yawgmoth's Bargain because AI is generally better with it

                // TODO: in presence of bad effects which deal damage when a card is drawn, probably better to prefer Necropotence instead?
                // (not sure how to detect the presence of such effects yet)
                return false;
            }

            PhaseHandler ph = game.getPhaseHandler();

            int exiledWithNecro = 1; // start with 1 because if this succeeds, one extra card will be exiled with Necro
            for (Card c : ai.getCardsIn(ZoneType.Exile)) {
                if (c.getExiledWith() != null && "Necropotence".equals(c.getExiledWith().getName()) && c.isFaceDown()) {
                    exiledWithNecro++;
                }
            }

            // TODO: Any other bad effects like that?
            boolean blackViseOTB = !CardLists.filter(game.getCardsIn(ZoneType.Battlefield), CardPredicates.nameEquals("Black Vise")).isEmpty();

            if (ph.getNextTurn().equals(ai) && ph.is(PhaseType.MAIN2)
                    && ai.getSpellsCastLastTurn() == 0 
                    && ai.getSpellsCastThisTurn() == 0
                    && ai.getLandsPlayedLastTurn() == 0) {
                // We're in a situation when we have nothing castable in hand, something needs to be done
                if (!blackViseOTB) {
                    // exile-loot +1 card when at max hand size, hoping to get a workable spell or land
                    return computerHandSize + exiledWithNecro - 1 == maxHandSize; 
                } else {
                    // Loot to 7 in presence of Black Vise, hoping to find what to do
                    // NOTE: can still currently get theoretically locked with 7 uncastable spells. Loot to 8 instead?
                    return computerHandSize + exiledWithNecro <= maxHandSize;
                }
            } else if (blackViseOTB && computerHandSize + exiledWithNecro - 1 >= 4) { 
                // try not to overdraw in presence of Black Vise
                return false; 
            } else if (computerHandSize + exiledWithNecro - 1 >= maxHandSize) {
                // Only draw until we reach max hand size
                return false;
            } else if (!ph.isPlayerTurn(ai) || !ph.is(PhaseType.MAIN2)) {
                // Only activate in AI's own turn (sans the exception above)
                return false;
            } 

            return true;
        }
    }

    // Null Brooch
    public static class NullBrooch {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            // TODO: improve the detection of Ensnaring Bridge type effects ("GTX", "X" need generalization)
            boolean hasEnsnaringBridgeEffect = false;
            for (Card otb : ai.getCardsIn(ZoneType.Battlefield)) {
                for (StaticAbility stab : otb.getStaticAbilities()) {
                    if ("CARDNAME can't attack.".equals(stab.getParam("AddHiddenKeyword"))
                            && "Creature.powerGTX".equals(stab.getParam("Affected"))
                            && "Count$InYourHand".equals(otb.getSVar("X"))) {
                        hasEnsnaringBridgeEffect = true;
                        break;
                    }
                }

            }
            // Maybe use it for some important high-impact spells even if there are more cards in hand?
            if (ai.getCardsIn(ZoneType.Hand).size() > 1 && !hasEnsnaringBridgeEffect) {
                return false;
            }

            return true;
        }
    }

    // Nykthos, Shrine to Nyx
    public static class NykthosShrineToNyx {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            Game game = ai.getGame();
            PhaseHandler ph = game.getPhaseHandler();
            if (!ph.isPlayerTurn(ai) || ph.getPhase().isBefore(PhaseType.MAIN2)) {
                // TODO: currently limited to Main 2, somehow improve to let the AI use this SA at other time?
                return false;
            }
            String prominentColor = ComputerUtilCard.getMostProminentColor(ai.getCardsIn(ZoneType.Battlefield));
            int devotion = CardFactoryUtil.xCount(sa.getHostCard(), "Count$Devotion." + prominentColor);
            int activationCost = sa.getPayCosts().getTotalMana().getCMC() + (sa.getPayCosts().hasTapCost() ? 1 : 0);

            // do not use this SA if devotion to most prominent color is less than its own activation cost + 1 (to actually get advantage)
            if (devotion < activationCost + 1) {
                return false;
            }

            final CardCollectionView cards = ai.getCardsIn(new ZoneType[] {ZoneType.Hand, ZoneType.Battlefield, ZoneType.Command});
            List<SpellAbility> all = ComputerUtilAbility.getSpellAbilities(cards, ai);

            int numManaSrcs = CardLists.filter(ComputerUtilMana.getAvailableManaSources(ai, true), CardPredicates.Presets.UNTAPPED).size();

            for (final SpellAbility testSa : ComputerUtilAbility.getOriginalAndAltCostAbilities(all, ai)) {
                ManaCost cost = testSa.getPayCosts().getTotalMana();
                boolean canPayWithAvailableColors = cost.canBePaidWithAvaliable(ColorSet.fromNames(
                    ComputerUtilCost.getAvailableManaColors(ai, sa.getHostCard())).getColor());
                
                byte colorProfile = cost.getColorProfile();
                
                if (cost.getCMC() == 0 && cost.countX() == 0) {
                    // no mana cost, no need to activate this SA then (additional mana not needed)
                    continue;
                } else if (colorProfile != 0 && !canPayWithAvailableColors
                    && (cost.getColorProfile() & MagicColor.fromName(prominentColor)) == 0) {
                    // don't have at least one of each shard required to pay, so most likely won't be able to pay
                    continue;
                } else if ((testSa.getPayCosts().getTotalMana().getCMC() > devotion + numManaSrcs - activationCost)) {
                    // the cost may be too high even if we activate this SA
                    continue;
                }

                if (ComputerUtilAbility.getAbilitySourceName(testSa).equals(ComputerUtilAbility.getAbilitySourceName(sa))
                        || testSa.hasParam("AINoRecursiveCheck")) {
                    // prevent infinitely recursing abilities that are susceptible to reentry
                    continue;
                }

                testSa.setActivatingPlayer(ai);
                if (((PlayerControllerAi)ai.getController()).getAi().canPlaySa(testSa) == AiPlayDecision.WillPlay) {
                    // the AI is willing to play the spell
                    return true;
                }
            }

            return false; // haven't found anything to play with the excess generated mana
        }
    }

    // Phyrexian Dreadnought
    public static class PhyrexianDreadnought {
        public static CardCollection reviseCreatureSacList(final Player ai, final SpellAbility sa, final CardCollection choices) {
            choices.sort(Collections.reverseOrder(ComputerUtilCard.EvaluateCreatureComparator));
            int power = 0;
            List<Card> toKeep = Lists.newArrayList();
            for (Card c : choices) {
                if (c.getName().equals(ComputerUtilAbility.getAbilitySourceName(sa))) {
                    continue; // not worth it sac'ing another Dreadnaught
                }
                if (c.getNetPower() < 1) {
                    continue; // contributes nothing to Dreadnought requirements
                }
                if (power >= 12) {
                    break;
                }
                toKeep.add(c);
                power += c.getNetPower();
            }

            return new CardCollection(toKeep);
        }
    }

    // Sarkhan the Mad
    public static class SarkhanTheMad {
        public static boolean considerDig(final Player ai, final SpellAbility sa) {
            return sa.getHostCard().getCounters(CounterType.LOYALTY) == 1;
        }

        public static boolean considerMakeDragon(final Player ai, final SpellAbility sa) {
            // TODO: expand this logic to make the AI force the opponent to sacrifice a big threat bigger than a 5/5 flier?
            CardCollection creatures = ai.getCreaturesInPlay();
            boolean hasValidTgt = !CardLists.filter(creatures, new Predicate<Card>() {
                @Override
                public boolean apply(Card t) {
                    return t.getNetPower() < 5 && t.getNetToughness() < 5;
                }
            }).isEmpty();
            if (hasValidTgt) {
                Card worstCreature = ComputerUtilCard.getWorstCreatureAI(creatures);
                sa.getTargets().add(worstCreature);
                return true;
            }
            return false;
        }

        public static boolean considerUltimate(final Player ai, final SpellAbility sa, final Player weakestOpp) {
            int minLife = weakestOpp.getLife();

            int dragonPower = 0;
            CardCollection dragons = CardLists.filter(ai.getCreaturesInPlay(), CardPredicates.isType("Dragon"));
            for (Card c : dragons) {
                dragonPower += c.getNetPower();
            }

            return dragonPower >= minLife;
        }
    }

    // Survival of the Fittest
    public static class SurvivalOfTheFittest {
        public static Card considerDiscardTarget(final Player ai) {
            // The AI here only checks the number of available creatures of various CMC, which is equivalent to knowing
            // your deck composition and checking (and counting) the cards in other zones so you know what you have left
            // in the library. As such, this does not cause unfair advantage, at least unless there are cards that are
            // face down (on the battlefield or in exile). Might need some kind of an update to consider hidden information
            // like that properly (probably by adding all those cards to the evaluation mix so the AI doesn't "know" which
            // ones are already face down in play and which are still in the library)
            CardCollectionView creatsInLib = CardLists.filter(ai.getCardsIn(ZoneType.Library), CardPredicates.Presets.CREATURES);
            CardCollectionView creatsInHand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.Presets.CREATURES);
            CardCollectionView manaSrcsInHand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.Presets.LANDS_PRODUCING_MANA);

            if (creatsInHand.isEmpty() || creatsInLib.isEmpty()) { return null; }

            int numManaSrcs = ComputerUtilMana.getAvailableManaEstimate(ai, false)
                    + Math.min(1, manaSrcsInHand.size());

            // Cards in library that are either below/at (preferred) or above the max CMC affordable by the AI
            // (the latter might happen if we're playing a Reanimator deck with lots of fatties)
            CardCollection atTargetCMCInLib = CardLists.filter(creatsInLib, new Predicate<Card>() {
                @Override
                public boolean apply(Card card) {
                    return ComputerUtilMana.hasEnoughManaSourcesToCast(card.getSpellPermanent(), ai);
                }
            });
            if (atTargetCMCInLib.isEmpty()) {
                atTargetCMCInLib = CardLists.filter(creatsInLib, CardPredicates.greaterCMC(numManaSrcs));
            }
            Collections.sort(atTargetCMCInLib, CardLists.CmcComparatorInv);
            if (atTargetCMCInLib.isEmpty()) {
                // Nothing to aim for?
                return null;
            }

            // Cards in hand that are below the max CMC affordable by the AI
            CardCollection belowMaxCMC = CardLists.filter(creatsInHand, CardPredicates.lessCMC(numManaSrcs - 1));
            Collections.sort(belowMaxCMC, Collections.reverseOrder(CardLists.CmcComparatorInv));

            // Cards in hand that are above the max CMC affordable by the AI
            CardCollection aboveMaxCMC = CardLists.filter(creatsInHand, CardPredicates.greaterCMC(numManaSrcs + 1));
            Collections.sort(aboveMaxCMC, CardLists.CmcComparatorInv);

            Card maxCMC = !aboveMaxCMC.isEmpty() ? aboveMaxCMC.getFirst() : null;
            Card minCMC = !belowMaxCMC.isEmpty() ? belowMaxCMC.getFirst() : null;
            Card bestInLib = !atTargetCMCInLib.isEmpty() ? atTargetCMCInLib.getFirst() : null;

            int maxCMCdiff = 0;
            if (maxCMC != null) {
                maxCMCdiff = maxCMC.getCMC() - numManaSrcs; // how far are we from viably casting it?
            }

            // We have something too fat to viably cast in the nearest future, discard it hoping to
            // grab something more immediately valuable (or maybe we're playing Reanimator and we want
            // it to be in the graveyard anyway)
            if (maxCMCdiff >= 3) {
                return maxCMC;
            }
            // We have a card in hand that is worse than the one in library, so discard the worst card
            if (maxCMCdiff <= 0 && minCMC != null
                    && ComputerUtilCard.evaluateCreature(bestInLib) > ComputerUtilCard.evaluateCreature(minCMC)) {
                return minCMC;
            }
            // We have a card in the library that is closer to being castable than the one in hand, and
            // no options with smaller CMC, so discard the one that is harder to cast for the one that is
            // easier to cast right now, but only if the best card in the library is at least CMC 3
            // (probably not worth it to grab low mana cost cards this way)
            if (maxCMC != null && maxCMC.getCMC() < bestInLib.getCMC() && bestInLib.getCMC() >= 3) {
                return maxCMC;
            }
            // We appear to be playing Reanimator (or we have a reanimator card in hand already), so it's
            // worth to fill the graveyard now
            if (ComputerUtil.isPlayingReanimator(ai) && !creatsInLib.isEmpty()) {
                CardCollection creatsInHandByCMC = new CardCollection(creatsInHand);
                Collections.sort(creatsInHandByCMC, CardLists.CmcComparatorInv);
                return creatsInHandByCMC.getFirst();
            }

            // probably nothing that is worth changing, so bail
            return null;
        }

        public static Card considerCardToGet(final Player ai, final SpellAbility sa) {
            CardCollectionView creatsInLib = CardLists.filter(ai.getCardsIn(ZoneType.Library), CardPredicates.Presets.CREATURES);
            if (creatsInLib.isEmpty()) { return null; }

            CardCollectionView manaSrcsInHand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.Presets.LANDS_PRODUCING_MANA);
            int numManaSrcs = ComputerUtilMana.getAvailableManaEstimate(ai, false)
                    + Math.min(1, manaSrcsInHand.size());

            CardCollection atTargetCMCInLib = CardLists.filter(creatsInLib, new Predicate<Card>() {
                @Override
                public boolean apply(Card card) {
                    return ComputerUtilMana.hasEnoughManaSourcesToCast(card.getSpellPermanent(), ai);
                }
            });
            if (atTargetCMCInLib.isEmpty()) {
                atTargetCMCInLib = CardLists.filter(creatsInLib, CardPredicates.greaterCMC(numManaSrcs));
            }
            Collections.sort(atTargetCMCInLib, CardLists.CmcComparatorInv);

            Card bestInLib = atTargetCMCInLib != null ? atTargetCMCInLib.getFirst() : null;

            if (bestInLib == null && ComputerUtil.isPlayingReanimator(ai)) {
                // For Reanimator, we don't mind grabbing the biggest thing possible to recycle it again with SotF later.
                CardCollection creatsInLibByCMC = new CardCollection(creatsInLib);
                Collections.sort(creatsInLibByCMC, CardLists.CmcComparatorInv);
                return creatsInLibByCMC.getFirst();
            }

            return bestInLib;
        }
    }

    // Timetwister
    public static class Timetwister {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            final int aiHandSize = ai.getCardsIn(ZoneType.Hand).size();
            int maxOppHandSize = 0;

            final int HAND_SIZE_THRESHOLD = 3;

            for (Player p : ai.getOpponents()) {
                int handSize = p.getCardsIn(ZoneType.Hand).size();
                if (handSize > maxOppHandSize) {
                    maxOppHandSize = handSize;
                }
            }

            if (aiHandSize < HAND_SIZE_THRESHOLD || maxOppHandSize - aiHandSize > HAND_SIZE_THRESHOLD) {
                // use in case we're getting low on cards or if we're significantly behind our opponent in cards in hand
                return true;
            }

            return false;
        }
    }

    // Volrath's Shapeshifter
    public static class VolrathsShapeshifter {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            CardCollectionView aiGY = ai.getCardsIn(ZoneType.Graveyard);
            Card topGY = null;
            Card creatHand = ComputerUtilCard.getBestCreatureAI(ai.getCardsIn(ZoneType.Hand));
            int numCreatsInHand = CardLists.filter(ai.getCardsIn(ZoneType.Hand), CardPredicates.Presets.CREATURES).size();

            if (!aiGY.isEmpty()) {
                topGY = ai.getCardsIn(ZoneType.Graveyard).get(0);
            }

            if ((topGY != null && !topGY.isCreature()) || creatHand != null) {
                if (numCreatsInHand > 1 || !ComputerUtilMana.canPayManaCost(creatHand.getSpellPermanent(), ai, 0)) {
                    return true;
                }
            }

            return false;
        }

        public static CardCollection targetBestCreature(final Player ai, final SpellAbility sa) {
            Card creatHand = ComputerUtilCard.getBestCreatureAI(ai.getCardsIn(ZoneType.Hand));
            if (creatHand != null) {
                CardCollection cc = new CardCollection();
                cc.add(creatHand);
                return cc;
            }

            // Should ideally never get here
            System.err.println("Volrath's Shapeshifter AI: Could not find a discard target despite the previous confirmation to proceed!");
            return null;
        }
    }

    // Ugin, the Spirit Dragon
    public static class UginTheSpiritDragon {
        public static boolean considerPWAbilityPriority(final Player ai, final SpellAbility sa, final ZoneType origin, CardCollectionView oppType, CardCollectionView computerType) {
            Card source = sa.getHostCard();
            Game game = source.getGame();
            
            final int loyalty = source.getCounters(CounterType.LOYALTY);
            int x = -1, best = 0;
            Card single = null;
            for (int i = 0; i < loyalty; i++) {
                sa.setSVar("ChosenX", "Number$" + i);
                oppType = CardLists.filterControlledBy(game.getCardsIn(origin), ai.getOpponents());
                oppType = AbilityUtils.filterListByType(oppType, sa.getParam("ChangeType"), sa);
                computerType = AbilityUtils.filterListByType(ai.getCardsIn(origin), sa.getParam("ChangeType"), sa);
                int net = ComputerUtilCard.evaluatePermanentList(oppType) - ComputerUtilCard.evaluatePermanentList(computerType) - i;
                if (net > best) {
                    x = i;
                    best = net;
                    if (oppType.size() == 1) {
                        single = oppType.getFirst();
                    } else {
                        single = null;
                    }
                }
            }
            // check if +1 would be sufficient
            if (single != null) {
                SpellAbility ugin_burn = null;
                for (final SpellAbility s : source.getSpellAbilities()) {
                    if (s.getApi() == ApiType.DealDamage) {
                        ugin_burn = s;
                        break;
                    }
                }
                if (ugin_burn != null) {
                    // basic logic copied from DamageDealAi::dealDamageChooseTgtC
                    if (ugin_burn.canTarget(single)) {
                        final boolean can_kill = single.getSVar("Targeting").equals("Dies")
                                || (ComputerUtilCombat.getEnoughDamageToKill(single, 3, source, false, false) <= 3)
                                        && !ComputerUtil.canRegenerate(ai, single)
                                        && !(single.getSVar("SacMe").length() > 0);
                        if (can_kill) {
                            return false;
                        }
                    }
                    // simple check to burn player instead of exiling planeswalker
                    if (single.isPlaneswalker() && single.getCurrentLoyalty() <= 3) {
                        return false;
                    }
                }
            }
             if (x == -1) {
                return false;
            }
            sa.setSVar("ChosenX", "Number$" + x);
            return true;
        }
    }

    // Yawgmoth's Bargain
    public static class YawgmothsBargain {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            Game game = ai.getGame();
            PhaseHandler ph = game.getPhaseHandler();

            int computerHandSize = ai.getZone(ZoneType.Hand).size();
            int maxHandSize = ai.getMaxHandSize();

            // TODO: Any other bad effects like that?
            boolean blackViseOTB = !CardLists.filter(game.getCardsIn(ZoneType.Battlefield), CardPredicates.nameEquals("Black Vise")).isEmpty();

            // TODO: Consider effects like "whenever a player draws a card, he loses N life" (e.g. Nekusar, the Mindraiser),
            //       and effects that draw an additional card whenever a card is drawn.

            if (ph.getNextTurn().equals(ai) && ph.is(PhaseType.END_OF_TURN) 
                    && ai.getSpellsCastLastTurn() == 0 
                    && ai.getSpellsCastThisTurn() == 0 
                    && ai.getLandsPlayedLastTurn() == 0) {
                // We're in a situation when we have nothing castable in hand, something needs to be done
                if (!blackViseOTB) {
                    // draw +1 card when at max hand size, hoping to draw a workable spell or land
                    return computerHandSize == maxHandSize;
                } else {
                    // draw cards hoping to draw answers even in presence of Black Vise if there's no valid play
                    // TODO: maybe limit to 1 or 2 cards at a time?
                    return computerHandSize + 1 <= maxHandSize; // currently draws to 7 cards
                }
            } else if (blackViseOTB && computerHandSize + 1 > 4) {
                    // try not to overdraw in presence of Black Vise
                    return false;
            } else if (computerHandSize + 1 > maxHandSize) {
                // Only draw until we reach max hand size
                return false;
            } else if (!ph.isPlayerTurn(ai)) {
                // Only activate in AI's own turn (sans the exception above)
                return false;
            } 

            return true;
        }
    }
    
    // Yawgmoth's Will (can potentially be expanded for other broadly similar effects too)
    public static class YawgmothsWill {
        public static boolean consider(final Player ai, final SpellAbility sa) {
            CardCollectionView cardsInGY = ai.getCardsIn(ZoneType.Graveyard);
            if (cardsInGY.size() == 0) {
                return false;
            }

            int minManaAdj = 2; // we want the AI to have some spare mana for possible other spells to cast
            float minCastableInGY = 3.0f; // we want the AI to have several castable cards in GY before attempting this effect
            List<SpellAbility> saList = ComputerUtilAbility.getSpellAbilities(cardsInGY, ai);
            int selfCMC = sa.getPayCosts().getCostMana().getMana().getCMC();

            float numCastable = 0.0f;
            for (SpellAbility ab : saList) {
                final Card src = ab.getHostCard();

                if (ab.getApi() == ApiType.Counter) {
                    // cut short considering to play counterspells via Yawgmoth's Will
                    continue;
                }

                if (ComputerUtilAbility.getAbilitySourceName(ab).equals(ComputerUtilAbility.getAbilitySourceName(sa))
                        || ab.hasParam("AINoRecursiveCheck")) {
                    // prevent infinitely recursing abilities that are susceptible to reentry
                    continue;
                }

                // check to see if the AI is willing to play this card
                final SpellAbility testAb = ab.copy();
                testAb.getRestrictions().setZone(ZoneType.Graveyard);
                testAb.setActivatingPlayer(ai);

                boolean willPlayAb = ((PlayerControllerAi)ai.getController()).getAi().canPlaySa(testAb) == AiPlayDecision.WillPlay;

                // Land drops are generally made by the AI in main 1 before casting spells, so testing for them is iffy.
                if (!src.getType().isLand() && willPlayAb) {
                    int CMC = ab.getPayCosts().getTotalMana() != null ? ab.getPayCosts().getTotalMana().getCMC() : 0;
                    int Xcount = ab.getPayCosts().getTotalMana() != null ? ab.getPayCosts().getTotalMana().countX() : 0;

                    if ((Xcount == 0 && CMC == 0) || ComputerUtilMana.canPayManaCost(ab, ai, selfCMC + minManaAdj)) {
                        if (src.isInstant() || src.isSorcery()) {
                            // instants and sorceries are one-shot, so only treat them as 1/2 value for the purpose of meeting minimum 
                            // castable cards in graveyard requirements 
                            numCastable += 0.5f;
                        } else {
                            numCastable += 1.0f;
                        }
                    }
                }
            }

            return numCastable >= minCastableInGY;
        }
    }

}