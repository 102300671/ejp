// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package server.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ServiceConfig {
   private static ServiceConfig instance;
   private Properties properties = new Properties();

   private ServiceConfig() {
      this.loadConfig();
   }

   public static synchronized ServiceConfig getInstance() {
      if (instance == null) {
         instance = new ServiceConfig();
      }

      return instance;
   }

   private void loadConfig() {
      String var1 = System.getProperty("config.path", "config/service.properties");

      try {
         FileInputStream var2 = new FileInputStream(var1);

         try {
            this.properties.load(var2);
            System.out.println("服务配置加载成功: " + var1);
         } catch (Throwable var6) {
            try {
               var2.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         var2.close();
      } catch (IOException var7) {
         System.err.println("加载服务配置失败: " + var7.getMessage());
         this.loadDefaultConfig();
      }

   }

   private void loadDefaultConfig() {
      this.properties.setProperty("server.base.url", "http://localhost:8081");
      this.properties.setProperty("websocket.ssl.enabled", "false");
      this.properties.setProperty("zfile.server.url", "http://localhost:8081");
      this.properties.setProperty("zfile.username", "chatroom-system");
      this.properties.setProperty("zfile.password", "zeXvrDUDacr56Nt");
      this.properties.setProperty("onlyoffice.server.url", "http://localhost:8082");
      this.properties.setProperty("onlyoffice.api.url", "http://localhost:8082/web-apps/apps/api/documents/api.js");
      System.out.println("使用默认服务配置");
   }

   private String resolveValue(String var1) {
      if (var1 == null) {
         return null;
      } else if (var1.startsWith("${") && var1.endsWith("}")) {
         String var2 = var1.substring(2, var1.length() - 1);
         String[] var3 = var2.split(":", 2);
         String var4 = var3[0];
         String var5 = var3.length > 1 ? var3[1] : "";
         String var6 = System.getenv(var4);
         return var6 != null ? var6 : var5;
      } else {
         return var1;
      }
   }

   public String getServerBaseUrl() {
      return this.resolveValue(this.properties.getProperty("server.base.url"));
   }

   public String getZfileServerUrl() {
      return this.resolveValue(this.properties.getProperty("zfile.server.url"));
   }

   public String getZfileUsername() {
      return this.resolveValue(this.properties.getProperty("zfile.username"));
   }

   public String getZfilePassword() {
      return this.resolveValue(this.properties.getProperty("zfile.password"));
   }

   public boolean isWebSocketSslEnabled() {
      String var1 = this.resolveValue(this.properties.getProperty("websocket.ssl.enabled"));
      return "true".equalsIgnoreCase(var1);
   }

   public String getWebSocketSslKeystorePath() {
      return this.resolveValue(this.properties.getProperty("websocket.ssl.keystore.path"));
   }

   public String getWebSocketSslKeystorePassword() {
      return this.resolveValue(this.properties.getProperty("websocket.ssl.keystore.password"));
   }

   public String getWebSocketSslKeystoreType() {
      return this.resolveValue(this.properties.getProperty("websocket.ssl.keystore.type"));
   }

   public String getWebSocketSslKeyPassword() {
      return this.resolveValue(this.properties.getProperty("websocket.ssl.key.password"));
   }

   public void reload() {
      this.properties.clear();
      this.loadConfig();
   }
}
