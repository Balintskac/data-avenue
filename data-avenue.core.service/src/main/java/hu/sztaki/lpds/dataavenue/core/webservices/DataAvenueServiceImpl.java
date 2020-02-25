package hu.sztaki.lpds.dataavenue.core.webservices;

import hu.sztaki.lpds.dataavenue.core.AdaptorRegistry;
import hu.sztaki.lpds.dataavenue.core.Configuration;
import hu.sztaki.lpds.dataavenue.core.CopyTaskManager;
import hu.sztaki.lpds.dataavenue.core.HttpAliasRegistry;
import hu.sztaki.lpds.dataavenue.core.TaskManager;
import hu.sztaki.lpds.dataavenue.core.TicketManager;
import hu.sztaki.lpds.dataavenue.core.TransferDetails;
import hu.sztaki.lpds.dataavenue.core.ExtendedTransferMonitor;
import hu.sztaki.lpds.dataavenue.core.interfaces.exceptions.NotSupportedProtocolException;
import hu.sztaki.lpds.dataavenue.core.interfaces.exceptions.SessionExpiredException;
import hu.sztaki.lpds.dataavenue.core.interfaces.exceptions.UnexpectedException;
import hu.sztaki.lpds.dataavenue.core.interfaces.impl.URIFactory;
import hu.sztaki.lpds.dataavenue.core.interfaces.impl.URIImpl;
import hu.sztaki.lpds.dataavenue.interfaces.Adaptor;
import hu.sztaki.lpds.dataavenue.interfaces.AsyncCommands;
import hu.sztaki.lpds.dataavenue.interfaces.Credentials;
import hu.sztaki.lpds.dataavenue.interfaces.DataAvenueSession;
import hu.sztaki.lpds.dataavenue.interfaces.DirectURLsSupported;
import hu.sztaki.lpds.dataavenue.interfaces.OperationsEnum;
import hu.sztaki.lpds.dataavenue.interfaces.URIBase;
import hu.sztaki.lpds.dataavenue.interfaces.URIBase.URIType;
import hu.sztaki.lpds.dataavenue.interfaces.exceptions.CredentialException;
import hu.sztaki.lpds.dataavenue.interfaces.exceptions.OperationException;
import hu.sztaki.lpds.dataavenue.interfaces.exceptions.TaskIdException;
import hu.sztaki.lpds.dataavenue.core.interfaces.exceptions.TicketException;
import hu.sztaki.lpds.dataavenue.interfaces.exceptions.URIException;
import hu.sztaki.lpds.dataavenue.interfaces.impl.DataAvenueSessionImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.jws.WebService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
@WebService(
		portName="DataAvenueServicePort", // sets <port name=""> only in the wsdl generated by wsgen
		targetNamespace="http://ws.dataavenue.lpds.sztaki.hu/",
		endpointInterface="hu.sztaki.lpds.dataavenue.core.webservices.DataAvenueService"
		//serviceName="DataAvenueServiceImplService" - has no any effect on the gereated wsdl
		)
//@HttpSessionScope @Stateful - one DataAvenueService instance is created for each session (@PostConstruct invoked after constructor)
public class DataAvenueServiceImpl implements DataAvenueService {
    
	private static final Logger log = LoggerFactory.getLogger(DataAvenueService.class); 

    // possibly cause memory leak in tomcat? http://javarevisited.blogspot.hu/2012/01/tomcat-javalangoutofmemoryerror-permgen.html
    @Resource private WebServiceContext wsContext;
    
    @PostConstruct public void sessionCreated() { // invoked once at web app start-up (there is no HTTP session here) after INFO: WSSERVLET12: JAX-WS context listener initializing 
    	log.info("Data Avenue service ready!");
    } 
    @PreDestroy public void sessionDestroyed() { // invoked once on tomcat shutdown (or never!!!)
    } 

