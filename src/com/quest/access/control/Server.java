package com.quest.access.control;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.taskqueue.DeferredTask;
import com.quest.access.common.UniqueRandom;
import com.quest.access.common.io;
import com.quest.access.common.datastore.Datastore;
import com.quest.access.crypto.Security;
import com.quest.access.useraccess.services.Service;
import com.quest.access.useraccess.services.Serviceable;
import com.quest.access.useraccess.services.Endpoint;
import com.quest.access.useraccess.services.WebService;
import com.quest.servlets.ClientWorker;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 *
 * @author constant oduol
 * @version 1.0(4/1/2012)
 */
/**
 * This file defines a server When a new server is created a new database is
 * created along with it the database has the following tables
 * <p>
 * USERS- this table contain details of users on this server PRIVILEGES- this
 * table contains privileges of all the users on this server RESOURCE_GROUPS-
 * this table stores the permanent privileges on this server RESOURCES- this
 * table stores the resources on this server USER_HISTORY- this table stores
 * details of deleted users LOGIN- this table stores the login details of a user
 * for every new login LOGOUT- this table stores the logout details of a user
 * for every logout SERVICES- this table stores the services registered on this
 * server
 * </p>
 * <p>
 * when an instance of a server is created it starts listening for client
 * connections on the port specified during its creation when a client connects
 * the server sends the client a new request for the client to login, the client
 * needs to respond by sending a response with the login details. The server
 * then tries to log in the user, the server then responds to the client with
 * the login status
 * </p>
 *
 * <p>
 * Clients can send requests to a server and a servers can sent requests to
 * clients A server has the method processRequest() which when overriden can be
 * used to process requests from clients. When clients send a request for a
 * service the server class invokes the required service through the private
 * method processClientRequest()
 * </p>
 *
 * <p>
 * A server has a privilege handler class that ensures that only users that have
 * the required privileges access services on the server, users with no
 * privileges have an security exception object returned back to the client to
 * show that the client was denied access to the privilege he was not
 * assigned.Also, once a client logs out of the system, if he tries to access
 * any service a security exception is sent to the client in a response object
 * therefore clients should check the message in a response object if it equals
 * "exception" so as to handle exceptions send by the server
 * </p>
 *
 * <p>
 * Clients can make standard requests to the server sending a request with the
 * following messages makes the server respond as specified
 * <p>
 * logoutuser- this asks the server to log out the user accessing the server
 * through the client that sent the request, sending this request requires the
 * client to send the users username along with the message e.g. new
 * Request(userName,"logoutuser");
 * </p>
 * <p>
 * logoutclient- this asks the server to log out the user accessing the server
 * through the client that sent the request, sending this request does not
 * require the user's user name e.g. new Request("logoutclient");
 * </p>
 * <p>
 * forcelogout- this asks the server to mark the user in the database as logged
 * out, the user name of the user to be marked as logged out is sent in the
 * request object e.g. new Request(userName,"forcelogout");
 * </p>
 * </p>
 *
 * <p>
 * When a user logs in to a server a new Session is created for that user a
 * session has several attributes predefined by the server if ses is an instance
 * of a user session then ses.getAttribute("attributename") returns the required
 * attribute
 * <ol>
 * <li>clientid - this is the id of the connected client</li>
 * <li>username - this is the username of the connected client</li>
 * <li>host - this is the host from which the client is connecting</li>
 * <li>clientip - this is the ip address of the client machine</li>
 * <li>privileges - this is a hashmap containing the privileges of the user</li>
 * <li>userid - this is a string representing the twenty digit system generated
 * id</li>
 * <li>superiority - this is a double value representing the user's
 * superiority</li>
 * <li>created - this is a date object representing when the user was
 * created</li>
 * <li>group - this is the group that the user belongs to or "unassigned" if the
 * user does not belong to any group</li>
 * <li>loginid - this is the system generated id representing the user's most
 * recent login</li>
 * <li>lastlogin - this is the system generated id representing the user's
 * previous login</li>
 * <li>sessionstart - this is a date object representing when this user's
 * session started</li>
 * </ol>
 * </p>
 *
 * <p>
 * The LOGIN table contains details about user logins, the user name, client ip,
 * server ip, and time of login are stored in the login table,similarly the
 * LOGOUT table contains details about successful logouts that is the user name,
 * client ip, server ip and logout time.Login and logout from one session by a
 * client is marked by one id i.e the login id is the same as the logout id for
 * any user session
 * </p>
 *
 * <p>
 * During user login the server normally returns messages depending on the
 * status of the login these messages can be obtained from the returned response
 * object by calling the getResponse() method
 * <ol>
 * <li>notexist - the server returns this response if the user attempting to log
 * in does not exist</li>
 * <li>disabled - the server returns this if the user attempting to log in has
 * his account disabled</li>
 * <li>loggedin - the server returns this if the user attempting to log in is
 * already logged in</li>
 * <li>loginsuccess - the server returns this if the user has been successfully
 * logged in</li>
 * <li>invalidpass - the server returns this if the user trying to log in has a
 * valid username but invalid password</li>
 * <li>changepass - the server returns this if a user's password is expired or
 * for a new user in order for the user to change his password</li>
 * <li>maxpassattempts - this message is sent to inform the client that he has
 * reached the maximum allowed password attempts</li>
 * </ol>
 * </p>
 */
