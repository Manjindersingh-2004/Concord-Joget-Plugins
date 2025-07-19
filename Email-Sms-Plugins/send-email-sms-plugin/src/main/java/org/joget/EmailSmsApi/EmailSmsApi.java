package org.joget.EmailSmsApi;

import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FileUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SetupManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joget.apps.app.lib.EmailTool;
import org.joget.commons.util.UuidGenerator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

public class EmailSmsApi extends ApiPluginAbstract{
    @Override
    public String getIcon() {
        return "<i class=\"fas fa-file-alt\"></i>";
    }

    @Override
    public String getTag() {
        return "API";
    }

    @Override
    public String getName() {
        return "Send SMS/ Email Api";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Custom api endpoints are made to send email and sms and save logs to the table";
    }

    @Override
    public String getLabel() {
        return "Send SMS/ Email Api";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return null;
    }





    @Operation(path = "/sendEmail", type = Operation.MethodType.POST, summary = "Send Email", description = "Custom Api to send Emails")
    @Responses({
            @Response(responseCode = 200, description = "Success"),
            @Response(responseCode = 400, description = "Failed"),
            @Response(responseCode = 405, description = "Internal Error")})
    public ApiResponse sendEmail(  @Param(value = "body", description = "Complete JSON Data", definition ="email_json_payload-FormData") JSONObject body){
        String content = null,templateId,referenceId,source,id,subject=null,atachments=null,filesId=null,smtp_id=null;

        try {
            // Log incoming request


            LogUtil.info(getClass().getName(), "Received request body: " + body.toString());

            // Collecting validation errors
            List<String> errors = new ArrayList<>();


            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            String formId = "email_template";
            String tableName = "email_template";
            FormRow row = null;

            // Required field validations
            if (!body.has("recipientEmail") || body.optString("recipientEmail").isEmpty()) {
                errors.add("recipientEmail is required.");
                LogUtil.info(getClass().getName(), "Validation failed: recipientEmail is missing or empty.");
            }
            if (!body.has("templateId") || body.optString("templateId").isEmpty()) {
                errors.add("templateId is required.");
                LogUtil.info(getClass().getName(), "Validation failed: templateId is missing or empty.");
            }else{
                row=formDataDao.load(formId, tableName, body.optString("templateId"));
                if(row==null || row.isEmpty()){
                    errors.add("Invalid Template ID.");
                    LogUtil.info(getClass().getName(), "Validation failed: Invalid Template Id.");
                }else{
                    String language;
                    if (body.has("language") || !body.optString("language").isEmpty()) {
                        language=body.optString("language");
                        if(!language.equalsIgnoreCase("english") && !language.equalsIgnoreCase("arabic")){
                            language="english";
                        }
                        LogUtil.info(getClass().getName(), "Validation failed: source is missing or empty.");
                    }else{
                        language="english";
                    }

                    if(language.equalsIgnoreCase("english")){
                        content = row.getProperty("email_content");
                    }else{
                        content = row.getProperty("email_content_arabic");
                    }

                    subject=row.getProperty("email_subject");

//                    fetch the atachments
                    atachments= row.getProperty("attachments");
                    filesId=row.getProperty("id");

                    smtp_id= row.getProperty("select_smtp");
                    String variables= row.getProperty("dynamic_variables");
                    int numbersOfVariables=Integer.parseInt(row.getProperty("no_of_variables"));
                    LogUtil.info("Send Email: ","Dynamic Variables: "+variables);
                    LogUtil.info("Send Email: ","NoOfVariables: "+numbersOfVariables);
                    LogUtil.info("Send Email: ","Content: "+content);

                    if(numbersOfVariables>0){
                        if(!body.has("dynamicVariables") || body.optJSONObject("dynamicVariables").isEmpty()){
                            errors.add("dynamicVariables are required.");
                            LogUtil.info(getClass().getName(), "Validation failed: Dynamic Variables are missing or empty.");
                        }else{
                            JSONObject dynamicVariables=body.optJSONObject("dynamicVariables");
                            int dynamicVariablesCount = body.optJSONObject("dynamicVariables").keySet().size();
                            if (dynamicVariablesCount != numbersOfVariables) {
                                LogUtil.info("Send Email: ", "Number of dynamic variables does not match the expected count from the database.");
                                errors.add("Exact "+numbersOfVariables+" DynamicVariables are required.");
                            }
                            //List expectedVariables = Arrays.asList(variables.split(";"));
                            List<String> expectedVariables = Arrays.asList(variables.substring(1, variables.length() - 1).split(","));



                            // Validate if all keys in dynamicVariables match the expected variables from the DB
                            boolean allKeysMatch = true;
                            for (String key : body.optJSONObject("dynamicVariables").keySet()) {
                                if (!expectedVariables.contains(key)) {
                                    allKeysMatch = false;
                                    LogUtil.error("Send Email: ", null, "Mismatch in variable: " + key);
                                    break;
                                }
                            }
                            if (allKeysMatch) {
                                LogUtil.info("Send Email: ", "All dynamic variable keys match the expected values.");
                                // Replace placeholders in the content with corresponding values from JSON
                                for (String key : body.optJSONObject("dynamicVariables").keySet()) {
                                    String placeholder = "{{" + key + "}}";
                                    content = content.replace(placeholder, body.optJSONObject("dynamicVariables").getString(key));
                                }

                                // Log the updated content and set it in workflow variables
                                LogUtil.info("Send Email: ", "Updated Content: " + content);
                            } else {
                                errors.add("Invalid keys in Dynamic variables");
                                LogUtil.info("Send Email: ", "One or more dynamic variable keys do not match the expected variables.");
                            }
                        }
                    }



                }
            }

            if (!body.has("referenceNumber") || body.optString("referenceNumber").isEmpty()) {
                errors.add("referenceNumber is required.");
                LogUtil.info(getClass().getName(), "Validation failed: referenceNumber is missing or empty.");
            }
            if (!body.has("source") || body.optString("source").isEmpty()) {
                errors.add("source is required.");
                LogUtil.info(getClass().getName(), "Validation failed: source is missing or empty.");
            }


            // Validate email format
            String recipientEmail = body.optString("recipientEmail", "");
            if (!recipientEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                errors.add("Invalid email format for recipientEmail.");
                LogUtil.info(getClass().getName(), "Validation failed: Invalid recipientEmail format: " + recipientEmail);
            }

            // Validate reference number format (example: NY2025123456)
            String referenceNumber = body.optString("referenceNumber", "");


            if (body.has("subject") || !body.optString("subject").isEmpty()) {
                LogUtil.info("Subject",subject);
                subject=body.optString("subject");
            }


            SetupManager setupManager = (SetupManager) AppUtil.getApplicationContext().getBean("setupManager");

            // Fetch SMTP settings
//            String smtpHost = setupManager.getSettingValue("smtpHost");
//            String smtpPort = setupManager.getSettingValue("smtpPort");
//            String smtpEmail = setupManager.getSettingValue("smtpEmail");

            //------------------new code version2--------------------------------------------------------------------------
            String smtpHost=null;
            String smtpPort=null;
//            String smtpEmail=null;
            String username=null;
            String password=null;
            String security=null;

            ResultSet rs_ = null;
            PreparedStatement stmt_ = null;
            Connection con_ = null;
            DataSource ds_ = null;
            String cc=null;
            String bcc=null;


            try {
                ds_ = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
                con_ = ds_.getConnection();

                String querySql_ = "SELECT * FROM app_fd_smtp_master WHERE c_status = 'ACTIVE'";

                if(smtp_id!=null && !smtp_id.isEmpty()){
                    LogUtil.info("smtp_id","geting from template");
                    querySql_ = "SELECT * FROM app_fd_smtp_master WHERE c_status = 'ACTIVE' and id = '"+smtp_id+"'";
                }

                stmt_ = con_.prepareStatement(querySql_);
                rs_ = stmt_.executeQuery();



                if(rs_.next()) {
                    smtpHost=rs_.getString("c_host");
                    smtpPort=rs_.getString("c_port");
                    security=rs_.getString("c_security");
                    username=rs_.getString("c_username");
                    password= rs_.getString("c_password");
                    cc=rs_.getString("c_cc");
                    bcc=rs_.getString("c_bcc");
                    LogUtil.info("Data: ",smtpHost+":"+smtpPort+":"+security+":"+username+":"+password);

                }

            } catch (Exception e) {
                LogUtil.info("bulk-------", "Error while fetching ACTIVE records: " + e);
            } finally {
                try {
                    if (rs_ != null) rs_.close();
                    if (stmt_ != null) stmt_.close();
                    if (con_ != null) con_.close();
                } catch (Exception ex) {
                    LogUtil.info("bulk-------", "Error closing database resources: " + ex);
                }
            }


            if (smtpHost == null || smtpHost.isEmpty() || smtpPort == null || smtpPort.isEmpty()) {
                errors.add("No Active SMTP Configuration found. Please setup through SMTP Master in Joget App");
                LogUtil.info(getClass().getName(), "No Active SMTP Configuration found. Please setup through SMTP Master in Joget App");
            }



            //------------------new code version2--------------------------------------------------------------------------

//            if (smtpHost == null || smtpHost.isEmpty() || smtpPort == null || smtpPort.isEmpty() || smtpEmail == null || smtpEmail.isEmpty()) {
//                errors.add("SMTP settings are not configured correctly");
//                LogUtil.info(getClass().getName(), "SMTP settings are not configured correctly");
//            }


            // If any validation errors exist, return them
            if (!errors.isEmpty()) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("message", "Validation Failed");
                errorResponse.put("errors", errors);
                LogUtil.info(getClass().getName(), "Returning validation errors: " + errorResponse.toString());
                return new ApiResponse(200, errorResponse);
            }



            //change images urls in content dynamically

            String scheme="#request.scheme#";
            String serverName="#request.serverName#";
            String port="#request.serverPort#";
            String domain = scheme + "://" + serverName + ":" + port ;
            LogUtil.info("domain",domain);

            content = content.replaceAll(
                    "src=[\"'](/jw/web/client/.*?)['\"]",
                    "src=\"" + domain + "$1\""
            );

            Pattern pattern2 = Pattern.compile("src=\"(.*?)\"");
            Matcher matcher = pattern2.matcher(content);
            StringBuffer updatedContent = new StringBuffer();

            while (matcher.find()) {
                String originalUrl = matcher.group(1);
                String encodedUrl = originalUrl.replace(" ", "%20");
                matcher.appendReplacement(updatedContent, "src=\"" + Matcher.quoteReplacement(encodedUrl) + "\"");
            }
            matcher.appendTail(updatedContent);

            content = updatedContent.toString();


            LogUtil.info("Content After updation:",content);


            templateId= body.optString("templateId");
            String uuid = UuidGenerator.getInstance().getUuid();



            try {

                Map<String,Object> propertiesMap=new HashMap<>();
                propertiesMap.put("toSpecific", recipientEmail);
                propertiesMap.put("subject", subject);
                propertiesMap.put("message", content);
                propertiesMap.put("emailFormat", "html");

                if(cc!=null){

                    propertiesMap.put("cc",cc);
                }
                if(bcc!=null){
                    propertiesMap.put("bcc", bcc);
                }

//                new version 2
                propertiesMap.put("host", smtpHost);
                propertiesMap.put("port", smtpPort);
                propertiesMap.put("username", username);
                propertiesMap.put("password", password);
                propertiesMap.put("security", security);
                propertiesMap.put("from", username);
//-------------------------------update 3.1 -------------------------------
                List filesList = new ArrayList();
                if(atachments!=null && !atachments.isEmpty()){
//                    SetupManager setupManager2 = (SetupManager) AppUtil.getApplicationContext().getBean("setupManager");
//                    String baseDir = setupManager2.getBaseDirectory();
//                    String docxStorePath = baseDir + "/app_formuploads/email_template/"+filesId+"/";
//                    LogUtil.info("Path: ",baseDir);
//                add the attachments
                    String[] files = atachments.split(";");
                    StringBuilder filePathsBuilder = new StringBuilder(); // for storing file paths separated by ;

                    for (String filename : files) {
//                        filename = filename.trim(); // remove spaces if any
//                        String filePath = docxStorePath + filename;
//                        LogUtil.info("path: ", filePath);

//                        File file = new File(filePath);
                        File file= FileUtil.getFile(filename,"email_template", templateId);
                        if (file.exists()) {
                            LogUtil.info("file", filename + " exists");
                            Map fileMap = new HashMap();
                            fileMap.put("path", file.getAbsolutePath());
                            fileMap.put("fileName", file.getName());
                            fileMap.put("type", "system"); 							//"system" for loading from the local directory
                            fileMap.put("embed", "false"); 							//"true" or "false"
                            filesList.add(fileMap);
                        } else {
                            LogUtil.info("file", filename + " does not exist");
                        }
                    }
                    Object[] filesArray = filesList.toArray();
                    propertiesMap.put("files", filesArray);
                }else{
                    LogUtil.info("not found files","not found files");
                }








//----------------------------update 3.1------------------------------------

//                new version 2
                new EmailTool().execute(propertiesMap);
                JSONObject successResponse = new JSONObject();
                successResponse.put("message", "Email sent successfully to "+recipientEmail);
                successResponse.put("referenceNumber", referenceNumber);
                successResponse.put("jogetID", uuid);

                LogUtil.info("EmailSender", "Email sent successfully to "+recipientEmail);
                saveEmailLog(referenceNumber,templateId,uuid,subject,recipientEmail,"Success","Email sent successfully to "+recipientEmail);
                return new ApiResponse(200, successResponse);

            } catch (Exception e) {
                LogUtil.info("SingleEmailSender", "Error sending email. "+e);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("message", "Failed");
                errorResponse.put("referenceNumber", referenceNumber);
                errorResponse.put("jogetID", uuid);
                errorResponse.put("error", e+"");
                saveEmailLog(referenceNumber,templateId,uuid,subject,recipientEmail,"Failed",e+"");
                return new ApiResponse(200, errorResponse);
            }


        } catch (Exception e) {
            LogUtil.info(getClass().getName(),"Exception encountered during API processing. "+e);
            return new ApiResponse(405, e.getMessage());
        }
    }

    @Operation(path = "/sendSMS", type = Operation.MethodType.POST, summary = "Send SMS", description = "Custom Api to send SMS")
    @Responses({
            @Response(responseCode = 200, description = "Success"),
            @Response(responseCode = 400, description = "Failed"),
            @Response(responseCode = 405, description = "Internal Error")})
    public ApiResponse sendSMS(@Param(value = "body", description = "Complete JSON Data",definition="sms_json_payload--FormData") JSONObject body, HttpServletRequest request, HttpServletResponse httpServletResponse){
        String content = null,templateId,referenceId,source,id,senderIdDynamic=null;
        try {
            // Log incoming request
            LogUtil.info(getClass().getName(), "Received request body: " + body.toString());

            // Collecting validation errors
            List<String> errors = new ArrayList<>();


            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
            String formId = "sms_template";
            String tableName = "sms_template";
            FormRow row = null;

            // Required field validations
            if (!body.has("recipientPhone") || body.optString("recipientPhone").isEmpty()) {
                errors.add("recipientPhone is required.");
                LogUtil.info(getClass().getName(), "Validation failed: recipientPhone is missing or empty.");
            }
            if (!body.has("templateId") || body.optString("templateId").isEmpty()) {
                errors.add("templateId is required.");
                LogUtil.info(getClass().getName(), "Validation failed: templateId is missing or empty.");
            }else{
                row=formDataDao.load(formId, tableName, body.optString("templateId"));
                if(row==null || row.isEmpty()){
                    errors.add("Invalid Template ID.");
                    LogUtil.info(getClass().getName(), "Validation failed: Invalid Template Id.");
                }else{

                    String language;
                    if (body.has("language") || !body.optString("language").isEmpty()) {
                        language=body.optString("language");
                        if(!language.equalsIgnoreCase("english") && !language.equalsIgnoreCase("arabic")){
                            language="english";
                        }
                        LogUtil.info(getClass().getName(), "Validation failed: source is missing or empty.");
                    }else{
                        language="english";
                    }

                    if(language.equalsIgnoreCase("english")){
                        content = row.getProperty("sms_content");
                    }else{
                        content = row.getProperty("sms_content_arabic");
                    }
                    String variables= row.getProperty("dynamic_variables");
                    int numbersOfVariables=Integer.valueOf(row.getProperty("no_of_variables"));
                    LogUtil.info(getClass().getName(),"Dynamic Variables: "+variables);
                    LogUtil.info(getClass().getName(),"NoOfVariables: "+numbersOfVariables);
                    LogUtil.info(getClass().getName(),"Content: "+content);

                    senderIdDynamic= row.getProperty("sender_id");

                    if(numbersOfVariables>0){
                        if(!body.has("dynamicVariables") || body.optJSONObject("dynamicVariables").isEmpty()){
                            errors.add("DynamicVariables are required.");
                            LogUtil.info(getClass().getName(), "Validation failed: Dynamic Variables are missing or empty.");
                        }else{
                            JSONObject dynamicVariables=body.optJSONObject("dynamicVariables");
                            int dynamicVariablesCount = body.optJSONObject("dynamicVariables").keySet().size();
                            if (dynamicVariablesCount != numbersOfVariables) {
                                LogUtil.info(getClass().getName(), "Number of dynamic variables does not match the expected count from the database.");
                                errors.add("Exact "+numbersOfVariables+" DynamicVariables are required.");
                            }
//                            List expectedVariables = Arrays.asList(variables.split(";"));
                            List<String> expectedVariables = Arrays.asList(variables.substring(1, variables.length() - 1).split(","));


                            // Validate if all keys in dynamicVariables match the expected variables from the DB
                            boolean allKeysMatch = true;
                            for (String key : body.optJSONObject("dynamicVariables").keySet()) {
                                if (!expectedVariables.contains(key)) {
                                    allKeysMatch = false;
                                    LogUtil.error(getClass().getName(), null, "Mismatch in variable: " + key);
                                    break;
                                }
                            }
                            if (allKeysMatch) {
                                LogUtil.info(getClass().getName(), "All dynamic variable keys match the expected values.");
                                // Replace placeholders in the content with corresponding values from JSON
                                for (String key : body.optJSONObject("dynamicVariables").keySet()) {
                                    String placeholder = "{{" + key + "}}";
                                    content = content.replace(placeholder, body.optJSONObject("dynamicVariables").getString(key));
                                }

                                // Log the updated content and set it in workflow variables
                                LogUtil.info(getClass().getName(), "Updated Content: " + content);
                            } else {
                                errors.add("Invalid keys in Dynamic variables");
                                LogUtil.info(getClass().getName(), "One or more dynamic variable keys do not match the expected variables.");
                            }
                        }
                    }



                }
            }

            if (!body.has("referenceNumber") || body.optString("referenceNumber").isEmpty()) {
                errors.add("referenceNumber is required.");
                LogUtil.info(getClass().getName(), "Validation failed: referenceNumber is missing or empty.");
            }
            if (!body.has("source") || body.optString("source").isEmpty()) {
                errors.add("source is required.");
                LogUtil.info(getClass().getName(), "Validation failed: source is missing or empty.");
            }







            String referenceNumber = body.optString("referenceNumber", "");



            // If any validation errors exist, return them
            if (!errors.isEmpty()) {
//                JSONObject errorResponse = new JSONObject();
//                errorResponse.put("message", "Validation Failed");
//                errorResponse.put("errors", errors);
//                LogUtil.info(getClass().getName(), "Returning validation errors: " + errorResponse.toString());
                ApiResponse errorResponse = buildErrorResponseJson(errors,referenceNumber,400,httpServletResponse,"Validation Error",null);
                return errorResponse;
            }


            templateId= body.optString("templateId");
            String uuid = UuidGenerator.getInstance().getUuid();
            String recipientPhone=body.optString("recipientPhone");


//            ------------------------version 3---------------------------------------------------------------------




            Connection con=null;
            PreparedStatement stmt=null;


            String apiUrl=null;
            String method=null;
            String authType=null;
            String username=null;
            String password=null;
            String apiKey=null;
            String oAuthToken=null;
            String senderId=null;
            String payload=null;
            String usernameKey=null;
            String passwordKey=null;
            String recipentNumberKey=null;
            String senderIdKey=null;
            String MessageKey=null;



            String status=null;
            String message=null;
            String messageId="null";

            try {
                DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
                con = ds.getConnection();


                String Sql = "select * from app_fd_sms_gateway_master  WHERE c_status = 'ACTIVE'";
                stmt = con.prepareStatement(Sql);
                ResultSet rs = stmt.executeQuery();

                if(rs.next()){
                    LogUtil.info("Data fetch"," found");

                    apiUrl=rs.getString("c_api_url");
                    method=rs.getString("c_method");
                    authType=rs.getString("c_auth_type");
                    username=rs.getString("c_username");
                    password=rs.getString("c_password");
                    apiKey=rs.getString("c_api_key");
                    oAuthToken=rs.getString("c_oauth_token");
                    senderId=rs.getString("c_sender_id");
                    payload=rs.getString("c_payload");
                    usernameKey=rs.getString("c_username_key");
                    passwordKey=rs.getString("c_password_key");
                    recipentNumberKey=rs.getString("c_recipent_number_key");
                    senderIdKey=rs.getString("c_sender_id_key");
                    MessageKey=rs.getString("c_message_key");


                    //use dynamic sender id
                    if(senderIdDynamic!=null && !senderIdDynamic.isEmpty()){
                        LogUtil.info("sender id","fetch dynamically");
                        senderId=senderIdDynamic;
                    }

                    if(authType.equals("payload") && method.equals("POST")){
                        JSONArray arr=null;
                        JSONObject data = null;
                        try {
                            if (payload.trim().startsWith("[")) {
                                arr = new JSONArray(payload);
                                if (arr.length() > 0 && arr.get(0) instanceof JSONObject) {
                                    data = arr.getJSONObject(0);
                                }
                            } else {
                                data = new JSONObject(payload);
                            }
                        } catch (Exception e) {
                            LogUtil.info("Invalid JSON Payload: ", e.getMessage());
                        }


                        // If parsed successfully, now populate keys dynamically
                        if (data != null) {
                            // Safely add non-empty key-value pairs
                            if (usernameKey != null && !usernameKey.trim().isEmpty() &&
                                    username != null && !username.trim().isEmpty()) {
                                data.put(usernameKey, username);
                            }

                            if (passwordKey != null && !passwordKey.trim().isEmpty() &&
                                    password != null && !password.trim().isEmpty()) {
                                data.put(passwordKey, password);
                            }

                            if (recipentNumberKey != null && !recipentNumberKey.trim().isEmpty() &&
                                    recipientPhone != null && !recipientPhone.trim().isEmpty()) {
                                data.put(recipentNumberKey, recipientPhone);
                            }

                            if (senderIdKey != null && !senderIdKey.trim().isEmpty() &&
                                    senderId != null && !senderId.trim().isEmpty()) {
                                data.put(senderIdKey, senderId);
                            }

                            if (MessageKey != null && !MessageKey.trim().isEmpty() &&
                                    content != null && !content.trim().isEmpty()) {
                                data.put(MessageKey, content);
                            }

                            // If original was array, put it back in
                            if (arr != null) {
                                arr.put(0, data);
                                payload = arr.toString();
                            } else {
                                payload = data.toString();
                            }
                            LogUtil.info("payload: ",payload);
                            LogUtil.info("Api url: ",apiUrl);




                            // STEP 3: Send POST request
                            String responseString = "";
                            int responseCode = 0;

                            try {
                                URL url = new URL(apiUrl);
                                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                                conn.setRequestMethod("POST");
                                conn.setRequestProperty("Content-Type", "application/json");
                                conn.setDoOutput(true);

                                OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
                                writer.write(payload);
                                writer.flush();
                                writer.close();

                                responseCode = conn.getResponseCode();

                                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                String inputLine;
                                StringBuffer response = new StringBuffer();

                                while ((inputLine = in.readLine()) != null) {
                                    response.append(inputLine);
                                }
                                in.close();

                                responseString = response.toString();

                                LogUtil.info("Payload Sent: " , payload+"");
                                LogUtil.info("Response Code: " ,responseCode+"");
                                LogUtil.info("Raw Response: " , responseString+"");
                                String strResponse="";
                                JSONObject apiResponse = new JSONObject();
                                boolean success = false;
                                if(responseString.startsWith("[")){
                                    apiResponse.put("message", new JSONArray(responseString));
                                    JSONArray responseArray = new JSONArray(responseString);
                                    JSONObject firstItem = responseArray.getJSONObject(0);
                                    strResponse = firstItem.getString("str_response").trim();

                                    if (strResponse.contains("Send Successful")) {
                                        success=true;
                                    } else {
                                        success=false;
                                    }

                                }else{
                                    apiResponse.put("message", new JSONObject(responseString));
                                }

                                apiResponse.put("referenceNumber", referenceNumber);
                                apiResponse.put("jogetID", uuid);
                                saveSMSLog(referenceNumber,templateId,uuid,recipientPhone,responseCode+"",responseString,"null");
                                if(success){
                                    return new ApiResponse(200, apiResponse);
                                }else{
//                                    httpServletResponse.setStatus(400);
                                    LogUtil.info("Sending Custom Response----> " , payload+"");
//                                    httpServletResponse.setContentType("application/json");
                                    LogUtil.info("Sending Custom Response----> 111" , payload+"");

//                                    httpServletResponse.getWriter().write(apiResponse.toString());
//                                    ApiResponse apiRes = new ApiResponse(400, true);
//                                    apiRes.write(httpServletResponse);
                                    ApiResponse errorResponse = buildErrorResponseJson(Arrays.asList(strResponse),referenceNumber,400,httpServletResponse,"Message Not Sent",uuid);
                                    return errorResponse;
                                }

                            } catch (Exception e) {
                                LogUtil.info("Exception occurred while sending request: ", e+"");
//                                JSONObject apiResponse = new JSONObject();
//                                apiResponse.put("message", e+"");
//                                apiResponse.put("referenceNumber", referenceNumber);
//                                apiResponse.put("jogetID", uuid);
                                saveSMSLog(referenceNumber,templateId,uuid,recipientPhone,responseCode+"",e+"","null");
                                ApiResponse errorResponse = buildErrorResponseJson(Arrays.asList(e+""),referenceNumber,400,httpServletResponse,"Internal Server Error",uuid);
                                return errorResponse;
                            }

                        }
                    }else{
                        LogUtil.info("Authtype","not available right now");
//                        JSONObject apiResponse = new JSONObject();
//                        apiResponse.put("message", "Currently Available AuthType: Payload & Method: Post");
//                        apiResponse.put("referenceNumber", referenceNumber);
                        ApiResponse errorResponse = buildErrorResponseJson(Arrays.asList("Currently Available AuthType: Payload & Method: Post"),referenceNumber,400,httpServletResponse,"Currently Available AuthType: Payload & Method: Post",null);
                        return errorResponse;
                    }
                }else{
                    LogUtil.info("Data not fetch","not found");
//                    JSONObject apiResponse = new JSONObject();
//                    apiResponse.put("message", "No Active SMS Gateway found. Please manage through SMS Gateways tab available in  joget application");
//                    apiResponse.put("referenceNumber", referenceNumber);
                    ApiResponse errorResponse = buildErrorResponseJson(Arrays.asList("No Active SMS Gateway found. Please manage through SMS Gateways tab available in  joget application"),referenceNumber,400,httpServletResponse,"No Active SMS Gateway found. Please manage through SMS Gateways tab available in  joget application",null);
                    return errorResponse;
//                    return new ApiResponse(200, apiResponse);
                }



            } catch (Exception e) {
                LogUtil.info("data", "Error while selection records.: "+e);
//                JSONObject apiResponse = new JSONObject();
//                apiResponse.put("message", e+"");
//                apiResponse.put("referenceNumber", referenceNumber);
//                return new ApiResponse(200, apiResponse);
                ApiResponse errorResponse = buildErrorResponseJson(Arrays.asList(e+""),referenceNumber,400,httpServletResponse,"Internal Server Error",null);
                return errorResponse;
            } finally {
                // Close resources
                try {
                    if (stmt != null) stmt.close();
                    if (con != null) con.close();
                } catch (Exception ex) {
                    LogUtil.info("data","Error closing database connection: "+ex);
                }
            }







//            try {
//                //get data from master table if not return errror else go for send.
//
//                apiUrl = "https://saudi.mshastra.com/sendsms_api_json.aspx";
//                username = "Econcord";
//                password= "dP5MFmBB";
//                //recipientPhone = "966591262652";
//                sender = "Concord";
//                language = "Unicode";
//
//                JSONObject obj = new JSONObject();
//                obj.put("user", username);
//                obj.put("pwd", password);
//                obj.put("number", recipientPhone);
//                obj.put("msg", content);
//                obj.put("sender", sender);
//                obj.put("language ", language);
//
//                JSONArray payloadArray = new JSONArray();
//                payloadArray.put(obj);
//
//                String payload = payloadArray.toString();
//                String responseString = "";
//                int responseCode = 0;
//
//                URL url = new URL("https://saudi.mshastra.com/sendsms_api_json.aspx");
//                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//
//                conn.setRequestMethod("POST");
//                conn.setRequestProperty("Content-Type", "application/json");
//                conn.setDoOutput(true);
//
//                OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
//                writer.write(payload);
//                writer.flush();
//                writer.close();
//
//                responseCode = conn.getResponseCode();
//
//                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//                String inputLine;
//                StringBuffer response = new StringBuffer();
//
//                while ((inputLine = in.readLine()) != null) {
//                    response.append(inputLine);
//                }
//                in.close();
//
//                responseString = response.toString();
//
//                LogUtil.info("Payload Sent: " , payload+"");
//                LogUtil.info("Response Code: " ,responseCode+"");
//                LogUtil.info("Raw Response: " , responseString+"");
//                if (responseCode == 200) {
//                    JSONArray jsonResponse = new JSONArray(responseString);// sms spi response
//                    JSONObject apiResponse = new JSONObject();//sending to client about result
//                    String status=null;
//                    String message=null;
//                    String messageId="null";
//
//                    if (jsonResponse.length() == 0) {
//                        LogUtil.info("hi: ","Empty response received. No SMS sent or unknown issue.");
//                        status="Failed";
//                        message="Empty response received. No SMS sent or unknown issue.";
//
//                    } else {
//                        for (int i = 0; i < jsonResponse.length(); i++) {
//                            JSONObject item = jsonResponse.getJSONObject(i);
//                            String str_response = item.optString("str_response").trim();
//
//                            if (str_response.toLowerCase().contains("send successful")) {
//                                messageId=item.optString("msg_id").trim();
//                                LogUtil.info("SMS sent successfully to: " , item.optString("number").trim());
//                                LogUtil.info(" Message ID: " ,messageId);
//                                status="Success";
//                                message="SMS sent successfully to the provided mobile number: "+item.optString("number").trim();
//                                apiResponse.put("messageId",messageId);
//                            }else if (str_response.toLowerCase().contains("invalid password")) {
//                                LogUtil.info("Failed: " , str_response);
//                                status="Failed";
//                                message="Error: Authentication failed due to incorrect password. Please check and try again.";
//                            }else if (str_response.toLowerCase().contains("invalid profile id")) {
//                                LogUtil.info("Failed: " , str_response);
//                                status="Failed";
//                                message="Error: The profile ID provided is not valid. Please verify your credentials";
//                            }else if (str_response.toLowerCase().contains("no more credits")) {
//                                LogUtil.info("Failed: " , str_response);
//                                status="Failed";
//                                message="Error: SMS credits exhausted. Please recharge your account to send more messages.";
//                            }else if (str_response.toLowerCase().contains("invalid mobile no.")) {
//                                LogUtil.info("Failed: " , str_response);
//                                status="Failed";
//                                message="Error: The mobile number format is invalid. Please check and enter a valid number.";
//                            }
//                            else {
//                                LogUtil.info("Unknown response format: " ,""+ str_response);
//                                status="Failed";
//                                message="Error: "+str_response;
//                            }
//                        }
//                    }
//
//
//
//                    apiResponse.put("message", message);
//                    apiResponse.put("referenceNumber", referenceNumber);
//                    apiResponse.put("jogetID", uuid);
//
//                    saveSMSLog(referenceNumber,templateId,uuid,recipientPhone,status,message,messageId);
//                    return new ApiResponse(200, apiResponse);
//                } else {
//                    LogUtil.info(getClass().getName(), "Error sending email. ");
//                    JSONObject errorResponse = new JSONObject();
//                    errorResponse.put("message", "Failed");
//                    errorResponse.put("referenceNumber", referenceNumber);
//                    errorResponse.put("jogetID", uuid);
//                    errorResponse.put("error", "responce is not 200");
//                    saveSMSLog(referenceNumber,templateId,uuid,recipientPhone,"Failed","responce is not 200","null");
//                    return new ApiResponse(200, errorResponse);
//                }
//
//
//            } catch (Exception e) {
//                LogUtil.info(getClass().getName(), "Error sending sms. "+e);
//                JSONObject errorResponse = new JSONObject();
//                errorResponse.put("message", "Failed");
//                errorResponse.put("referenceNumber", referenceNumber);
//                errorResponse.put("jogetID", uuid);
//                errorResponse.put("error", e+"");
//                saveSMSLog(referenceNumber,templateId,uuid,recipientPhone,"Failed",e+"","null");
//                return new ApiResponse(200, errorResponse);
//            }
//-------------------------------------version 3------------------------------------------------------------------------------------------


        } catch (Exception e) {
//            LogUtil.info(getClass().getName(),"Exception encountered during API processing. "+e);
//            return new ApiResponse(405, e.getMessage());
            LogUtil.info("error---->  ",""+e);
            e.printStackTrace();
            ApiResponse errorResponse;
            try {
                errorResponse = buildErrorResponseJson(Arrays.asList(e+""),null,400,httpServletResponse,"Internal Server Error",null);
                return errorResponse;
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                LogUtil.info("error e1---->  ",""+e1);

                e1.printStackTrace();
            }
        }
        return null;
    }




    void saveEmailLog(String referenceId, String templateId, String jogetId, String emailSubject, String email, String status, String logMessage) {
        // Define Form Definition ID and Table Name
        String formDefId = "email_log";  // Replace with actual form definition ID
        String tableName = "email_log";  // Replace with actual table name in Joget

        try {
            // Get FormDataDao bean from Joget
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            if (formDataDao != null) {
                // Create a new FormRow to store log details
                FormRow row = new FormRow();
                row.setId(jogetId);  // Unique identifier for log entry
                row.setProperty("template_id", templateId);
                row.setProperty("reference_id", referenceId);
                row.setProperty("joget_id", jogetId);
                row.setProperty("email_subject", emailSubject);
                row.setProperty("recipient_email", email);
                row.setProperty("status", status);
                row.setProperty("log_description", logMessage);
                row.setProperty("timestamp", new Timestamp(System.currentTimeMillis()).toString());

                // Add row to FormRowSet
                FormRowSet rowSet = new FormRowSet();
                rowSet.add(row);

                // Save or update the log data
                formDataDao.saveOrUpdate(formDefId, tableName, rowSet);

                LogUtil.info(getLabel(),"Email Log Saved successfully");
            } else {
                System.err.println("Failed to retrieve FormDataDao bean.");
                LogUtil.info(getLabel(),"form dao not exixts");
            }
        } catch (Exception e) {
            LogUtil.info(getLabel(),"Error: "+e);
        }
    }

    void saveSMSLog(String referenceId, String templateId, String jogetId, String phone, String status, String logMessage,String messageId) {
        // Define Form Definition ID and Table Name
        String formDefId = "sms_log";  // Replace with actual form definition ID
        String tableName = "sms_log";  // Replace with actual table name in Joget

        try {
            // Get FormDataDao bean from Joget
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");

            if (formDataDao != null) {
                // Create a new FormRow to store log details
                FormRow row = new FormRow();
                row.setId(jogetId);  // Unique identifier for log entry
                row.setProperty("template_id", templateId);
                row.setProperty("reference_id", referenceId);
                row.setProperty("joget_id", jogetId);
                row.setProperty("recipient_phone", phone);
                row.setProperty("status", status);
                row.setProperty("log_description", logMessage);
                row.setProperty("message_id", messageId);
                row.setProperty("timestamp", new Timestamp(System.currentTimeMillis()).toString());

                // Add row to FormRowSet
                FormRowSet rowSet = new FormRowSet();
                rowSet.add(row);

                // Save or update the log data
                formDataDao.saveOrUpdate(formDefId, tableName, rowSet);

                LogUtil.info(getLabel(),"SMS Log Saved successfully");
            } else {
                System.err.println("Failed to retrieve FormDataDao bean.");
                LogUtil.info(getLabel(),"form dao not exixts");
            }
        } catch (Exception e) {
            LogUtil.info(getLabel(),"Error: "+e);
        }
    }


    private ApiResponse buildErrorResponseJson(List<String> errors, String referenceNo, int statusCode, HttpServletResponse resp,String errorMsg,String jogetID) throws IOException {
        JSONArray errorList = new JSONArray();

        for (String message : errors) {
            JSONObject error = new JSONObject();
            error.put("ErrorCode", "ERR_5001");
            error.put("ErrorMessage", message);
            errorList.put(error);
        }

        JSONObject data = new JSONObject();
        data.put("ErrorList", errorList);
        data.put("requestReferenceNo", referenceNo);

        JSONObject root = new JSONObject();
        root.put("data", data);
        root.put("statusCode", statusCode);
        root.put("message", errorMsg);
        root.put("success", false);
        if(jogetID!=null && !jogetID.isEmpty())
            root.put("jogetID", jogetID);
        resp.setContentType("application/json");
        resp.getWriter().write(root.toString());
        ApiResponse apiRes = new ApiResponse(400, true);
        apiRes.write(resp);
        return apiRes;
    }

}
