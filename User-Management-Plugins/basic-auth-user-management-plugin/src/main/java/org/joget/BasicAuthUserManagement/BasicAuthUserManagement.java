package org.joget.BasicAuthUserManagement;

import org.joget.api.model.ApiAuthenticatorAbstract;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.workflow.security.WorkflowUserDetails;
import org.joget.commons.util.LogUtil;
import org.joget.directory.dao.UserDao;
import org.joget.directory.model.User;
import org.joget.workflow.model.dao.WorkflowHelper;
import org.json.JSONObject;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

public class BasicAuthUserManagement extends ApiAuthenticatorAbstract {
    @Override
    public String getName() {
        return "Basic Authenticator User Management Api";
    }

    @Override
    public String getVersion() {
        return "8.0-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "This is concord plugin to check user permissions to access user Management Apis.";
    }

    @Override
    public boolean authenticate(HttpServletRequest request, HttpServletResponse response) {
        LogUtil.info("Basic Auth","Plugin start");

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            LogUtil.info("Auth","invalid auth header");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }


        LogUtil.info("Auth",authHeader);

        String token = authHeader.substring(6); // after "Basic "
        byte[] decodedBytes =  Base64.getDecoder().decode(token);
        String decodedString = null;
        try {
            decodedString = new String(decodedBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        String[] auth = decodedString.split(":", 2);
        if (auth.length != 2) {
            return false;
        }

        String usernameB2B = auth[0];
        String password = auth[1];

        LogUtil.info("Username", usernameB2B);
        LogUtil.info("Password", password);

        boolean isEmail = usernameB2B.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");


        if (isEmail) {
            LogUtil.info("Authorization Check", "Received email: " + usernameB2B);
            usernameB2B = getUsernameByEmail(usernameB2B);

            if(usernameB2B!=null && !usernameB2B.isEmpty()){
                return validateUser(usernameB2B,password,request);
            }else{
                return false;
            }
        } else {
            LogUtil.info("Authorization Check", "Received usernameB2B: " + usernameB2B);
            return validateUser(usernameB2B,password,request);
        }

    }

    private String getUsernameByEmail(String email) {
        Connection con;
        try{
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();
            if (!con.isClosed()) {
                // First, check if the user already exists
                PreparedStatement checkStmt = con.prepareStatement("SELECT id FROM dir_user where email= ?");
                checkStmt.setString(1, email);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    LogUtil.info("Auth","username fetch by username");
                    return rs.getString("id");
                }else{
                    LogUtil.info("Auth","username not fetch by username");
                }
                rs.close();
                checkStmt.close();
            }
        }catch(Exception e){
            LogUtil.info("Auth",""+e);
        }
        return null;
    }


    private boolean validateUser(String usernameB2B,String password, HttpServletRequest request) {
        UserDao userDao = (UserDao) AppUtil.getApplicationContext().getBean("userDao");
        User userB2B = userDao.getUserById(usernameB2B);

        if (userB2B != null) {
            LogUtil.info("Basic Auth","user exists");

            String isB2B=AppUtil.processHashVariable("#user."+usernameB2B+".meta.userType#", null, null, null);;
            if(isB2B == null || !isB2B.equals("b2b")){
                LogUtil.info("Basic Auth", usernameB2B+" is not B2B user");
                return false;
            }

            if(userB2B.getActive()==1){
                LogUtil.info("Basic Auth","user active");
                String origionalPasswordEncripted=userB2B.getPassword();
                LogUtil.info("origional password",origionalPasswordEncripted);
                password=decriptPassword(password);
                LogUtil.info("user password",password);
                if(password.equals(origionalPasswordEncripted)){
                    LogUtil.info("Auth","Password matched");


                    UserDetails details = new WorkflowUserDetails(userB2B);
                    UsernamePasswordAuthenticationToken result = new UsernamePasswordAuthenticationToken(usernameB2B,password);
                    result.setDetails(details);
                    SecurityContextHolder.getContext().setAuthentication(result);
                    WorkflowHelper workflowHelper = (WorkflowHelper) AppUtil.getApplicationContext().getBean("workflowHelper");
                    workflowHelper.addAuditTrail(this.getClass().getName(), "authenticate", "Authentication for user " + usernameB2B + ": " + true);


                    return true;
//
                }else{
                    LogUtil.info("Auth", "Invalid Password");
                    return false;
                }
            }else{
                LogUtil.info("Basic Auth","user not active");
                return false;
            }
        }else{
            LogUtil.info("Basic Auth","user not exists");
            return false;
        }
    }

    private String decriptPassword(String origionalPasswordEncripted) {

        try {
            MessageDigest md = MessageDigest.getInstance("MD5"); // MD5 algorithm
            byte[] messageDigest = md.digest(origionalPasswordEncripted.getBytes("UTF-8"));

            // Convert byte[] to hex string
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messageDigest.length; i++) {
                int val = messageDigest[i] & 0xff;
                if (val < 16) {
                    sb.append("0");
                }
                sb.append(Integer.toHexString(val));
            }

            return sb.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

    }

    public boolean isValidIdInBody(HttpServletRequest request,String usernameB2B) {

        try {
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String body = sb.toString();
            LogUtil.info("here in body",body);
            JSONObject json = new JSONObject(body);


            // Check for "id"
            if (json.has("id") && !json.isNull("id")) {
                String usernameB2C = (String) json.get("id");
                if(usernameB2C!=null){
                    String userParent = AppUtil.processHashVariable("#user."+usernameB2C+".meta.parentId#", null, null, null);
                    LogUtil.info("parent of "+usernameB2C+": ",userParent);
                    if(userParent.equals(usernameB2B)){
                        LogUtil.info(usernameB2B+" is parent of "+usernameB2C,"true");
                        return true;
                    }else{
                        LogUtil.info(usernameB2B+" is parent of "+usernameB2C,"false");
                        return false;
                    }
                }else{
                    LogUtil.info("UsernameB2C", "not pass in payload");
                }
            }else{
                LogUtil.info("Id","not send in payload");
            }
        } catch (Exception e) {
            LogUtil.info("Error",e.getMessage());
        }

        LogUtil.info("default :","return true");
        return true;
    }




    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        //return AppUtil.readPluginResource(getClassName(), "/properties/basicAuth.json", null, true, null);
        return null;
    }

}




