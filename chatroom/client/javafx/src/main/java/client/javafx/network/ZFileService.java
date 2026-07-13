package client.javafx.network;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import client.javafx.util.Logger;
import client.javafx.util.PlatformUtils;

public class ZFileService {
    
    private static final Logger logger = new Logger(ZFileService.class);
    
    private String zfileServerUrl;
    private String uploadToken;
    private final Gson gson = new Gson();
    private final String cacheDir;
    
    public static final String CHAT_TYPE_PUBLIC_ROOM = "PUBLIC_ROOM";
    public static final String CHAT_TYPE_PRIVATE_ROOM = "PRIVATE_ROOM";
    public static final String CHAT_TYPE_PRIVATE_CHAT = "PRIVATE_CHAT";
    
    public ZFileService() {
        this.cacheDir = PlatformUtils.getAvatarsCacheDir();
        ensureCacheDirExists();
    }
    
    public void setZfileServerUrl(String url) {
        logger.info("设置ZFile服务器URL: " + url);
        this.zfileServerUrl = url;
    }
    
    public String getZfileServerUrl() {
        return zfileServerUrl;
    }
    
    public void setUploadToken(String token) {
        logger.info("设置ZFile上传Token: " + (token != null ? "***" : null));
        this.uploadToken = token;
    }
    
    public String getUploadToken() {
        return uploadToken;
    }
    
    private void ensureCacheDirExists() {
        try {
            Path path = Paths.get(cacheDir);
            if (!Files.exists(path)) {
                logger.info("创建头像缓存目录: " + cacheDir);
                Files.createDirectories(path);
                logger.info("头像缓存目录创建成功");
            }
        } catch (IOException e) {
            logger.error("创建头像缓存目录失败: " + e.getMessage(), e);
        }
    }
    
    private String getCacheFilePath(String relativePath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(relativePath.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String ext = "";
            if (relativePath.contains(".")) {
                ext = relativePath.substring(relativePath.lastIndexOf("."));
            }
            String cachePath = cacheDir + sb.toString() + ext;
            logger.debug("缓存文件路径: relativePath=" + relativePath + ", cachePath=" + cachePath);
            return cachePath;
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            logger.warn("MD5哈希失败，使用hashCode作为缓存文件名: " + e.getMessage());
            return cacheDir + Math.abs(relativePath.hashCode()) + ".dat";
        }
    }
    
    public byte[] downloadFileWithCache(String relativePath) throws Exception {
        logger.debug("开始下载文件(带缓存): relativePath=" + relativePath);
        String cacheFile = getCacheFilePath(relativePath);
        File cacheFileObj = new File(cacheFile);
        
        if (cacheFileObj.exists()) {
            long fileSize = cacheFileObj.length();
            logger.debug("缓存命中，直接读取缓存文件: cacheFile=" + cacheFile + ", size=" + fileSize + " bytes");
            byte[] data = Files.readAllBytes(cacheFileObj.toPath());
            logger.debug("缓存文件读取完成: relativePath=" + relativePath + ", dataSize=" + data.length + " bytes");
            return data;
        }
        
        logger.debug("缓存未命中，从服务器下载: relativePath=" + relativePath);
        byte[] data = downloadFile(relativePath);
        logger.debug("服务器下载完成: relativePath=" + relativePath + ", dataSize=" + data.length + " bytes");
        
        try {
            Files.write(Paths.get(cacheFile), data);
            logger.debug("缓存文件保存成功: cacheFile=" + cacheFile);
        } catch (IOException e) {
            logger.error("保存缓存文件失败: cacheFile=" + cacheFile + ", error=" + e.getMessage(), e);
        }
        
        return data;
    }
    
