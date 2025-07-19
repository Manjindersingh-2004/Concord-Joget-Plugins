package org.joget.BasicAuth;

import org.joget.api.model.ApiAuthenticatorAbstract;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.commons.util.LogUtil;
import org.joget.directory.dao.UserDao;
import org.joget.directory.dao.UserMetaDataDao;
import org.joget.directory.model.User;
import org.joget.directory.model.UserMetaData;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.UnsupportedEncodingException;

public class BasicAuth extends ApiAuthenticatorAbstract {
    @Override
    public String getName() {
        return "Basic Authenticator App Permissions";
    }

    @Override
    public String getVersion() {
        return "8.0-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "This is concord plugin to check user permissions to access applications.";
    }

    @Override
    public boolean authenticate(HttpServletRequest request, HttpServletResponse response) {
        String permission = getPropertyString("permission");
        LogUtil.info("Basic Auth","Plugin start");

        String authHeader = request.getHeader("Authorization");
        String usernameB2C = request.getHeader("username");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            LogUtil.info("Auth","invalid auth header");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        if (!(usernameB2C != null && !usernameB2C.isEmpty())){
            LogUtil.info("UserID","UserID is missing");
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

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        LogUtil.info("app def",appDef.toString());

        String appId = "";
        if (appDef != null) {
            appId = appDef.getAppId();
        }

        boolean isEmail = usernameB2B.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

        if (isEmail) {
            LogUtil.info("Authorization Check", "Received email: " + usernameB2B);
            usernameB2B = getUsernameByEmail(usernameB2B);
            if(usernameB2B!=null && !usernameB2B.isEmpty()){
                return validateUser(usernameB2B,password,usernameB2C,appId);
            }
            return false;
        } else {
            LogUtil.info("Authorization Check", "Received usernameB2B: " + usernameB2B);
            return validateUser(usernameB2B,password,usernameB2C,appId);
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


    private boolean validateUser(String usernameB2B,String password, String usernameB2C,String appId) {
        UserDao userDao = (UserDao) AppUtil.getApplicationContext().getBean("userDao");
        User userB2B = userDao.getUserById(usernameB2B);
        User userB2C= userDao.getUserById(usernameB2C);
        if(userB2C==null){
            LogUtil.info("Basic Auth",usernameB2C+" not exists");
            return false;
        }

        String isB2C=AppUtil.processHashVariable("#user."+usernameB2C+".meta.userType#", null, null, null);;
        if(isB2C == null || !isB2C.equals("b2c")){
            LogUtil.info("Basic Auth", usernameB2C+" is not B2C user");
            return false;
        }

        String isB2B=AppUtil.processHashVariable("#user."+usernameB2B+".meta.userType#", null, null, null);;
        if(isB2B == null || !isB2B.equals("b2b")){
            LogUtil.info("Basic Auth", usernameB2B+" is not B2B user");
            return false;
        }


        String isDeletedB2C=AppUtil.processHashVariable("#user."+usernameB2C+".meta.isDeleted#", null, null, null);;
        LogUtil.info("isDeletedB2C",isDeletedB2C);

        if(isDeletedB2C.equals("true")){
            LogUtil.info("Basic Auth",usernameB2C+" is Deleted");
            return false;
        }

        String userParent = AppUtil.processHashVariable("#user."+usernameB2C+".meta.parentId#", null, null, null);
        LogUtil.info("Parent Id",userParent);

        if(!userParent.equals(usernameB2B)){
            LogUtil.info("Basic Auth",usernameB2C+" is not belongs to "+usernameB2B);
            return false;
        }

        String appAllowed = AppUtil.processHashVariable("#user."+usernameB2B+".meta.appServices#", null, null, null);
        LogUtil.info("services",appAllowed);

        if (userB2B != null) {
            LogUtil.info("Basic Auth","user exists");
            if(userB2B.getActive()==1){
                LogUtil.info("Basic Auth","user active");
                String origionalPasswordEncripted=userB2B.getPassword();
                LogUtil.info("origional password",origionalPasswordEncripted);
                password=decriptPassword(password);
                LogUtil.info("user password",password);
                if(password.equals(origionalPasswordEncripted)){
                    LogUtil.info("Auth","Password matched");

                    if(containsExactMatch(appAllowed,appId)){
                        LogUtil.info("Basic Auth","Grant access for:"+appId);
                        return true;
                    }else{
                        LogUtil.info("Basic Auth","Denied access for: "+appId);
                        return false;
                    }

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

    private boolean containsExactMatch(String appAllowed, String appId) {
        if (appAllowed == null || appAllowed.trim().isEmpty()) {
            return false;
        }

        String[] parts = appAllowed.split(";");
        for (String part : parts) {
            if (part.trim().equals(appId)) {
                return true;
            }
        }

        return false;
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
