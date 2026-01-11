package server.message;

public enum MessageType {
    TEXT,
    JOIN,
    LEAVE,
    SYSTEM,
    REGISTER,
    LOGIN,
    AUTH_SUCCESS,
    AUTH_FAILURE,
    UUID_AUTH,
    UUID_AUTH_SUCCESS,
    UUID_AUTH_FAILURE
}