    public String uploadAvatar(File file, String username) throws Exception {
        logger.info("开始上传头像: username=" + username + ", fileName=" + file.getName() + ", fileSize=" + file.length() + " bytes");
        
        if (zfileServerUrl == null || uploadToken == null) {
            logger.error("上传头像失败: ZFile服务器配置未完成, zfileServerUrl=" + zfileServerUrl + ", uploadToken=" + (uploadToken != null ? "***" : null));
            throw new Exception("ZFile 服务器配置未完成");
        }
        
        String uploadPath = "/avatars/users/" + username;
        String fileExtension = "";
        int dotIndex = file.getName().lastIndexOf('.');
        if (dotIndex > 0) {
            fileExtension = file.getName().substring(dotIndex).toLowerCase();
        }
        String uniqueFileName = "avatar" + fileExtension;
        
        logger.debug("上传参数: uploadPath=" + uploadPath + ", uniqueFileName=" + uniqueFileName);
        
        String createUploadUrl = zfileServerUrl + "/api/file/operator/upload/file";
        logger.debug("创建上传任务URL: " + createUploadUrl);
        
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
        logger.debug("创建上传任务响应状态码: " + responseCode);
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = readResponse(conn);
            logger.error("创建上传任务失败，状态码: " + responseCode + ", 响应内容: " + errorResponse);
            throw new Exception("创建上传任务失败，状态码: " + responseCode);
        }
        
        String responseBody = readResponse(conn);
        logger.debug("创建上传任务响应: " + responseBody);
        
        JsonObject response = parseResponsePayload(responseBody, "创建上传任务");
        
        if (!response.has("code") || !"0".equals(response.get("code").getAsString())) {
            logger.error("创建上传任务失败，响应码不为0: " + responseBody);
            throw new Exception("创建上传任务失败");
        }
        
        String uploadUrl = extractStringField(response, "data");
        logger.debug("获取到上传URL: " + uploadUrl);
        
        String originalUploadUrl = uploadUrl;
        try {
            URL urlObj = new URL(uploadUrl);
            URL configUrlObj = new URL(zfileServerUrl);
            URL correctedUrl = new URL(configUrlObj.getProtocol(), configUrlObj.getHost(), 
                    configUrlObj.getPort(), urlObj.getPath());
            uploadUrl = correctedUrl.toString();
            logger.debug("URL修正: original=" + originalUploadUrl + ", corrected=" + uploadUrl);
        } catch (Exception e) {
            logger.warn("URL修正失败，使用原始URL: " + e.getMessage());
        }
        
