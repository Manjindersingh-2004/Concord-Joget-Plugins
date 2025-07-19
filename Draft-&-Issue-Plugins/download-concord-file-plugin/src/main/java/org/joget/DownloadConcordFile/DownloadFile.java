package org.joget.DownloadConcordFile;

import org.apache.commons.compress.utils.IOUtils;
import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;

import org.joget.commons.util.LogUtil;

import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONObject;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;


public class DownloadFile extends ApiPluginAbstract{
    @Override
    public String getIcon() {
        return "<i class=\"fas fa-file-alt\"></i>";
    }

    @Override
    public String getTag() {
        return "Download";
    }

    @Override
    public String getName() {
        return "Download File";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Custom api endpoints to download the file";
    }

    @Override
    public String getLabel() {
        return "Download File";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return null;
    }



    @Operation(path = "/", type = Operation.MethodType.GET, summary = "Download File", description = "Custom Api to Download files")
    @Responses({
            @Response(responseCode = 200, description = "Success"),
            @Response(responseCode = 400, description = "Failed"),
            @Response(responseCode = 405, description = "Internal Error")})
    public ApiResponse downloadFile(@Param(value = "token", required = true, description = "Encrypted file path") String token, HttpServletRequest request, HttpServletResponse response){
        DataInputStream in = null;
        OutputStream out = null;
        String filePath = token;
        File file = new File(filePath);
        try{

                if (!file.exists() || file.isDirectory()) {
                    response.setDateHeader("Expires", System.currentTimeMillis());
                    response.setHeader("Cache-Control", "no-cache, no-store");
                    return new ApiResponse(404, "File not found");
                }

                in = new DataInputStream(new FileInputStream(file));
                out = response.getOutputStream();

                // Set MIME type
                String contentType = request.getSession().getServletContext().getMimeType(file.getName());
                if (contentType == null) {
                    contentType = new MimetypesFileTypeMap().getContentType(file);
                }
                response.setContentType(contentType);



                    // Stream file to output
                byte[] buffer = new byte[65536];
                int length = 0;
                while ((length = in.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }

                LogUtil.info("Download File", "✅ File sent successfully: " + filePath);
                return new ApiResponse(200, true);

            } catch (Exception e) {
                LogUtil.info("Download File", "Error. "+e);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", e+"");
                return new ApiResponse(200, errorResponse);
            }finally {
                    try { if (in != null) in.close(); } catch (Exception e) {}
                    try { if (out != null) { out.flush(); out.close(); } } catch (Exception e) {}
            }

    }

}
