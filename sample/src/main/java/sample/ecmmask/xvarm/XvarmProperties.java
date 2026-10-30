package sample.ecmmask.xvarm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.properties 의 xvarm.* — EDMS 의 ecm.config.* 와 1:1 */
@ConfigurationProperties("xvarm")
public class XvarmProperties {
    private String mode = "local";          // local | inzent
    private String localDir = "./data/xvarm";
    private String jarPath;                 // 고객사 xvarmapi jar (없으면 앱 클래스패스에서 찾음)
    private String ip;                      // ecm.config.ip
    private int port = 2102;                // ecm.config.port
    private String desc = "ECM Mask";       // ecm.config.desc  → asysConnectData 3번째 인자, m_descr
    private String user;                    // ecm.config.id
    private String password;                // ecm.config.pw
    private String gateway;                 // ecm.config.gateway
    private String contentClass;            // ecm.config.cclassid → m_cClassId
    private String elementClass;            // ecm.config.eclassid → m_eClassId (fullId 뒷부분)
    private String securityClass;           // ecm.config.sclassid → m_userSClass

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getLocalDir() { return localDir; }
    public void setLocalDir(String localDir) { this.localDir = localDir; }
    public String getJarPath() { return jarPath; }
    public void setJarPath(String jarPath) { this.jarPath = jarPath; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
    public String getContentClass() { return contentClass; }
    public void setContentClass(String contentClass) { this.contentClass = contentClass; }
    public String getElementClass() { return elementClass; }
    public void setElementClass(String elementClass) { this.elementClass = elementClass; }
    public String getSecurityClass() { return securityClass; }
    public void setSecurityClass(String securityClass) { this.securityClass = securityClass; }
}
