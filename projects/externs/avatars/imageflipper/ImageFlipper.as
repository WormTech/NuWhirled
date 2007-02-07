//
// $Id$

package {

import flash.events.Event;

import flash.display.DisplayObject;
import flash.display.Sprite;

import flash.utils.getTimer; // function import

import com.threerings.msoy.export.AvatarControl;
import com.threerings.msoy.export.ControlEvent;

//[SWF(width="161", height="150")]
[SWF(width="222", height="363")]
public class ImageFlipper extends Sprite
{
    public function ImageFlipper ()
    {
        var o :Object = new Data().content;

        _bounceAmplitude = Number(o["bounce"]);
        _bounceFreq = Number(o["bounceFreq"]);

        var imgClass :Class = (o["image"] as Class);
        _image = (new imgClass() as DisplayObject);
        addChild(_image);

        _control = new AvatarControl(this);
        _control.addEventListener(ControlEvent.APPEARANCE_CHANGED, setupVisual);
        setupVisual();
    }

    protected function setupVisual (evt :Object = null) :void
    {
        var orient :Number = _control.getOrientation();
        if (orient < 180) {
            _image.x = _image.width;
            _image.scaleX = -1;

        } else {
            _image.x = 0;
            _image.scaleX = 1;
        }

        // see if we should be bouncing
        if (!isNaN(_bounceAmplitude) && !isNaN(_bounceFreq) &&
                _bouncing != _control.isMoving()) {
            _bouncing = _control.isMoving();
            if (_bouncing) {
                addEventListener(Event.ENTER_FRAME, handleEnterFrame);
                _bounceBase = getTimer();

            } else {
                removeEventListener(Event.ENTER_FRAME, handleEnterFrame);
                _image.y = 0;
            }
        }
    }

    protected function handleEnterFrame (evt :Object = null) :void
    {
        var now :Number = getTimer();
        var dur :Number = now - _bounceBase;
        while (dur > _bounceFreq) {
            dur -= _bounceFreq;
            _bounceBase += _bounceFreq;
        }

        var val :Number = dur * Math.PI / _bounceFreq;
        _image.y = Math.sin(val) * _bounceAmplitude;
    }

    protected var _image :DisplayObject;
    protected var _control :AvatarControl;

    protected var _bouncing :Boolean = false;

    protected var _bounceAmplitude :Number;
    protected var _bounceFreq :Number;

    protected var _bounceBase :Number;
}
}
