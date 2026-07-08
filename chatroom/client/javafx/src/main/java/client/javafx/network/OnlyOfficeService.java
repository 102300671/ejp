package client.javafx.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class OnlyOfficeService {
    
    private String onlyOfficeApiUrl = "http://localhost:8082/web-apps/apps/api/documents/api.js";
    private final Gson gson = new Gson();
    
    public void setOnlyOfficeApiUrl(String url) {
        this.onlyOfficeApiUrl = url;
    }
    
    public String getOnlyOfficeApiUrl() {
        return onlyOfficeApiUrl;
    }
    
    public JsonObject getOnlyOfficeConfig(String filePath, String fileName, String openMode) throws Exception {
        String zfileServerUrl = ZFileService.getInstance().getZfileServerUrl();
        String uploadToken = ZFileService.getInstance().getUploadToken();
        
        if (zfileServerUrl == null) {
            throw new Exception("ZFile 服务器未配置");
        }
        
        String configUrl = zfileServerUrl + "/onlyOffice/config/token";
        
        JsonObject configData = new JsonObject();
        configData.addProperty("path", filePath);
        configData.addProperty("name", fileName);
        
        HttpURLConnection conn = (HttpURLConnection) new URL(configUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (uploadToken != null) {
            conn.setRequestProperty("Zfile-Token", uploadToken);
        }
        conn.setDoOutput(true);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(gson.toJson(configData).getBytes("UTF-8"));
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("OnlyOffice 配置请求失败，状态码: " + responseCode);
        }
        
        String responseBody = readResponse(conn);
        JsonObject response = gson.fromJson(responseBody, JsonObject.class);
        
        if (!response.has("code") || !"0".equals(response.get("code").getAsString())) {
            String msg = response.has("msg") ? response.get("msg").getAsString() : "配置错误";
            throw new Exception("OnlyOffice 配置错误: " + msg);
        }
        
        JsonObject config = response.getAsJsonObject("data");
        
        if (config.has("editorConfig")) {
            JsonObject editorConfig = config.getAsJsonObject("editorConfig");
            switch (openMode) {
                case "download":
                    editorConfig.addProperty("mode", "view");
                    editorConfig.addProperty("readonly", true);
                    editorConfig.addProperty("canDownload", true);
                    editorConfig.addProperty("canPrint", true);
                    break;
                case "view":
                    editorConfig.addProperty("mode", "view");
                    editorConfig.addProperty("readonly", true);
                    break;
                case "edit":
                default:
                    break;
            }
        } else {
            JsonObject editorConfig = new JsonObject();
            if ("download".equals(openMode) || "view".equals(openMode)) {
                editorConfig.addProperty("mode", "view");
                editorConfig.addProperty("readonly", true);
            }
            config.add("editorConfig", editorConfig);
        }
        
        return config;
    }
    
    public boolean canPreviewWithOnlyOffice(String fileName) {
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex);
        }
        
        String[] onlyOfficeExtensions = {
            ".doc", ".docx", ".docm", ".dot", ".dotx", ".dotm", ".odt", ".fodt",
            ".xls", ".xlsx", ".xlsm", ".xlt", ".xltx", ".xltm", ".ods", ".fods",
            ".ppt", ".pptx", ".pptm", ".pps", ".ppsx", ".ppsm", ".odp", ".fodp",
            ".pdf", ".epub", ".djvu", ".xps", ".oxps"
        };
        
        for (String ext : onlyOfficeExtensions) {
            if (extension.toLowerCase().equals(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is;
        if (conn.getResponseCode() >= 400) {
            is = conn.getErrorStream();
        } else {
            is = conn.getInputStream();
        }
        
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
    
    public static OnlyOfficeService getInstance() {
        return InstanceHolder.INSTANCE;
    }
    
    private static class InstanceHolder {
        private static final OnlyOfficeService INSTANCE = new OnlyOfficeService();
    }
}