package dev.georgekazan;

import com.google.inject.Provides;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.Text;
import okhttp3.HttpUrl;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.stream.Stream;

@Slf4j
@PluginDescriptor(
        name = "BrowserWiki",
        description = "A rework of the wiki plugin to open an embedded browser"
)
public class BrowserWikiPlugin extends Plugin {
    static final HttpUrl WIKI_BASE = HttpUrl.get("https://oldschool.runescape.wiki");
    static final HttpUrl WIKI_API = WIKI_BASE.newBuilder().addPathSegments("api.php").build();
    static final String UTM_SORUCE_KEY = "utm_source";
    static final String UTM_SORUCE_VALUE = "runelite";

    private static final String MENUOP_WIKI = "wiki";

    public static BrowserWikiPlugin Instance;

    Stage stage;
    WebView browser;
    WebEngine webEngine;
    JFXPanel jfxPanel = new JFXPanel();
    @Inject
    private BrowserWikiConfig config;

    @Inject
    private ClientThread clientThread;

    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private Provider<WikiSearchBox> WikiSearchBoxProvider;

    private Widget icon;

    private boolean wikiSelected = false;

    static final String CONFIG_GROUP_KEY = "wiki";
    private JFrame browserFrame = null;

    @Provides
    BrowserWikiConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(BrowserWikiConfig.class);
    }

    @Override
    public void startUp() {
        clientThread.invokeLater(this::addWidgets);
        Instance = this;

        SwingUtilities.invokeLater(() -> {
            browserFrame = new JFrame("Browser");
            browserFrame.setFocusableWindowState(false);
            browserFrame.setType(Window.Type.UTILITY);
            browserFrame.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2));

            browserFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            browserFrame.setAlwaysOnTop(config.alwaysOnTop());

            Platform.runLater(() -> {
                stage = new Stage();
                stage.setMaximized(true);

                stage.setTitle("Browser2");
                stage.setResizable(false);

                Group root = new Group();
                Scene scene = new Scene(root);
                stage.setScene(scene);

                browser = new WebView();
                webEngine = browser.getEngine();

                ObservableList<Node> children = root.getChildren();
                children.add(browser);

                jfxPanel.setPreferredSize(new Dimension(config.sizeX(), config.sizeY()));
                jfxPanel.setScene(scene);
            });

            browserFrame.setPreferredSize(new Dimension(config.sizeX(), config.sizeY()));
            browserFrame.setResizable(false);

            browserFrame.add(jfxPanel);

            browserFrame.pack();
            browserFrame.setLocationRelativeTo(null);
            browserFrame.setVisible(false);
        });

    }

    @Override
    public void shutDown() {
        clientThread.invokeLater(this::removeWidgets);

        if (browserFrame != null) {
            browserFrame.setVisible(false);
            browserFrame.dispose();
            browserFrame = null;
        }

        for (Frame frame : Frame.getFrames()) {
            if (frame.getTitle() != null && frame.getTitle().equals("Browser")) {
                frame.setVisible(false);
                frame.dispose();
            }
        }
    }


    private void removeWidgets() {
        Widget wikiBannerParent = client.getWidget(WidgetInfo.MINIMAP_WIKI_BANNER_PARENT);
        if (wikiBannerParent == null) {
            return;
        }
        Widget[] children = wikiBannerParent.getChildren();
        if (children == null || children.length < 1) {
            return;
        }
        children[0] = null;

        Widget vanilla = client.getWidget(WidgetInfo.MINIMAP_WIKI_BANNER);
        if (vanilla != null && client.getVarbitValue(Varbits.WIKI_ENTITY_LOOKUP) == 0) {
            vanilla.setHidden(false);
        }

        onDeselect();
        client.setWidgetSelected(false);
    }

    @Subscribe
    private void onWidgetLoaded(WidgetLoaded l) {
        if (l.getGroupId() == WidgetID.MINIMAP_GROUP_ID) {
            addWidgets();
        }
    }

    private void addWidgets() {
        Widget wikiBannerParent = client.getWidget(WidgetInfo.MINIMAP_WIKI_BANNER_PARENT);
        if (wikiBannerParent == null) {
            return;
        }

        if (client.getVarbitValue(Varbits.WIKI_ENTITY_LOOKUP) == 1) {
            wikiBannerParent.setOriginalX(client.isResized() ? 0 : 8);
            wikiBannerParent.setOriginalY(135);
            wikiBannerParent.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
            wikiBannerParent.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            wikiBannerParent.revalidate();
        }

        Widget vanilla = client.getWidget(WidgetInfo.MINIMAP_WIKI_BANNER);
        if (vanilla != null) {
            vanilla.setHidden(true);
        }

        icon = wikiBannerParent.createChild(0, WidgetType.GRAPHIC);

        icon.setSpriteId(SpriteID.WIKI_DESELECTED);
        icon.setOriginalX(0);
        icon.setOriginalY(0);
        icon.setXPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
        icon.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
        icon.setOriginalWidth(40);
        icon.setOriginalHeight(14);
        icon.setTargetVerb("Lookup");
        icon.setName("wiki");
        icon.setClickMask(WidgetConfig.USE_GROUND_ITEM | WidgetConfig.USE_ITEM | WidgetConfig.USE_NPC
                | WidgetConfig.USE_OBJECT | WidgetConfig.USE_WIDGET);
        icon.setNoClickThrough(true);
        icon.setOnTargetEnterListener((JavaScriptCallback) ev ->
        {
            wikiSelected = true;
            icon.setSpriteId(SpriteID.WIKI_SELECTED);
            client.setAllWidgetsAreOpTargetable(true);
        });

        final int searchIndex = 4;
        icon.setAction(searchIndex, "Search");
        icon.setOnOpListener((JavaScriptCallback) ev ->
        {
            if (ev.getOp() == searchIndex + 1) {
                openSearchInput();
            }
        });

        icon.setOnTargetLeaveListener((JavaScriptCallback) ev -> onDeselect());
        icon.revalidate();
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired scriptPostFired) {
        if (scriptPostFired.getScriptId() == ScriptID.WIKI_ICON_UPDATE) {
            Widget w = client.getWidget(WidgetInfo.MINIMAP_WIKI_BANNER);
            w.setHidden(true);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals(CONFIG_GROUP_KEY)) {
            clientThread.invokeLater(() ->
            {
                removeWidgets();
                addWidgets();
            });
        }
    }

    private void onDeselect() {
        client.setAllWidgetsAreOpTargetable(false);

        wikiSelected = false;
        if (icon != null) {
            icon.setSpriteId(SpriteID.WIKI_DESELECTED);
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked ev) {
        optarget:
        if (wikiSelected) {
            onDeselect();
            client.setWidgetSelected(false);
            ev.consume();

            String type;
            int id;
            String name;
            WorldPoint location;

            switch (ev.getMenuAction()) {
                case RUNELITE:
                    break optarget;
                case CANCEL:
                    return;
                case WIDGET_USE_ON_ITEM:
                case WIDGET_TARGET_ON_GROUND_ITEM: {
                    type = "item";
                    id = itemManager.canonicalize(ev.getId());
                    name = itemManager.getItemComposition(id).getMembersName();
                    location = null;
                    break;
                }
                case WIDGET_TARGET_ON_NPC: {
                    type = "npc";
                    NPC npc = ev.getMenuEntry().getNpc();
                    assert npc != null;
                    NPCComposition nc = npc.getTransformedComposition();
                    id = nc.getId();
                    name = nc.getName();
                    location = npc.getWorldLocation();
                    break;
                }
                case WIDGET_TARGET_ON_GAME_OBJECT: {
                    type = "object";
                    ObjectComposition lc = client.getObjectDefinition(ev.getId());
                    if (lc.getImpostorIds() != null) {
                        lc = lc.getImpostor();
                    }
                    id = lc.getId();
                    name = lc.getName();
                    location = WorldPoint.fromScene(client, ev.getParam0(), ev.getParam1(), client.getPlane());
                    break;
                }
                case WIDGET_TARGET_ON_WIDGET:
                    Widget w = getWidget(ev.getParam1(), ev.getParam0());

                    if (w.getType() == WidgetType.GRAPHIC && w.getItemId() != -1) {
                        type = "item";
                        id = itemManager.canonicalize(w.getItemId());
                        name = itemManager.getItemComposition(id).getMembersName();
                        location = null;
                        break;
                    }
                default:
                    log.info("Unknown menu option: {} {} {}", ev, ev.getMenuAction(), ev.getMenuAction() == MenuAction.CANCEL);
                    return;
            }

            name = Text.removeTags(name);
            HttpUrl.Builder urlBuilder = WIKI_BASE.newBuilder();
            urlBuilder.addPathSegments("w/Special:Lookup")
                    .addQueryParameter("type", type)
                    .addQueryParameter("id", "" + id)
                    .addQueryParameter("name", name)
                    .addQueryParameter(UTM_SORUCE_KEY, UTM_SORUCE_VALUE);

            if (location != null) {
                urlBuilder.addQueryParameter("x", "" + location.getX())
                        .addQueryParameter("y", "" + location.getY())
                        .addQueryParameter("plane", "" + location.getPlane());
            }

            HttpUrl url = urlBuilder.build();
            openBrowser(url.toString());
            return;
        }
    }

    private void openSearchInput() {
        WikiSearchBoxProvider.get()
                .build();
    }

    private Widget getWidget(int wid, int index) {
        Widget w = client.getWidget(wid);
        if (index != -1) {
            w = w.getChild(index);
        }
        return w;
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        int widgetIndex = event.getActionParam0();
        int widgetID = event.getActionParam1();

        if (wikiSelected && event.getType() == MenuAction.WIDGET_TARGET_ON_WIDGET.getId()) {
            MenuEntry[] menuEntries = client.getMenuEntries();
            Widget w = getWidget(widgetID, widgetIndex);
            if (w.getType() == WidgetType.GRAPHIC && w.getItemId() != -1 && w.getItemId() != NullItemID.NULL_6512) {
                for (int ourEntry = menuEntries.length - 1; ourEntry >= 0; ourEntry--) {
                    MenuEntry entry = menuEntries[ourEntry];
                    if (entry.getType() == MenuAction.WIDGET_TARGET_ON_WIDGET) {
                        int id = itemManager.canonicalize(w.getItemId());
                        String name = itemManager.getItemComposition(id).getMembersName();
                        entry.setTarget(JagexColors.MENU_TARGET_TAG + name);
                        break;
                    }
                }
            } else {
                MenuEntry[] oldEntries = menuEntries;
                menuEntries = Arrays.copyOf(menuEntries, menuEntries.length - 1);
                for (int ourEntry = oldEntries.length - 1;
                     ourEntry >= 2 && oldEntries[oldEntries.length - 1].getType() != MenuAction.WIDGET_TARGET_ON_WIDGET;
                     ourEntry--) {
                    menuEntries[ourEntry - 1] = oldEntries[ourEntry];
                }
                client.setMenuEntries(menuEntries);
            }
        }

        if (WidgetInfo.TO_GROUP(widgetID) == WidgetInfo.SKILLS_CONTAINER.getGroupId()) {
            Widget w = getWidget(widgetID, widgetIndex);
            if (w.getActions() == null || w.getParentId() != WidgetInfo.SKILLS_CONTAINER.getId()) {
                return;
            }

            String action = Stream.of(w.getActions())
                    .filter(s -> s != null && !s.isEmpty())
                    .findFirst().orElse(null);
            if (action == null) {
                return;
            }

            client.createMenuEntry(-1)
                    .setTarget(action.replace("View ", "").replace(" guide", ""))
                    .setOption(MENUOP_WIKI)
                    .setType(MenuAction.RUNELITE)
                    .onClick(ev -> openBrowser(WIKI_BASE.newBuilder()
                            .addPathSegment("w")
                            .addPathSegment(Text.removeTags(ev.getTarget()))
                            .addQueryParameter(UTM_SORUCE_KEY, UTM_SORUCE_VALUE)
                            .build().toString()));
        }
    }

    public void openBrowser(String url) {
        Platform.runLater(() -> {
            webEngine.load(url);
            browserFrame.toFront();
            browserFrame.setVisible(true);
            browserFrame.repaint();
        });

    }

}
