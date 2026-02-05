<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.io.*, java.text.SimpleDateFormat, java.util.Date" %>
<%
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    
    String level = request.getParameter("level");
    String message = request.getParameter("message");
    String timestamp = request.getParameter("timestamp");
    
    // 增强验证
    if (level == null || message == null || 
        level.trim().isEmpty() || message.trim().isEmpty()) {
        response.getWriter().write("{\"success\": false, \"error\": \"Missing or empty required parameters\"}");
        return;
    }
    
    // 清理输入
    level = level.trim().toUpperCase();
    message = message.trim().replace("\n", " ").replace("\r", " ");
    
    if (timestamp == null || timestamp.trim().isEmpty()) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        timestamp = sdf.format(new Date());
    } else {
        timestamp = timestamp.trim();
    }
    
    String logDirectory = "/var/lib/tomcat10/webapps/chat/log";
    
    String logFileName = "client_log_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
    String logFilePath = logDirectory + File.separator + logFileName;
    
    String logEntry = String.format("[%s] [%s] %s%n", timestamp, level, message);
    
    boolean success = false;
    String error = null;
    BufferedWriter bw = null;
    
    try {
        File directory = new File(logDirectory);
        
        // 检查目录是否存在且可写
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Cannot create directory: " + logDirectory);
            }
        }
        
        // 检查目录是否可写
        if (!directory.canWrite()) {
            throw new IOException("Directory not writable: " + logDirectory + 
                " (Owner: " + getFileOwner(directory) + ")");
        }
        
        // 直接写入，不需要先创建文件
        bw = new BufferedWriter(new FileWriter(logFilePath, true));
        bw.write(logEntry);
        bw.flush();
        success = true;
        
    } catch (IOException e) {
        error = "I/O Error: " + e.getMessage();
        // 记录到Tomcat日志
        System.err.println("[JSP-Logger] Error: " + e.getMessage());
        
    } catch (SecurityException e) {
        error = "Security Error: " + e.getMessage();
        System.err.println("[JSP-Logger] Security: " + e.getMessage());
        
    } finally {
        if (bw != null) {
            try { bw.close(); } catch (IOException e) { /* ignore */ }
        }
    }
    
    // 构建JSON响应
    StringBuilder json = new StringBuilder();
    json.append("{\"success\":").append(success);
    if (error != null) {
        json.append(",\"error\":\"")
            .append(error.replace("\\", "\\\\").replace("\"", "\\\""))
            .append("\"");
    }
    json.append("}");
    
    response.getWriter().write(json.toString());
%>

<%!
    // 辅助方法：获取文件所有者信息
    private String getFileOwner(File file) {
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{"stat", "-c", "%U:%G", file.getAbsolutePath()});
            BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()));
            return br.readLine();
        } catch (Exception e) {
            return "Unknown";
        }
    }
%>