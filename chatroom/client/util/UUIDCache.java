package client.util;
import java.io.*;
import java.util.Properties;

public class UUIDCache {
    private static final String CACHE_FILE = "./user_cache.properties";
    private Properties properties;
    
    public UUIDCache() {
        this.properties = new Properties();
        loadCache();
    }
    
    /**
     * 加载缓存文件
     */
    private void loadCache() {
        File file = new File(CACHE_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
                System.out.println("成功加载UUID缓存文件");
            } catch (IOException e) {
                System.err.println("加载UUID缓存文件失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 保存缓存文件
     */
    private void saveCache() {
        try (FileOutputStream fos = new FileOutputStream(CACHE_FILE)) {
            properties.store(fos, "User UUID Cache");
            System.out.println("成功保存UUID缓存文件");
        } catch (IOException e) {
            System.err.println("保存UUID缓存文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 保存用户的UUID
     * @param username 用户名
     * @param uuid UUID
     */
    public void saveUUID(String username, String uuid) {
        if (username != null && uuid != null) {
            properties.setProperty(username.toLowerCase(), uuid);
            saveCache();
        }
    }
    
    /**
     * 获取用户的UUID
     * @param username 用户名
     * @return UUID，如果不存在则返回null
     */
    public String getUUID(String username) {
        if (username != null) {
            return properties.getProperty(username.toLowerCase());
        }
        return null;
    }
    
    /**
     * 检查用户是否有缓存的UUID
     * @param username 用户名
     * @return true表示有缓存的UUID，false表示没有
     */
    public boolean hasUUID(String username) {
        return getUUID(username) != null;
    }
    
    /**
     * 删除用户的UUID缓存
     * @param username 用户名
     */
    public void removeUUID(String username) {
        if (username != null) {
            properties.remove(username.toLowerCase());
            saveCache();
        }
    }
}