public class Server {

    private static ConcurrentHashMap<String, HttpSession> sessions = new ConcurrentHashMap();
    /*
     * this contains the services available to the server
     */
    private static HashMap<String, ArrayList> services;

    /*
     * this variable controls whether the server prints out error messages or
     * not
     */
    private boolean debugmode;

    /*
     * this hashmap contains instances of started services
     */
    private final ConcurrentHashMap<String, Object> runtimeServices;

    /*
     * this hashmap contains mappings of message names to their respective
     * methods
     */
    private final ConcurrentHashMap<String, Method> serviceRegistry;


    private ServletConfig config;


    /*
     * contains information about method sharing between services
     */
    private ConcurrentHashMap<String, Object[]> sharedRegistry;
    /**
     * the key is the root worker id and the value is an array of client workers
     */
    private ConcurrentHashMap<String, ClientWorker[]> rootWorkers;

    private static ScriptEngine scriptEngine;
    
    public Server() {
        this.runtimeServices = new ConcurrentHashMap();
        this.serviceRegistry = new ConcurrentHashMap();
        this.sharedRegistry = new ConcurrentHashMap<>();
        this.rootWorkers = new ConcurrentHashMap<>();
        ScriptEngineManager factory = new ScriptEngineManager();
        scriptEngine = factory.getEngineByName("JavaScript");
    }


    public void setConfig(ServletConfig config) {
        this.config = config;
    }

    public ServletConfig getConfig() {
        return this.config;
    }

    /*
     * if this is true the server will print stacktraces on errors
     */
    public void setDebugMode(boolean mode) {
        this.debugmode = mode;
    }


    /**
     * this method gets all the services belonging to a server in a hash map
     * with the key as the service name and the value as the location of the
     * service class
     */
    public static HashMap<String, ArrayList> getServices() {
        return services;
    }


    
    public long timestamp(){
        return System.currentTimeMillis();
    }

