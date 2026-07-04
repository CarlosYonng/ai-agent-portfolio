package com.yonng.agent.dto.knowledge;

import java.nio.file.Path;

/**
 * 文档原文件下载信息。
 *
 * <p>Service 只返回受控后的文件路径和下载名，HTTP 响应头与文件流由 API 层组装。</p>
 *
 * @param docId 文档 ID
 * @param filename 下载时展示的文件名
 * @param path 后端本机已校验存在的源文件路径
 */
public record DocumentDownloadInfo(Long docId, String filename, Path path) {
}
