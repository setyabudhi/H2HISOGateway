



package com.bdi;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOField;
import org.jpos.iso.ISOServer;
import org.jpos.iso.ISOSource;
import org.jpos.iso.ISOChannel;
import org.jpos.iso.BaseChannel;
import org.jpos.iso.ISOException;
import org.jpos.iso.MUX;
import org.jpos.q2.iso.QMUX;
import org.jpos.iso.ServerChannel;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.channel.HEXChannel;
import org.jpos.iso.channel.LoopbackChannel;
import org.jpos.iso.packager.ISO87APackager;

import org.jpos.util.LogEvent;
import org.jpos.util.Logger;
import org.jpos.util.LogSource;
import org.jpos.util.SimpleLogSource;
import org.jpos.util.SimpleLogListener;
import org.jpos.util.ThreadPool;
import org.jpos.util.NameRegistrar.NotFoundException;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ReConfigurable;

public class BDIRouterMux1 
	extends SimpleLogSource
	implements ISORequestListener,ReConfigurable  {

	Configuration cfg;
	ISOChannel destChannel=null;
    public BDIRouterMux1 () {
        super();
    }
    public void setConfiguration (Configuration cfg) {
        this.cfg = cfg;
    }
	
    public boolean process(ISOSource source, ISOMsg m) {
boolean result = false;
try {
	ISOMsg response = null;
	MUX qmux = QMUX.getMUX("mux.mux2");
	//-- do your stuffs here. Cloning the msg is just an example.
	ISOMsg request = (ISOMsg) m.clone();
	info("--- sending request to host. ---");
	response = qmux.request(request, 10000);

	if (request != null) {
	//-- send response back to requisting client
	info("--- sending back response to client. ---");
	source.send(request);
	result = true;
} else {
	//-- timeout case
	error(this.getClass().getName(),
	"--- remote host is not responding timely! --0");
}

} catch (NotFoundException nfe) {
	error(this.getClass().getName(), nfe);
} catch (ISOException isoe) {
	error(this.getClass().getName(), isoe);
} catch (java.io.IOException ioe) {
	error(this.getClass().getName(), ioe);
}
	return result;
}    
}




