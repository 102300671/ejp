<%@ page language="java" contentType="image/jpeg" pageEncoding="UTF-8"%>
<%@ page import="java.io.*, java.util.*, java.awt.image.*, javax.imageio.*" %>
<%
    String username = request.getParameter("username");
    
    if (username == null || username.trim().isEmpty()) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username parameter is required");
        return;
    }
    
    String uploadPath = getServletContext().getRealPath("") + "../../files/chatroom/avatars/users/" + username + "/";
    File userDir = new File(uploadPath);
    
    if (userDir.exists() && userDir.isDirectory()) {
        File[] files = userDir.listFiles();
        
        if (files != null && files.length > 0) {
            File avatarFile = files[0];
            
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
