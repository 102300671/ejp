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
    UUID_AUTH_FAILURE,
    CREATE_ROOM,
    EXIT_ROOM,
    LIST_ROOMS
}