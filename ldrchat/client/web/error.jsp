<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>ChatRoom - Error</title>
    <link rel="stylesheet" href="css/style.css">
    <style>
        .error-container {
            width: 500px;
            padding: 40px;
            background-color: white;
            border-radius: 10px;
            box-shadow: 0 0 20px rgba(0, 0, 0, 0.1);
            text-align: center;
        }
        .error-code {
            font-size: 72px;
            color: #e74c3c;
            margin: 20px 0;
        }
        .error-message {
            font-size: 24px;
            margin: 20px 0;
        }
        .error-details {
            color: #666;
            margin: 20px 0;
        }
        .back-link {
            display: inline-block;
            margin-top: 20px;
            padding: 10px 20px;
            background-color: #4a6fa5;
            color: white;
            text-decoration: none;
            border-radius: 5px;
        }
        .back-link:hover {
            background-color: #3a5a85;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="error-container">
            <h1>ChatRoom Error</h1>
            <div class="error-code">
                <%= request.getAttribute("javax.servlet.error.status_code") != null ? 
                     request.getAttribute("javax.servlet.error.status_code") : "500" %>
            </div>
            <div class="error-message">
                <%= request.getAttribute("javax.servlet.error.status_code") != null ? 
                     ("404".equals(request.getAttribute("javax.servlet.error.status_code").toString()) ? 
                      "Page Not Found" : "Internal Server Error") : "Unknown Error" %>
            </div>
            <div class="error-details">
                <% if (request.getAttribute("javax.servlet.error.message") != null) { %>
                    <%= request.getAttribute("javax.servlet.error.message") %>
                <% } else if (request.getAttribute("javax.servlet.error.exception") != null) { %>
                    <%= ((Exception)request.getAttribute("javax.servlet.error.exception")).getMessage() %>
                <% } else { %>
                    An unexpected error occurred. Please try again later.
                <% } %>
            </div>
            <a href="connect.jsp" class="back-link">Back to Connect Page</a>
        </div>
    </div>
</body>
</html>