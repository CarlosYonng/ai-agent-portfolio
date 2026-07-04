package com.yonng.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 相关配置。
 *
 * <p>把外部服务地址放进配置类，后续可以继续加入超时时间、租户开关等配置。</p>
 */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** Python AI 服务的基础地址，例如 http://localhost:8000。 */
    private String aiServiceBaseUrl;
    /** 连接 Python AI 服务的超时时间，单位毫秒。 */
    private int aiConnectTimeoutMs = 3000;
    /** 等待 Python AI 服务返回的超时时间，单位毫秒。 */
    private int aiReadTimeoutMs = 60000;
    /** 外部诊断服务地址（预留组件，当前未部署）。推送失败由 DiagnosisLogClient 降级为本地日志。 */
    private String diagnosisServiceBaseUrl = "http://localhost:8200";
    /** 诊断服务推送连接超时，单位毫秒。 */
    private int diagnosisConnectTimeoutMs = 1000;
    /** 诊断服务推送读取超时，单位毫秒。 */
    private int diagnosisReadTimeoutMs = 2000;
    /** 上传文档在本机保存的目录；生产环境建议挂载持久化卷或替换为对象存储。 */
    private String kbUploadDir = "data/uploads/kb-docs";
    /** 文档入库脚本路径。为空时会自动尝试 scripts/ 和 ../scripts/。 */
    private String kbIngestScriptPath;
    /** 触发文档入库时使用的 Python 可执行文件。 */
    private String kbIngestPython = "python3";
    /** 文档入库脚本最长等待时间，单位秒。 */
    private int kbIngestTimeoutSeconds = 180;
    /** 传给入库脚本的 MySQL DSN；为空时脚本使用自身默认值或环境变量。 */
    private String kbIngestMysqlDsn;

    public String getAiServiceBaseUrl() {
        return aiServiceBaseUrl;
    }

    public void setAiServiceBaseUrl(String aiServiceBaseUrl) {
        this.aiServiceBaseUrl = aiServiceBaseUrl;
    }

    public int getAiConnectTimeoutMs() {
        return aiConnectTimeoutMs;
    }

    public void setAiConnectTimeoutMs(int aiConnectTimeoutMs) {
        this.aiConnectTimeoutMs = aiConnectTimeoutMs;
    }

    public int getAiReadTimeoutMs() {
        return aiReadTimeoutMs;
    }

    public void setAiReadTimeoutMs(int aiReadTimeoutMs) {
        this.aiReadTimeoutMs = aiReadTimeoutMs;
    }

    public String getDiagnosisServiceBaseUrl() {
        return diagnosisServiceBaseUrl;
    }

    public void setDiagnosisServiceBaseUrl(String diagnosisServiceBaseUrl) {
        this.diagnosisServiceBaseUrl = diagnosisServiceBaseUrl;
    }

    public int getDiagnosisConnectTimeoutMs() {
        return diagnosisConnectTimeoutMs;
    }

    public void setDiagnosisConnectTimeoutMs(int diagnosisConnectTimeoutMs) {
        this.diagnosisConnectTimeoutMs = diagnosisConnectTimeoutMs;
    }

    public int getDiagnosisReadTimeoutMs() {
        return diagnosisReadTimeoutMs;
    }

    public void setDiagnosisReadTimeoutMs(int diagnosisReadTimeoutMs) {
        this.diagnosisReadTimeoutMs = diagnosisReadTimeoutMs;
    }

    public String getKbUploadDir() {
        return kbUploadDir;
    }

    public void setKbUploadDir(String kbUploadDir) {
        this.kbUploadDir = kbUploadDir;
    }

    public String getKbIngestScriptPath() {
        return kbIngestScriptPath;
    }

    public void setKbIngestScriptPath(String kbIngestScriptPath) {
        this.kbIngestScriptPath = kbIngestScriptPath;
    }

    public String getKbIngestPython() {
        return kbIngestPython;
    }

    public void setKbIngestPython(String kbIngestPython) {
        this.kbIngestPython = kbIngestPython;
    }

    public int getKbIngestTimeoutSeconds() {
        return kbIngestTimeoutSeconds;
    }

    public void setKbIngestTimeoutSeconds(int kbIngestTimeoutSeconds) {
        this.kbIngestTimeoutSeconds = kbIngestTimeoutSeconds;
    }

    public String getKbIngestMysqlDsn() {
        return kbIngestMysqlDsn;
    }

    public void setKbIngestMysqlDsn(String kbIngestMysqlDsn) {
        this.kbIngestMysqlDsn = kbIngestMysqlDsn;
    }
}
