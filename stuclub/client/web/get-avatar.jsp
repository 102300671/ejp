<%@ page language="java" contentType="image/jpeg" pageEncoding="UTF-8"%>
<%@ page import="java.io.*, java.util.*, java.awt.image.*, javax.imageio.*, java.nio.file.*" %>
<%
    String username = request.getParameter("username");
    
    if (username == null || username.trim().isEmpty()) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username parameter is required");
        return;
    }
    
    // 严格的参数白名单校验，只允许字母、数字、下划线或连字符
    if (!username.matches("^[a-zA-Z0-9_-]+$")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid username format");
        return;
    }
    
    // 构建基础路径
    String basePath = getServletContext().getRealPath("") + "../../files/chatroom/avatars/users/";
    
    // 使用 Paths.get() 和 normalize() 方法进行路径规范化
    Path userDirPath = Paths.get(basePath, username).normalize();
    
    // 确保最终路径仍在预期的基目录内
    Path normalizedBasePath = Paths.get(basePath).normalize();
    if (!userDirPath.startsWith(normalizedBasePath)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
        return;
    }
    
    File userDir = userDirPath.toFile();
    
    if (userDir.exists() && userDir.isDirectory()) {
        // 限制文件访问范围，只读取特定扩展名的文件
        File[] files = userDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lowerName = name.toLowerCase();
                return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || 
                       lowerName.endsWith(".png") || lowerName.endsWith(".gif") || 
                       lowerName.endsWith(".webp");
            }
        });
        
        if (files != null && files.length > 0) {
            File avatarFile = files[0];
            
            // 再次校验最终物理路径是否越界
            Path avatarFilePath = avatarFile.toPath().normalize();
            if (!avatarFilePath.startsWith(normalizedBasePath)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
                return;
            }
            
            String fileName = avatarFile.getName().toLowerCase();
            String contentType = "image/jpeg";
            
            if (fileName.endsWith(".png")) {
                contentType = "image/png";
            } else if (fileName.endsWith(".gif")) {
                contentType = "image/gif";
            } else if (fileName.endsWith(".webp")) {
                contentType = "image/webp";
            }
            
            response.setContentType(contentType);
            response.setHeader("Cache-Control", "public, max-age=86400");
            
            FileInputStream fis = new FileInputStream(avatarFile);
            OutputStream os = response.getOutputStream();
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            
            fis.close();
            os.flush();
            return;
        }
    }
    
    response.setContentType("image/svg+xml");
    response.setHeader("Cache-Control", "public, max-age=86400");
    
    String firstLetter = username.substring(0, 1).toUpperCase();
    String defaultAvatar = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">" +
        "<circle cx=\"50\" cy=\"50\" r=\"45\" fill=\"#4a6fa5\"/>" +
        "<text x=\"50\" y=\"60\" font-size=\"40\" text-anchor=\"middle\" fill=\"white\">" + firstLetter + "</text>" +
        "</svg>";
    
    out.print(defaultAvatar);
%>
