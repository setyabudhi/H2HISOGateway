
package com.bdi.channel;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ServerSocket;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOFilter;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.ISOFilter.VetoException;
import org.jpos.util.LogEvent;
import org.jpos.util.Logger;

/**
 * Implements an ISOChannel able to exchange messages with
 * ACI's BASE24 over a TCP link, modified from BASE24ISOChannel 
 * by Victor A. Salaman (salaman@teknos.com) .<br>
 * An instance of this class exchanges messages by means of an
 * intermediate 'port server' as described in the
 * <a href="/doc/javadoc/overview-summary.html">Overview</a> page.
 * @author apr@cs.com.uy
 * @author salaman@teknos.com
 *
 * @version $Id: BDIISOGateway-v3/src/com/bdi/channel/BDICBSChannel.java 1.1 2010/12/17 08:25:17CET Setyabudhi, Muhammad (taufiq.setyabudhi) Exp  $
 *
 * @see ISOMsg
 * @see ISOException
 * @see ISOChannel
 */

public class BDICBSChannel extends BDIBaseChannel {
    /**
     * Public constructor (used by Class.forName("...").newInstance())
     */
    public BDICBSChannel () {
        super();
    }
    /**
     * Construct client ISOChannel
     * @param host  server TCP Address
     * @param port  server port number
     * @param p     an ISOPackager
     * @see ISOPackager
     */
    public BDICBSChannel (String host, int port, ISOPackager p) {
        super(host, port, p);
    }
    /**
     * Construct server ISOChannel
     * @param p     an ISOPackager
     * @see ISOPackager
     * @exception IOException
     */
    public BDICBSChannel (ISOPackager p) throws IOException {
        super(p);
    }
    /**
     * constructs a server ISOChannel associated with a Server Socket
     * @param p     an ISOPackager
     * @param serverSocket where to accept a connection
     * @exception IOException
     * @see ISOPackager
     */
    public BDICBSChannel (ISOPackager p, ServerSocket serverSocket) 
        throws IOException
    {
        super(p, serverSocket);
    }
    /**
     * @param m the Message to send (in this case it is unused)
     * @param len   message len (ignored)
     * @exception IOException
     */
    protected void sendMessageTrailler(ISOMsg m, int len) throws IOException {
        //serverOut.write (3);   	    
    }
    
    protected void sendMessageLength(int len) throws IOException {
        //len++;  // one byte trailler
        serverOut.write (len >> 8);
        serverOut.write (len);
    }
    protected int getMessageLength() throws IOException, ISOException {
        int l = 0;
        byte[] b = new byte[2];
        Logger.log (new LogEvent (this, "get-message-length"));
        while (l == 0) {
            serverIn.readFully(b,0,2);
            l = ((((int)b[0])&0xFF) << 8) | (((int)b[1])&0xFF);
            if (l == 0) {
                serverOut.write(b);
                serverOut.flush();
            }
        }
        Logger.log (new LogEvent (this, "got-message-length", Integer.toString(l)));
        return l;   // trailler length
    }
    protected void getMessageTrailler() throws IOException {
        Logger.log (new LogEvent (this, "get-message-trailler"));
        byte[] b = new byte[1];
        //serverIn.readFully(b,0,1);
        Logger.log (new LogEvent (this, "got-message-trailler", ISOUtil.hexString(b)));
    }
    
    
    /**
     * sends an ISOMsg over the TCP/IP session
     * @param m the Message to be sent
     * @exception IOException
     * @exception ISOException
     * @exception ISOFilter.VetoException;
     */
    public void send (ISOMsg m) 
        throws IOException, ISOException, VetoException
    {
        LogEvent evt = new LogEvent (this, "send");
        evt.addMessage (m);
        try {
            if (!isConnected())
                throw new ISOException ("unconnected ISOChannel");
            m.setDirection(ISOMsg.OUTGOING);
            m = applyOutgoingFilters (m, evt);
            m.setDirection(ISOMsg.OUTGOING); // filter may have drop this info
            m.setPackager (getDynamicPackager(m));
            String header="ISO016000070";
            
            int lengthHeader0800 = 0;
                               
            byte[] b = m.pack();
            System.out.println("<--ORG-->"+new String(b)+"<--ORG-->");            
            synchronized (serverOut) {
        		sendMessageLength(b.length + getHeaderLength());
        		sendMessageHeader(m, b.length);        		
        	    if (m.getMTI().equalsIgnoreCase("0200")){
                	if (new String(b).indexOf(header)== -1){
                		b = (header + new String(b)).getBytes();
                	}               	
                }
        	    System.out.println("<--SEND-->"+new String(b)+"<--SEND-->");
        		sendMessage (b, 0, b.length);
        		sendMessageTrailler(m, b);
        		serverOut.flush ();
 
            }
            cnt[TX]++;
            setChanged();
            notifyObservers(m);
        } catch (VetoException e) {
            evt.addMessage (e);
            throw e;
        } catch (ISOException e) {
            evt.addMessage (e);
            throw e;
        } catch (IOException e) {
            evt.addMessage (e);
            throw e;
        } catch (Exception e) {
            evt.addMessage (e);
            throw new ISOException ("unexpected exception", e);
        } finally {
            Logger.log (evt);
        }
    }
    