    @Override public List<String> getSupportedProtocols(String ticketParam) throws TicketException, CredentialException, SessionExpiredException, OperationException  {
    	logRequest("getSupportedProtocols", ticketParam, null, null);
    	DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
    	if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY) + "");
    	return AdaptorRegistry.getSupportedProtocols();
    }    
    
    @Override public List<OperationsEnum> getSupportedOperations(String ticketParam, String protocol) throws TicketException, NotSupportedProtocolException, CredentialException, SessionExpiredException, OperationException {
    	logRequest("getSupportedOperations", ticketParam, null, null);
    	DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam); 
    	if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));
    	Adaptor adaptor = AdaptorRegistry.getAdaptorInstance(protocol);
    	return adaptor.getSupportedOperationTypes(protocol);
    }   
    
    @Override public List<String> getSupportedAuthenticationTypes(String ticketParam, String protocol) throws TicketException, NotSupportedProtocolException, CredentialException, SessionExpiredException, OperationException {
    	logRequest("getSupportedAuthenticationTypes", ticketParam, null, null);
    	DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
    	if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));
    	Adaptor adaptor = AdaptorRegistry.getAdaptorInstance(protocol);
    	return adaptor.getAuthenticationTypes(protocol);
    }  
    
    @Override public List<DirEntry> list(String ticketParam, String uriString, CredentialAttributes creds) throws TicketException, URIException, NotSupportedProtocolException, OperationException, CredentialException, SessionExpiredException {
    	final String op = "list";
    	logRequest(op, ticketParam, uriString, creds);
    	canAcceptCommands();
    	
    	if (uriString == null) throw new URIException("null URI");
    	List<DirEntry> dirContents = new ArrayList<DirEntry> ();
        try {
        	// auto-complete missing trailing slash (before query if exists)
        	if (!uriString.contains("?")) {
        		if (!uriString.endsWith("/")) uriString += "/"; 
        	} else {
        		String [] parts = uriString.split("\\?");
        		if (!parts[0].endsWith("/")) uriString = parts[0] + "/" + parts[1];
        	}
        	
        	if (!uriString.endsWith("/")) uriString += "/"; 
	    	
			URIBase uri = URIFactory.createURI(uriString);
			
	        Adaptor adaptor = AdaptorRegistry.getAdaptorInstance(uri.getProtocol());
	        Credentials credentials = creds != null ? creds.getCredentials() : null;
	        DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	        if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));
	        
        	for (URIBase i : adaptor.list(uri, credentials, session)) dirContents.add(new DirEntry(i));
        	
        	log.trace(dirContents.size() + " entries");
        } 
        catch (URIException e) { logErr(op, e); throw e; } 
        catch (OperationException e) { logErr(op, e); throw e; } 
        catch (CredentialException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
        
        logDone(op);
        return dirContents;
    }

    @Override public void mkdir(String ticketParam, String uriString, CredentialAttributes creds) throws TicketException, URIException, NotSupportedProtocolException, OperationException, CredentialException, SessionExpiredException {
    	final String op = "mkdir";

    	logRequest(op, ticketParam, uriString, creds);
    	canAcceptCommands();
    	
        try {
	    	if (uriString != null && !uriString.endsWith("/")) uriString += "/"; // auto-complete missing trailing slash
	        URIBase uri = URIFactory.createURI(uriString);
	        Adaptor adaptor = AdaptorRegistry.getAdaptorInstance(uri.getProtocol());
	    	Credentials credentials = creds != null ? creds.getCredentials() : null;
	        DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	        if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));

        	adaptor.mkdir(uri, credentials, session);
        } 
        catch (URIException e) { logErr(op, e); throw e; } 
        catch (OperationException e) { logErr(op, e); throw e; } 
        catch (CredentialException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
        
        logDone(op);
    }

    @Override public void rmdir(String ticketParam, String uriString, CredentialAttributes creds) throws TicketException, URIException, NotSupportedProtocolException, OperationException, CredentialException, SessionExpiredException {
    	final String op = "rmdir";

    	logRequest(op, ticketParam, uriString, creds);
    	canAcceptCommands();
    	
        try {
        	// auto-complete missing trailing slash (before query if exists)
        	if (!uriString.contains("?")) {
        		if (!uriString.endsWith("/")) uriString += "/"; 
        	} else {
        		String [] parts = uriString.split("\\?");
        		if (!parts[0].endsWith("/")) uriString = parts[0] + "/" + parts[1];
        	}
	        URIBase uri = URIFactory.createURI(uriString);
	        Adaptor adaptor = AdaptorRegistry.getAdaptorInstance(uri.getProtocol());
	        Credentials credentials = creds != null ? creds.getCredentials() : null;
	        DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	        if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));
        
        	adaptor.rmdir(uri, credentials, session);
        } 
        catch (URIException e) { logErr(op, e); throw e; } 
        catch (OperationException e) { logErr(op, e); throw e; } 
        catch (CredentialException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
        
        logDone(op);
    }
    
    @Override public void delete(String ticketParam, String uriString, CredentialAttributes creds) throws TicketException, URIException, NotSupportedProtocolException, OperationException, CredentialException, SessionExpiredException {
    	final String op = "delete";

    	logRequest(op, ticketParam, uriString, creds);
    	canAcceptCommands();

        try {
	        URIBase uri = URIFactory.createURI(uriString);
	        Adaptor adaptor = AdaptorRegistry.getAdaptorInstance(uri.getProtocol());
	        Credentials credentials = creds != null ? creds.getCredentials() : null;
	        DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	        if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));

        	adaptor.delete(uri, credentials, session);
        } 
        catch (URIException e) { logErr(op, e); throw e; } 
        catch (OperationException e) { logErr(op, e); throw e; } 
        catch (CredentialException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
        
        logDone(op);
    }

    @Override public void setPermissions(String ticketParam, String uriString, String permissionsString, CredentialAttributes creds) throws TicketException, URIException, NotSupportedProtocolException, OperationException, CredentialException, SessionExpiredException {
    	final String op = "setPermissions";

    	logRequest(op, ticketParam, uriString, creds);
    	canAcceptCommands();

        try {
	        URIBase uri = URIFactory.createURI(uriString);
	        Adaptor adaptor = AdaptorRegistry.getAdaptorInstance(uri.getProtocol());
	        Credentials credentials = creds != null ? creds.getCredentials() : null;
	        DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	        if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));

        	adaptor.permissions(uri, credentials, session, permissionsString);
        } 
        catch (URIException e) { logErr(op, e); throw e; } 
        catch (OperationException e) { logErr(op, e); throw e; } 
        catch (CredentialException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
        
        logDone(op);
    }
    
    @Override public void rename(String ticketParam, String uriString, String newName, CredentialAttributes creds) throws TicketException, URIException, NotSupportedProtocolException, OperationException, CredentialException, SessionExpiredException {
    	final String op = "rename";

    	logRequest(op, ticketParam, uriString + "->" + newName, creds);
    	canAcceptCommands();

        try {
	    	if (newName == null) throw new OperationException("New name is null!");
	    	newName = newName.trim();
	    	if (newName.length() == 0) throw new OperationException("New name is of length 0!");
	    	// Win XP: not containing / \ : * ? " < > | it cannot begin with a space or period" 
	    	// OS X  other than a colon. Additionally, it cannot begin with a period.
	    	String pattern = "[^\\\\/:\"*?<>|]+";
	    	if (!newName.matches(pattern)) throw new OperationException("New name contains illegal character!");
	    	
	        URIBase uri = URIFactory.createURI(uriString);
	        Adaptor adaptor = AdaptorRegistry.getAdaptorInstance(uri.getProtocol());
	        Credentials credentials = creds != null ? creds.getCredentials() : null;
	    	DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	    	if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));
        
        	adaptor.rename(uri, newName, credentials, session);
        } 
        catch (URIException e) { logErr(op, e); throw e; } 
        catch (OperationException e) { logErr(op, e); throw e; } 
        catch (CredentialException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
        
        logDone(op);
    }
    
    @Override public String copy(String ticketParam, String uriString, CredentialAttributes creds, String targetUriString, CredentialAttributes targetCreds, boolean overwrite) throws TicketException, URIException, OperationException, CredentialException, NotSupportedProtocolException, IOException, SessionExpiredException {
    	final String op = "copy";

    	logRequest(op, ticketParam, uriString + "->" + targetUriString, creds);
    	canAcceptCopyCommands();
    	
    	String result = null;
    	try {
    		result = copyOrMove(ticketParam, uriString, creds, targetUriString, targetCreds, overwrite, true);
        } 
        catch (URIException e) { logErr(op, e); throw e; } 
        catch (OperationException e) { logErr(op, e); throw e; } 
        catch (CredentialException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
    	
        logDone(op);
    	return result;
    }
    
    @Override public String move(String ticketParam, String uriString, CredentialAttributes creds, String targetUriString, CredentialAttributes targetCreds, boolean overwrite) throws TicketException, URIException, OperationException, CredentialException, NotSupportedProtocolException, IOException, SessionExpiredException {
    	final String op = "move";

    	logRequest(op, ticketParam, uriString + "->" + targetUriString, creds);
    	canAcceptCopyCommands();
    	
    	String result = null;
    	try {
    		result = copyOrMove(ticketParam, uriString, creds, targetUriString, targetCreds, overwrite, false);
        } 
        catch (URIException e) { logErr(op, e); throw e; }
        catch (OperationException e) { logErr(op, e); throw e; } 
        catch (CredentialException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
    	
        logDone(op);
    	return result;
    }
    
    private String copyOrMove(final String ticketParam, String sourceUriString, final CredentialAttributes sourceCreds, final String targetUriString, final CredentialAttributes targetCreds, final boolean overwrite, final boolean isCopy)  throws TicketException, URIException, OperationException, CredentialException, NotSupportedProtocolException, IOException, SessionExpiredException {
    	URIImpl sourceUri = URIFactory.createURI(sourceUriString);
    	URIImpl targetUri = URIFactory.createURI(targetUriString);

    	// if target file name is not given on file copy, auto-add it
    	if (sourceUri.isFile() && targetUri.isDir()) targetUri = URIFactory.createURI(targetUriString + sourceUri.getEntryName()); 

    	if (sourceUri.isDir() && targetUri.isFile()) throw new OperationException("Target must be a directory (cannot copy a directory to a file)");
    	
    	if (sourceUri.isIdenticalFileOrDirEntry(targetUri) || sourceUri.isSameSubdirWithoutFileName(targetUri) || targetUri.isSubdirOf(sourceUri)) {
        	log.warn("Copy/move entry to itself {} -> {} ", sourceUri, targetUri);
        	throw new OperationException("Cannot copy/move to itself or to its subdirectory!");
        }
        
        Adaptor sourceAdaptor = AdaptorRegistry.getAdaptorInstance(sourceUri.getProtocol());
        Adaptor targetAdaptor = AdaptorRegistry.getAdaptorInstance(targetUri.getProtocol());
        Credentials sourceCredentials = sourceCreds != null ? sourceCreds.getCredentials() : null;
        Credentials targetCredentials = targetCreds != null ? targetCreds.getCredentials() : null;
        
        DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam); // session only used to verify ticket
        String ticket = (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY);
        if (ticketParam == null) log.debug("session ticket: " + ticket);

        AsyncCommands managingAdaptor;
    	// if source and target adaptors are identical and copy operation is supported, use its internal function (possibly, third-party transfer)
    	if (	
    			(sourceAdaptor == targetAdaptor && sourceUri.isDir() && isCopy && sourceAdaptor.getSupportedOperationTypes(sourceUri, targetUri).contains(OperationsEnum.COPY_DIR)) ||
    			(sourceAdaptor == targetAdaptor && sourceUri.isDir() && !isCopy && sourceAdaptor.getSupportedOperationTypes(sourceUri, targetUri).contains(OperationsEnum.MOVE_DIR)) ||
    			(sourceAdaptor == targetAdaptor && sourceUri.isFile() && isCopy &&  sourceAdaptor.getSupportedOperationTypes(sourceUri, targetUri).contains(OperationsEnum.COPY_FILE)) ||
    			(sourceAdaptor == targetAdaptor && sourceUri.isFile() && !isCopy && sourceAdaptor.getSupportedOperationTypes(sourceUri, targetUri).contains(OperationsEnum.MOVE_FILE))) {
    		log.debug("Using adator's supplied copy/move function...");
    		managingAdaptor = sourceAdaptor;
    	} else {
    		log.debug("Using core streaming copy/move function...");
    		managingAdaptor = CopyTaskManager.getInstance();
    	}
    		
       	// create a new monitor instance (some data are managed by the adaptor)
       	ExtendedTransferMonitor monitor =
       			sourceUri.isDir() ? 
       						new ExtendedTransferMonitor(ticket, sourceUriString, targetUriString, isCopy ? OperationsEnum.COPY_DIR : OperationsEnum.MOVE_DIR) :
       						new ExtendedTransferMonitor(ticket, sourceUriString, targetUriString, isCopy ? OperationsEnum.COPY_FILE : OperationsEnum.MOVE_FILE);
       	// start internal task, provide the monitor to it
   		String adaptorManagedTaskId =
   				isCopy ?
    				managingAdaptor.copy(sourceUri, sourceCredentials, targetUri, targetCredentials, overwrite, monitor) :
    				managingAdaptor.move(sourceUri, sourceCredentials, targetUri, targetCredentials, overwrite, monitor);
       	// set monitor's internal task id (potential cancel)
   		monitor.setManagingAdaptor(managingAdaptor);
   		monitor.setInternalTaskId(adaptorManagedTaskId);		
       	// if no exception, register this task in taskregistry
   		TaskManager.getInstance().registerTransferMonitor(monitor);
       	// return task id (not the internal)
       	return monitor.getTaskId();
    }

    @Override public TransferDetails getState(String ticketParam, String id) throws TicketException, TaskIdException, OperationException, SessionExpiredException, CredentialException {
    	final String op = "getState";

    	logRequest(op, ticketParam, id, null);
    	canAcceptCommands();
        
    	try {
	    	DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	    	if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));
    	
    		TransferDetails result = TaskManager.getInstance().getTaskDetails(id);
    		
    		if (result != null) log.trace(result.getBytesTransferred() + " bytes transferred");
            logDone(op + "(" + result.getState() + ")");
    		return result;
    	}
    	catch (TaskIdException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
    }
    
    
    @Override public void cancel(String ticketParam, String id) throws TicketException, TaskIdException, OperationException, SessionExpiredException, CredentialException {
    	final String op = "cancel";

    	logRequest(op, ticketParam, id.toString(), null);
    	canAcceptCommands();
        
    	try {
	    	DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	        if (ticketParam == null) log.debug("session ticket: " + (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));
        
    		TaskManager.getInstance().cancelTransfer(id);
        } 
        catch (OperationException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
    	
        logDone(op);
    }
	
    @Override public String createAlias(final String ticketParam, final String uriString, final CredentialAttributes creds, final boolean isRead, final int lifetime, final boolean archive) throws TicketException, URIException, CredentialException, NotSupportedProtocolException, OperationException, SessionExpiredException {
    	final String op = "createAlias" + (isRead ? "(read)" : "(write)");

    	logRequest(op, ticketParam, uriString, creds);
    	canAcceptHttpAliases();
    	
        try {
	        URIBase uri = URIFactory.createURI(uriString);
			Adaptor adaptor = AdaptorRegistry.getAdaptorInstance(uri.getProtocol());
	        Credentials credentials = creds != null ? creds.getCredentials() : null; 
	        DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	        String ticket = (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY);
	        if (ticketParam == null) log.debug("session ticket: " + ticket);
	        
	    	if (archive) {
	    		if (uri.getType() != URIType.DIRECTORY) throw new OperationException("Extract archive mode requires directory URI parameter! (" + uriString + ")");
	    	} else {
	    		if (uri.getType() == URIType.DIRECTORY) throw new OperationException("Cannot create alias for a directory! (" + uriString + ")");
	    	}
        
        	// check source readability
        	// pass null dataavenue session: create and discard a new jsaga session
        	if (Configuration.CHECK_ALIASES_ON_CREATION) {
        		if (!archive) {
		            if (isRead) { if (!adaptor.isReadable(uri, credentials, null)) throw new URIException("URI is not readable! (" + uri + ")"); } 
		            else { if (!adaptor.isWritable(uri, credentials, null)) throw new URIException("URI is not writable! (" + uri + ")"); }
        		}
        	}
        	
        	String directURL = null;
   			if (adaptor instanceof DirectURLsSupported) {
   	    		try {
    				directURL = ((DirectURLsSupported) adaptor).createDirectURL(uri, credentials, isRead, lifetime, session);
    				log.debug("DirectURL created: " + directURL);
   	    		} catch (Exception e) { log.warn("Cannot create direct URL!", e); }
			}
        	
            String result = HttpAliasRegistry.getInstance().createAndRegisterHttpAlias(ticket, uri, credentials, session, isRead, lifetime, archive, directURL);
            
            log.debug("Alias: " + result);
            logDone(op);
            // register and persists alias and credentials
        	return result;
        }
	    catch (URIException e) { logErr(op, e); throw e; } 
	    catch (OperationException e) { logErr(op, e); throw e; } 
	    catch (CredentialException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
    }

    @Override public String createTicket(final String ticketParam, final String name, final String company, final String email) throws TicketException, OperationException, SessionExpiredException {
    	final String op = "createTicket (" + email + ")";

    	logRequest(op, ticketParam, null, null);

    	try {
	    	DataAvenueSession session = getDataAvenueSessionAndCheckTicket(ticketParam);
	        String ticket = (String) session.get(DataAvenueSessionImpl.TICKET_SESSION_KEY);
	        if (ticketParam == null) log.debug("session ticket: " + ticket);

        	String result = TicketManager.getInstance().createUserTicket(ticket, name, company, email);
        	
        	logDone(op);
        	return result;
    	}
    	catch (TicketException e) { logErr(op, e); throw e; }
	    catch (OperationException e) { logErr(op, e); throw e; }
        catch (Throwable e) { logFatal(op, e); throw new UnexpectedException(e); }
    }
    
	// log ws request message
    private void logRequest(final String op, final String ticket, final String uri, final CredentialAttributes creds) {
        MessageContext mc = wsContext.getMessageContext(); assert mc != null;
        HttpServletRequest req = (HttpServletRequest) mc.get(MessageContext.SERVLET_REQUEST); assert req != null;
        log.trace("==========================================================");
        log.info("> WS:'" + op + "' | from:" + req.getRemoteAddr() + " | ticket:" + ticket + " | uri:" + uri + " | creds:" + (creds != null ? creds : "null"));
    }

    private void logDone(final String op) {
        log.info("< WS:'" + op + "' OK");
        log.trace("==========================================================");
    }

    private void logErr(final String op, final Throwable e) {
        log.info("< WS:'" + op + "' ERROR: " + (e != null ? e.getMessage() : "null"));
        log.trace("Exception: ", e);
        log.trace("==========================================================");
    }
    
    private void logFatal(final String op, final Throwable e) {
    	log.error("Exception: " + (e != null ? e.getMessage() : "null"), e);
    	log.trace("Exception: ", e);
        log.info("< WS:'" + op + "' FATAL: " + (e != null ? e.getMessage() : "null"));
        log.trace("==========================================================");
    }
    
    // check whether server accepts commands, throw OperationException if not
    private void canAcceptCommands() throws OperationException {
    	if (!Configuration.acceptCommands) {
    		log.info("Configuration.acceptCommands disabled, request denied");
    		throw new OperationException("Server does not accept commands temporarily");
    	}
    }
    
    //check whether server accepts commands, throw OperationException if not
    private void canAcceptCopyCommands() throws OperationException {
    	if (!Configuration.acceptCopyCommands) {
    		log.info("Configuration.acceptCopyCommands disabled, request denied");
    		throw new OperationException("Server does not accept copy commands temporarily");
    	}
    }
    
    // check whether server accepts commands, throw OperationException if not
    private void canAcceptHttpAliases() throws OperationException {
    	if (!Configuration.acceptHttpAliases) {
    		log.info("Configuration.acceptHttpAliases disabled, request denied");
    		throw new OperationException("Server does not accept HTTP alias commands temporarily");
    	}
    }
    
    @SuppressWarnings("unused")
	private String getSessionId () {
    	if (wsContext == null) return null;
    	MessageContext mc = wsContext.getMessageContext();
    	if (mc == null) return null;
    	Object requestObject = mc.get(MessageContext.SERVLET_REQUEST);
    	if (requestObject == null || !(requestObject instanceof HttpServletRequest)) return null;
    	if (!(requestObject instanceof HttpServletRequest)) return null;
    	HttpServletRequest request = (HttpServletRequest) requestObject;
    	return request.getRequestedSessionId();
    }
    
    // get DataAvenue session object belonging to the (http) web service session
    // check ticket and store in session if valid
    // NOTE: use this code in client: ((BindingProvider) ws).getRequestContext().put(BindingProvider.SESSION_MAINTAIN_PROPERTY, true);
    private DataAvenueSession getDataAvenueSessionAndCheckTicket(final String ticket) throws SessionExpiredException, TicketException, OperationException {
    	
    	if (ticket != null) TicketManager.getInstance().checkTicket(ticket);
    	// else try to read ticket from session, see below
    	
    	assert wsContext != null;
    	MessageContext mc = wsContext.getMessageContext();
    	assert mc != null;
    	Object requestObject = mc.get(MessageContext.SERVLET_REQUEST);
    	if (requestObject == null || !(requestObject instanceof HttpServletRequest)) throw new RuntimeException("Can't get http servlet request"); 
    	HttpServletRequest request = (HttpServletRequest) requestObject;
    	
    	// indicate session expired event (in this case client must resubmit authentication data, if applicable)
    	boolean expired = false;
    	if (request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid()) expired = true;
    	
    	HttpSession httpSession = request.getSession(); // create a new valid session
    	assert httpSession != null;
    	
    	if (expired) {
    		log.info("Session expired: " + httpSession.getId() + " (ticket: " + ticket + ")");
    		throw new SessionExpiredException(); // throw exception to resubmit authentication data
    	}
    	
        DataAvenueSession dataAvenueSession = (DataAvenueSession) httpSession.getAttribute(DataAvenueSessionImpl.DATA_AVENUE_SESSION_KEY);
    	if (dataAvenueSession == null) { // new session
    		
    		if (ticket == null) throw new TicketException("No ticket provided");
    		log.trace("Creating new DataAvenue session...");
    		dataAvenueSession = new DataAvenueSessionImpl();
    		
    		// increase session per ticket, throw exception if limit reached
    		TicketManager.getInstance().newSession(ticket);

    		dataAvenueSession.put(DataAvenueSessionImpl.TICKET_SESSION_KEY, ticket);

    		httpSession.setAttribute(DataAvenueSessionImpl.DATA_AVENUE_SESSION_KEY, dataAvenueSession);
    	} else {
    		if (ticket == null) TicketManager.getInstance().checkTicket((String)dataAvenueSession.get(DataAvenueSessionImpl.TICKET_SESSION_KEY));	
    		else dataAvenueSession.put(DataAvenueSessionImpl.TICKET_SESSION_KEY, ticket); // replace old ticket with the new one
    		
    		log.trace("Using existing DataAvenue session...");
    	}
    	return dataAvenueSession;
    }
}