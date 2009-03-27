//
// $Id$

package com.threerings.msoy.chat.client {

import mx.containers.HBox;
import mx.containers.VBox;
import mx.controls.Label;
import mx.controls.RadioButton;
import mx.controls.RadioButtonGroup;
import mx.controls.Text;

import com.threerings.util.Log;

import com.threerings.flex.CommandCheckBox;
import com.threerings.flex.FlexUtil;

import com.threerings.msoy.ui.FloatingPanel;
import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.client.Msgs;
import com.threerings.msoy.client.MsoyClient;
import com.threerings.msoy.client.MsoyContext;
import com.threerings.msoy.client.MsoyController;
import com.threerings.msoy.client.MsoyService;

import com.threerings.msoy.money.client.BuyButton;
import com.threerings.msoy.money.data.all.Currency;
import com.threerings.msoy.money.data.all.PriceQuote;

import com.threerings.msoy.chat.client.MsoyChatDirector;

import com.threerings.msoy.world.client.WorldContext;
import com.threerings.msoy.world.client.WorldController;

/**
 * Panel to be popped up when a user requests to broadcast a paid announcement. Retrieves the quote
 * from the server and if the user agrees, the broadcast is sent and the money deducted.
 */
public class BroadcastPanel extends FloatingPanel
{
    public static var log :Log = Log.getLog(BroadcastPanel);

    public function BroadcastPanel (ctx :MsoyContext, msg :String)
    {
        super(ctx, Msgs.CHAT.get("t.broadcast"));
        _msg = msg;
        open();

        var client :MsoyClient = _ctx.getMsoyClient();
        var msoySvc :MsoyService = client.requireService(MsoyService) as MsoyService;
        msoySvc.secureBroadcastQuote(client, _ctx.resultListener(gotQuote));
    }

    override protected function createChildren () :void
    {
        super.createChildren();
        styleName = "broadcastPanel";

        addChild(FlexUtil.createText("\" " + _msg + " \"", 350));

        _instructions = FlexUtil.createText(
            Msgs.CHAT.get("m.broadcast_instructions_initial", "..."), 350);
        addChild(_instructions);

        var hbox :HBox = new HBox();
        hbox.addChild(new CommandCheckBox("", setAgreeTOS));
        var tos :Label = new Label();
        tos.selectable = true;
        tos.htmlText = Msgs.CHAT.get("l.broadcast_tos");
        hbox.addChild(tos);
        addChild(hbox);

        addChild(_barButton = new BuyButton(Currency.BARS, processPurchase));
        _barButton.enabled = false;

        var vbox :VBox = new VBox();
        vbox.setStyle("horizontalAlignment", "left");
        vbox.addChild(makeLinkOption("m.br_linkGroup_none", true, true));
        var placeInfo :Array = _ctx.getMsoyController().getPlaceInfo();
        vbox.addChild(makeLinkOption("m.br_linkGroup_room",
            !Boolean(placeInfo[0]) && (placeInfo[2] != 0), false,
            Msgs.CHAT.get("m.visit", placeInfo[2])));
        vbox.addChild(makeLinkOption("m.br_linkGroup_party",
            WorldContext(_ctx).getPartyDirector().isInParty(), false,
            Msgs.CHAT.get("m.view_party", WorldContext(_ctx).getPartyDirector().getPartyId())));
        addChild(vbox);

        addButtons(CANCEL_BUTTON);
    }

    protected function makeLinkOption (
        labelKey :String, enabled :Boolean, selected :Boolean,
        link :String = null) :RadioButton
    {
        var rb :RadioButton = new RadioButton();
        rb.enabled = enabled;
        rb.selected = selected;
        rb.group = _linkGroup;
        rb.label = Msgs.CHAT.get(labelKey);
        rb.value = link;
        return rb;
    }

    protected function gotQuote (quote :PriceQuote, first :Boolean = true) :void
    {
        _quote = quote;

        _instructions.text = Msgs.CHAT.get(
            "m.broadcast_instructions_" + (first ? "initial" : "price_change"), _quote.getBars());
        _barButton.setValue(_quote.getBars());
        setAgreeTOS(_agreeTos);
    }

    protected function broadcastSent (result :PriceQuote) :void
    {
        log.info("Broadcast sent", "result", result);
        if (result != null) {
            // oops, the price went up, inform the user and keep the dialog open
            // TODO: do something more exciting here
            gotQuote(result, false);

        } else {
            // otherwise, close. The user should see the broadcast as feedback
            close();
        }
    }

    protected function setAgreeTOS (agree :Boolean) :void
    {
        _agreeTos = agree;
        _barButton.enabled = (_quote != null) && _agreeTos;
    }

    protected function processPurchase () :void
    {
        var finalMsg :String = _msg;
        if (_linkGroup.selectedValue != null) {
            finalMsg += " " + _linkGroup.selectedValue;
        }

        var client :MsoyClient = _ctx.getMsoyClient();
        var msoySvc :MsoyService = client.requireService(MsoyService) as MsoyService;
        msoySvc.purchaseAndSendBroadcast(client, _quote.getBars(), finalMsg,
            _ctx.resultListener(broadcastSent, MsoyCodes.GENERAL_MSGS, null, _barButton));
    }

    protected var _msg :String;
    protected var _instructions :Text;
    protected var _quote :PriceQuote;
    protected var _barButton :BuyButton;
    protected var _agreeTos :Boolean;
    protected var _linkGroup :RadioButtonGroup = new RadioButtonGroup();
}
}
