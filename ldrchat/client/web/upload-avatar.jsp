<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.io.*, java.util.*, org.apache.commons.fileupload.*, org.apache.commons.fileupload.disk.*, org.apache.commons.fileupload.servlet.*" %>
<%
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    String result = "{\"success\": false, \"message\": \"Unknown error\"}";

    try {
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        
        if (!isMultipart) {
            result = "{\"success\": false, \"message\": \"Not a multipart request\"}";
        } else {
            String uploadPath = getServletContext().getRealPath("") + "../../files/chatroom/avatars/users/";
            File uploadDir = new File(uploadPath);
            
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold(1024 * 1024);
            factory.setRepository(new File(System.getProperty("java.io.tmpdir")));
            
            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setSizeMax(2 * 1024 * 1024);
            
            List<FileItem> items = upload.parseRequest(request);
            String username = null;
            FileItem fileItem = null;
            
            for (FileItem item : items) {
                if (item.isFormField()) {
                    if ("username".equals(item.getFieldName())) {
                        username = item.getString("UTF-8");
                    }
                } else {
                    if (item.getFieldName().equals("avatar")) {
                        fileItem = item;
                    }
                }
            }
            
            if (username == null || username.trim().isEmpty()) {
                result = "{\"success\": false, \"message\": \"Username is required\"}";
            } else if (fileItem == null || fileItem.getSize() == 0) {
                result = "{\"success\": false, \"message\": \"No file uploaded\"}";
            } else {
                String fileName = fileItem.getName();
                String fileExtension = "";
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    fileExtension = fileName.substring(dotIndex).toLowerCase();
                }
                
                if (!fileExtension.matches("\\.(jpg|jpeg|png|gif|webp)")) {
                    result = "{\"success\": false, \"message\": \"Invalid file type. Only JPG, PNG, GIF, and WEBP are allowed\"}";
                } else {
                    String userDirPath = uploadPath + username + "/";
                    File userDir = new File(userDirPath);
                    
                    if (!userDir.exists()) {
                        userDir.mkdirs();
                    }
                    
                    File[] existingFiles = userDir.listFiles();
                    if (existingFiles != null) {
                        for (File existingFile : existingFiles) {
                            existingFile.delete();
                        }
                    }
                    
                    String newFileName = "avatar" + fileExtension;
                    File uploadedFile = new File(userDirPath + newFileName);
                    
                    fileItem.write(uploadedFile);
                    
                    result = "{\"success\": true, \"message\": \"Avatar uploaded successfully\", \"path\": \"../../files/chatroom/avatars/users/" + username + "/" + newFileName + "\"}";
                }
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
        result = "{\"success\": false, \"message\": \"Error: " + e.getMessage() + "\"}";
    }

    out.print(result);
%>
