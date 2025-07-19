package org.joget.ListApi;


import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.dao.DatalistDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.DatalistDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.service.DataListService;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

public class ListApi extends ApiPluginAbstract {
    @Override
    public String getIcon() {
        return "<i class=\"fas fa-file-alt\"></i>";
    }

    @Override
    public String getTag() {
        return "list/{listDefId}";
    }

    @Override
    public String getName() {
        return "CustomListApi";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Custom List Api to get active payment urls ";
    }

    @Override
    public String getLabel() {
        return "Custom list api (GET)";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        String path=AppUtil.readPluginResource(getClass().getName(), "/properties/api/" + getName() + ".json", null, true, getResourceBundlePath());
        LogUtil.info(getClassName(),path);
        return path;
    }

    @Operation(path = "/", type = Operation.MethodType.GET, summary = "Get Active Payments URLS", description = "Get Active Payments URLS \nCustom response by modify json and filtred list to get active payment urls")
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
                        if (jsonObj.has("paymentgateways")) {
                            Object paymentGateways = jsonObj.get("paymentgateways");

                            if (paymentGateways instanceof String) {
                                try {
                                    JSONArray parsedPaymentGateways = new JSONArray((String) paymentGateways);
                                    jsonObj.put("paymentgateways", parsedPaymentGateways);
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
            JSONArray filteredArray = new JSONArray();


            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                JSONArray gateways = obj.getJSONArray("paymentgateways");
                JSONArray validGateways = new JSONArray();

                for (int j = 0; j < gateways.length(); j++) {
                    JSONObject gateway = gateways.getJSONObject(j);
                    JSONArray methods = gateway.getJSONArray("paymentMethods");
                    JSONArray validMethods = new JSONArray();

                    for (int k = 0; k < methods.length(); k++) {
                        JSONObject method = methods.getJSONObject(k);
                        Object url = method.get("url");

                        if ("ACTIVE".equals(method.getString("status")) && url instanceof String && !((String) url).isEmpty()) {
                            validMethods.put(method);
                        }
                    }

                    if (validMethods.length() > 0) {
                        JSONObject validGateway = new JSONObject(gateway.toString());
                        validGateway.put("paymentMethods", validMethods);
                        validGateways.put(validGateway);
                    }
                }

                if (validGateways.length() > 0) {
                    JSONObject filteredObj = new JSONObject(obj.toString());
                    filteredObj.put("paymentgateways", validGateways);
                    filteredArray.put(filteredObj);
                }
            }
            return new ApiResponse(200, filteredArray);
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
            return new ApiResponse(405,e.getMessage());
        }
    }

}
