package server.zfile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ZFileTokenManager {
    private static ZFileTokenManager instance;
    private String zfileServerUrl = "http://localhost:8081";
    private String zfileUsername = "chatroom-system";
    private String zfilePassword = "zeXvrDUDacr56Nt";
    
    private String zfileToken;
    private long zfileTokenExpireTime;
    
    private Map<String, TokenInfo> uploadTokens;
    
    private static class TokenInfo {
        String token;
        long expireTime;
        boolean used;
        
        TokenInfo(String token, long expireTime) {
            this.token = token;
            this.expireTime = expireTime;
            this.used = false;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
    
    private ZFileTokenManager() {
        uploadTokens = new ConcurrentHashMap<>();
        System.out.println("zfile 配置初始化完成: " + zfileServerUrl);
    }
    
    public static synchronized ZFileTokenManager getInstance() {
        if (instance == null) {
            instance = new ZFileTokenManager();
        }
        return instance;
    }
    
    private String getZFileToken() throws Exception {
        if (zfileToken != null && System.currentTimeMillis() < zfileTokenExpireTime) {
            return zfileToken;
        }
        
        String loginUrl = zfileServerUrl + "/user/login";
        URL url = URI.create(loginUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        String jsonBody = "{\"username\":\"" + zfileUsername + "\",\"password\":\"" + zfilePassword + "\"}";
        
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("zfile 登录失败，响应码: " + responseCode);
        }
        
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();
        
        String responseBody = response.toString();
        System.out.println("zfile 登录响应: " + responseBody);
        zfileToken = extractToken(responseBody);
        
        if (zfileToken == null) {
            throw new Exception("从 zfile 响应中提取 token 失败");
        }
        
        zfileTokenExpireTime = System.currentTimeMillis() + 3600000;
        System.out.println("zfile token 获取成功");
        
        return zfileToken;
    }
    
    private String extractToken(String responseBody) {
        try {
            int dataIndex = responseBody.indexOf("\"data\":{");
            if (dataIndex != -1) {
                int tokenIndex = responseBody.indexOf("\"token\":\"", dataIndex);
                if (tokenIndex != -1) {
                    int startIndex = tokenIndex + 9;
                    int endIndex = responseBody.indexOf("\"", startIndex);
                    if (endIndex != -1) {
                        return responseBody.substring(startIndex, endIndex);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("提取 token 失败: " + e.getMessage());
        }
        return null;
    }
    
    public String generateUploadToken(String username) throws Exception {
        String zfileToken = getZFileToken();
        
        System.out.println("为用户 " + username + " 提供 zfile token: " + zfileToken);
        
        return zfileToken;
    }
    
    public boolean validateUploadToken(String uploadToken) {
        TokenInfo tokenInfo = uploadTokens.get(uploadToken);
        
        if (tokenInfo == null) {
            return false;
        }
        
        if (tokenInfo.isExpired()) {
            uploadTokens.remove(uploadToken);
            return false;
        }
        
        if (tokenInfo.used) {
            uploadTokens.remove(uploadToken);
            return false;
        }
        
        tokenInfo.used = true;
        
        return true;
    }
    
    public String getUsernameFromToken(String uploadToken) {
        try {
            byte[] decoded = Base64.getDecoder().decode(uploadToken);
            String decodedStr = new String(decoded, StandardCharsets.UTF_8);
            return decodedStr.split("_")[0];
        } catch (Exception e) {
            return null;
        }
    }
    
    public void invalidateUploadToken(String uploadToken) {
        uploadTokens.remove(uploadToken);
        System.out.println("上传 token 已失效: " + uploadToken);
    }
    
    public String getZfileServerUrl() {
        return zfileServerUrl;
    }
    
    public void cleanupExpiredTokens() {
        long currentTime = System.currentTimeMillis();
        uploadTokens.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                System.out.println("清理过期的上传 token: " + entry.getKey());
                return true;
            }
            return false;
        });
    }
}