package forge.toolbox;


import com.badlogic.gdx.graphics.g2d.GlyphLayout;

/**
 * Created by Relintai on 2017. 10. 06..
 */

public class TextBounds {
    public float width;
    public float height;

    public TextBounds () {
    }

    public TextBounds (TextBounds bounds) {
        set(bounds);
    }

    public void set (TextBounds bounds) {
        width = bounds.width;
        height = bounds.height;
    }

    public void set (GlyphLayout glyphLayout) {
        width = glyphLayout.width;
        height = glyphLayout.height;
    }
}