package org.joget.UserviewPermissions;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.userview.model.UserviewPermission;
import org.joget.commons.util.LogUtil;


public class CustomUserviewPermissions extends UserviewPermission{
    @Override
    public boolean isAuthorize() {

        LogUtil.info("Basic Auth", "Auth started");

        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        LogUtil.info("app def",appDef.toString());
        String appId = "";
        if (appDef != null) {
            appId = appDef.getAppId();
        }

        String currentUser=AppUtil.processHashVariable("#currentUser.id#", null, null, null);;


        if(currentUser.equals("admin")){
            LogUtil.info("Basic Auth","Grant access for: "+appId +" ("+currentUser+")");
            return true;
        }

        if(appId.equals("appcenter")){
            LogUtil.info("Basic Auth","Grant access for: "+appId +" ("+currentUser+")");
            return true;
        }



        String appAllowed = AppUtil.processHashVariable("#currentUser.meta.appServices#", null, null, null);
        LogUtil.info("services",appAllowed);

        if(containsExactMatch(appAllowed,appId)){
            LogUtil.info("Basic Auth","Grant access for: "+appId +" ("+currentUser+")");
            return true;
        }else{
            LogUtil.info("Basic Auth","Denied access for: "+appId +" ("+currentUser+")");
            return false;
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
    public String getName() {
        return "Userview Permissions";
    }

    @Override
    public String getVersion() {
        return "8.0-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "plugin for checking app permissions for concord users";
    }

    @Override
    public String getLabel() {
        return "Userview Permissions";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "[{}]";
    }

}
