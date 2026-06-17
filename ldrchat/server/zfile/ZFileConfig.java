package server.zfile;

public class ZFileConfig {
    private String zfileServerUrl;
    private String zfileUsername;
    private String zfilePassword;

    public ZFileConfig(String zfileServerUrl, String zfileUsername, String zfilePassword) {
        this.zfileServerUrl = zfileServerUrl;
        this.zfileUsername = zfileUsername;
        this.zfilePassword = zfilePassword;
    }

    public String getZfileServerUrl() {
        return zfileServerUrl;
    }

    public void setZfileServerUrl(String zfileServerUrl) {
        this.zfileServerUrl = zfileServerUrl;
    }

    public String getZfileUsername() {
        return zfileUsername;
    }

    public void setZfileUsername(String zfileUsername) {
        this.zfileUsername = zfileUsername;
    }

    public String getZfilePassword() {
        return zfilePassword;
    }

    public void setZfilePassword(String zfilePassword) {
        this.zfilePassword = zfilePassword;
    }
}
