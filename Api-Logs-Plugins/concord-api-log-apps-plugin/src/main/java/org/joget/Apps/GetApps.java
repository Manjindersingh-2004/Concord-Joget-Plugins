package org.joget.Apps;

import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.*;

public class GetApps extends ApiPluginAbstract {
    public String getIcon() {
        return "<i class=\"fas fa-file-alt\"></i>";
    }

    @Override
    public String getTag() {
        return "Apps";
    }

    @Override
    public String getName() {
        return "Get Apps Name";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Custom api endpoints to get apps names of concord";
    }

    @Override
    public String getLabel() {
        return "Get Apps Name";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/api/CustomAppFormAPI.json", null, true, getResourceBundlePath());
    }



    @Operation(path = "/", type = Operation.MethodType.GET, summary = "Get Apps Name", description = "Custom Api to get apps name")
    @Responses({
            @Response(responseCode = 200, description = "Success"),
            @Response(responseCode = 400, description = "Failed"),
            @Response(responseCode = 405, description = "Internal Error")})
    public ApiResponse getApps(HttpServletRequest request, HttpServletResponse response) {
        String csgUrl = getPropertyString("csg_url");
        String aggUrl = getPropertyString("agg_url");
        String csgApiKey = getPropertyString("csg_api_key");
        String csgApiId = getPropertyString("csg_api_id");
        String aggApiKey = getPropertyString("agg_api_key");
        String aggApiId = getPropertyString("agg_api_id");

        try {
            Set<String> appNames = new HashSet<String>();

            LogUtil.info("AppFetcher", "Starting app fetch from both environments...");

            appNames.addAll(fetchAppNames(csgUrl, csgApiKey, csgApiId));
            appNames.addAll(fetchAppNames(aggUrl, aggApiKey, aggApiId));

            List<String> sortedApps = new ArrayList<String>(appNames);
            Collections.sort(sortedApps);

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("total", sortedApps.size());
            result.put("apps", new JSONArray(sortedApps));

            LogUtil.info("AppFetcher", "Fetched total " + sortedApps.size() + " unique apps");

            return new ApiResponse(200, result);

        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", "Failed to fetch apps");
            error.put("error", e.getMessage());

            LogUtil.info("AppFetcher", "Error during fetch: " + e.getMessage());

            response.setStatus(400);
            response.setContentType("application/json");

            try {
                response.getWriter().write(error.toString());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            ApiResponse res = new ApiResponse(400, true);
            res.write(response);
            return res;
        }
    }


    public static List<String> fetchAppNames(String urlString, String apiKey, String apiId) throws Exception {
        List<String> appNames = new ArrayList<String>();
        HttpURLConnection conn = null;
        BufferedReader reader = null;

        try {
            LogUtil.info("AppFetcher", "Fetching apps from: " + urlString);
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            LogUtil.info("AppFetcher", "connection opened");
            conn.setRequestMethod("GET");
            conn.setRequestProperty("api_key", apiKey);
            conn.setRequestProperty("api_id", apiId);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            LogUtil.info("AppFetcher", "Response Code from " + urlString + ": " + responseCode);

            if (responseCode == 200) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder responseBuilder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }

                JSONObject json = new JSONObject(responseBuilder.toString());
                JSONArray data = json.optJSONArray("data");

                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject obj = data.getJSONObject(i);
                        String appName = obj.optString("app_name");
                        if (appName != null && !appName.trim().isEmpty()) {
                            appNames.add(appName.trim());
                        }
                    }
                }

                LogUtil.info("AppFetcher", "Fetched " + appNames.size() + " app(s) from " + urlString);
            } else {
                LogUtil.info("AppFetcher", "Failed to fetch from " + urlString + ": HTTP " + responseCode);
                throw new Exception("Failed to fetch from " + urlString + ": HTTP " + responseCode);
            }
        } catch (Exception e) {
            LogUtil.info("AppFetcher", "Error fetching from " + urlString + ": " + e);
            throw new Exception("Error fetching from " + urlString + ": " + e);
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }

        return appNames;
    }


}
