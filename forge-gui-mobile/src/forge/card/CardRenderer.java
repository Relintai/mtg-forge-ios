package forge.card;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.HAlignment;
import com.badlogic.gdx.graphics.g2d.BitmapFont.TextBounds;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;
import forge.FThreads;
import forge.Graphics;
import forge.StaticData;
import forge.assets.*;
import forge.card.CardDetailUtil.DetailColors;
import forge.card.CardZoom.ActivateHandler;
import forge.card.mana.ManaCost;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.card.CounterType;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.properties.ForgeConstants;
import forge.properties.ForgeConstants.CounterDisplayType;
import forge.properties.ForgePreferences;
import forge.properties.ForgePreferences.FPref;
import forge.screens.match.MatchController;
import forge.toolbox.FList;
import forge.util.Utils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CardRenderer {
    public enum CardStackPosition {
        Top,
        BehindHorz,
        BehindVert
    }

    private static final FSkinFont NAME_FONT = FSkinFont.get(16);
    public static final float NAME_BOX_TINT = 0.2f;
    public static final float TEXT_BOX_TINT = 0.1f;
    public static final float PT_BOX_TINT = 0.2f;
    private static final float MANA_COST_PADDING = Utils.scale(3);
    public static final float SET_BOX_MARGIN = Utils.scale(1);
    public static final float MANA_SYMBOL_SIZE = FSkinImage.MANA_1.getNearestHQWidth(2 * (NAME_FONT.getCapHeight() - MANA_COST_PADDING));
    private static final float NAME_COST_THRESHOLD = Utils.scale(200);
    private static final float BORDER_THICKNESS = Utils.scale(1);
    public static final float PADDING_MULTIPLIER = 0.021f;

    private static Map<Integer, BitmapFont> counterFonts = new HashMap<>();
    private static final Color counterBackgroundColor = new Color(0f, 0f, 0f, 0.9f);
    private static final Map<CounterType, Color> counterColorCache = new HashMap<>();

    static {
        try {
            for (int fontSize = 11; fontSize <= 22; fontSize++) {
                generateFontForCounters(fontSize);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Color fromDetailColor(DetailColors detailColor) {
        return FSkinColor.fromRGB(detailColor.r, detailColor.g, detailColor.b);
    }

    public static Color getRarityColor(CardRarity rarity) {
        switch(rarity) {
        case Uncommon:
            return fromDetailColor(DetailColors.UNCOMMON);
        case Rare:
            return fromDetailColor(DetailColors.RARE);
        case MythicRare:
            return fromDetailColor(DetailColors.MYTHIC);
        case Special: //"Timeshifted" or other Special Rarity Cards
            return fromDetailColor(DetailColors.SPECIAL);
        default: //case BasicLand: + case Common:
            return fromDetailColor(DetailColors.COMMON);
        }
    }

    public static float getCardListItemHeight(boolean compactMode) {
        if (compactMode) {
            return MANA_SYMBOL_SIZE + 2 * FList.PADDING;
        }
        return Math.round(MANA_SYMBOL_SIZE + FSkinFont.get(12).getLineHeight() + 3 * FList.PADDING + 1);
    }

    private static final Map<String, FImageComplex> cardArtCache = new HashMap<String, FImageComplex>();
    public static final float CARD_ART_RATIO = 1.302f;
    public static final float CARD_ART_HEIGHT_PERCENTAGE = 0.43f;

    //extract card art from the given card
    public static FImageComplex getCardArt(IPaperCard pc) {
        CardType type = pc.getRules().getType();
        return getCardArt(pc.getImageKey(false), pc.getRules().getSplitType() == CardSplitType.Split, type.isPlane() || type.isPhenomenon(),pc.getRules().getOracleText().contains("Aftermath"));
    }

    public static FImageComplex getCardArt(CardView card) {
        CardTypeView type = card.getCurrentState().getType();
        return getCardArt(card.getCurrentState().getImageKey(), card.isSplitCard(), type.isPlane() || type.isPhenomenon(),card.getText().contains("Aftermath"));
    }

    public static FImageComplex getCardArt(String imageKey, boolean isSplitCard, boolean isHorizontalCard, boolean isAftermathCard) {
        FImageComplex cardArt = cardArtCache.get(imageKey);
        if (cardArt == null) {
            Texture image = ImageCache.getImage(imageKey, true);
            if (image != null) {
                if (image == ImageCache.defaultImage) {
                    cardArt = CardImageRenderer.forgeArt;
                }
                else {
                    float x, y;
                    float w = image.getWidth();
                    float h = image.getHeight();
                    if (isSplitCard && !isAftermathCard) { //allow rotated image for split cards
                        x = w * 33f / 250f;
                        y = 0; //delay adjusting y and h until drawn
                        w *= 106f / 250f;
                    }
                    else if (isHorizontalCard) { //allow rotated image for horizontal cards
                        float artX = 40f, artY = 40f;
                        float artW = 350f, artH = 156f;
                        float srcW = 430f, srcH = 300f;
                        if (w > h) {
                            x = w * 40f / 430f;
                            y = h * 40f / srcH;
                            w *= artW / srcW;
                            h *= artH / srcH;
                        }
                        else { //rotate art clockwise if its not the correct orientation
                            x = w * artY / srcH;
                            y = h * (srcW - artW - artX) / srcW;
                            w *= artH / srcH;
                            h *= artW / srcW;
                            cardArt = new FRotatedImage(image, Math.round(x), Math.round(y), Math.round(w), Math.round(h), true);
                            cardArtCache.put(imageKey, cardArt);
                            return cardArt;
                        }
                    }
                    else {
                        x = w * 0.1f;
                        y = h * 0.11f;
                        w -= 2 * x;
                        h *= CARD_ART_HEIGHT_PERCENTAGE;
                        float ratioRatio = w / h / CARD_ART_RATIO;
                        if (ratioRatio > 1) { //if too wide, shrink width
                            float dw = w * (ratioRatio - 1);
                            w -= dw;
                            x += dw / 2;
                        }
                        else { //if too tall, shrink height
                            float dh = h * (1 - ratioRatio);
                            h -= dh;
                            y += dh / 2;
                        }
                    }
                    cardArt = new FTextureRegionImage(new TextureRegion(image, Math.round(x), Math.round(y), Math.round(w), Math.round(h)));
                }
                cardArtCache.put(imageKey, cardArt);
            }
        }
        return cardArt;
    }

    public static FImageComplex getAftermathSecondCardArt(String imageKey) {
        FImageComplex cardArt = cardArtCache.get("Aftermath_second_"+imageKey);
        if (cardArt == null) {
            Texture image = ImageCache.getImage(imageKey, true);
            if (image != null) {
                if (image == ImageCache.defaultImage) {
                    cardArt = CardImageRenderer.forgeArt;
                }
                else {
                    float x, y;
                    float w = image.getWidth();
                    float h = image.getHeight();
                    //allow rotated image for split cards
                        x = w * 138f / 250f;
                        y = h * 210f / 370f; //delay adjusting y and h until drawn
                        w *= 68f / 250f;
                        h *= 128f / 370f;

                    cardArt = new FTextureRegionImage(new TextureRegion(image, Math.round(x), Math.round(y), Math.round(w), Math.round(h)));

                }
                cardArtCache.put("Aftermath_second_"+imageKey, cardArt);
            }
        }
        return cardArt;
    }

    public static void drawCardListItem(Graphics g, FSkinFont font, FSkinColor foreColor, CardView card, int count, String suffix, float x, float y, float w, float h, boolean compactMode) {
        final CardStateView state = card.getCurrentState();
        if (card.getId() > 0) {
            drawCardListItem(g, font, foreColor, getCardArt(card), card, state.getSetCode(),
                    state.getRarity(), state.getPower(), state.getToughness(),
                    state.getLoyalty(), count, suffix, x, y, w, h, compactMode);
        }
        else { //if fake card, just draw card name centered
            String name = state.getName();
            if (count > 0) { //preface name with count if applicable
                name = count + " " + name;
            }
            if (suffix != null) {
                name += suffix;
            }
            g.drawText(name, font, foreColor, x, y, w, h, false, HAlignment.CENTER, true);
        }
    }

    public static void drawCardListItem(Graphics g, FSkinFont font, FSkinColor foreColor, IPaperCard pc, int count, String suffix, float x, float y, float w, float h, boolean compactMode) {
        final CardView card = CardView.getCardForUi(pc);
        final CardStateView state = card.getCurrentState();
        drawCardListItem(g, font, foreColor, getCardArt(pc), card, pc.getEdition(),
                pc.getRarity(), state.getPower(), state.getToughness(),
                state.getLoyalty(), count, suffix, x, y, w, h, compactMode);
    }

    public static void drawCardListItem(Graphics g, FSkinFont font, FSkinColor foreColor, FImageComplex cardArt, CardView card, String set, CardRarity rarity, int power, int toughness, int loyalty, int count, String suffix, float x, float y, float w, float h, boolean compactMode) {
        float cardArtHeight = h + 2 * FList.PADDING;
        float cardArtWidth = cardArtHeight * CARD_ART_RATIO;
        if (cardArt != null) {
            float artX = x - FList.PADDING;
            float artY = y - FList.PADDING;

            if (card.isSplitCard() && !card.getText().contains("Aftermath")) {
                //draw split art with proper orientation
                float srcY = cardArt.getHeight() * 13f / 354f;
                float srcHeight = cardArt.getHeight() * 150f / 354f;
                float dh = srcHeight * (1 - cardArt.getWidth() / srcHeight / CARD_ART_RATIO);
                srcHeight -= dh;
                srcY += dh / 2;
                g.drawRotatedImage(cardArt.getTexture(), artX, artY, cardArtHeight, cardArtWidth / 2, artX + cardArtWidth / 2, artY + cardArtWidth / 2, cardArt.getRegionX(), (int)srcY, (int)cardArt.getWidth(), (int)srcHeight, -90);
                g.drawRotatedImage(cardArt.getTexture(), artX, artY + cardArtWidth / 2, cardArtHeight, cardArtWidth / 2, artX + cardArtWidth / 2, artY + cardArtWidth / 2, cardArt.getRegionX(), (int)cardArt.getHeight() - (int)(srcY + srcHeight), (int)cardArt.getWidth(), (int)srcHeight, -90);
            }
            else if (card.getText().contains("Aftermath")) {
                FImageComplex secondArt = CardRenderer.getAftermathSecondCardArt(card.getCurrentState().getImageKey());
                g.drawRotatedImage(cardArt.getTexture(), artX, artY, cardArtWidth, cardArtHeight / 2, artX + cardArtWidth, artY + cardArtHeight / 2, cardArt.getRegionX(), cardArt.getRegionY(), (int)cardArt.getWidth(), (int)cardArt.getHeight() /2, 0);
                g.drawRotatedImage(secondArt.getTexture(), artX - cardArtHeight / 2 , artY + cardArtHeight / 2, cardArtHeight /2, cardArtWidth, artX, artY + cardArtHeight / 2, secondArt.getRegionX(), secondArt.getRegionY(), (int)secondArt.getWidth(), (int)secondArt.getHeight(), 90);
            }
            else {
                g.drawImage(cardArt, artX, artY, cardArtWidth, cardArtHeight);
            }
        }

        //render card name and mana cost on first line
        float manaCostWidth = 0;
        ManaCost mainManaCost = card.getCurrentState().getManaCost();
        if (card.isSplitCard()) {
            //handle rendering both parts of split card
            ManaCost otherManaCost = card.getAlternateState().getManaCost();
            manaCostWidth = CardFaceSymbols.getWidth(otherManaCost, MANA_SYMBOL_SIZE) + MANA_COST_PADDING;
            CardFaceSymbols.drawManaCost(g, otherManaCost, x + w - manaCostWidth + MANA_COST_PADDING, y, MANA_SYMBOL_SIZE);
            //draw "//" between two parts of mana cost
            manaCostWidth += font.getBounds("//").width + MANA_COST_PADDING;
            g.drawText("//", font, foreColor, x + w - manaCostWidth + MANA_COST_PADDING, y, w, MANA_SYMBOL_SIZE, false, HAlignment.LEFT, true);
        }
        manaCostWidth += CardFaceSymbols.getWidth(mainManaCost, MANA_SYMBOL_SIZE);
        CardFaceSymbols.drawManaCost(g, mainManaCost, x + w - manaCostWidth, y, MANA_SYMBOL_SIZE);

        x += cardArtWidth;
        String name = card.getCurrentState().getName();
        if (count > 0) { //preface name with count if applicable
            name = count + " " + name;
        }
        if (suffix != null) {
            name += suffix;
        }
        g.drawText(name, font, foreColor, x, y, w - manaCostWidth - cardArtWidth - FList.PADDING, MANA_SYMBOL_SIZE, false, HAlignment.LEFT, true);

        if (compactMode) {
            return; //skip second line if rendering in compact mode
        }

        y += MANA_SYMBOL_SIZE + FList.PADDING + SET_BOX_MARGIN;

        //render card type, p/t, and set box on second line
        FSkinFont typeFont = FSkinFont.get(12);
        float availableTypeWidth = w - cardArtWidth;
        float lineHeight = typeFont.getLineHeight();
        if (!StringUtils.isEmpty(set)) {
            float setWidth = getSetWidth(typeFont, set);
            availableTypeWidth -= setWidth;
            drawSetLabel(g, typeFont, set, rarity, x + availableTypeWidth + SET_BOX_MARGIN, y - SET_BOX_MARGIN, setWidth, lineHeight + 2 * SET_BOX_MARGIN);
        }
        String type = CardDetailUtil.formatCardType(card.getCurrentState(), true);
        if (card.getCurrentState().isCreature()) { //include P/T or Loyalty at end of type
            type += " (" + power + " / " + toughness + ")";
        }
        else if (card.getCurrentState().isPlaneswalker()) {
            type += " (" + loyalty + ")";
        }
        else if (card.getCurrentState().getType().hasSubtype("Vehicle")) {
            type += String.format(" [%s / %s]", power, toughness);
        }
        g.drawText(type, typeFont, foreColor, x, y, availableTypeWidth, lineHeight, false, HAlignment.LEFT, true);
    }

    public static boolean cardListItemTap(List<?> cards, int selectedIndex, ActivateHandler activateHandler, float x, float y, int count, boolean compactMode) {
        if (x <= getCardListItemHeight(compactMode) * CARD_ART_RATIO) {
            CardZoom.show(cards, selectedIndex, activateHandler);
            return true;
        }
        return false;
    }

    public static boolean paperCardListItemTap(List<?> cards, int selectedIndex, ActivateHandler activateHandler, float x, float y, int count, boolean compactMode) {
        float cardArtHeight = getCardListItemHeight(compactMode);
        float cardArtWidth = cardArtHeight * CARD_ART_RATIO;
        if (x <= cardArtWidth && y <= cardArtHeight) {
            CardZoom.show(cards, selectedIndex, activateHandler);
            return true;
        }
        return false;
    }

    public static float getSetWidth(FSkinFont font, String set) {
        return font.getBounds(set).width + font.getCapHeight();
    }

    public static void drawSetLabel(Graphics g, FSkinFont font, String set, CardRarity rarity, float x, float y, float w, float h) {
        Color backColor = getRarityColor(rarity);
        Color foreColor = FSkinColor.getHighContrastColor(backColor);
        g.fillRect(backColor, x, y, w, h);
        g.drawText(set, font, foreColor, x, y, w, h, false, HAlignment.CENTER, true);
    }

    public static void drawCard(Graphics g, IPaperCard pc, float x, float y, float w, float h, CardStackPosition pos) {
        Texture image = ImageCache.getImage(pc);
        if (image != null) {
            if (image == ImageCache.defaultImage) {
                CardImageRenderer.drawCardImage(g, CardView.getCardForUi(pc), false, x, y, w, h, pos);
            }
            else {
                g.drawImage(image, x, y, w, h);
            }
            if (pc.isFoil()) { //draw foil effect if needed
                final CardView card = CardView.getCardForUi(pc);
                if (card.getCurrentState().getFoilIndex() == 0) { //if foil finish not yet established, assign a random one
                    card.getCurrentState().setFoilIndexOverride(-1);
                }
                drawFoilEffect(g, card, x, y, w, h);
            }
        }
        else { //draw cards without textures as just a black rectangle
            g.fillRect(Color.BLACK, x, y, w, h);
        }
    }

    public static void drawCard(Graphics g, CardView card, float x, float y, float w, float h, CardStackPosition pos, boolean rotate) {
        Texture image = ImageCache.getImage(card);
        if (image != null) {
            if (image == ImageCache.defaultImage) {
                CardImageRenderer.drawCardImage(g, card, false, x, y, w, h, pos);
            }
            else {
                if(FModel.getPreferences().getPrefBoolean(ForgePreferences.FPref.UI_ROTATE_PLANE_OR_PHENOMENON)
                        && (card.getCurrentState().isPhenomenon() || card.getCurrentState().isPlane()) && rotate)
                    g.drawRotatedImage(image, x, y, w, h, x + w / 2, y + h / 2, -90);
                else
                    g.drawImage(image, x, y, w, h);
            }
            drawFoilEffect(g, card, x, y, w, h);
        }
        else { //draw cards without textures as just a black rectangle
            g.fillRect(Color.BLACK, x, y, w, h);
        }
    }

    public static void drawCardWithOverlays(Graphics g, CardView card, float x, float y, float w, float h, CardStackPosition pos) {
        drawCard(g, card, x, y, w, h, pos, false);

        float padding = w * PADDING_MULTIPLIER; //adjust for card border
        x += padding;
        y += padding;
        w -= 2 * padding;
        h -= 2 * padding;

        boolean canShow = MatchController.instance.mayView(card);

        // TODO: A hacky workaround is currently used to make the game not leak the color information for Morph cards.
        final CardStateView details = card.getCurrentState();
        final boolean isFaceDown = details.getState() == CardStateName.FaceDown;
        final DetailColors borderColor = isFaceDown ? CardDetailUtil.DetailColors.FACE_DOWN : CardDetailUtil.getBorderColor(details, canShow); // canShow doesn't work here for face down Morphs
        Color color = FSkinColor.fromRGB(borderColor.r, borderColor.g, borderColor.b);
        color = FSkinColor.tintColor(Color.WHITE, color, CardRenderer.PT_BOX_TINT);

        //draw name and mana cost overlays if card is small or default card image being used
        if (h <= NAME_COST_THRESHOLD && canShow) {
            if (showCardNameOverlay(card)) {
                g.drawOutlinedText(details.getName(), FSkinFont.forHeight(h * 0.18f), Color.WHITE, Color.BLACK, x + padding, y + padding, w - 2 * padding, h * 0.4f, true, HAlignment.LEFT, false);
            }
            if (showCardManaCostOverlay(card)) {
                float manaSymbolSize = w / 4;
                if (card.isSplitCard() && card.hasAlternateState()) {
                    if (!card.isFaceDown()) { // no need to draw mana symbols on face down split cards (e.g. manifested)
                        float dy = manaSymbolSize / 2 + Utils.scale(5);

                        PaperCard pc = StaticData.instance().getCommonCards().getCard(card.getName());
                        if (Card.getCardForUi(pc).hasKeyword("Aftermath")){
                            dy *= -1; // flip card costs for Aftermath cards
                        }

                        drawManaCost(g, card.getAlternateState().getManaCost(), x - padding, y - dy, w + 2 * padding, h, manaSymbolSize);
                        drawManaCost(g, card.getCurrentState().getManaCost(), x - padding, y + dy, w + 2 * padding, h, manaSymbolSize);
                    }
                }
                else {
                    drawManaCost(g, card.getCurrentState().getManaCost(), x - padding, y, w + 2 * padding, h, manaSymbolSize);
                }
            }
        }

        if (pos == CardStackPosition.BehindVert) { return; } //remaining rendering not needed if card is behind another card in a vertical stack
        boolean onTop = (pos == CardStackPosition.Top);

        if (canShow && showCardIdOverlay(card)) {
            FSkinFont idFont = FSkinFont.forHeight(h * 0.12f);
            float idHeight = idFont.getCapHeight();
            g.drawOutlinedText(String.valueOf(card.getId()), idFont, Color.WHITE, Color.BLACK, x + padding, y + h - idHeight - padding, w, h, false, HAlignment.LEFT, false);
        }

        if (card.getCounters() != null && !card.getCounters().isEmpty()) {

            switch (CounterDisplayType.from(FModel.getPreferences().getPref(FPref.UI_CARD_COUNTER_DISPLAY_TYPE))) {
                case OLD_WHEN_SMALL:
                case TEXT:
                    drawCounterTabs(card, g, x, y, w, h);
                    break;
                case IMAGE:
                    drawCounterImage(card, g, x, y, w, h);
                    break;
                case HYBRID:
                    drawCounterImage(card, g, x, y, w, h);
                    drawCounterTabs(card, g, x, y, w, h);
                    break;
            }

        }

        float otherSymbolsSize = w / 3.5f;
        final float combatXSymbols = (x + (w / 4)) - otherSymbolsSize / 2 - 10;
        final float stateXSymbols = (x + (w / 2)) - otherSymbolsSize / 2 - 10;
        final float ySymbols = (y + h) - (h / 12) - otherSymbolsSize / 2;

        if (card.isAttacking()) {
            CardFaceSymbols.drawSymbol("attack", g, combatXSymbols, ySymbols, otherSymbolsSize, otherSymbolsSize);
        }
        else if (card.isBlocking()) {
            CardFaceSymbols.drawSymbol("defend", g, combatXSymbols, ySymbols, otherSymbolsSize, otherSymbolsSize);
        }

        if (onTop && card.isSick()) {
            //only needed if on top since otherwise symbol will be hidden
            CardFaceSymbols.drawSymbol("summonsick", g, stateXSymbols, ySymbols, otherSymbolsSize, otherSymbolsSize);
        }

        if (card.isPhasedOut()) {
            CardFaceSymbols.drawSymbol("phasing", g, stateXSymbols, ySymbols, otherSymbolsSize, otherSymbolsSize);
        }

        if (MatchController.instance.isUsedToPay(card)) {
            float sacSymbolSize = otherSymbolsSize * 1.2f;
            CardFaceSymbols.drawSymbol("sacrifice", g, (x + (w / 2)) - sacSymbolSize / 2, (y + (h / 2)) - sacSymbolSize / 2, otherSymbolsSize, otherSymbolsSize);
        }

        if (onTop && showCardPowerOverlay(card) && (canShow || card.isFaceDown())) { //make sure card p/t box appears on top
            //only needed if on top since otherwise P/T will be hidden
            drawPtBox(g, card, details, color, x, y, w, h);
        }

    }

    private static void drawCounterTabs(final CardView card, final Graphics g, final float x, final float y, final float w, final float h) {

        int fontSize = Math.max(11, Math.min(22, (int) (h * 0.08)));
        BitmapFont font = counterFonts.get(fontSize);

        final float additionalXOffset = 3f * ((fontSize - 11) / 11f);
        final float variableWidth = ((fontSize - 11) / 11f) * 44f;

        float otherSymbolsSize = w / 3.5f;
        final float ySymbols = (h / 12) - otherSymbolsSize / 2;

        final float counterBoxHeight = 9 + fontSize;
        final float counterBoxBaseWidth = 50 + variableWidth + additionalXOffset * 2;
        final float counterBoxSpacing = -4;

        final float spaceFromTopOfCard = y + h - counterBoxHeight - counterBoxSpacing - otherSymbolsSize + ySymbols;

        int currentCounter = 0;

        if (CounterDisplayType.from(FModel.getPreferences().getPref(FPref.UI_CARD_COUNTER_DISPLAY_TYPE)) == CounterDisplayType.OLD_WHEN_SMALL) {

            int maxCounters = 0;
            for (Integer numberOfCounters : card.getCounters().values()) {
                maxCounters = Math.max(maxCounters, numberOfCounters);
            }

            if (counterBoxBaseWidth + font.getBounds(String.valueOf(maxCounters)).width > w) {
                drawCounterImage(card, g, x, y, w, h);
                return;
            }

        }

        for (Map.Entry<CounterType, Integer> counterEntry : card.getCounters().entrySet()) {

            final CounterType counter = counterEntry.getKey();
            final int numberOfCounters = counterEntry.getValue();
            final float counterBoxRealWidth = counterBoxBaseWidth + font.getBounds(String.valueOf(numberOfCounters)).width + 4;

            final float counterYOffset = spaceFromTopOfCard - (currentCounter++ * (counterBoxHeight + counterBoxSpacing));

            g.fillRect(counterBackgroundColor, x - 3, counterYOffset, counterBoxRealWidth, counterBoxHeight);

            if (!counterColorCache.containsKey(counter)) {
                counterColorCache.put(counter, new Color(counter.getRed() / 255.0f, counter.getGreen() / 255.0f, counter.getBlue() / 255.0f, 1.0f));
            }

            Color counterColor = counterColorCache.get(counter);

            drawText(g, counter.getCounterOnCardDisplayName(), font, counterColor, x + 2 + additionalXOffset, counterYOffset, counterBoxRealWidth, counterBoxHeight, HAlignment.LEFT);
            drawText(g, String.valueOf(numberOfCounters), font, counterColor, x + counterBoxBaseWidth - 4f - additionalXOffset, counterYOffset, counterBoxRealWidth, counterBoxHeight, HAlignment.LEFT);

        }

    }

    private static final int GL_BLEND = GL20.GL_BLEND;

    private static void drawText(Graphics g, String text, BitmapFont font, Color color, float x, float y, float w, float h, HAlignment horizontalAlignment) {

        if (color.a < 1) { //enable blending so alpha colored shapes work properly
            Gdx.gl.glEnable(GL_BLEND);
        }

        TextBounds textBounds = font.getMultiLineBounds(text);

        float textHeight = textBounds.height;
        if (h > textHeight) {
            y += (h - textHeight) / 2;
        }

        font.setColor(color);
        font.drawMultiLine(g.getBatch(), text, g.adjustX(x), g.adjustY(y, 0), w, horizontalAlignment);

        if (color.a < 1) {
            Gdx.gl.glDisable(GL_BLEND);
        }

    }

    private static void drawCounterImage(final CardView card, final Graphics g, final float x, final float y, final float w, final float h) {

        int number = 0;
        if (card.getCounters() != null) {
            for (final Integer i : card.getCounters().values()) {
                number += i;
            }
        }

        final int counters = number;

        float countersSize = w / 2;
        final float xCounters = x - countersSize / 2;
        final float yCounters = y + h * 2 / 3 - countersSize;

        if (counters == 1) {
            CardFaceSymbols.drawSymbol("counters1", g, xCounters, yCounters, countersSize, countersSize);
        }
        else if (counters == 2) {
            CardFaceSymbols.drawSymbol("counters2", g, xCounters, yCounters, countersSize, countersSize);
        }
        else if (counters == 3) {
            CardFaceSymbols.drawSymbol("counters3", g, xCounters, yCounters, countersSize, countersSize);
        }
        else if (counters > 3) {
            CardFaceSymbols.drawSymbol("countersMulti", g, xCounters, yCounters, countersSize, countersSize);
        }

    }

    private static void drawPtBox(Graphics g, CardView card, CardStateView details, Color color, float x, float y, float w, float h) {
        //use array of strings to render separately with a tiny amount of space in between
        //instead of using actual spaces which are too wide
        List<String> pieces = new ArrayList<String>();
        if (details.isCreature()) {
            pieces.add(String.valueOf(details.getPower()));
            pieces.add("/");
            pieces.add(String.valueOf(details.getToughness()));
        }
        else if (details.getType().hasSubtype("Vehicle")) {
            pieces.add("[");
            pieces.add(String.valueOf(details.getPower()));
            pieces.add("/");
            pieces.add(String.valueOf(details.getToughness()));
            pieces.add("]");
        }
        if (details.isPlaneswalker()) {
            if (pieces.isEmpty()) {
                pieces.add(String.valueOf(details.getLoyalty()));
            }
            else {
                pieces.add("(" + details.getLoyalty() + ")");
            }
        }

        if (pieces.isEmpty()) { return; }

        FSkinFont font = FSkinFont.forHeight(h * 0.15f);
        float padding = Math.round(font.getCapHeight() / 4);
        float boxWidth = padding;
        List<Float> pieceWidths = new ArrayList<Float>();
        for (String piece : pieces) {
            float pieceWidth = font.getBounds(piece).width + padding;
            pieceWidths.add(pieceWidth);
            boxWidth += pieceWidth;
        }
        float boxHeight = font.getCapHeight() + font.getAscent() + 2 * padding;

        x += w - boxWidth;
        y += h - boxHeight;
        w = boxWidth;
        h = boxHeight;

        //draw card damage above P/T box if needed
        if (card.getDamage() > 0) {
            g.drawOutlinedText(">" + card.getDamage() + "<", font, Color.RED, Color.WHITE, x, y - h + padding, w, h, false, HAlignment.CENTER, true);
        }

        g.fillRect(color, x, y, w, h);
        g.drawRect(BORDER_THICKNESS, Color.BLACK, x, y, w, h);

        x += padding;
        for (int i = 0; i < pieces.size(); i++) {
            g.drawText(pieces.get(i), font, Color.BLACK, x, y, w, h, false, HAlignment.LEFT, true);
            x += pieceWidths.get(i);
        }
    }

    private static void drawManaCost(Graphics g, ManaCost cost, float x, float y, float w, float h, float manaSymbolSize) {
        float manaCostWidth = CardFaceSymbols.getWidth(cost, manaSymbolSize);
        if (manaCostWidth > w) {
            manaCostWidth = w;
            manaSymbolSize = w / cost.getGlyphCount();
        }
        CardFaceSymbols.drawManaCost(g, cost, x + (w - manaCostWidth) / 2, y + (h - manaSymbolSize) / 2, manaSymbolSize);
    }

    public static void drawFoilEffect(Graphics g, CardView card, float x, float y, float w, float h) {
        if (isPreferenceEnabled(FPref.UI_OVERLAY_FOIL_EFFECT) && MatchController.instance.mayView(card)) {
            int foil = card.getCurrentState().getFoilIndex();
            if (foil > 0) {
                CardFaceSymbols.drawOther(g, String.format("foil%02d", foil), x, y, w, h, card.isSplitCard());
            }
        }
    }

    private static boolean isPreferenceEnabled(FPref preferenceName) {
        return FModel.getPreferences().getPrefBoolean(preferenceName);
    }

    private static boolean isShowingOverlays(CardView card) {
        return isPreferenceEnabled(FPref.UI_SHOW_CARD_OVERLAYS) && card != null;
    }

    private static boolean showCardNameOverlay(CardView card) {
        return isShowingOverlays(card) && isPreferenceEnabled(FPref.UI_OVERLAY_CARD_NAME);
    }

    private static boolean showCardPowerOverlay(CardView card) {
        return isShowingOverlays(card) && isPreferenceEnabled(FPref.UI_OVERLAY_CARD_POWER);
    }

    private static boolean showCardManaCostOverlay(CardView card) {
        return isShowingOverlays(card) &&
                isPreferenceEnabled(FPref.UI_OVERLAY_CARD_MANA_COST);
    }

    private static boolean showCardIdOverlay(CardView card) {
        return card.getId() > 0 && isShowingOverlays(card) && isPreferenceEnabled(FPref.UI_OVERLAY_CARD_ID);
    }

    //TODO Make FSkinFont accept more than one kind of font and merge this with it
    private static void generateFontForCounters(final int fontSize) {

        FileHandle ttfFile = Gdx.files.absolute(ForgeConstants.COMMON_FONTS_DIR).child("Roboto-Bold.ttf");

        if (!ttfFile.exists()) { return; }

        final FreeTypeFontGenerator generator = new FreeTypeFontGenerator(ttfFile);

        //approximate optimal page size
        int pageSize = 128;

        //only generate images for characters that could be used by Forge
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890/-+";

        final PixmapPacker packer = new PixmapPacker(pageSize, pageSize, Pixmap.Format.RGBA8888, 2, false);
        final FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.characters = chars;
        parameter.size = fontSize;
        parameter.packer = packer;
        final FreeTypeFontGenerator.FreeTypeBitmapFontData fontData = generator.generateData(parameter);
        final Array<PixmapPacker.Page> pages = packer.getPages();

        //TODO Cache this
        //finish generating font on UI thread
        FThreads.invokeInEdtNowOrLater(new Runnable() {
            @Override
            public void run() {

                TextureRegion[] textureRegions = new TextureRegion[pages.size];
                for (int i = 0; i < pages.size; i++) {
                    PixmapPacker.Page p = pages.get(i);
                    Texture texture = new Texture(new PixmapTextureData(p.getPixmap(), p.getPixmap().getFormat(), false, false)) {
                        @Override
                        public void dispose() {
                            super.dispose();
                            getTextureData().consumePixmap().dispose();
                        }
                    };
                    texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                    textureRegions[i] = new TextureRegion(texture);
                }

                counterFonts.put(fontSize, new BitmapFont(fontData, textureRegions, true));

                generator.dispose();
                packer.dispose();

            }
        });

    }

}
