package com.quest.access.control;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.quest.access.common.io;
import com.quest.access.useraccess.services.Service;
import com.quest.access.useraccess.services.Serviceable;
import com.quest.access.useraccess.services.Endpoint;
import com.quest.access.useraccess.services.WebService;
import com.quest.servlets.ClientWorker;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.Scanner;
import java.util.logging.Logger;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 *
 * @author constant oduol
 * @version 1.0(4/1/2012)
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
    
    public Server() {
        this.runtimeServices = new ConcurrentHashMap();
        this.serviceRegistry = new ConcurrentHashMap();
        this.sharedRegistry = new ConcurrentHashMap<>();
        this.rootWorkers = new ConcurrentHashMap<>();
        
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
            for (Method method : methods) {
                Endpoint endpoint = method.getAnnotation(Endpoint.class);
                if (endpoint != null) {
                    String message = endpoint.name();
                    String key = message + "_" + serviceClass.getName();
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
    
    public static String post(String remoteUrl, String urlParams) {
        try {
            URL url = new URL(remoteUrl);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("Accept", "text/html");
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
                return inputLine;
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
    
    public static String streamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
    
    //_service_ service
    public static Object execScript(String script, HashMap<String, Object> objects){
        Context ctx = Context.enter();
        try {
            Scriptable scope = ctx.initStandardObjects();
            if(objects != null){
                for(String key : objects.keySet()){
                    Object wrappedObject = Context.javaToJS(objects.get(key), scope);
                    ScriptableObject.putProperty(scope, key, wrappedObject);   
                }
            }
            return ctx.evaluateString(scope, script, "<cmd>", 1, null);
        } finally {
            Context.exit();
        }
    }
    

    public static class BackgroundTask implements DeferredTask{
        
        private String script;
        
        private String aggregator;
        
        private String requestId;
        
        private String node;
        
        private final String postScript = "\nnextData()";
        
        private String aggregatorUrl;
        
        private String mcpScript;
        
        private final long maxDuration = 480000; //8 min
        
        private long startTime;
        
        
        private class DeadlineReachedException extends Exception {
            public DeadlineReachedException(){
                super("Deadline reached when executing deferred task");
            }
        }
        
        public BackgroundTask(String aggregator, String requestId, 
                String script, String node, String mcpScript){
            this.script = script;
            this.aggregator = aggregator;
            this.requestId = requestId;
            this.aggregatorUrl = "https://" + aggregator + ".appspot.com/server";
            //this.aggregatorUrl = "http://localhost:8200/server";
            this.node = node;
            this.mcpScript = mcpScript;
            startTime = System.currentTimeMillis();
        }
        
        public void sendMessage(String msg){
            String params = "svc=mcp_service&msg=bg_message&request_id="
                    +requestId+"&message="+msg+"&script="+script;
            Server.post(aggregatorUrl, params);
        }
        
        private void restartTask(){
            Queue queue = QueueFactory.getDefaultQueue();
            queue.add(TaskOptions.Builder
                    .withPayload(new BackgroundTask(aggregator, requestId, script, node, mcpScript))
                    .etaMillis(System.currentTimeMillis()));//start executing the task now!
        }
        
        public String post(String url, String params, String action){
            try {
                if(shouldComplete() && action.equals("nextdata")){
                    //we need to restart this background task
                    restartTask();
                    throw new DeadlineReachedException();
                }
                String result = Server.post(url, params);
                return result;
            } catch (Exception ex) {
                //send this error message to the aggregator
                sendMessage(ex.getLocalizedMessage());
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                return "{}";
            }
        }
        
        private boolean shouldComplete(){
            return (System.currentTimeMillis() - startTime) > maxDuration;
        }
        
        @Override
        public void run() {
            try {
                //perform long running task, with a deadline of 10min
                HashMap params = new HashMap();
                params.put("_aggregator_url_", aggregatorUrl);
                params.put("_request_id_", requestId);
                params.put("_task_", this);
                params.put("_script_", script);
                params.put("_aggregator_state_", new JSONObject());
                script = URLDecoder.decode(script, "utf-8");
                String completeScript = mcpScript + script + postScript;
                Server.execScript(completeScript, params);
                io.log("run script in background initiated", Level.WARNING, this.getClass());
            } catch (Exception ex) {
                sendMessage(ex.getLocalizedMessage());
                //send this error message to the aggregator
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
    }
}
