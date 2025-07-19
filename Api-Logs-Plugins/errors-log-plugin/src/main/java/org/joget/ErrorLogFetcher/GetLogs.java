package org.joget.ErrorLogFetcher;

import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;


public class GetLogs extends ApiPluginAbstract{
    @Override
    public String getIcon() {
        return "<i class=\"fas fa-bug\"></i>";
    }

    @Override
    public String getTag() {
        return "Logs";
    }

    @Override
    public String getName() {
        return "ErrorLogFetcher";
    }

    @Override
    public String getVersion() {
        return "8.2-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "API endpoint for  server to fetch 20 error logs per page, filtered by app name and date.";
    }

    @Override
    public String getLabel() {
        return "Error Log API (20 per page)";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return null;
    }



    @Operation(path = "/", type = Operation.MethodType.GET, summary = "Get Error Logs", description = "API endpoint for  server to fetch 20 error logs per page, filtered by app name and date.")
    @Responses({
            @Response(responseCode = 200, description = "Success"),
            @Response(responseCode = 400, description = "Failed"),
            @Response(responseCode = 405, description = "Internal Error")})
    public ApiResponse getLogs(@Param(value = "app", required = true, description = "App name") String app,@Param(value = "date",required = false, description = "Date") String date,@Param(value = "page",required = false,description = "page") Integer page, HttpServletRequest request, HttpServletResponse response){
        try {
            if (date == null || date.trim().isEmpty()) {
                date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            }

            if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return new ApiResponse(400, "Invalid date format");
            }

            if (page==null || page <= 0) page = 1;

            List<Map<String, Object>> logs = fetchErrorLogs(app, date, page,request);
            if (logs.isEmpty()) {
                return new ApiResponse(404, "Data not found!");
            } else {
                JSONObject result = new JSONObject();
                result.put("logs", logs);
                return new ApiResponse(200,result);
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "Exception in getLogs");
            return new ApiResponse(400, e.getMessage());
        }

    }


    public  Map<String, Object> getFormattedApiIds(String appName) throws Exception {
        Map<String, Object> result = new HashMap<>();
        List<String> apiIds = new ArrayList<>();

        try (Connection con = ((DataSource) AppUtil.getApplicationContext().getBean("setupDataSource")).getConnection();
             PreparedStatement stmt = con.prepareStatement("SELECT c_api_id FROM app_fd_app_name_master WHERE c_app_name = ?")) {

            stmt.setString(1, appName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String apiId = rs.getString("c_api_id");
                    if (apiId != null && !apiId.trim().isEmpty()) {
                        apiIds.add(apiId.trim());
                    }
                }
            }

            if (apiIds.isEmpty()) {
                throw new Exception("No API IDs found for app: " + appName);
            }

            StringBuilder clause = new StringBuilder("apiid IN (");
            clause.append("?, ".repeat(apiIds.size()));
            clause.setLength(clause.length() - 2);
            clause.append(")");

            result.put("clause", clause.toString());
            result.put("params", apiIds);
            return result;
        }
    }
    public  List<Map<String, Object>> fetchErrorLogs(String app, String date, int page,HttpServletRequest request) throws Exception {
        List<Map<String, Object>> logs = new ArrayList<>();
        Map<String, Object> apiIdMap = getFormattedApiIds(app);

        int requiredErrors = 20;
        int batchLimit = 100;
        int collected = 0;
        int scanOffset = 0;
        int resultOffset = (page - 1) * requiredErrors;
        int count=0;

        String sessionKey = "offset_" + app + "_" + date;
        HttpSession session = request.getSession();
        if (page == 1) {
            session.setAttribute(sessionKey, 0);
            LogUtil.info(getClassName(),"Set session: "+sessionKey+" =0");
        } else {
            Object savedOffset = session.getAttribute(sessionKey);
            scanOffset = (savedOffset instanceof Integer) ? (Integer) savedOffset : 0;
            LogUtil.info(getClassName(),"get session: "+sessionKey+" =" +scanOffset);

        }

        try (Connection con = ((DataSource) AppUtil.getApplicationContext().getBean("setupDataSource")).getConnection()) {
            while (collected < (resultOffset + requiredErrors)) {
                String inClause = (String) apiIdMap.get("clause");
                List<String> apiIds = (List<String>) apiIdMap.get("params");
                String sql = "SELECT * FROM api_log WHERE timestamp >= ?::timestamp AND timestamp <= ?::timestamp";
                if (!inClause.isEmpty()) {
                    sql += " AND " + inClause;
                }
                sql += " ORDER BY timestamp DESC LIMIT ? OFFSET ?";

                try (PreparedStatement stmt = con.prepareStatement(sql)) {
                    int i = 1;
                    stmt.setString(i++, date + " 00:00:00");
                    stmt.setString(i++, date + " 23:59:59");
                    for (String id : apiIds) {
                        stmt.setString(i++, id);
                    }
                    stmt.setInt(i++, batchLimit);
                    stmt.setInt(i++, scanOffset);

                    try (ResultSet rs = stmt.executeQuery()) {
                        boolean hasMore = false;
                        while (rs.next()) {
                            count++;
                            hasMore = true;
                            boolean isError = false;
                            String responseStatus = rs.getString("response_status");
                            String logDetails = rs.getString("logdetails") != null ? rs.getString("logdetails") : "{}";
                            JSONObject logDetailsObj = new JSONObject(logDetails);
                            if ("200".equals(responseStatus)) {
                                try {
                                    JSONObject obj = new JSONObject(logDetails);
                                    if (obj.has("content") && obj.get("content") instanceof JSONObject &&
                                            ((JSONObject) obj.get("content")).has("errors") && ((JSONObject) obj.get("content")).get("errors") instanceof JSONObject) {
                                        isError = !((JSONObject) ((JSONObject) obj.get("content")).get("errors")).isEmpty();
                                    }
                                } catch (Exception ignored) {}
                            } else {
                                isError = true;
                            }

                            if (isError) {
                                if (logs.size() < requiredErrors) {
                                    collected++;
                                    Map<String, Object> row = new HashMap<>();
                                    row.put("id", Optional.ofNullable(rs.getString("id")).orElse("N/A"));
                                    row.put("method", Optional.ofNullable(rs.getString("method")).orElse("N/A"));
                                    row.put("response_status", Optional.ofNullable(responseStatus).orElse("N/A"));
                                    row.put("timestamp", Optional.ofNullable(rs.getString("timestamp")).orElse("N/A"));
                                    row.put("exectimems", Optional.ofNullable(rs.getString("exectimems")).orElse("N/A"));
                                    row.put("sourceip", Optional.ofNullable(rs.getString("sourceip")).orElse("N/A"));
                                    row.put("logdetails",logDetailsObj);
                                    row.put("message", Optional.ofNullable(rs.getString("message")).orElse("N/A"));
                                    row.put("useragent", Optional.ofNullable(rs.getString("useragent")).orElse("N/A"));
                                    row.put("apikey", Optional.ofNullable(rs.getString("apikey")).orElse("N/A"));
                                    row.put("apiid", Optional.ofNullable(rs.getString("apiid")).orElse("N/A"));
                                    logs.add(row);
                                }
                                if (logs.size() >= requiredErrors) break;
                            }
                        }
                        if (!hasMore) break;
                    }
                }
                scanOffset += batchLimit;
            }
        }
        session.setAttribute(sessionKey,count);
        LogUtil.info(getClassName(),"Set session: "+sessionKey+" = "+count);
        return logs;
    }

}

