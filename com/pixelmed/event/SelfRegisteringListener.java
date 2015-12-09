/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.event;

/**
 * @author	dclunie
 */
public abstract class SelfRegisteringListener extends Listener {

	/***/
	static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/event/SelfRegisteringListener.java,v 1.4 2015/08/26 15:44:24 dclunie Exp $";

	/**
	 * @param	classOfEventHandled
	 */
	//public SelfRegisteringListener(Class classOfEventHandled) {
	//	super(classOfEventHandled);
	//	ApplicationEventDispatcher.getApplicationEventDispatcher().addListener(this);
	//}
	
	/**
	 * @param	classOfEventHandled
	 * @param	eventContext
	 */
	//public SelfRegisteringListener(Class classOfEventHandled,EventContext eventContext) {
	//	super(classOfEventHandled,eventContext);
	//	ApplicationEventDispatcher.getApplicationEventDispatcher().addListener(this);
	//}

	/**
	 * @param	className
	 * @param	eventContext
	 */
	public SelfRegisteringListener(String className,EventContext eventContext) {
		super(className,eventContext);
		ApplicationEventDispatcher applicationEventDispatcher = ApplicationEventDispatcher.getApplicationEventDispatcher();
		if (applicationEventDispatcher != null) {
			applicationEventDispatcher.addListener(this);
		}
		else {
			throw new RuntimeException("Internal error - cannot get ApplicationEventDispatcher");
		}
	}
}

