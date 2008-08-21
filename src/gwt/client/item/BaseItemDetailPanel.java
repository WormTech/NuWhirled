//
// $Id$

package client.item;

import java.util.List;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.SmartTable;
import com.threerings.gwt.ui.WidgetUtil;

import com.threerings.msoy.item.data.all.Avatar;
import com.threerings.msoy.item.data.all.Game;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.item.gwt.CatalogQuery;
import com.threerings.msoy.item.gwt.ItemDetail;
import com.threerings.msoy.item.gwt.ItemService;
import com.threerings.msoy.item.gwt.ItemServiceAsync;
import com.threerings.msoy.web.data.TagHistory;

import client.shell.Args;
import client.shell.CShell;
import client.shell.Pages;
import client.ui.CreatorLabel;
import client.ui.HeaderBox;
import client.ui.MsoyUI;
import client.ui.PopupMenu;
import client.ui.RoundBox;
import client.ui.StyledTabPanel;
import client.util.FlashClients;
import client.util.Link;
import client.util.MsoyCallback;
import client.util.ServiceUtil;

/**
 * Defines the base item detail popup from which we derive an inventory and catalog item detail.
 */
public abstract class BaseItemDetailPanel extends SmartTable
{
    protected BaseItemDetailPanel (ItemDetail detail)
    {
        super("itemDetailPanel", 0, 10);

        _detail = detail;
        _item = detail.item;

        HeaderBox bits = new HeaderBox(null, _item.name);
        SimplePanel preview = new SimplePanel();
        preview.setStyleName("ItemPreview");

        preview.setWidget(ItemUtil.createViewer(_item, userOwnsItem()));
        bits.add(preview);
        if (_item.isRatable()) {
            ItemRating rating = new ItemRating(_detail.item, _detail.memberItemInfo, true);
            rating.addStyleName("Rating");
            bits.add(rating);
        }
        setWidget(0, 0, bits);
        getFlexCellFormatter().setRowSpan(0, 0, 2);
        getFlexCellFormatter().setVerticalAlignment(0, 0, HorizontalPanel.ALIGN_TOP);

        // a place for details
        setWidget(0, 1, _details = new RoundBox(RoundBox.BLUE), 1, "Details");
        _details.setWidth("100%");
        getFlexCellFormatter().setVerticalAlignment(0, 1, HorizontalPanel.ALIGN_TOP);

        // set up our detail bits
        _details.add(_creator = new CreatorLabel());
        _creator.setMember(_detail.creator);

        // add a link to the creator's shop
        _creator.add(MsoyUI.createHTML("&nbsp;", "inline"));
        CatalogQuery query = new CatalogQuery();
        query.itemType = _detail.item.getType();
        query.creatorId = _detail.creator.getMemberId();
        Widget bshop = Link.create(_imsgs.browseCatalogFor(), Pages.SHOP,
                                   ShopUtil.composeArgs(query, 0));
        bshop.setTitle(_imsgs.browseCatalogTip(_detail.creator.toString()));
        _creator.add(bshop);

        // if we're not viewing the actual prototype item, create a link to it
        if (_item.catalogId != 0 && _item.ownerId != 0) {
            _details.add(WidgetUtil.makeShim(10, 10));
            String args = Args.compose("l", "" + _item.getType(), "" + _item.catalogId);
            _details.add(Link.create(_imsgs.viewInShop(), Pages.SHOP, args));
        }

        _details.add(WidgetUtil.makeShim(10, 10));
        _indeets = new RoundBox(RoundBox.WHITE);
        _indeets.addStyleName("Description");
        _details.add(_indeets);
        _indeets.add(MsoyUI.createRestrictedHTML(ItemUtil.getDescription(_item)));

        if (isRemixable()) {
            HorizontalPanel panel = new HorizontalPanel();
            panel.add(new Image("images/item/remixable_icon.png"));
            panel.add(WidgetUtil.makeShim(10, 10));
            panel.add(new Label(_imsgs.remixTip()));
            _indeets.add(WidgetUtil.makeShim(10, 10));
            _indeets.add(panel);
        }

        if (_item instanceof Game) {
            _details.add(WidgetUtil.makeShim(10, 10));
            String args = Args.compose("d" , ((Game)_item).gameId);
            _details.add(Link.create(_imsgs.bidPlay(), Pages.GAMES, args));
        }

        // add our tag business at the bottom
        getFlexCellFormatter().setHeight(1, 0, "10px");
        setWidget(1, 0, new TagDetailPanel(new TagDetailPanel.TagService() {
            public void tag (String tag, AsyncCallback<TagHistory> callback) {
                _itemsvc.tagItem(_item.getIdent(), tag, true, callback);
            }
            public void untag (String tag, AsyncCallback<TagHistory> callback) {
                _itemsvc.tagItem(_item.getIdent(), tag, false, callback);
            }
            public void getRecentTags (AsyncCallback<List<TagHistory>> callback) {
                _itemsvc.getRecentTags(callback);
            }
            public void getTags (AsyncCallback<List<String>> callback) {
                _itemsvc.getTags(_item.getIdent(), callback);
            }
            public boolean supportFlags () {
                return true;
            }
            public void setFlags (final byte flag) {
                ItemIdent ident = new ItemIdent(_item.getType(), _item.getPrototypeId());
                _itemsvc.setFlags(ident, flag, flag, new MsoyCallback<Void>() {
                    public void onSuccess (Void result) {
                        _item.flagged |= flag;
                    }
                });
            }
            public void addMenuItems (String tag, PopupMenu menu) {
                addTagMenuItems(tag, menu);
            }
        }, detail.tags, true));

        configureCallbacks(this);
    }