    /**
     * Waits and receive an ISOMsg over the TCP/IP session
     * @return the Message received
     * @exception IOException
     * @exception ISOException
     */
    public ISOMsg receive() throws IOException, ISOException {
    
        byte[] b=null;
        byte[] header=null;
        LogEvent evt = new LogEvent (this, "receive");
        ISOMsg m = createMsg ();
        m.setSource (this);
        try {
            if (!isConnected())
                throw new ISOException ("unconnected ISOChannel");

            synchronized (serverIn) {
                int len  = getMessageLength();
                int hLen = getHeaderLength();

                System.out.println("len: "+len);
                System.out.println("hlen: "+hLen);
                if (len == -1) {
                    if (hLen > 0) {
                        header = readHeader(hLen);
                    }
                    b = streamReceive();
                }
                else if (len > 0 && len <= 10000) {
                    if (hLen > 0) {
                        // ignore message header (TPDU)
                        // Note header length is not necessarily equal to hLen (see VAPChannel)
                        header = readHeader(hLen);
                        len -= header.length;
                    }
                    b = new byte[len];
                    serverIn.readFully(b, 0, len);
                    getMessageTrailler();
                }
                else
                    throw new ISOException(
                        "receive length " +len + " seems strange");
            }
            
            if ((new String(b)).indexOf("ISO016000073") > -1){
            	b = (new String(b)).replace("ISO016000073","").getBytes();
            }
            
            System.out.println("<--REC-->"+new String(b)+"<--REC-->");
            m.setPackager (getDynamicPackager(b));
            m.setHeader (getDynamicHeader(header));
            if (b.length > 0 && !shouldIgnore (header))  // Ignore NULL messages
                m.unpack (b);
            m.setDirection(ISOMsg.INCOMING);
            m = applyIncomingFilters (m, header, b, evt);
            m.setDirection(ISOMsg.INCOMING);
            evt.addMessage (m);
            cnt[RX]++;
            setChanged();
            notifyObservers(m);
        } catch (ISOException e) {
            evt.addMessage (e);
            if (header != null) {
                evt.addMessage ("--- header ---");
                evt.addMessage (ISOUtil.hexdump (header));
            }
            if (b != null) {
                evt.addMessage ("--- data ---");
                evt.addMessage (ISOUtil.hexdump (b));
            }
            throw e;
        } catch (EOFException e) {
            if (socket != null)
                socket.close ();
            evt.addMessage ("<peer-disconnect/>");
            throw e;
        } catch (InterruptedIOException e) {
            if (socket != null)
                socket.close ();
            evt.addMessage ("<io-timeout/>");
            throw e;
        } catch (IOException e) { 
            if (socket != null)
                socket.close ();
            if (usable) 
                evt.addMessage (e);
            throw e;
        } catch (Exception e) { 
            evt.addMessage (m);
            evt.addMessage (e);
            throw new ISOException ("unexpected exception", e);
        } finally {
            Logger.log (evt);
        }
        return m;
    }
}

