package org.joget.CustomListApiEmailSMS;

import org.enhydra.shark.api.internal.toolagent.AppParameter;
import org.joget.api.model.ApiDefinition;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;


import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;


import java.util.HashMap;
import java.util.Map;

public class CustomListApiEmailSms extends ApiPluginAbstract {
    @Override
    public String getIcon() {
        return "<i class=\"fas fa-file-alt\"></i>";
    }

    @Override
    public String getTag() {
        return "templates/{listDefId}";
    }

    @Override
    public String getName() {
        return "CustomListApiEmailSmsProp";
    }


    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Custom List Api to return dynamic vars as array";
    }

    @Override
    public String getLabel() {
        return "Custom list api (Templates Dynamic vars)";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }



    @Override
    public String getPropertyOptions() {

        String contextPath = AppUtil.getRequestContextPath(); // e.g., /jw
        LogUtil.info("context path",contextPath);
        // Get current app definition
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        LogUtil.info("app def",appDef.toString());

        String appPath = "";
        if (appDef != null) {
            appPath = appDef.getAppId() + "/" + appDef.getVersion();
        }
        LogUtil.info("app path",appPath);


//        String path = AppUtil.readPluginResource(getClassName(), "/properties/api/CustomPropertyFile.json", null, true, getResourceBundlePath());
//        LogUtil.info(getClassName(), path);
        //return AppUtil.readPluginResource(getClassName(), "/properties/api/CustomPropertyFile.json", null, true, getResourceBundlePath());

        return "[{" +
                "\"title\": \"\"," +
                "\"properties\": [" +
                "{" +
                "\"name\": \"listDefId\"," +
                "\"label\": \"List\"," +
                "\"type\": \"selectbox\"," +
                "\"options_ajax\": \""+contextPath+"/web/json/console/app/"+appPath+"/datalist/options\"," +
                "\"required\": \"True\"" +
                "}," +
                "{" +
                "\"name\": \"label\"," +
                "\"label\": \"Discription\"," +
                "\"type\": \"textfield\"" +
                "}" +
                "]" +
                "}]";
    }


    @Operation(path = "/", type = Operation.MethodType.GET, summary = "Get templates with array of dynamic vars", description = "Get templates with array of dynamic vars\nModify the dynamc vars fro string to array")
    @Responses({
            @Response(responseCode = 200, description = "Success"),
            @Response(responseCode = 405, description = "Failed")})
    public ApiResponse getListData() {
        JSONArray jsonArray = new JSONArray();
        try {
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            DataListService dataListService = (DataListService) AppUtil.getApplicationContext().getBean("dataListService");
            DatalistDefinitionDao datalistDefinitionDao = (DatalistDefinitionDao) AppUtil.getApplicationContext().getBean("datalistDefinitionDao");

            String id = getPropertyString("listDefId");
            LogUtil.info("Id :",id);

            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            DatalistDefinition datalistDefinition = datalistDefinitionDao.loadById(id, appDef);

            if (datalistDefinition != null) {
                DataList list = dataListService.fromJson(datalistDefinition.getJson());
                LogUtil.info("JSON: ",datalistDefinition.getJson().toString());

                DataListCollection rows = list.getRows();
                for (Object row : rows) {
                    if (row instanceof Map) {
                        Map<String, Object> rowData = (Map<String, Object>) row;
                        JSONObject jsonObj = new JSONObject(rowData);
                        LogUtil.info("Row Data: ",jsonObj.toString());

                        // Convert paymentgateways from string to JSON array
                        if (jsonObj.has("dynamic_variables")) {
                            Object dynamicVariables = jsonObj.get("dynamic_variables");

                            if (dynamicVariables instanceof String) {
                                try {
                                    JSONArray parsedDynamicVariables = new JSONArray((String) dynamicVariables);
                                    jsonObj.put("dynamic_variables", parsedDynamicVariables);
                                } catch (Exception e) {
                                    LogUtil.error(getClassName(), e, "Error parsing paymentgateways field");
                                }
                            }
                        }

                        // Add processed row to array
                        jsonArray.put(jsonObj);
                    } else {
                        LogUtil.warn(getClassName(), "Unexpected row format: " + row);
                    }
                }
            }

            LogUtil.info("All List: ",jsonArray.toString());


            return new ApiResponse(200, jsonArray);
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
            return new ApiResponse(405,e.getMessage());
        }
    }


}