    public void initExternalServices(String services) {
        StringTokenizer token = new StringTokenizer(services, ",");
        HashMap serviceMap = new HashMap();
        while (token.hasMoreTokens()) {
            try {
                String serviceLocation = token.nextToken().trim();
                Class serviceClass = Class.forName(serviceLocation.trim());
                WebService webService = (WebService) serviceClass.getAnnotation(WebService.class);
                if (webService != null) {
                    ArrayList values = new ArrayList();
                    int level = webService.level();
                    String serviceName = webService.name();
                    Service service = new Service(serviceName, serviceClass, this);
                    values.add(serviceLocation);
                    values.add(level);
                    values.add(webService.privileged());
                    values.add(service);
                    serviceMap.put(serviceName, values);
                }
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Server.services = serviceMap;
    }

    public void startService(String serviceLocation) {
        try {
            Class serviceClass = Class.forName(serviceLocation);
            Object newInstance = serviceClass.newInstance();
            runtimeServices.put(serviceLocation, newInstance);
            registerMethods(serviceClass);
            Method method = serviceClass.getMethod("onStart", new Class[]{Server.class});
            method.invoke(newInstance, new Object[]{this});
            io.log("Service " + serviceLocation + " Started successfully ", Level.INFO, Server.class);
        } catch (Exception e) {
            io.log("Error starting service " + serviceLocation + ": " + e, Level.SEVERE, Server.class);
            e.printStackTrace();
        }
    }

    /**
     * this method registers message name mappings to methods
     */
    private void registerMethods(Class<?> serviceClass) {
        try {
            Method[] methods = serviceClass.getDeclaredMethods();
            WebService webService = (WebService) serviceClass.getAnnotation(WebService.class);
            String serviceName = webService != null ? webService.name() : "";
            for (Method method : methods) {
                Endpoint endpoint = method.getAnnotation(Endpoint.class);
                if (endpoint != null) {
                    String message = endpoint.name();
                    String[] modifiers = endpoint.cacheModifiers();
                    String key = message + "_" + serviceClass.getName();
                    String key1 = serviceName + "_" + message;
                    serviceRegistry.put(key, method);
                    String[] shareWith = endpoint.shareMethodWith();
                    for (String shareWith1 : shareWith) {
                        String shareKey = message + "_" + shareWith1; // all_fields_mark_service
                        sharedRegistry.put(shareKey, new Object[]{message, serviceClass.getName()});
                    }
                }
            }
        } catch (Exception e) {
            io.log(e, Level.SEVERE, Server.class);
        }
    }
    
    public static String get(String urlToRead) throws Exception {
        StringBuilder result = new StringBuilder();
        URL url = new URL(urlToRead);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }

    /**
     * this method sends a synchronous request to a remote url and awaits the
     * response the returned data is in json format
     *
     * @param requestData the data passed to the remote url
     * @param remoteUrl the remote url e.g https://10.1.10.190:8080/web/server
     * @return a json object with the data returned by the url
     */
    public static JSONObject remote(Object requestData, String remoteUrl) {
        try {
            String urlParams = URLEncoder.encode("json", "UTF-8") + "=" + URLEncoder.encode(requestData.toString(), "UTF-8");
            URL url = new URL(remoteUrl);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("Accept", "application/json");
            HttpURLConnection httpConn = (HttpURLConnection) conn;
            httpConn.setRequestMethod("POST");
            httpConn.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(httpConn.getOutputStream());
            wr.writeBytes(urlParams);
            wr.flush();
            wr.close();
            int responseCode = httpConn.getResponseCode();
            BufferedReader reader;
            if (responseCode == 200) {
                reader = new BufferedReader(new InputStreamReader(httpConn.getInputStream()));
                String inputLine = reader.readLine();
                reader.close();
                return new JSONObject(inputLine);
            } else {
                return null;
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    public void processClientRequest(ClientWorker worker) {
        try {
            String service = worker.getService();
            ArrayList values = (ArrayList) Server.services.get(service);
            if (values != null) {
                try {
                    String location = (String) values.get(0);
                    //TODO make more service instances available in future
                    Object serviceInstance = this.runtimeServices.get(location); //we have only one instance of this service
                    Serviceable serviceProx = (Serviceable) this.proxify(serviceInstance, worker, service, location);
                    serviceProx.service();
                } catch (Exception e) {
                    io.out("An error occurred while invoking service: " + service + " Reason:" + e);
                    e.printStackTrace();
                    worker.setResponseData(e);
                    exceptionToClient(worker);
                }
            } else {
                io.out("Service " + service + " not found on server");
                worker.setResponseData("Service " + service + " not found on server");
                messageToClient(worker);
            }
        } catch (Exception e) {
            worker.setResponseData(e);
            exceptionToClient(worker);
        }
    }




    /**
     * this method is used to ensure that a user on the front end can invoke
     * multiple services with multiple messages at the same time, if one of the
     * requests fail due to insufficient privileges all the requests fail also
     * e.g.
     *
     * <code>
     * <br>
     * Ajax.run({<br>
     * url : serverUrl,<br>
     * type : "post",<br>
     * data : {<br>
     * request_header : {<br>
     * request_msg : "all_streams",<br>
     * request_svc :"mark_service"<br>
     * }<br>
     * },<br>
     * error : function(err){<br>
     *
     * },<br>
     * success : function(json){<br>
     *
     * } <br>
     * }); <br>
     * </code> and <code>
     * <br>
     * Ajax.run({ <br>
     * url : serverUrl,<br>
     * type : "post",<br>
     * data : {<br>
     * request_header : {<br>
     * request_msg : "all_students",<br>
     * request_svc :"student_service"<br>
     * }<br>
     * },<br>
     * error : function(err){<br>
     *
     * },<br>
     * success : function(json){<br>
     *
     * } <br>
     * });
     * </code>
     *
     * can be combined to <code>
     * <br>
     * Ajax.run({ <br>
     * url : serverUrl,<br>
     * type : "post",<br>
     * data : {<br>
     * request_header : { <br>
     * request_msg : "all_streams, all_students", <br>
     * request_svc :"mark_service, student_service" <br>
     * } <br>
     * }, <br>
     * error : function(err){ <br>
     *
     * }, <br>
     * success : function(json){ <br>
     * var all_streams = json.data.response[0] <br>
     * var all_students = json.data.response[1] <br>
     * } <br>
     * });
     * </code> <br>
     * data is only sent back to the client after the last request is completed
     * if request one returns immediately but request two delays then the data
     * will be transmitted to the client after request two completes
     *
     * @param rootWorker
     */
    public void invokeMultipleServices(ClientWorker rootWorker) {

        String servicez = rootWorker.getService();
        String messagez = rootWorker.getMessage();
        StringTokenizer st = new StringTokenizer(servicez, ",");
        StringTokenizer st1 = new StringTokenizer(messagez, ",");
        if (st.countTokens() == 1) {
            processClientRequest(rootWorker); // there is only one service and
            // one message so just invoke
            // the required service
        } else {
            /*
             * here we have more than one service and one message e.g
             * request_msg : "all_streams, all_students", request_svc
             * :"mark_service, student_service" the strategy is to split the
             * root worker into many workers, we discard the root worker and
             * then service each slave worker individually, now when the first
             * slave worker responds we check to see whether its other slave
             * workers have responded, if its the last slave worker then we send
             * the response to the client we save the worker id and its data
             */
            ClientWorker[] workers = new ClientWorker[st.countTokens()];
            for (int x = 0; st.hasMoreTokens(); x++) {
                String service = st.nextToken().trim();
                String message = st1.nextToken().trim();
                ClientWorker worker =  new ClientWorker(message, service,rootWorker.getRequestHeader(),rootWorker.getRequestData(), 
                        rootWorker.getSession(),rootWorker.getResponse(), rootWorker.getRequest());
                worker.setRootWorkerID(rootWorker.getID());
                workers[x] = worker;
            }
            rootWorkers.put(rootWorker.getID(), workers);
            for (ClientWorker theWorker : workers) {
                processClientRequest(theWorker);
            }
        }

    }


    /**
     * the strategy is to send the response directly to the client if this
     * worker has no root worker id. if this worker has a root worker id, it
     * means it was spawned from a root worker, so check if this is the last
     * worker, if it is take all the pending data and send it to the client. the
     * keys for the response are servicename_messagename
     *
     * @param worker the client worker that we are responding to
     *
     */
    public void messageToClient(ClientWorker worker) {
        try {
            String rootWorkerId = worker.getRootWorkerID();
            String busId = worker.getRequestData().optString("business_id");
            if (rootWorkerId == null && worker.getPropagateResponse()) {
                // this is a root worker, complete the request
                // propagate the response if we have been requested to
                JSONObject object = new JSONObject();
                object.put("data", worker.getResponseData());
                object.put("reason", worker.getReason());
                worker.toClient(object);
            } else if (rootWorkerId == null) {
                // do nothing because we shouldnt propagate
            } else {
                /*
                 * the strategy is to check which workers have their response
                 * data as null if any worker still has no response data, keep
                 * waiting, otherwise bundle up the response and send it
                 */
                JSONObject data = new JSONObject();
                boolean complete = false;
                ClientWorker[] workers = rootWorkers.get(rootWorkerId);
                for (ClientWorker theWorker : workers) {
                    complete = theWorker.getResponseData() != null;
                    JSONObject object = new JSONObject();
                    object.put("data", theWorker.getResponseData());
                    object.put("reason", theWorker.getReason());
                    data.put(theWorker.getService() + "_" + theWorker.getMessage(), object);
                }
                if (complete && worker.getPropagateResponse()) {
                    worker.toClient(data); // propagate response because we have
                    // been asked to do it
                    rootWorkers.remove(rootWorkerId);
                } else if (complete && !worker.getPropagateResponse()) {
                    rootWorkers.remove(rootWorkerId);
                }
            }
        } catch (JSONException ex) {
            io.log(ex, Level.SEVERE, Server.class);
        }
    }

    public void exceptionToClient(ClientWorker worker) {
        try {
            Throwable obj = (Throwable) worker.getResponseData();
            obj.printStackTrace();
            String rootWorkerId = worker.getRootWorkerID();
            if (rootWorkerId == null && worker.getPropagateResponse()) {
                JSONObject object = new JSONObject();
                object.put("exception", obj);
                object.put("reason", worker.getReason());
                object.put("type", "exception");
                object.put("ex_reason", obj.getMessage());
                worker.toClient(object);
            } else if (rootWorkerId == null) {
                // do nothing
            } else {
                /*
                 * the strategy is to check which workers have their response
                 * data as null if any worker still has no response data, keep
                 * waiting, otherwise bundle up the response and send it
                 */
                JSONObject data = new JSONObject();
                boolean complete = false;
                ClientWorker[] workers = rootWorkers.get(rootWorkerId);
                for (ClientWorker theWorker : workers) {
                    complete = theWorker.getResponseData() != null;
                    JSONObject object = new JSONObject();
                    object.put("exception", obj);
                    object.put("reason", theWorker.getReason());
                    object.put("type", "exception");
                    object.put("ex_reason", obj.getMessage());
                    data.put(theWorker.getService() + "_" + theWorker.getMessage(), object);
                }
                if (complete && worker.getPropagateResponse()) {
                    worker.toClient(data);
                    rootWorkers.remove(rootWorkerId);
                } else if (complete && !worker.getPropagateResponse()) {
                    rootWorkers.remove(rootWorkerId);
                }
            }
        } catch (JSONException ex) {
            io.log(ex, Level.SEVERE, Server.class);
        }
    }

    /**
     * this method is used to get an object that has been proxied, to control
     * access to that object by checking whether a user has access to the
     * specified permanent privilege, access to the returned object is
     * controlled by the proxy class that implements the interface wrapped in
     * the resource object
     *
     * @param obj the object we want to have proxy control to
     * @param res the resource object containing the wrapped interface to be
     * implemented by the dynamic proxy class
     * @param clientID the current client
     * @param priv the privilege we want to control to
     * @return the object whose access is controlled through a proxy
     */
    public Object proxify(Object obj, ClientWorker worker, String priv, String clazz) {
        ClassLoader cl = obj.getClass().getClassLoader();
        return Proxy.newProxyInstance(cl, new Class[]{Serviceable.class},
                new PrivilegeHandler(obj, this, worker, priv, clazz));
    }

    /**
     * this class controls access to a server's privileged services or methods
     * it uses java.lang.reflect.Proxy class for dynamic proxies
     */
    private class PrivilegeHandler implements InvocationHandler, java.io.Serializable {

        private Object obj;
        private String priv;
        private String clazz;
        private ClientWorker worker;

        public PrivilegeHandler(Object obj, Server serv, ClientWorker worker, String priv, String clazz) {
            this.obj = obj;
            this.worker = worker;
            this.priv = priv;
            this.clazz = clazz;
        }

        private Object[] getSharedData() {
            String serviceName = worker.getService(); // mark_service
            String message = worker.getMessage(); // all_fields
            String key = message + "_" + serviceName;
            Object[] data = sharedRegistry.get(key);
            if (data == null) {
                return null;
            }
            String methodKey = data[0] + "_" + data[1];
            Method method = serviceRegistry.get(methodKey);
            Object[] shareData = new Object[]{data[0], data[1], method}; // messagename,
            // service
            // instance,method
            return shareData;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            boolean permContains = false;
            Object[] sharedData;
            String uName;
            String privState = Server.services.get(worker.getService()).get(2).toString();
            if (privState.equals("yes")) {
                HttpSession ses = worker.getSession();
                uName = (String) ses.getAttribute("username");
                JSONArray privileges = (JSONArray) ses.getAttribute("privileges");
                permContains = privileges != null && privileges.toList().contains(this.priv); // privilege
            } else {
                uName = "anonymous";
            }

            sharedData = getSharedData();

            if (permContains || privState.equals("no")) {
                try {
                    Method met = serviceRegistry.get(worker.getMessage() + "_" + clazz);
                    if (met != null) { //this is the first attempt
                        io.log("[" + uName + "] Service invoked: " + obj.getClass().getSimpleName() + " Method: " + met.getName(), Level.INFO, Server.class);
                        return met.invoke(obj, new Object[]{Server.this, worker});
                    } else { //this is a shared method
                        if (sharedData != null) {
                            Object serviceInstance = runtimeServices.get(sharedData[1].toString());
                            Method sharedMethod = (Method) sharedData[2];
                            io.log(" [" + uName + "] Service invoked: " + serviceInstance.getClass().getSimpleName() + " Shared Method: " + sharedMethod.getName(), Level.INFO, Server.class);
                            return sharedMethod.invoke(serviceInstance, new Object[]{Server.this, worker});
                        } else {
                            worker.setResponseData("The specified message " + worker.getMessage() + ""
                                    + " does not exist for service " + worker.getService());
                            messageToClient(worker);
                        }
                    }
                    // if this fails check to see if there is a service that has shared this method with the currently invoked service
                } catch (Exception e) {
                    if (Server.this.debugmode) {
                        io.log(e.getCause(), Level.SEVERE, this.getClass());
                    }
                }
            } else {
                worker.setResponseData("No privileges found for requested service");
                messageToClient(worker);
            }

            return null;
        }
    }

    /**
     * this method can be used to invoke a service within another service
     *
     * @param worker this represents the client request
     */
    public void invokeService(ClientWorker worker) {
        processClientRequest(worker);
    }

    public static class BackgroundTask implements DeferredTask{
        
        private String script;
        
        private String aggregator;
        
        private String requestId;
        
        private String preScript = "var kinds, filters, orders, limits;";
        
        public BackgroundTask(String aggregator, String requestId, String script){
            this.script = script;
            this.aggregator = aggregator;
            this.requestId = requestId;
        }
        @Override
        public void run() {
            try {
                //perform long running task, with a deadline of 10min
                script = preScript + script;
                Object resp = Server.scriptEngine.eval(script);
                io.out("^^^^^^^^^^^^^^^^^6");
                io.out(resp);
            } catch (ScriptException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
    }
}
