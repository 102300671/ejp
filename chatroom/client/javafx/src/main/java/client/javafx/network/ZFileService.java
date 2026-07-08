package client.javafx.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.UUID;

public class ZFileService {
    
    private String zfileServerUrl;
    private String uploadToken;
    private final Gson gson = new Gson();
    
    public void setZfileServerUrl(String url) {
        this.zfileServerUrl = url;
    }
    
    public String getZfileServerUrl() {
        return zfileServerUrl;
    }
    
    public void setUploadToken(String token) {
        this.uploadToken = token;
    }
    
    public String getUploadToken() {
        return uploadToken;
    }
    
    public String uploadImage(File file, String chatName) throws Exception {
        return uploadFile(file, chatName, true);
    }
    
    public String uploadFile(File file, String chatName) throws Exception {
        return uploadFile(file, chatName, false);
    }
    
    private String uploadFile(File file, String chatName, boolean isImage) throws Exception {
        if (zfileServerUrl == null || uploadToken == null) {
            throw new Exception("ZFile 服务器配置未完成");
        }
        
        Date now = new Date();
        int year = now.getYear() + 1900;
        int month = now.getMonth() + 1;
        int day = now.getDate();
        String datePath = String.format("%04d/%02d/%02d", year, month, day);
        
        String basePath;
        if (isImage) {
            basePath = "/images/group/room/" + chatName;
        } else {
            basePath = "/files/group/room/" + chatName;
        }
        String uploadPath = basePath + "/" + datePath;
        
        String uniqueFileName = generateUniqueFileName(file.getName());
        
        String createUploadUrl = zfileServerUrl + "/api/file/operator/upload/file";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("storageKey", "chatroom-files");
        requestBody.addProperty("path", uploadPath);
        requestBody.addProperty("name", uniqueFileName);
        requestBody.addProperty("size", file.length());
        requestBody.addProperty("password", "");
        
        HttpURLConnection conn = (HttpURLConnection) new URL(createUploadUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Zfile-Token", uploadToken);
        conn.setRequestProperty("Axios-Request", "true");
        conn.setRequestProperty("Axios-From", zfileServerUrl);
        conn.setDoOutput(true);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(gson.toJson(requestBody).getBytes("UTF-8"));
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("创建上传任务失败，状态码: " + responseCode);
        }
        
        String responseBody = readResponse(conn);
        JsonObject response = gson.fromJson(responseBody, JsonObject.class);
        
        if (!response.has("code") || !"0".equals(response.get("code").getAsString())) {
            throw new Exception("创建上传任务失败");
        }
        
        String uploadUrl = response.get("data").getAsString();
        
        try {
            URL urlObj = new URL(uploadUrl);
            URL configUrlObj = new URL(zfileServerUrl);
            URL correctedUrl = new URL(configUrlObj.getProtocol(), configUrlObj.getHost(), 
                    configUrlObj.getPort(), urlObj.getPath());
            uploadUrl = correctedUrl.toString();
        } catch (Exception e) {
        }
        
        HttpURLConnection uploadConn = (HttpURLConnection) new URL(uploadUrl).openConnection();
        uploadConn.setRequestMethod("PUT");
        uploadConn.setRequestProperty("Zfile-Token", uploadToken);
        uploadConn.setRequestProperty("Axios-Request", "true");
        uploadConn.setRequestProperty("Axios-From", zfileServerUrl);
        uploadConn.setDoOutput(true);
        
        String boundary = UUID.randomUUID().toString();
        uploadConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        
        try (OutputStream os = uploadConn.getOutputStream()) {
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
            os.write("Content-Type: application/octet-stream\r\n".getBytes());
            os.write("\r\n".getBytes());
            
            try (InputStream is = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            
            os.write(("\r\n--" + boundary + "--\r\n").getBytes());
        }
        
        responseCode = uploadConn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("文件上传失败，状态码: " + responseCode);
        }
        
        responseBody = readResponse(uploadConn);
        response = gson.fromJson(responseBody, JsonObject.class);
        
        if (!response.has("code") || !"0".equals(response.get("code").getAsString())) {
            throw new Exception("文件上传失败");
        }
        
        return uploadPath + "/" + uniqueFileName;
    }
    
    public byte[] downloadFile(String relativePath) throws Exception {
        if (zfileServerUrl == null) {
            throw new Exception("ZFile 服务器配置未完成");
        }
        
        String fileUrl = zfileServerUrl + "/pd/chatroom-files/chatroom" + relativePath;
        
        HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
        conn.setRequestMethod("GET");
        
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("文件下载失败，状态码: " + responseCode);
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = conn.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
        }
        
        return baos.toByteArray();
    }
    
    public String getFilePreviewUrl(String relativePath) {
        if (zfileServerUrl == null) {
            return null;
        }
        return zfileServerUrl + "/pd/chatroom-files/chatroom" + relativePath;
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
    
    private String generateUniqueFileName(String originalName) {
        int lastDotIndex = originalName.lastIndexOf('.');
        String extension = lastDotIndex > 0 ? originalName.substring(lastDotIndex) : "";
        String nameWithoutExtension = lastDotIndex > 0 ? originalName.substring(0, lastDotIndex) : originalName;
        
        return nameWithoutExtension + "_" + System.currentTimeMillis() + extension;
    }
    
    public static ZFileService getInstance() {
        return InstanceHolder.INSTANCE;
    }
    
    private static class InstanceHolder {
        private static final ZFileService INSTANCE = new ZFileService();
    }
}