    protected void addTabBelow (String title, Widget content, boolean select)
    {
        if (_belowTabs == null) {
            addBelow(_belowTabs = new StyledTabPanel());
        }
        _belowTabs.add(content, title);
        if (select) {
            _belowTabs.selectTab(_belowTabs.getWidgetCount() - 1);
        }
    }

    /**
     * Adds a widget below the primary item detail contents.
     */
    protected void addBelow (Widget widget)
    {
        int row = getRowCount();
        setWidget(row, 0, widget);
        getFlexCellFormatter().setColSpan(row, 0, 3);
    }

    /**
     * Add any menu items to the tag widget.
     */
    protected void addTagMenuItems (String tag, PopupMenu menu)
    {
        // nothing here
    }

    /**
     * Returns true if the user owns this specific item.
     */
    protected boolean userOwnsItem ()
    {
        return false; // overrideable in subclasses
    }

    /**
     * Returns true if the item we're displaying is remixable.
     */
    protected boolean isRemixable ()
    {
        return (_item != null) && _item.getPrimaryMedia().isRemixable();
    }

    /**
     * Called from the avatarviewer to effect a scale change.
     */
    protected void updateAvatarScale (float newScale)
    {
        if (!(_item instanceof Avatar)) {
            return;
        }

        Avatar av = (Avatar) _item;
        if (av.scale != newScale) {
            // stash the new scale in the item
            av.scale = newScale;
            _scaleUpdated = true;

            // try immediately updating in the whirled client
            Element client = FlashClients.findClient();
            if (client != null) {
                sendAvatarScaleToWorld(client, av.itemId, newScale);
            }
        }
    }

    /**
     * Called when the user clicks our up arrow.
     */
    protected void onUpClicked ()
    {
        History.back();
    }

    @Override // Panel
    protected void onDetach ()
    {
        super.onDetach();

        // persist our new scale to the server
        if (_scaleUpdated && _item.ownerId == CShell.getMemberId()) {
            _itemsvc.scaleAvatar(
                _item.itemId, ((Avatar) _item).scale, new MsoyCallback.NOOP<Void>());
        }
    }

    /**
     * Sends the new avatar scale to the whirled client.
     */
    protected static native void sendAvatarScaleToWorld (
        Element client, int avatarId, float newScale) /*-{
        client.updateAvatarScale(avatarId, newScale);
    }-*/;

    /**
     * Configures interface to be called by the avatarviewer.
     */
    protected static native void configureCallbacks (BaseItemDetailPanel panel) /*-{
        $wnd.updateAvatarScale = function (newScale) {
            panel.@client.item.BaseItemDetailPanel::updateAvatarScale(F)(newScale);
        }
    }-*/;

    protected Item _item;
    protected ItemDetail _detail;

    protected RoundBox _details;
    protected RoundBox _indeets;

    protected CreatorLabel _creator;

    protected StyledTabPanel _belowTabs;

    /** Have we updated the scale (of an avatar?) */
    protected boolean _scaleUpdated;

    protected static final ItemMessages _imsgs = GWT.create(ItemMessages.class);
    protected static final ItemServiceAsync _itemsvc = (ItemServiceAsync)
        ServiceUtil.bind(GWT.create(ItemService.class), ItemService.ENTRY_POINT);
}
