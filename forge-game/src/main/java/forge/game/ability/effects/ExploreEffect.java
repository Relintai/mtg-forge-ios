package forge.game.ability.effects;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import forge.game.Game;
import forge.game.ability.SpellAbilityEffect;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CounterType;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;

import java.util.List;
import java.util.Map;

public class ExploreEffect extends SpellAbilityEffect {

    

    /* (non-Javadoc)
     * @see forge.game.ability.SpellAbilityEffect#getStackDescription(forge.game.spellability.SpellAbility)
     */
    @Override
    protected String getStackDescription(SpellAbility sa) {
        final Card host = sa.getHostCard();
        return host.getName() + " explores.";
    }

    /* (non-Javadoc)
     * @see forge.game.ability.SpellAbilityEffect#resolve(forge.game.spellability.SpellAbility)
     */
    @Override
    public void resolve(SpellAbility sa) {
        // check if only the activating player counts
        final Card card = sa.getHostCard();
        final Player pl = sa.getActivatingPlayer();
        final PlayerController pc = pl.getController();
        final Game game = pl.getGame();
        List<Card> tgt = getTargetCards(sa);
        
        for (final Card c : tgt) {
            // revealed land card
            boolean revealedLand = false;
            CardCollection top = pl.getTopXCardsFromLibrary(1);
            if (!top.isEmpty()) {
                game.getAction().reveal(top, pl, false, "Revealed for Explore - ");
                final Card r = top.getFirst();
                if (r.isLand()) {
                    game.getAction().moveTo(ZoneType.Hand, r, sa);
                    revealedLand = true;
                } else {
                    // TODO find better way to choose optional send away
                    final Card choosen = pc.chooseSingleCardForZoneChange(
                            ZoneType.Graveyard, Lists.newArrayList(ZoneType.Library), sa, top, null,
                            "Put this card in your graveyard?", true, pl);
                    if (choosen != null) {
                        game.getAction().moveTo(ZoneType.Graveyard, choosen, sa);
                    }
                }
            }
            if (!revealedLand) {
                // currently the timestamp check only works for if the card itself explore,
                // if something else can make it explore that need to be updated
                if (game.getZoneOf(c).is(ZoneType.Battlefield) && card.getTimestamp() == c.getTimestamp()) {
                    c.addCounter(CounterType.P1P1, 1, card, true);
                }
            }

            // a creature does explore even if it isn't on the battlefield anymore
            final Map<String, Object> runParams = Maps.newHashMap();
            runParams.put("Card", c);
            game.getTriggerHandler().runTrigger(TriggerType.Explores, runParams, false);
        }
    }

}
