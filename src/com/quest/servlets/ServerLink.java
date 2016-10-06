package com.quest.servlets;

import com.quest.access.common.io;
import com.quest.access.control.Server;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONObject;


/** 
 *
 * @author connie
 */


public class ServerLink extends HttpServlet {
    
    private static Server server;
    
    private static HashMap<String,String> requestMappings;
    

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            response.setContentType("text/html;charset=UTF-8");
            response.setHeader("Access-Control-Allow-Origin", "*");
            String json = request.getParameter("json");
            HttpSession session = request.getSession();
            //session, request, response
            JSONObject obj = new JSONObject();
            JSONObject requestData;
            JSONObject headers = new JSONObject();
            String msg,service;
            if (json != null) { //here we are dealing with json
                obj = new JSONObject(json);
                headers = obj.optJSONObject("request_header");
                msg = headers.optString("request_msg");
                service = headers.optString("request_svc");
                requestData = (JSONObject) obj.optJSONObject("request_object");
            }
            else { 
                //here we are dealing with a url string e.g name=me&age=20
                //json is null check for other parameters and build the required 
                //request
                //check for svc, msg, ses_id
                service = request.getParameter("svc");
                msg = request.getParameter("msg");
                headers.put("request_msg", msg);
                headers.put("request_svc", service);
                Map<String, String[]> paramz = request.getParameterMap();
                HashMap<String, String[]> params = new HashMap(paramz);
                params.remove("svc");
                params.remove("msg");
                Iterator iter = params.keySet().iterator();
                while(iter.hasNext()){
                    String key = iter.next().toString();
                    String [] param = params.get(key);
                    if(param.length == 1){
                        obj.put(key, param[0]);
                    }
                    else {
                        JSONArray arr = new JSONArray();
                        for(String value : param){
                            arr.put(value);
                        }
                       obj.put(key, arr);
                    }
                }
                requestData = obj;
            }
            new ClientWorker(msg, service,headers,
                    requestData,session, response, request).work();
          
        } catch (Exception ex) {
            Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     *
     * @param response
     * @param msgKey
     * @param msgValue
     */
    public static void sendMessage(HttpServletResponse response,String msgKey, String msgValue){
        try {
            JSONObject object = new JSONObject();  
            object.put("request_msg",msgKey);
            object.put("data",msgValue);
            response.getWriter().print(object);
        } catch (Exception ex) {
            Logger.getLogger(ServerLink.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
 
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a String containing servlet description
     */ 
    @Override
    public String getServletInfo() {
        return "Entry point to the server";
    }// </editor-fold>
    
    /**
     *
     */
    @Override 
    public void init(){
         ServletConfig config = getServletConfig();
         String services = config.getInitParameter("external-services");
         String debugMode = config.getInitParameter("debug-mode");
         boolean mode = debugMode != null && debugMode.equals("true");
         server = new Server();
         server.setConfig(config);
         server.initExternalServices(services);
         server.setDebugMode(mode);
    }
    
    /**
     *
     * @return
     */
    public static Server getServerInstance(){
        return server;
    }
    
  
}