        logger.debug("开始上传文件到URL: " + uploadUrl);
        
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
        logger.debug("文件上传响应状态码: " + responseCode);
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = readResponse(uploadConn);
            logger.error("文件上传失败，状态码: " + responseCode + ", 响应内容: " + errorResponse);
            throw new Exception("文件上传失败，状态码: " + responseCode);
        }
        
        responseBody = readResponse(uploadConn);
        logger.debug("文件上传响应: " + responseBody);
        
        response = parseResponsePayload(responseBody, "文件上传");
        
        if (!response.has("code") || !"0".equals(response.get("code").getAsString())) {
            logger.error("文件上传失败，响应码不为0: " + responseBody);
            throw new Exception("文件上传失败");
        }
        
        String resultPath = uploadPath + "/" + uniqueFileName;
        logger.info("头像上传成功: resultPath=" + resultPath);
        
        return resultPath;
    }
    
    public String uploadImage(File file, String chatName, String chatType, String privateChatRecipient) throws Exception {
        return uploadFile(file, chatName, chatType, privateChatRecipient, true);
    }
    
    public String uploadFile(File file, String chatName, String chatType, String privateChatRecipient) throws Exception {
        return uploadFile(file, chatName, chatType, privateChatRecipient, false);
    }
    
    public String uploadVoice(File file, String chatName, String chatType, String privateChatRecipient) throws Exception {
        logger.info("开始上传语音文件: fileName=" + file.getName() + ", fileSize=" + file.length() + " bytes, chatName=" + chatName + ", chatType=" + chatType);
        
        if (zfileServerUrl == null || uploadToken == null) {
            logger.error("上传语音文件失败: ZFile服务器配置未完成");
            throw new Exception("ZFile 服务器配置未完成");
        }
        
        Date now = new Date();
        int year = now.getYear() + 1900;
        int month = now.getMonth() + 1;
        int day = now.getDate();
        String datePath = String.format("%04d/%02d/%02d", year, month, day);
        
        String basePath;
        if (CHAT_TYPE_PRIVATE_CHAT.equals(chatType) && privateChatRecipient != null) {
            basePath = "/voice/private/" + privateChatRecipient;
        } else if (CHAT_TYPE_PUBLIC_ROOM.equals(chatType)) {
            basePath = "/voice/group/public/" + chatName;
        } else if (CHAT_TYPE_PRIVATE_ROOM.equals(chatType)) {
            basePath = "/voice/group/private/" + chatName;
        } else {
            basePath = "/voice/group/public/" + chatName;
        }
        String uploadPath = basePath + "/" + datePath;
        
        String uniqueFileName = generateUniqueFileName(file.getName());
        
        logger.debug("上传参数: basePath=" + basePath + ", datePath=" + datePath + ", uploadPath=" + uploadPath + ", uniqueFileName=" + uniqueFileName);
        
        String createUploadUrl = zfileServerUrl + "/api/file/operator/upload/file";
        logger.debug("创建上传任务URL: " + createUploadUrl);
        
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
        logger.debug("创建上传任务响应状态码: " + responseCode);
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = readResponse(conn);
            logger.error("创建上传任务失败，状态码: " + responseCode + ", 响应内容: " + errorResponse);
            throw new Exception("创建上传任务失败，状态码: " + responseCode);
        }
        
        String responseBody = readResponse(conn);
        logger.debug("创建上传任务响应: " + responseBody);
        
        JsonObject response = parseResponsePayload(responseBody, "创建上传任务");
        
        if (!response.has("code") || !"0".equals(response.get("code").getAsString())) {
            logger.error("创建上传任务失败，响应码不为0: " + responseBody);
            throw new Exception("创建上传任务失败");
        }
        
        String uploadUrl = extractStringField(response, "data");
        logger.debug("获取到上传URL: " + uploadUrl);
        
        String originalUploadUrl = uploadUrl;
        try {
            URL urlObj = new URL(uploadUrl);
            URL configUrlObj = new URL(zfileServerUrl);
            URL correctedUrl = new URL(configUrlObj.getProtocol(), configUrlObj.getHost(), 
                    configUrlObj.getPort(), urlObj.getPath());
            uploadUrl = correctedUrl.toString();
            logger.debug("URL修正: original=" + originalUploadUrl + ", corrected=" + uploadUrl);
        } catch (Exception e) {
            logger.warn("URL修正失败，使用原始URL: " + e.getMessage());
        }
        
        logger.debug("开始上传文件到URL: " + uploadUrl);
        
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
            os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + uniqueFileName + "\"\r\n").getBytes());
            os.write("Content-Type: audio/mpeg\r\n".getBytes());
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
        logger.debug("文件上传响应状态码: " + responseCode);
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = readResponse(uploadConn);
            logger.error("文件上传失败，状态码: " + responseCode + ", 响应内容: " + errorResponse);
            throw new Exception("文件上传失败，状态码: " + responseCode);
        }
        
        responseBody = readResponse(uploadConn);
        logger.debug("文件上传响应: " + responseBody);
        
        response = parseResponsePayload(responseBody, "文件上传");
        
        if (!response.has("code") || !"0".equals(response.get("code").getAsString())) {
            logger.error("文件上传失败，响应码不为0: " + responseBody);
            throw new Exception("文件上传失败");
        }
        
        String resultPath = uploadPath + "/" + uniqueFileName;
        logger.info("语音文件上传成功: resultPath=" + resultPath);
        
        return resultPath;
    }
    
    private String uploadFile(File file, String chatName, String chatType, String privateChatRecipient, boolean isImage) throws Exception {
        logger.info("开始上传文件: fileName=" + file.getName() + ", fileSize=" + file.length() + " bytes, chatName=" + chatName + ", chatType=" + chatType + ", isImage=" + isImage);
        
        if (zfileServerUrl == null || uploadToken == null) {
            logger.error("上传文件失败: ZFile服务器配置未完成, zfileServerUrl=" + zfileServerUrl + ", uploadToken=" + (uploadToken != null ? "***" : null));
            throw new Exception("ZFile 服务器配置未完成");
        }
        
        Date now = new Date();
        int year = now.getYear() + 1900;
        int month = now.getMonth() + 1;
        int day = now.getDate();
        String datePath = String.format("%04d/%02d/%02d", year, month, day);
        
        String basePath;
        if (CHAT_TYPE_PRIVATE_CHAT.equals(chatType) && privateChatRecipient != null) {
            if (isImage) {
                basePath = "/images/private/" + privateChatRecipient;
            } else {
                basePath = "/files/private/" + privateChatRecipient;
            }
        } else if (CHAT_TYPE_PUBLIC_ROOM.equals(chatType)) {
            if (isImage) {
                basePath = "/images/group/public/" + chatName;
            } else {
                basePath = "/files/group/public/" + chatName;
            }
        } else if (CHAT_TYPE_PRIVATE_ROOM.equals(chatType)) {
            if (isImage) {
                basePath = "/images/group/private/" + chatName;
            } else {
                basePath = "/files/group/private/" + chatName;
            }
        } else {
            if (isImage) {
                basePath = "/images/group/public/" + chatName;
            } else {
                basePath = "/files/group/public/" + chatName;
            }
        }
        String uploadPath = basePath + "/" + datePath;
        
        String uniqueFileName = generateUniqueFileName(file.getName());
        
        logger.debug("上传参数: basePath=" + basePath + ", datePath=" + datePath + ", uploadPath=" + uploadPath + ", uniqueFileName=" + uniqueFileName);
        
        String createUploadUrl = zfileServerUrl + "/api/file/operator/upload/file";
        logger.debug("创建上传任务URL: " + createUploadUrl);
        
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
        logger.debug("创建上传任务响应状态码: " + responseCode);
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = readResponse(conn);
            logger.error("创建上传任务失败，状态码: " + responseCode + ", 响应内容: " + errorResponse);
            throw new Exception("创建上传任务失败，状态码: " + responseCode);
        }
        
        String responseBody = readResponse(conn);
        logger.debug("创建上传任务响应: " + responseBody);
        
        JsonObject response = parseResponsePayload(responseBody, "创建上传任务");
        
        if (!response.has("code") || !"0".equals(response.get("code").getAsString())) {
            logger.error("创建上传任务失败，响应码不为0: " + responseBody);
            throw new Exception("创建上传任务失败");
        }
        
        String uploadUrl = extractStringField(response, "data");
        logger.debug("获取到上传URL: " + uploadUrl);
        
        String originalUploadUrl = uploadUrl;
        try {
            URL urlObj = new URL(uploadUrl);
            URL configUrlObj = new URL(zfileServerUrl);
            URL correctedUrl = new URL(configUrlObj.getProtocol(), configUrlObj.getHost(), 
                    configUrlObj.getPort(), urlObj.getPath());
            uploadUrl = correctedUrl.toString();
            logger.debug("URL修正: original=" + originalUploadUrl + ", corrected=" + uploadUrl);
        } catch (Exception e) {
            logger.warn("URL修正失败，使用原始URL: " + e.getMessage());
        }
        
        logger.debug("开始上传文件到URL: " + uploadUrl);
        
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
            os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + uniqueFileName + "\"\r\n").getBytes());
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
        logger.debug("文件上传响应状态码: " + responseCode);
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = readResponse(uploadConn);
            logger.error("文件上传失败，状态码: " + responseCode + ", 响应内容: " + errorResponse);
            throw new Exception("文件上传失败，状态码: " + responseCode);
        }
        
        responseBody = readResponse(uploadConn);
        logger.debug("文件上传响应: " + responseBody);
        
        response = parseResponsePayload(responseBody, "文件上传");
        
        if (!response.has("code") || !"0".equals(response.get("code").getAsString())) {
            logger.error("文件上传失败，响应码不为0: " + responseBody);
            throw new Exception("文件上传失败");
        }
        
        String resultPath = uploadPath + "/" + uniqueFileName;
        logger.info("文件上传成功: resultPath=" + resultPath);
        
        return resultPath;
    }
    
    public byte[] downloadFile(String relativePath) throws Exception {
        logger.debug("开始从服务器下载文件: relativePath=" + relativePath);
        
        if (zfileServerUrl == null) {
            logger.error("下载文件失败: ZFile服务器配置未完成");
            throw new Exception("ZFile 服务器配置未完成");
        }
        
        String fileUrl = zfileServerUrl + "/pd/chatroom-files/chatroom" + relativePath;
        logger.debug("下载文件URL: " + fileUrl);
        
        HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
        conn.setRequestMethod("GET");
        
        int responseCode = conn.getResponseCode();
        logger.debug("下载文件响应状态码: " + responseCode);
        
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = readResponse(conn);
            logger.error("文件下载失败，状态码: " + responseCode + ", 响应内容: " + errorResponse);
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
        
        byte[] data = baos.toByteArray();
        logger.debug("文件下载完成: relativePath=" + relativePath + ", dataSize=" + data.length + " bytes");
        
        return data;
    }
    
    public String getFilePreviewUrl(String relativePath) {
        if (zfileServerUrl == null) {
            return null;
        }
        return zfileServerUrl + "/pd/chatroom-files/chatroom" + relativePath;
    }
    
    static JsonObject parseResponsePayload(String responseBody, String context) throws Exception {
        logger.debug("解析响应Payload: context=" + context + ", responseBody=" + (responseBody != null ? responseBody.substring(0, Math.min(500, responseBody.length())) + (responseBody.length() > 500 ? "..." : "") : "null"));
        
        if (responseBody == null || responseBody.trim().isEmpty()) {
            logger.error("解析响应失败: context=" + context + ", 响应为空");
            throw new Exception(context + "响应为空");
        }

        JsonElement element = JsonParser.parseString(responseBody.trim());
        if (element.isJsonObject()) {
            logger.debug("响应解析为JsonObject");
            return element.getAsJsonObject();
        }

        if (element.isJsonPrimitive()) {
            logger.debug("响应解析为JsonPrimitive，包装为JsonObject");
            JsonObject wrapped = new JsonObject();
            wrapped.addProperty("code", "0");
            wrapped.addProperty("data", element.getAsString());
            return wrapped;
        }

        logger.error("解析响应失败: context=" + context + ", 响应格式异常");
        throw new Exception(context + "响应格式异常");
    }

    private String extractStringField(JsonObject response, String fieldName) {
        JsonElement element = response.get(fieldName);
        if (element == null || element.isJsonNull()) {
            logger.error("提取字段失败: fieldName=" + fieldName + ", 响应缺少此字段");
            throw new IllegalStateException("响应缺少字段: " + fieldName);
        }

        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            logger.debug("提取字段成功: fieldName=" + fieldName + ", value=" + value);
            return value;
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("url")) {
                String value = object.get("url").getAsString();
                logger.debug("提取字段成功: fieldName=" + fieldName + ", 从url属性获取: " + value);
                return value;
            }
            if (object.has("path")) {
                String value = object.get("path").getAsString();
                logger.debug("提取字段成功: fieldName=" + fieldName + ", 从path属性获取: " + value);
                return value;
            }
        }

        String value = element.toString();
        logger.debug("提取字段成功: fieldName=" + fieldName + ", 返回toString值: " + value);
        return value;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        int responseCode = conn.getResponseCode();
        InputStream is;
        if (responseCode >= 400) {
            is = conn.getErrorStream();
            logger.debug("读取错误响应流: responseCode=" + responseCode);
        } else {
            is = conn.getInputStream();
            logger.debug("读取正常响应流: responseCode=" + responseCode);
        }
        
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        
        String response = sb.toString();
        logger.debug("响应内容读取完成: length=" + response.length() + ", content=" + (response.length() > 500 ? response.substring(0, 500) + "..." : response));
        return response;
    }
    
    private String generateUniqueFileName(String originalName) {
        int lastDotIndex = originalName.lastIndexOf('.');
        String extension = lastDotIndex > 0 ? originalName.substring(lastDotIndex) : "";
        String nameWithoutExtension = lastDotIndex > 0 ? originalName.substring(0, lastDotIndex) : originalName;
        
        String randomStr = UUID.randomUUID().toString().substring(0, 8);
        String uniqueName = nameWithoutExtension + "_" + System.currentTimeMillis() + "_" + randomStr + extension;
        
        logger.debug("生成唯一文件名: originalName=" + originalName + ", uniqueName=" + uniqueName);
        return uniqueName;
    }
    
    public static ZFileService getInstance() {
        return InstanceHolder.INSTANCE;
    }
    
    private static class InstanceHolder {
        private static final ZFileService INSTANCE = new ZFileService();
    }
}