package org.joget.CustomPostResponseFormApi;

import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiDefinition;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.api.model.JSONOrderedObject;
import org.joget.api.service.ApiService;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.FileUpload;
import org.joget.apps.form.lib.Grid;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;



public class FormApiPlugin extends ApiPluginAbstract {

    @Override
    public String getName() {
        return "Custom Form Api get Referral code and get user by username or email";
    }
    JSONObject firstObject=new JSONObject();
    String ansString=null;
    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Custom Form Api (referral code & get user by username or email)";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-file-alt\"></i>";
    }

    @Override
    public String getTag() {
        return "form/{formDefId}";
    }

    @Override
    public String getLabel() {
        return "Custom Form Api (referral code & get user by username or email)";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
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
        String json = "[\n" +
                "    {\n" +
                "        \"title\": \"\",\n" +
                "        \"properties\": [\n" +
                "            {\n" +
                "                \"name\": \"formDefId\",\n" +
                "                \"label\": \"Form\",\n" +
                "                \"type\": \"selectbox\",\n" +
                "                \"options_ajax\": \""+contextPath+"/web/json/console/app/"+appPath+"/forms/options\"," +
                "                \"required\": \"True\"\n" +
                "            },\n" +
                "            {\n" +
                "                \"name\": \"label\",\n" +
                "                \"label\": \"Description\",\n" +
                "                \"type\": \"textfield\"\n" +
                "            },\n" +
                "            {\n" +
                "                \"name\" : \"ignorePermission\",\n" +
                "                \"label\" : \"ignorePermission\",\n" +
                "                \"type\" : \"checkbox\",\n" +
                "                \"options\" : [{\n" +
                "                    \"value\" : \"true\",\n" +
                "                    \"label\" : \"\"\n" +
                "                }]\n" +
                "            }\n" +
                "        ]\n" +
                "    }\n" +
                "]";
        return json;
    }

    @Operation(path = "/", summary = "addFormData", description = "addFormData")
    @Responses({
            @Response(responseCode = 200, description = "200", definition = "FormDataResponse"),
            @Response(responseCode = 405, description = "405")})
    public ApiResponse addFormData(@Param(value = "body", description = "addFormData", definition = "{formDefId}-FormData") JSONObject body) {
        try {
            LogUtil.info("FormDefID(1)",getPropertyString("formDefId"));
            LogUtil.info("IgnorePermission",getPropertyString("ignorePermission"));

            Form form = getForm();

            LogUtil.info("FormDefID(2)",getPropertyString("formDefId"));
            FormData formData = new FormData();
            if ("true".equalsIgnoreCase(getPropertyString("ignorePermission"))) {
                formData.addFormResult("_PREVIEW_MODE", "true");
            }
            LogUtil.info("FormDefID(3)",getPropertyString("formDefId"));
            if (body.has("id")
                    && !body.getString("id").isEmpty()) {

                formData.setPrimaryKeyValue(body.getString("id"));

                if(form==null){
                    LogUtil.info("from","null");
                }else{
                    LogUtil.info("from","available");
                }
                if(formData==null){
                    LogUtil.info("formData","null");
                }else{
                    LogUtil.info("formData","available");
                }

                FormUtil.executeLoadBinders((Element) form, formData);//error at this point
                FormRowSet rows = formData.getLoadBinderData((Element) form);
                LogUtil.info("fullbody",body.toString());
                if (!rows.isEmpty()) {
                    JSONObject resp = new JSONObject();
                    resp.put("id", "");
                    resp.put("errors", "Id Already Exists");
                    return new ApiResponse(200, resp);
                }
            }
            body.put("referral_code",storeReferenceId());
            jsonToFormData((Element) form, formData, body);
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            FormData submitted = appService.submitForm(form, formData, false);
            LogUtil.info("submitteddatayehai",submitted.toString());
            JSONObject resp = new JSONObject();
            if (submitted.getFormErrors().isEmpty()) {
                resp.put("id", submitted.getPrimaryKeyValue());
                String referenceCode= submitted.getRequestParameter("referral_code");
                resp.put("referral_code", referenceCode);
            } else {
                resp.put("id", "");
                resp.put("errors", submitted.getFormErrors());
            }

            return new ApiResponse(200, resp);
        } catch (JSONException e) {
            LogUtil.error(getClassName(), (Throwable) e, "");
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPIPlugin.resp.405", getClassName(), getResourceBundlePath()));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
            return new ApiResponse(500, AppPluginUtil.getMessage("FormAPIPlugin.resp.500", getClassName(), getResourceBundlePath()));
        }
    }

    @Operation(path = "/saveOrUpdate", summary = "addOrUpdateFormData", description = "addOrUpdateFormData")
    @Responses({
            @Response(responseCode = 200, description = "200", definition = "FormDataResponse"),
            @Response(responseCode = 405, description = "405")})
    public ApiResponse saveOrUpdateFormData(@Param(value = "body", description = "addFormData", definition = "{formDefId}-FormData") JSONObject body) {
        try {
            Form form = getForm();
            FormData formData = new FormData();
            if ("true".equalsIgnoreCase(getPropertyString("ignorePermission"))) {
                formData.addFormResult("_PREVIEW_MODE", "true");
            }
            if (body.has("id")
                    && !body.getString("id").isEmpty()) {
                formData.setPrimaryKeyValue(body.getString("id"));
                FormUtil.executeLoadBinders((Element) form, formData);
                FormRowSet rows = formData.getLoadBinderData((Element) form);
                if (!rows.isEmpty()) {
                    Element el = FormUtil.findElement("id", (Element) form, formData);
                    if (el != null) {
                        String idValue = FormUtil.getElementPropertyValue(el, formData);
                        if (idValue != null && !idValue.trim().isEmpty() && !"".equals(formData.getRequestParameter("_FORM_META_ORIGINAL_ID"))) {
                            el.setProperty("readonly", "true");
                        }
                    }
                }
            }
            jsonToFormData((Element) form, formData, body);
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            FormData submitted = appService.submitForm(form, formData, false);
            JSONObject resp = new JSONObject();
            if (submitted.getFormErrors().isEmpty()) {
                resp.put("id", submitted.getPrimaryKeyValue());
                resp.put("errors", new HashMap<>());
            } else {
                resp.put("id", "");
                resp.put("errors", submitted.getFormErrors());
            }
            return new ApiResponse(200, resp);
        } catch (JSONException e) {
            LogUtil.error(getClassName(), (Throwable) e, "");
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
            return new ApiResponse(500, AppPluginUtil.getMessage("FormAPI.resp.500", getClassName(), getResourceBundlePath()));
        }
    }

    @Operation(path = "/addWithFiles", summary = "addFormDataWithFiles", description = "addFormData", bodyContentType = "multipart/form-data")
    @Responses({
            @Response(responseCode = 200, description = "200", definition = "FormDataResponse"),
            @Response(responseCode = 405, description = "405")})
    public ApiResponse addFormDataWithFiles(@Param(value = "body", description = "addFormData", definition = "{formDefId}-FormDataWithFiles") Map body) {
        try {
            FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
            Form form = getForm();
            FormData formData = new FormData();
            formData = formService.retrieveFormDataFromRequestMap(formData, body);
            handleGridData((Element) form, formData);
            if ("true".equalsIgnoreCase(getPropertyString("ignorePermission"))) {
                formData.addFormResult("_PREVIEW_MODE", "true");
            }
            String id = formData.getRequestParameter("id");
            if (id != null && !id.isEmpty()) {
                formData.setPrimaryKeyValue(id);
                FormUtil.executeLoadBinders((Element) form, formData);
                FormRowSet rows = formData.getLoadBinderData((Element) form);
                if (!rows.isEmpty()) {
                    return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405.InvalidID", getClassName(), getResourceBundlePath()));
                }
            }
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            FormData submitted = appService.submitForm(form, formData, false);
            JSONObject resp = new JSONObject();
            if (submitted.getFormErrors().isEmpty()) {
                resp.put("id", submitted.getPrimaryKeyValue());
                resp.put("errors", new HashMap<>());
            } else {
                resp.put("id", "");
                resp.put("errors", submitted.getFormErrors());
            }
            return new ApiResponse(200, resp);
        } catch (JSONException e) {
            LogUtil.error(getClassName(), (Throwable) e, "");
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
            return new ApiResponse(500, AppPluginUtil.getMessage("FormAPI.resp.500", getClassName(), getResourceBundlePath()));
        }
    }

    @Operation(path = "/", type = Operation.MethodType.PUT, summary = "updateFormData", description = "updateFormData")
    @Responses({
            @Response(responseCode = 200, description = "200", definition = "FormDataResponse"),
            @Response(responseCode = 404, description = "404"),
            @Response(responseCode = 405, description = "405")})
    public ApiResponse updateFormData(@Param(value = "body", description = "updateFormData", definition = "{formDefId}-FormData") JSONObject body) {
        try {
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            Form form = getForm();
            FormData formData = new FormData();
            if ("true".equalsIgnoreCase(getPropertyString("ignorePermission"))) {
                formData.addFormResult("_PREVIEW_MODE", "true");
            }
            if (body.has("id")) {
                formData.setPrimaryKeyValue(body.getString("id"));
                formData.addRequestParameterValues("_FORM_META_ORIGINAL_ID", new String[]{body.getString("id")});
            } else {
                return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
            }
            FormUtil.executeLoadBinders((Element) form, formData);
            FormRowSet rows = formData.getLoadBinderData((Element) form);
            if (rows == null || rows.isEmpty()) {
                return new ApiResponse(404, AppPluginUtil.getMessage("FormAPI.resp.404", getClassName(), getResourceBundlePath()));
            }
            Element el = FormUtil.findElement("id", (Element) form, formData);
            if (el != null) {
                String idValue = FormUtil.getElementPropertyValue(el, formData);
                if (idValue != null && !idValue.trim().isEmpty() && !"".equals(formData.getRequestParameter("_FORM_META_ORIGINAL_ID"))) {
                    el.setProperty("readonly", "true");
                }
            }
            jsonToFormData((Element) form, formData, body);
            FormData submitted = appService.submitForm(form, formData, false);
            JSONObject resp = new JSONObject();
            if (submitted.getFormErrors().isEmpty()) {
                resp.put("recordid", submitted.getPrimaryKeyValue());
                resp.put("errors", new HashMap<>());
            } else {
                resp.put("recordid", submitted.getPrimaryKeyValue());
                resp.put("errors", submitted.getFormErrors());
            }
            return new ApiResponse(200, resp);
        } catch (JSONException e) {
            LogUtil.error(getClassName(), (Throwable) e, "");
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
            return new ApiResponse(500, AppPluginUtil.getMessage("FormAPI.resp.500", getClassName(), getResourceBundlePath()));
        }
    }

    @Operation(path = "/updateWithFiles", type = Operation.MethodType.POST, summary = "updateFormDataWithFiles", description = "updateFormData", bodyContentType = "multipart/form-data")
    @Responses({
            @Response(responseCode = 200, description = "200", definition = "FormDataResponse"),
            @Response(responseCode = 404, description = "404"),
            @Response(responseCode = 405, description = "405")})
    public ApiResponse updateFormDataWithFiles(@Param(value = "body", description = "updateFormData", definition = "{formDefId}-FormDataWithFiles") Map body, @Param(value = "appendFile", required = false, description = "appendFile") String appendFile) {
        try {
            AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
            FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
            Form form = getForm();
            FormData formData = new FormData();
            formData = formService.retrieveFormDataFromRequestMap(formData, body);
            if (appendFile != null && appendFile.equalsIgnoreCase("true")) {
                FormRowSet loadRows = form.getLoadBinder().load((Element) form, formData.getRequestParameter("id"), formData);
                if (loadRows != null && !loadRows.isEmpty()) {
                    FormRow row = (FormRow) loadRows.get(0);
                    for (Object fieldId : row.keySet()) {
                        Element field = FormUtil.findElement(fieldId.toString(), (Element) form, formData);
                        if (field instanceof FileUpload && formData.getRequestParameterValues(fieldId.toString()) != null) {
                            List<String> appendFiles = new ArrayList<>(Arrays.asList(formData.getRequestParameterValues(fieldId.toString())));
                            if (row.getProperty(fieldId.toString()).contains(";")) {
                                String[] existingFile = row.getProperty(fieldId.toString()).split(";");
                                for (int i = 0; i <= existingFile.length - 1; i++) {
                                    appendFiles.add(existingFile[i]);
                                }
                            } else {
                                appendFiles.add(row.getProperty(fieldId.toString()));
                            }
                            String[] UploadedFile = appendFiles.<String>toArray(new String[0]);
                            formData.addRequestParameterValues(fieldId.toString(), UploadedFile);
                        }
                    }
                }
            }
            handleGridData((Element) form, formData);
            if ("true".equalsIgnoreCase(getPropertyString("ignorePermission"))) {
                formData.addFormResult("_PREVIEW_MODE", "true");
            }
            String id = formData.getRequestParameter("id");
            if (id != null && !id.isEmpty()) {
                formData.setPrimaryKeyValue(id);
                formData.addRequestParameterValues("_FORM_META_ORIGINAL_ID", new String[]{id});
            } else {
                return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
            }
            FormUtil.executeLoadBinders((Element) form, formData);
            FormRowSet rows = formData.getLoadBinderData((Element) form);
            if (rows == null || rows.isEmpty()) {
                return new ApiResponse(404, AppPluginUtil.getMessage("FormAPI.resp.404", getClassName(), getResourceBundlePath()));
            }
            Element el = FormUtil.findElement("id", (Element) form, formData);
            if (el != null) {
                String idValue = FormUtil.getElementPropertyValue(el, formData);
                if (idValue != null && !idValue.trim().isEmpty() && !"".equals(formData.getRequestParameter("_FORM_META_ORIGINAL_ID"))) {
                    el.setProperty("readonly", "true");
                }
            }
            FormData submitted = appService.submitForm(form, formData, false);
            JSONObject resp = new JSONObject();
            if (submitted.getFormErrors().isEmpty()) {
                resp.put("id", submitted.getPrimaryKeyValue());
                resp.put("errors", new HashMap<>());
            } else {
                resp.put("id", submitted.getPrimaryKeyValue());
                resp.put("errors", submitted.getFormErrors());
            }
            return new ApiResponse(200, resp);
        } catch (JSONException e) {
            LogUtil.error(getClassName(), (Throwable) e, "");
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
            return new ApiResponse(500, AppPluginUtil.getMessage("FormAPI.resp.500", getClassName(), getResourceBundlePath()));
        }
    }

    @Operation(path = "/{recordId}", type = Operation.MethodType.GET, summary = "getFormData", description = "getFormData")
    @Responses({
            @Response(responseCode = 200, description = "200"),
            @Response(responseCode = 405, description = "405")})
    public ApiResponse getFormData(@Param(value = "recordId", description = "getFormData") String recordId, @Param(value = "includeSubformData", required = false, description = "includeSubformData") Boolean includeSubformData, @Param(value = "includeReferenceElements", required = false, description = "includeReferenceElements") Boolean includeReferenceElements, @Param(value = "flattenData", required = false, description = "flattenData") Boolean flatten) {
        try {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            Map<String, Object> result = FormUtil.loadFormData(appDef.getId(), appDef.getVersion().toString(), getPropertyString("formDefId"), recordId, (includeSubformData == null) ? false : includeSubformData.booleanValue(), (includeReferenceElements == null) ? false : includeSubformData.booleanValue(), (flatten == null) ? false : flatten.booleanValue(), null);
            if (!"true".equalsIgnoreCase(getPropertyString("ignorePermission"))) {
                FormData formData = new FormData();
                formData.setPrimaryKeyValue(recordId);
                Form form = getForm();
                Set<String> existFields = new HashSet<>();
                checkElementPermission((Element) form, result, formData, existFields, includeSubformData, flatten);
            }
            return new ApiResponse(200, new JSONObject(result));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, recordId);
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
        }
    }

    @Operation(path = "/{recordId}", type = Operation.MethodType.DELETE, summary = "deleteFormData", description = "deleteFormData.")
    @Responses({
            @Response(responseCode = 200, description = "200"),
            @Response(responseCode = 404, description = "404")})
    public ApiResponse deleteFormData(@Param(value = "recordId", description = "deleteFormData") String recordId) {
        try {
            Form form = getForm();
            FormData formData = new FormData();
            formData.setPrimaryKeyValue(recordId);
            formData.addFormResult("FORM_RESULT_LOAD_ALL_DATA", "FORM_RESULT_LOAD_ALL_DATA");
            formData = FormUtil.executeLoadBinders((Element) form, formData);
            FormUtil.recursiveExecuteFormDeleteBinders((Element) form, formData, true, true, true, true);
            return new ApiResponse(200, AppPluginUtil.getMessage("FormAPI.resp.200", getClassName(), getResourceBundlePath()));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, recordId);
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
        }
    }

    @Operation(path = "/{recordId}/download/{fileName}", type = Operation.MethodType.GET, summary = "downloadFile", description = "downloadFile")
    @Responses({
            @Response(responseCode = 200, description = "200", contentType = "*", definition = "{\"type\" : \"file\"}"),
            @Response(responseCode = 404, description = "404")})
    public ApiResponse downloadFile(@Param(value = "recordId", description = "getFormData") String recordId, @Param(value = "fileName", description = "fileName") String fileName, @Param(value = "attachment", required = false, description = "attachment") Boolean attachment, HttpServletRequest request, HttpServletResponse response) {
        try {
            boolean isAuthorize = false;
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            String tableName = null;
            if (!"true".equalsIgnoreCase(getPropertyString("ignorePermission"))) {
                Form form = getForm();
                try {
                    if (form != null && form.getLoadBinder() != null) {
                        tableName = form.getPropertyString("tableName");
                        FormData formData = new FormData();
                        FormRowSet rows = form.getLoadBinder().load((Element) form, recordId, formData);
                        if (rows != null && !rows.isEmpty()) {
                            FormRow row = (FormRow) rows.get(0);
                            for (Object fieldId : row.keySet()) {
                                String compareValue = fileName;
                                if (compareValue.endsWith(".thumb.jpg")) {
                                    compareValue = compareValue.replace(".thumb.jpg", "");
                                }
                                String value = row.getProperty(fieldId.toString());
                                if (value.equals(compareValue) || (value
                                        .contains(";") && (value
                                        .startsWith(compareValue + ";") || value
                                        .contains(";" + compareValue + ";") || value
                                        .endsWith(";" + compareValue))) || value
                                        .contains(getPropertyString("formDefId") + "/" + recordId + "/" + compareValue) || value
                                        .contains("{tempFilePath}" + compareValue)) {
                                    Element field = FormUtil.findElement(fieldId.toString(), (Element) form, formData);
                                    if (field instanceof FileDownloadSecurity) {
                                        FileDownloadSecurity security = (FileDownloadSecurity) field;
                                        isAuthorize = security.isDownloadAllowed(request.getParameterMap());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception exception) {
                }
            } else {
                isAuthorize = true;
                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
                tableName = appService.getFormTableName(appDef, getPropertyString("formDefId"));
            }
            if (!isAuthorize) {
                response.setDateHeader("Expires", System.currentTimeMillis() + 0L);
                response.setHeader("Cache-Control", "no-cache, no-store");
                return new ApiResponse(404, true);
            }
            ServletOutputStream stream = response.getOutputStream();
            String decodedFileName = fileName;
            try {
                decodedFileName = URLDecoder.decode(fileName, "UTF8");
            } catch (UnsupportedEncodingException unsupportedEncodingException) {
            }
            File file = FileUtil.getFile(decodedFileName, tableName, recordId);
            if (file.isDirectory() || !file.exists()) {
                response.setDateHeader("Expires", System.currentTimeMillis() + 0L);
                response.setHeader("Cache-Control", "no-cache, no-store");
                return new ApiResponse(404, true);
            }
            DataInputStream in = new DataInputStream(new FileInputStream(file));
            byte[] bbuf = new byte[65536];
            try {
                String contentType = request.getSession().getServletContext().getMimeType(decodedFileName);
                if (contentType != null) {
                    response.setContentType(contentType);
                }
                if (attachment != null && Boolean.valueOf(attachment.booleanValue()).booleanValue()) {
                    String name = URLEncoder.encode(decodedFileName, "UTF8").replaceAll("\\+", "%20");
                    response.setHeader("Content-Disposition", "attachment; filename=" + name + "; filename*=UTF-8''" + name);
                }
                int length = 0;
                while (in != null && (length = in.read(bbuf)) != -1) {
                    stream.write(bbuf, 0, length);
                }
            } finally {
                in.close();
                stream.flush();
                stream.close();
            }
            return new ApiResponse(200, true);
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, recordId);
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
        }
    }

    @Operation(path = "/{recordId}/{fieldId}/files", type = Operation.MethodType.GET, summary = "downloadFiles", description = "downloadFiles")
    @Responses({
            @Response(responseCode = 200, description = "@200", contentType = "application/zip", definition = "{\"type\" : \"file\"}"),
            @Response(responseCode = 404, description = "404")})
    public ApiResponse downloadFiles(@Param(value = "recordId", description = "getFormData") String recordId, @Param(value = "fieldId", description = "field id") String fieldId, HttpServletRequest request, HttpServletResponse response) {
        try {
            boolean isAuthorize = false;
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            String tableName = null;
            Form form = getForm();
            FormData formData = new FormData();
            if (!"true".equalsIgnoreCase(getPropertyString("ignorePermission"))) {
                try {
                    if (form != null && form.getLoadBinder() != null) {
                        tableName = form.getPropertyString("tableName");
                        Element field = FormUtil.findElement(fieldId, (Element) form, formData);
                        if (field instanceof FileDownloadSecurity) {
                            FileDownloadSecurity security = (FileDownloadSecurity) field;
                            isAuthorize = security.isDownloadAllowed(request.getParameterMap());
                        }
                    }
                } catch (Exception exception) {
                }
            } else {
                isAuthorize = true;
                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
                tableName = appService.getFormTableName(appDef, getPropertyString("formDefId"));
            }
            if (!isAuthorize) {
                response.setDateHeader("Expires", System.currentTimeMillis() + 0L);
                response.setHeader("Cache-Control", "no-cache, no-store");
                return new ApiResponse(404, true);
            }
            FormRowSet rows = form.getLoadBinder().load((Element) form, recordId, formData);
            FormRow row = null;
            if (rows != null && !rows.isEmpty()) {
                row = (FormRow) rows.get(0);
            }
            if (row == null || !row.containsKey(fieldId) || row.getProperty(fieldId).isEmpty()) {
                response.setDateHeader("Expires", System.currentTimeMillis() + 0L);
                response.setHeader("Cache-Control", "no-cache, no-store");
                return new ApiResponse(404, true);
            }
            ServletOutputStream stream = response.getOutputStream();
            ZipOutputStream zip = null;
            try {
                String[] values = row.getProperty(fieldId).split(";");
                Collection<File> files = new ArrayList<>();
                for (String v : values) {
                    File file = FileUtil.getFile(v, tableName, recordId);
                    if (file.exists() && !file.isDirectory()) {
                        files.add(file);
                    }
                }
                if (!files.isEmpty()) {
                    response.setContentType("application/zip");
                    response.setHeader("Content-Disposition", "attachment; filename=" + fieldId + ".zip");
                    zip = new ZipOutputStream((OutputStream) stream);
                    for (File file : files) {
                        if (file.canRead()) {
                            FileInputStream fis = null;
                            try {
                                zip.putNextEntry(new ZipEntry(file.getName()));
                                fis = new FileInputStream(file);
                                byte[] buffer = new byte[4092];
                                int byteCount = 0;
                                while ((byteCount = fis.read(buffer)) != -1) {
                                    zip.write(buffer, 0, byteCount);
                                }
                                zip.closeEntry();
                            } finally {
                                if (fis != null) {
                                    fis.close();
                                }
                            }
                        }
                    }
                    return new ApiResponse(200, true);
                }
                response.setDateHeader("Expires", System.currentTimeMillis() + 0L);
                response.setHeader("Cache-Control", "no-cache, no-store");
                return new ApiResponse(404, true);
            } finally {
                if (zip != null) {
                    zip.flush();
                }
                stream.flush();
                stream.close();
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, recordId);
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
        }
    }







    @Operation(path = "/fetchUser", type = Operation.MethodType.POST, summary = "getUserByUsernameOrEmail", description = "getUserByUsernameOrEmail")
    @Responses({
            @Response(responseCode = 200, description = "200"),
            @Response(responseCode = 405, description = "405")})
    public ApiResponse getFormData(@Param(value = "body", description = "getUserByUsernameOrEmail", definition = "{formDefId}-FormData") JSONObject body, @Param(value = "includeSubformData", required = false, description = "includeSubformData") Boolean includeSubformData, @Param(value = "includeReferenceElements", required = false, description = "includeReferenceElements") Boolean includeReferenceElements, @Param(value = "flattenData", required = false, description = "flattenData") Boolean flatten) {
        String recordId = null;
        try {

            if(body.has("id")){
                recordId= body.getString("id");
                if(recordId==null){
                    return new ApiResponse(HttpServletResponse.SC_NOT_FOUND, "User Not Exits for given ID");
                }
            } else if (body.has("email")) {
                recordId=body.getString("email");
                String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
                if (recordId.matches(emailRegex)) {
                    recordId=getUsernameByEmail(recordId);
                    if(recordId==null){
                        return new ApiResponse(HttpServletResponse.SC_NOT_FOUND, "User Not Exits for given email");
                    }
                }else{
                    return new ApiResponse(HttpServletResponse.SC_BAD_REQUEST, "Invalid Email Syntax");
                }
            }else{
                return new ApiResponse(HttpServletResponse.SC_BAD_REQUEST, "Missing ID or Email");
            }

            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            Map<String, Object> result = FormUtil.loadFormData(appDef.getId(), appDef.getVersion().toString(), getPropertyString("formDefId"), recordId, (includeSubformData == null) ? false : includeSubformData.booleanValue(), (includeReferenceElements == null) ? false : includeSubformData.booleanValue(), (flatten == null) ? false : flatten.booleanValue(), null);
            if (!"true".equalsIgnoreCase(getPropertyString("ignorePermission"))) {
                FormData formData = new FormData();
                formData.setPrimaryKeyValue(recordId);
                Form form = getForm();
                Set<String> existFields = new HashSet<>();
                checkElementPermission((Element) form, result, formData, existFields, includeSubformData, flatten);
            }

            if (result == null || result.isEmpty()) {
                return new ApiResponse(HttpServletResponse.SC_NOT_FOUND, "User Not Exits");
            }
            LogUtil.info("result",result.toString());

            String address="[]";
            if(result.get("address")!=null){
                address=result.get("address").toString();
            }
            result.put("address",new JSONArray(address));

            String[] removeKeys= {"validator","modifiedBy","createdBy","modifiedByName","createdByName","active"};
            for(String key : removeKeys){
                result.remove(key);
            }

            return new ApiResponse(200, new JSONObject(result));
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, recordId);
            return new ApiResponse(405, AppPluginUtil.getMessage("FormAPI.resp.405", getClassName(), getResourceBundlePath()));
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






    protected Form getForm() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        Form form = null;
        FormDefinition formDef = formDefinitionDao.loadById(getPropertyString("formDefId"), appDef);
        if (formDef != null && formDef.getJson() != null) {
            String formJson = formDef.getJson();
            formJson = AppUtil.processHashVariable(formJson, null, "json", null);
            form = (Form) formService.createElementFromJson(formJson);
        }
        return form;
    }

    public Map<String, ApiDefinition> getDefinitions() {
        Map<String, ApiDefinition> defs = new HashMap<>();
        Map<String, Class<?>> fields = new LinkedHashMap<>();
        fields.put("id", String.class);
        fields.put("errors", Map.class);
        defs.put("FormDataResponse", new ApiDefinition(fields));
        JSONObject obj = new JSONObject();
        try {
            obj.put("type", "object");
            JSONOrderedObject jSONOrderedObject = new JSONOrderedObject();
            jSONOrderedObject.put("id", ApiService.getSchema(String.class, null, false));
            Form form = getForm();
            FormData formData = new FormData();
            recursiveGenerateDefinition(formData, (JSONObject) jSONOrderedObject, (Element) form, false);
            jSONOrderedObject.put("dateCreated", ApiService.getSchema(Date.class, null, false));
            jSONOrderedObject.put("dateModified", ApiService.getSchema(Date.class, null, false));
            jSONOrderedObject.put("createdBy", ApiService.getSchema(String.class, null, false));
            jSONOrderedObject.put("createdByName", ApiService.getSchema(String.class, null, false));
            jSONOrderedObject.put("modifiedBy", ApiService.getSchema(String.class, null, false));
            jSONOrderedObject.put("modifiedByName", ApiService.getSchema(String.class, null, false));
            obj.put("properties", jSONOrderedObject);
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
        }
        defs.put(getPropertyString("formDefId") + "-FormData", new ApiDefinition(obj));
        JSONObject obj2 = new JSONObject();
        try {
            obj2.put("type", "object");
            JSONOrderedObject jSONOrderedObject = new JSONOrderedObject();
            jSONOrderedObject.put("id", ApiService.getSchema(String.class, null, false));
            Form form = getForm();
            FormData formData = new FormData();
            recursiveGenerateDefinition(formData, (JSONObject) jSONOrderedObject, (Element) form, true);
            jSONOrderedObject.put("dateCreated", ApiService.getSchema(Date.class, null, false));
            jSONOrderedObject.put("dateModified", ApiService.getSchema(Date.class, null, false));
            jSONOrderedObject.put("createdBy", ApiService.getSchema(String.class, null, false));
            jSONOrderedObject.put("createdByName", ApiService.getSchema(String.class, null, false));
            jSONOrderedObject.put("modifiedBy", ApiService.getSchema(String.class, null, false));
            jSONOrderedObject.put("modifiedByName", ApiService.getSchema(String.class, null, false));
            obj2.put("properties", jSONOrderedObject);
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "");
        }
        defs.put(getPropertyString("formDefId") + "-FormDataWithFiles", new ApiDefinition(obj2));
        return defs;
    }

    protected void handleGridData(Element el, FormData formData) throws JSONException {
        if (!"true".equalsIgnoreCase(getPropertyString("ignorePermission")) && (!el.isAuthorize(formData).booleanValue() || FormUtil.isHidden(el, formData) || FormUtil.isReadonly(el, formData))) {
            return;
        }
        if (el instanceof Grid || el instanceof GridInnerDataRetriever) {
            String fieldId = el.getPropertyString("id");
            if (el.getCustomParameterName() != null) {
                fieldId = el.getCustomParameterName();
            }
            String data = formData.getRequestParameter(fieldId);
            if (data != null && !data.isEmpty()) {
                if (!data.startsWith("[") || !data.endsWith("]")) {
                    data = "[" + data + "]";
                }
                setGridData(el, formData, new JSONArray(data));
            }
        } else {
            Collection<Element> children = el.getChildren(formData);
            if (children != null && !children.isEmpty()) {
                for (Element c : children) {
                    handleGridData(c, formData);
                }
            }
        }
    }

    protected void jsonToFormData(Element el, FormData formData, JSONObject obj) throws JSONException {
        if (!"true".equalsIgnoreCase(getPropertyString("ignorePermission")) && (!el.isAuthorize(formData).booleanValue() || FormUtil.isHidden(el, formData) || FormUtil.isReadonly(el, formData))) {
            return;
        }
        String name = el.getPropertyString("id");
        Collection<String> dynamic = el.getDynamicFieldNames();
        if (dynamic != null && dynamic.isEmpty()) {
            for (String s : dynamic) {
                if (obj.has(s)) {
                    addRequestParam(formData, s, obj.get(s));
                }
            }
        }
        if (el instanceof Grid || el instanceof GridInnerDataRetriever) {
            if (obj.has(name)) {
                setGridData(el, formData, obj.getJSONArray(name));
            }
        } else if (!(el instanceof FormContainer)) {
            if (obj.has(name)) {
                addRequestParam(formData, FormUtil.getElementParameterName(el), obj.get(name));
            }
        } else if (el instanceof AbstractSubForm) {
            if (obj.has(name)) {
                JSONObject sobj = obj.getJSONObject(name);
                Collection<Element> children = el.getChildren(formData);
                if (children != null && !children.isEmpty()) {
                    for (Element c : children) {
                        jsonToFormData(c, formData, sobj);
                    }
                }
            }
        } else {
            Collection<Element> children = el.getChildren(formData);
            if (children != null && !children.isEmpty()) {
                for (Element c : children) {
                    jsonToFormData(c, formData, obj);
                }
            }
        }
    }

    protected void setGridData(Element el, FormData formData, JSONArray data) {
        try {
            Field cachedRowSetField = getField(el.getClass(), "cachedRowSet");
            cachedRowSetField.setAccessible(true);
            Map<FormData, FormRowSet> cachedRowSet = (Map<FormData, FormRowSet>) cachedRowSetField.get(el);
            if (cachedRowSet != null && !cachedRowSet.containsKey(formData)) {
                cachedRowSet.put(formData, FormUtil.jsonToFormRowSet(data.toString()));
            }
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, el.getClassName());
        }
    }

    protected Field getField(Class clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            Class superClass = clazz.getSuperclass();
            if (superClass == null) {
                throw e;
            }
            return getField(superClass, fieldName);
        }
    }

    protected void addRequestParam(FormData formData, String name, Object value) throws JSONException {
        if (value instanceof JSONArray) {
            JSONArray ja = (JSONArray) value;
            Collection<String> arr = new ArrayList<>();
            for (int i = 0; i < ja.length(); i++) {
                arr.add(ja.getString(i));
            }
            formData.addRequestParameterValues(name, arr.<String>toArray(new String[0]));
        } else {
            formData.addRequestParameterValues(name, new String[]{value.toString()});
        }
    }

    protected void checkElementPermission(Element el, Map<String, Object> result, FormData formData, Set<String> existFields, Boolean includeSubformData, Boolean flatten) {
        if (el instanceof Form && (el.getParent() == null || (el.getParent() != null && flatten != null && !flatten.booleanValue()))) {
            existFields.addAll(result.keySet());
            existFields.remove("id");
            existFields.remove("dateCreated");
            existFields.remove("createdByName");
            existFields.remove("createdBy");
            existFields.remove("dateModified");
            existFields.remove("modifiedByName");
            existFields.remove("modifiedBy");
        }
        boolean isAuthorize = el.isAuthorize(formData).booleanValue();
        String name = el.getPropertyString("id");
        if (isAuthorize && !FormUtil.isHidden(el, formData)) {
            Collection<String> dynamic = el.getDynamicFieldNames();
            if (dynamic != null && dynamic.isEmpty()) {
                for (String s : dynamic) {
                    existFields.remove(s);
                }
            }
            if (el instanceof Grid || el instanceof GridInnerDataRetriever) {
                existFields.remove(name);
                if (result.containsKey(name) && result.get(name) instanceof Collection) {
                    Set<String> gFields = new HashSet<>();
                    Set<String> gRemoveFields = null;
                    //Error1
                    Object optionProperty = el.getProperty("options");
                    if (optionProperty instanceof Collection<?>) {
                        for (Map<?, ?> opt : (Collection<Map<?, ?>>) optionProperty) {
                            Object value = opt.get("value");
                            if (value != null) {
                                gFields.add(value.toString());
                            }
                        }
                    }
                    //Error1end
                    gFields.add("id");
                    gFields.add("dateCreated");
                    gFields.add("createdByName");
                    gFields.add("createdBy");
                    gFields.add("dateModified");
                    gFields.add("modifiedByName");
                    gFields.add("modifiedBy");
                    Collection<Map<String, Object>> gdata = (Collection<Map<String, Object>>) result.get(name);
                    for (Map<String, Object> r : gdata) {
                        gRemoveFields = new HashSet<>();
                        for (String field : r.keySet()) {
                            if (!gFields.contains(field)) {
                                gRemoveFields.add(field);
                            }
                        }
                        for (String rfield : gRemoveFields) {
                            r.remove(rfield);
                        }
                    }
                }
            } else if (!(el instanceof FormContainer)) {
                existFields.remove(name);
            } else if (el instanceof AbstractSubForm && includeSubformData != null && includeSubformData.booleanValue()) {
                Map<String, Object> data = null;
                Set<String> newExistingFields = null;
                if (flatten != null && flatten.booleanValue()) {
                    data = result;
                    newExistingFields = existFields;
                } else if (result.containsKey(name)) {
                    newExistingFields = new HashSet<>();
                    try {
                        data = (Map<String, Object>) result.get(name);
                    } catch (Exception e) {
                        LogUtil.debug(FormApiPlugin.class.getName(), name + " can't cast to map");
                    }
                    if (data != null) {
                        Collection<Element> children = el.getChildren(formData);
                        if (children != null && !children.isEmpty()) {
                            for (Element c : children) {
                                checkElementPermission(c, data, formData, newExistingFields, includeSubformData, flatten);
                            }
                        }
                    }
                }
            } else {
                Collection<Element> children = el.getChildren(formData);
                if (children != null && !children.isEmpty()) {
                    for (Element c : children) {
                        checkElementPermission(c, result, formData, existFields, includeSubformData, flatten);
                    }
                }
            }
        }
        if (el instanceof Form && (el.getParent() == null || (el.getParent() != null && flatten != null && !flatten.booleanValue()))) {
            for (String r : existFields) {
                result.remove(r);
            }
        }
    }

    protected void recursiveGenerateDefinition(FormData formData, JSONObject properties, Element el, boolean handleFile) throws JSONException {
        Collection<String> dynamic = el.getDynamicFieldNames();
        String fieldId = el.getPropertyString("id");
        if (handleFile && el.getCustomParameterName() != null) {
            fieldId = el.getCustomParameterName();
        }
        String fieldLabel = el.getPropertyString("label");
        if (dynamic != null && dynamic.isEmpty()) {
            for (String s : dynamic) {
                properties.put(s, ApiService.getSchema(String.class, null, false));
            }
        }
        if (el instanceof Grid || el instanceof GridInnerDataRetriever) {
            JSONObject grid = new JSONObject();
            grid.put("type", "array");
            JSONObject obj = new JSONObject();
            grid.put("items", obj);
            obj.put("type", "object");
            JSONOrderedObject jSONOrderedObject = new JSONOrderedObject();
            jSONOrderedObject.put("id", ApiService.getSchema(String.class, null, false));
            Object optionProperty = el.getProperty("options");
            //Error2start
            if (optionProperty instanceof Collection<?>) {
                for (Object opt : (Collection<?>) optionProperty) {
                    if (opt instanceof Map<?, ?>) {  // Ensure opt is a Map before casting
                        Map<?, ?> optMap = (Map<?, ?>) opt;
                        Object value = optMap.get("value");
                        if (value != null) {
                            jSONOrderedObject.put(value.toString(), ApiService.getSchema(String.class, null, false));
                        }
                    }
                }
            }
            //Error2end
            obj.put("properties", jSONOrderedObject);
            if (handleFile) {
                grid.put("description", fieldLabel);
            }
            properties.put(fieldId, grid);
        } else if (!(el instanceof FormContainer)) {
            if (handleFile && el instanceof FileUpload) {
                properties.put(fieldId, ApiService.getSchema(File.class, null, false, fieldLabel));
            } else {
                properties.put(fieldId, ApiService.getSchema(String.class, null, false, fieldLabel));
            }
        } else if (el instanceof AbstractSubForm) {
            JSONObject obj = new JSONObject();
            obj.put("type", "object");
            JSONOrderedObject jSONOrderedObject = new JSONOrderedObject();
            Collection<Element> children = el.getChildren(formData);
            if (children != null && !children.isEmpty()) {
                for (Element c : children) {
                    if (handleFile) {
                        recursiveGenerateDefinition(formData, properties, c, handleFile);
                        continue;
                    }
                    recursiveGenerateDefinition(formData, (JSONObject) jSONOrderedObject, c, handleFile);
                }
            }
            if (!handleFile) {
                obj.put("properties", jSONOrderedObject);
                properties.put(fieldId, obj);
            }
        } else {
            Collection<Element> children = el.getChildren(formData);
            if (children != null && !children.isEmpty()) {
                for (Element c : children) {
                    recursiveGenerateDefinition(formData, properties, c, handleFile);
                }
            }
        }
    }

    public String getResourceBundlePath() {
        return "messages/apiPlugin";
    }





    String generateReferenceNumber() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("RF");
        for (int i = 0; i < 7; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    boolean isReferenceExists(Connection con, String referenceNumber) throws Exception {
        String sql = "SELECT * FROM app_fd_user_profile WHERE c_referral_code = ?";
        PreparedStatement stmt = con.prepareStatement(sql);
        stmt.setString(1, referenceNumber);
        ResultSet rs = stmt.executeQuery();
        boolean exists = rs.next();
        rs.close();
        stmt.close();
        return exists;
    }

    String storeReferenceId(){
        Connection con = null;
        String uniqueReference = null;

        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();

            int attempts = 100;
            while (attempts-- > 0) {
                String candidate = generateReferenceNumber();
                if (!isReferenceExists(con, candidate)) {
                    uniqueReference = candidate;
                    break;
                }else{
                    LogUtil.info("BeanShell", "Already Exists: " + uniqueReference);

                }
            }

            if (uniqueReference != null) {
                LogUtil.info("BeanShell", "Generated and updated reference number: " + uniqueReference);
                return uniqueReference;
            } else {
                LogUtil.warn("BeanShell", "Unable to generate a unique reference number after multiple attempts.");
            }

        } catch (Exception e) {
            LogUtil.error("BeanShell", e, "Error generating and updating reference number.");
        } finally {
            if (con != null) try { con.close(); } catch (Exception e) {}
        }
        return "";
    }


}

