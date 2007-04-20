//
// $Id$

package com.threerings.msoy.swiftly.client {

import flash.utils.ByteArray;
import com.threerings.msoy.swiftly.client.ProjectRoomService;
import com.threerings.msoy.swiftly.data.PathElement;
import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.client.InvocationService_ConfirmListener;
import com.threerings.presents.client.InvocationService_InvocationListener;
import com.threerings.presents.data.InvocationMarshaller_ConfirmMarshaller;

/**
 * An ActionScript version of the Java ProjectRoomService interface.
 */
public interface ProjectRoomService extends InvocationService
{
    // from Java interface ProjectRoomService
    function addDocument (arg1 :Client, arg2 :PathElement, arg3 :InvocationService_InvocationListener) :void;

    // from Java interface ProjectRoomService
    function addPathElement (arg1 :Client, arg2 :PathElement) :void;

    // from Java interface ProjectRoomService
    function buildProject (arg1 :Client) :void;

    // from Java interface ProjectRoomService
    function commitProject (arg1 :Client, arg2 :String, arg3 :InvocationService_ConfirmListener) :void;

    // from Java interface ProjectRoomService
    function deleteDocument (arg1 :Client, arg2 :int) :void;

    // from Java interface ProjectRoomService
    function deletePathElement (arg1 :Client, arg2 :int, arg3 :InvocationService_ConfirmListener) :void;

    // from Java interface ProjectRoomService
    function finishFileUpload (arg1 :Client, arg2 :InvocationService_ConfirmListener) :void;

    // from Java interface ProjectRoomService
    function loadDocument (arg1 :Client, arg2 :PathElement, arg3 :InvocationService_ConfirmListener) :void;

    // from Java interface ProjectRoomService
    function renamePathElement (arg1 :Client, arg2 :int, arg3 :String, arg4 :InvocationService_ConfirmListener) :void;

    // from Java interface ProjectRoomService
    function startFileUpload (arg1 :Client, arg2 :String, arg3 :PathElement, arg4 :InvocationService_ConfirmListener) :void;

    // from Java interface ProjectRoomService
    function updateDocument (arg1 :Client, arg2 :int, arg3 :String) :void;

    // from Java interface ProjectRoomService
    function updatePathElement (arg1 :Client, arg2 :PathElement) :void;

    // from Java interface ProjectRoomService
    function uploadFile (arg1 :Client, arg2 :ByteArray) :void;
}
}
