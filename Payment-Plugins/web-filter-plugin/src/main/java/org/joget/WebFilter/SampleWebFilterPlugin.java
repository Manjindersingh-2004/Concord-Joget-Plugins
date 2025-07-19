package org.joget.WebFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.joget.apps.app.model.PluginWebFilterAbstract;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.SystemConfigurablePlugin;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

public class SampleWebFilterPlugin extends PluginWebFilterAbstract implements SystemConfigurablePlugin {
    static boolean reset=false;

    @Override
    public String getName() {
        return "SampleWebFilterPlugin";
    }

    @Override
    public String getVersion() {
        return "8.2.0";
    }

    @Override
    public String getLabel() {
        return "Sample Web Filter Plugin";
    }

    @Override
    public String getDescription() {
        return "Restrict Payment User to Access Joget Login Page & App center";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/"+getName()+".json", null, false, null);
    }

    @Override
    public String[] getUrlPatterns() {
        return new String[]{
                "/web/login",
                "/web/userview/appcenter/home/_/home",
                "/web/userview/payment/url/_/home"
        };
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        reset=false;
        log("-------------------------------------------------------");
        log("Start");
        log("Session ID: " +((HttpServletRequest) request).getSession().getId());
        if (workflowUserManager.isCurrentUserAnonymous()) {
            log("Anonymous User");
            HttpServletRequest httprequest = (HttpServletRequest) request;
            HttpServletResponse httpresponse = (HttpServletResponse) response;
            String fullUrl = getCurrentUrl(httprequest);

            if (fullUrl.contains("/web/userview/payment/url/_/home")) {
                log("Open Payment");
                if(reset){
                    resetFlags(httprequest);
                }else{
                    setFlags(httprequest,fullUrl);
                }
            }
            else if(fullUrl.contains("/web/login")){
                log("Open Login");
                try {
                    checkPermission(httprequest,httpresponse);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }else if(fullUrl.contains("/web/userview/appcenter/home/_/home")){
                log("Open Appcenter");
                try {
                    checkPermission(httprequest,httpresponse);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }else{
            log("Login User");
        }
        log("exit");
        log("-------------------------------------------------------");
        filterChain.doFilter(request, response);
    }


    public String getCurrentUrl(HttpServletRequest request) {
        StringBuffer fullUrl = request.getRequestURL();
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            fullUrl.append("?").append(query.replaceAll("&?__ajax_\\w+=[^&]*", ""));
        }
        if (query != null && query.contains("reset=true")) {
            reset = true;
        }
        return fullUrl.toString();
    }

    public  void checkPermission(HttpServletRequest httprequest,HttpServletResponse response) throws Exception{
        String uri = httprequest.getRequestURI();
        boolean fromPayment = Boolean.TRUE.equals(httprequest.getSession().getAttribute("fromPayment"));
        if(fromPayment){
            String redirectUrl= (String) httprequest.getSession().getAttribute("redirectTo");
            log("Blocked access to " + uri + " for payment user");
            log("Redirected to " + redirectUrl);
            ((HttpServletResponse) response).sendRedirect(redirectUrl);
        }else{
            log("Grant access to " + uri + " for non payment user");
        }
    }

    public  void setFlags(HttpServletRequest httprequest,String fullUrl){
        boolean isAlreadySet = Boolean.TRUE.equals(httprequest.getSession().getAttribute("fromPayment"));
        if(!isAlreadySet){
            httprequest.getSession().setAttribute("fromPayment", true);
            httprequest.getSession().setAttribute("redirectTo", fullUrl);
            log("Set flag=> fromPayment: true");
            log("Set flag=> redirectionUrl: " + fullUrl);
        }
    }

    public  void resetFlags(HttpServletRequest httprequest){
        log("Reset Session");
        httprequest.getSession().setAttribute("fromPayment", false);
    }


    public void log(String message){
        LogUtil.info(getClassName(),message);
    }

}