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

import forge.game.card.Card;
import forge.game.player.Player;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * <p>
 * AiCardMemory class.
 * </p>
 * 
 * A simple class that allows the AI to "memorize" different cards on the battlefield (and possibly in other zones
 * too, for instance as revealed from the opponent's hand) and assign them to different memory sets in order to help
 * make somewhat more "educated" decisions to attack with certain cards or play certain spell abilities. Each 
 * AiController has its own memory that is created when the AI player is spawned. The card memory is accessible 
 * via AiController.getCardMemory. 
 * 
 * @author Forge
 */
public class AiCardMemory {

    private final Set<Card> memMandatoryAttackers;
    private final Set<Card> memTrickAttackers;
    private final Set<Card> memHeldManaSources;
    private final Set<Card> memHeldManaSourcesForCombat;
    private final Set<Card> memAttachedThisTurn;
    private final Set<Card> memAnimatedThisTurn;
    private final Set<Card> memBouncedThisTurn;
    private final Set<Card> memActivatedThisTurn;

    public AiCardMemory() {
        this.memMandatoryAttackers = new HashSet<>();
        this.memHeldManaSources = new HashSet<>();
        this.memHeldManaSourcesForCombat = new HashSet<>();
        this.memAttachedThisTurn = new HashSet<>();
        this.memAnimatedThisTurn = new HashSet<>();
        this.memBouncedThisTurn = new HashSet<>();
        this.memActivatedThisTurn = new HashSet<>();
        this.memTrickAttackers = new HashSet<>();
    }

    /**
     * Defines the memory set in which the card is remembered
     * (which, in its turn, defines how the AI utilizes the information
     * about remembered cards).
     */
    public enum MemorySet {
        MANDATORY_ATTACKERS,
        TRICK_ATTACKERS,
        HELD_MANA_SOURCES_FOR_MAIN2,
        HELD_MANA_SOURCES_FOR_DECLBLK,
        ATTACHED_THIS_TURN,
        ANIMATED_THIS_TURN,
        BOUNCED_THIS_TURN,
        ACTIVATED_THIS_TURN,
        //REVEALED_CARDS // stub, not linked to AI code yet
    }

    private Set<Card> getMemorySet(MemorySet set) {
        switch (set) {
            case MANDATORY_ATTACKERS:
                return memMandatoryAttackers;
            case TRICK_ATTACKERS:
                return memTrickAttackers;
            case HELD_MANA_SOURCES_FOR_MAIN2:
                return memHeldManaSources;
            case HELD_MANA_SOURCES_FOR_DECLBLK:
                return memHeldManaSourcesForCombat;
            case ATTACHED_THIS_TURN:
                return memAttachedThisTurn;
            case ANIMATED_THIS_TURN:
                return memAnimatedThisTurn;
            case BOUNCED_THIS_TURN:
                return memBouncedThisTurn;
            case ACTIVATED_THIS_TURN:
                return memActivatedThisTurn;
            //case REVEALED_CARDS:
            //    return memRevealedCards;
            default:
                return null;
        }
    }

    /**
     * Checks if the given card was remembered in the given memory set. 
     * 
     * @param c
     *            the card
     * @param set the memory set that is to be checked 
     * @return true, if the card is remembered in the given memory set
     */
    public boolean isRememberedCard(Card c, MemorySet set) {
        if (c == null) {
            return false;
        }

        Set<Card> memorySet = getMemorySet(set);

        return memorySet == null ? false : memorySet.contains(c);
    }

    /**
     * Checks if at least one card of the given name was remembered in the given memory set.
     * 
     * @param cardName
     *            the card name
     * @param set the memory set that is to be checked 
     * @return true, if at least one card with the given name is remembered in the given memory set
     */
    public boolean isRememberedCardByName(String cardName, MemorySet set) {
        Set<Card> memorySet = getMemorySet(set);
        Iterator<Card> it = memorySet.iterator();

        while (it.hasNext()) {
            Card c = it.next();
            if (c.getName().equals(cardName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if at least one card of the given name was remembered in the given memory set such
     * that its owner is the given player.
     * 
     * @param cardName
     *            the card name
     * @param set the memory set that is to be checked 
     * @param owner the owner of the card
     * @return true, if at least one card with the given name is remembered in the given memory set
     */
    public boolean isRememberedCardByName(String cardName, MemorySet set, Player owner) {
        Set<Card> memorySet = getMemorySet(set);
        Iterator<Card> it = memorySet.iterator();

        while (it.hasNext()) {
            Card c = it.next();
            if (c.getName().equals(cardName) && c.getOwner().equals(owner)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Remembers the given card in the given memory set.
     * @param c
     *            the card
     * @param set the memory set to remember the card in
     * @return true, if the card is successfully stored in the given memory set 
     */
    public boolean rememberCard(Card c, MemorySet set) {
        if (c == null)
            return false;

        getMemorySet(set).add(c);
        return true;
    }

    /**
     * Forgets the given card in the given memory set.
     * @param c
     *            the card
     * @param set the memory set to forget the card in
     * @return true, if the card was previously remembered in the given memory set and was successfully forgotten
     */
    public boolean forgetCard(Card c, MemorySet set) {
        if (c == null) {
            return false;
        }
        if (!isRememberedCard(c, set)) {
            return false;
        }

        getMemorySet(set).remove(c);
        return true;
    }

    /**
     * Forgets a single card with the given name in the given memory set.
     * @param cardName
     *            the card name
     * @param set the memory set to forget the card in
     * @return true, if at least one card with the given name was previously remembered in the given memory set and was successfully forgotten
     */
    public boolean forgetAnyCardWithName(String cardName, MemorySet set) {
        Set<Card> memorySet = getMemorySet(set);
        Iterator<Card> it = memorySet.iterator();

        while (it.hasNext()) { 
            Card c = it.next();
            if (c.getName().equals(cardName)) {
                return forgetCard(c, set);
            }
        }
        
        return false;
    }

    /**
     * Forgets a single card with the given name owned by the given player in the given memory set.
     * @param cardName
     *            the card name
     * @param set the memory set to forget the card in
     * @param owner the owner of the card
     * @return true, if at least one card with the given name was previously remembered in the given memory set and was successfully forgotten
     */
    public boolean forgetAnyCardWithName(String cardName, MemorySet set, Player owner) {
        Set<Card> memorySet = getMemorySet(set);
        Iterator<Card> it = memorySet.iterator();

        while (it.hasNext()) { 
            Card c = it.next();
            if (c.getName().equals(cardName) && c.getOwner().equals(owner)) {
                return forgetCard(c, set);
            }
        }
        
        return false;
    }

    /**
     * Determines if the memory set is empty.
     * @param set the memory set to inspect.
     * @return true, if the given memory set contains no remembered cards.
     */
    public boolean isMemorySetEmpty(MemorySet set) {
        return getMemorySet(set).isEmpty();
    }
    
    /**
     * Clears the given memory set.
     */
    public void clearMemorySet(MemorySet set) {
        getMemorySet(set).clear();
    }

    /**
     * Clears all memory sets stored in this card memory for the given player.
     */
    public void clearAllRemembered() {
        clearMemorySet(MemorySet.MANDATORY_ATTACKERS);
        clearMemorySet(MemorySet.TRICK_ATTACKERS);
        clearMemorySet(MemorySet.HELD_MANA_SOURCES_FOR_MAIN2);
        clearMemorySet(MemorySet.HELD_MANA_SOURCES_FOR_DECLBLK);
        clearMemorySet(MemorySet.ATTACHED_THIS_TURN);
        clearMemorySet(MemorySet.ANIMATED_THIS_TURN);
        clearMemorySet(MemorySet.BOUNCED_THIS_TURN);
        clearMemorySet(MemorySet.ACTIVATED_THIS_TURN);
    }

    // Static functions to simplify access to AI card memory of a given AI player.
    public static void rememberCard(Player ai, Card c, MemorySet set) {
        ((PlayerControllerAi)ai.getController()).getAi().getCardMemory().rememberCard(c, set);
    }
    public static void forgetCard(Player ai, Card c, MemorySet set) {
        ((PlayerControllerAi)ai.getController()).getAi().getCardMemory().forgetCard(c, set);
    }
    public static boolean isRememberedCard(Player ai, Card c, MemorySet set) {
        return ((PlayerControllerAi)ai.getController()).getAi().getCardMemory().isRememberedCard(c, set);
    }
    public static boolean isRememberedCardByName(Player ai, String name, MemorySet set) {
        return ((PlayerControllerAi)ai.getController()).getAi().getCardMemory().isRememberedCardByName(name, set);
    }
    public static void clearMemorySet(Player ai, MemorySet set) {
        ((PlayerControllerAi)ai.getController()).getAi().getCardMemory().clearMemorySet(set);
    }
    public static boolean isMemorySetEmpty(Player ai, MemorySet set) {
        return ((PlayerControllerAi)ai.getController()).getAi().getCardMemory().isMemorySetEmpty(set);
    }
}